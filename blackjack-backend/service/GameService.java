package com.blackjack.blackjackbackend.service;

import com.blackjack.blackjackbackend.dto.*;
import com.blackjack.blackjackbackend.entity.*;
import com.blackjack.blackjackbackend.model.*;
import com.blackjack.blackjackbackend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GameService {

    private final PlayerRepository playerRepository;
    private final GameTableRepository tableRepository;
    private final SeatRepository seatRepository;
    private final SimpMessagingTemplate messaging;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Un shoe por mesa, guardado en memoria
    private final Map<UUID, Shoe> shoes = new HashMap<>();
    private final Map<UUID, List<Card>> dealerHands = new HashMap<>();

    // ── calcular puntaje de una mano ──
    public int calculateScore(List<Card> hand) {
        int score = 0;
        int aces = 0;
        for (Card card : hand) {
            score += card.getValue();
            if (card.isAce()) aces++;
        }
        while (score > 21 && aces > 0) {
            score -= 10;
            aces--;
        }
        return score;
    }

    // ── obtener o crear shoe de una mesa ──
    private Shoe getShoe(UUID tableId) {
        return shoes.computeIfAbsent(tableId, k -> new Shoe());
    }

    // ── unirse a mesa ──
    public TableDTO joinTable(UUID tableId, UUID playerId) {
        GameTable table = tableRepository.findById(tableId).orElseThrow();
        Player player = playerRepository.findById(playerId).orElseThrow();

        int count = seatRepository.countByTable(table);
        if (count >= 6) throw new RuntimeException("Mesa llena");

        // verificar si ya está sentado
        Optional<Seat> existing = seatRepository.findByTableAndPlayer(table, player);
        if (existing.isEmpty()) {
            Seat seat = new Seat();
            seat.setTable(table);
            seat.setPlayer(player);
            seat.setSeatNumber(count + 1);
            seat.setStatus("WAITING");
            seatRepository.save(seat);
        }

        TableDTO dto = buildTableDTO(table);
        messaging.convertAndSend("/topic/table/" + tableId, dto);
        return dto;
    }

    // ── colocar apuesta ──
    public void placeBet(UUID tableId, UUID playerId, Long bet) {
        GameTable table = tableRepository.findById(tableId).orElseThrow();
        Player player = playerRepository.findById(playerId).orElseThrow();

        if (bet < table.getMinBet()) throw new RuntimeException("Apuesta menor al mínimo");
        if (bet > player.getBalance()) throw new RuntimeException("Saldo insuficiente");
        if (bet % 50 != 0) throw new RuntimeException("La apuesta debe ser múltiplo de $50");

        Seat seat = seatRepository.findByTableAndPlayer(table, player).orElseThrow();
        seat.setBet(bet);
        seat.setStatus("BETTING");
        seatRepository.save(seat);

        // si todos apostaron, iniciar ronda
        List<Seat> seats = seatRepository.findByTable(table);
        boolean allBet = seats.stream().allMatch(s -> s.getBet() > 0);
        if (allBet) startRound(table);
        else messaging.convertAndSend("/topic/table/" + tableId, buildTableDTO(table));
    }

    // ── iniciar ronda: repartir cartas ──
    private void startRound(GameTable table) {
        Shoe shoe = getShoe(table.getId());
        List<Seat> seats = seatRepository.findByTable(table);

        // dealer recibe 2 cartas
        List<Card> dealerHand = new ArrayList<>();
        dealerHand.add(shoe.deal());
        dealerHand.add(shoe.deal());
        dealerHands.put(table.getId(), dealerHand);

        // cada jugador recibe 2 cartas
        for (Seat seat : seats) {
            List<Card> hand = new ArrayList<>();
            hand.add(shoe.deal());
            hand.add(shoe.deal());
            int score = calculateScore(hand);
            seat.setHand(cardsToJson(hand));
            seat.setScore(score);
            seat.setStatus(score == 21 ? "BLACKJACK" : "PLAYING");
            seatRepository.save(seat);
        }

        table.setStatus("PLAYING");
        tableRepository.save(table);

        TableDTO dto = buildTableDTO(table);
        messaging.convertAndSend("/topic/table/" + table.getId(), dto);
    }

    // ── acción del jugador: HIT ──
    public void hit(UUID tableId, UUID playerId) {
        GameTable table = tableRepository.findById(tableId).orElseThrow();
        Player player = playerRepository.findById(playerId).orElseThrow();
        Seat seat = seatRepository.findByTableAndPlayer(table, player).orElseThrow();

        if (!seat.getStatus().equals("PLAYING")) return;

        Shoe shoe = getShoe(tableId);
        List<Card> hand = jsonToCards(seat.getHand());
        hand.add(shoe.deal());
        int score = calculateScore(hand);
        seat.setHand(cardsToJson(hand));
        seat.setScore(score);

        if (score > 21) seat.setStatus("BUST");
        else if (score == 21) seat.setStatus("STAND");

        seatRepository.save(seat);

        checkRoundEnd(table);
    }

    // ── acción del jugador: STAND ──
    public void stand(UUID tableId, UUID playerId) {
        GameTable table = tableRepository.findById(tableId).orElseThrow();
        Player player = playerRepository.findById(playerId).orElseThrow();
        Seat seat = seatRepository.findByTableAndPlayer(table, player).orElseThrow();

        seat.setStatus("STAND");
        seatRepository.save(seat);

        checkRoundEnd(table);
    }

    // ── acción del jugador: DOUBLE ──
    public void doubleDown(UUID tableId, UUID playerId) {
        GameTable table = tableRepository.findById(tableId).orElseThrow();
        Player player = playerRepository.findById(playerId).orElseThrow();
        Seat seat = seatRepository.findByTableAndPlayer(table, player).orElseThrow();

        if (!seat.getStatus().equals("PLAYING")) return;
        if (seat.getBet() * 2 > player.getBalance()) throw new RuntimeException("Saldo insuficiente");

        seat.setBet(seat.getBet() * 2);

        Shoe shoe = getShoe(tableId);
        List<Card> hand = jsonToCards(seat.getHand());
        hand.add(shoe.deal());
        int score = calculateScore(hand);
        seat.setHand(cardsToJson(hand));
        seat.setScore(score);
        seat.setStatus(score > 21 ? "BUST" : "STAND");

        seatRepository.save(seat);
        checkRoundEnd(table);
    }

    // ── verificar si todos terminaron ──
    private void checkRoundEnd(GameTable table) {
        List<Seat> seats = seatRepository.findByTable(table);
        boolean allDone = seats.stream().allMatch(s ->
            s.getStatus().equals("STAND") ||
            s.getStatus().equals("BUST") ||
            s.getStatus().equals("BLACKJACK")
        );

        TableDTO dto = buildTableDTO(table);
        messaging.convertAndSend("/topic/table/" + table.getId(), dto);

        if (allDone) dealerPlay(table);
    }

    // ── dealer bot ──
    private void dealerPlay(GameTable table) {
        Shoe shoe = getShoe(table.getId());
        List<Card> dealerHand = dealerHands.getOrDefault(table.getId(), new ArrayList<>());

        // dealer pide carta mientras tenga 16 o menos
        while (calculateScore(dealerHand) <= 16) {
            dealerHand.add(shoe.deal());
        }
        dealerHands.put(table.getId(), dealerHand);
        int dealerScore = calculateScore(dealerHand);

        // calcular resultados y pagar
        List<Seat> seats = seatRepository.findByTable(table);
        for (Seat seat : seats) {
            Player player = seat.getPlayer();
            long bet = seat.getBet();
            int playerScore = seat.getScore();
            String result;

            if (seat.getStatus().equals("BUST")) {
                player.setBalance(player.getBalance() - bet);
                result = "BUST";
            } else if (seat.getStatus().equals("BLACKJACK") && dealerScore != 21) {
                long payout = (long) (bet * 1.5);
                player.setBalance(player.getBalance() + payout);
                result = "BLACKJACK";
            } else if (dealerScore > 21) {
                player.setBalance(player.getBalance() + bet);
                result = "DEALER_BUST";
            } else if (playerScore > dealerScore) {
                player.setBalance(player.getBalance() + bet);
                result = "WIN";
            } else if (playerScore == dealerScore) {
                result = "PUSH";
            } else {
                player.setBalance(player.getBalance() - bet);
                result = "LOSE";
            }

            seat.setStatus(result);
            playerRepository.save(player);
            seatRepository.save(seat);
        }

        table.setStatus("FINISHED");
        tableRepository.save(table);

        TableDTO dto = buildTableDTO(table);
        dto.setDealerHand(cardsToStringList(dealerHand));
        dto.setDealerScore(dealerScore);
        messaging.convertAndSend("/topic/table/" + table.getId() + "/result", dto);
    }

    // ── resetear mesa para nueva ronda ──
    public void resetTable(UUID tableId) {
        GameTable table = tableRepository.findById(tableId).orElseThrow();
        List<Seat> seats = seatRepository.findByTable(table);
        for (Seat seat : seats) {
            seat.setBet(0L);
            seat.setHand("[]");
            seat.setScore(0);
            seat.setStatus("WAITING");
            seatRepository.save(seat);
        }
        dealerHands.remove(tableId);
        table.setStatus("WAITING");
        tableRepository.save(table);
        messaging.convertAndSend("/topic/table/" + tableId, buildTableDTO(table));
    }

    // ── construir DTO de la mesa ──
    public TableDTO buildTableDTO(GameTable table) {
        List<Seat> seats = seatRepository.findByTable(table);
        List<PlayerDTO> playerDTOs = seats.stream().map(seat -> {
            PlayerDTO p = new PlayerDTO();
            p.setId(seat.getPlayer().getId());
            p.setUsername(seat.getPlayer().getUsername());
            p.setAvatarColor(seat.getPlayer().getAvatarColor());
            p.setBalance(seat.getPlayer().getBalance());
            p.setBet(seat.getBet());
            p.setScore(seat.getScore());
            p.setHand(seat.getHand());
            p.setStatus(seat.getStatus());
            p.setSeatNumber(seat.getSeatNumber());
            return p;
        }).toList();

        TableDTO dto = new TableDTO();
        dto.setId(table.getId());
        dto.setName(table.getName());
        dto.setMinBet(table.getMinBet());
        dto.setStatus(table.getStatus());
        dto.setPlayerCount(seats.size());
        dto.setPlayers(playerDTOs);
        dto.setRemainingCards(getShoe(table.getId()).remaining());

        List<Card> dealerHand = dealerHands.getOrDefault(table.getId(), new ArrayList<>());
        dto.setDealerHand(cardsToStringList(dealerHand));
        dto.setDealerScore(dealerHand.isEmpty() ? 0 : calculateScore(dealerHand));

        return dto;
    }

    // ── helpers de serialización ──
    private String cardsToJson(List<Card> cards) {
        try {
            return objectMapper.writeValueAsString(cards);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Card> jsonToCards(String json) {
        try {
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Card.class));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<String> cardsToStringList(List<Card> cards) {
        return cards.stream()
            .map(c -> c.getRank().name() + "_" + c.getSuit().name())
            .toList();
    }
}