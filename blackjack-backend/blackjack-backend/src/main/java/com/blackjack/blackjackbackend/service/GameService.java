package com.blackjack.blackjackbackend.service;

import com.blackjack.blackjackbackend.dto.*;
import com.blackjack.blackjackbackend.entity.*;
import com.blackjack.blackjackbackend.model.*;
import com.blackjack.blackjackbackend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class GameService {

    /** Pausa entre manos (segundos): tiempo para ver resultado y elegir nueva apuesta en /apuesta. */
    private static final int ROUND_BREAK_SECONDS = 12;

    private static final ScheduledExecutorService ROUND_RESET =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bj-round-reset");
            t.setDaemon(true);
            return t;
        });

    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> pendingRoundResets = new ConcurrentHashMap<>();

    private final PlayerRepository playerRepository;
    private final GameTableRepository tableRepository;
    private final SeatRepository seatRepository;
    private final SimpMessagingTemplate messaging;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<UUID, Shoe> shoes = new HashMap<>();
    private final Map<UUID, List<Card>> dealerHands = new HashMap<>();

    public GameService(PlayerRepository playerRepository,
                       GameTableRepository tableRepository,
                       SeatRepository seatRepository,
                       SimpMessagingTemplate messaging) {
        this.playerRepository = playerRepository;
        this.tableRepository = tableRepository;
        this.seatRepository = seatRepository;
        this.messaging = messaging;
    }

    public int calculateScore(List<Card> hand) {
        int score = 0;
        int aces = 0;
        for (Card card : hand) {
            score += card.getValue();
            if (card.isAce()) aces++;
        }
        while (score > 21 && aces > 0) { score -= 10; aces--; }
        return score;
    }

    private Shoe getShoe(UUID tableId) {
        return shoes.computeIfAbsent(tableId, k -> new Shoe());
    }

    public TableDTO joinTable(UUID tableId, UUID playerId) {
        GameTable table = tableRepository.findById(tableId)
            .orElseThrow(() -> new RuntimeException("Mesa no encontrada: " + tableId));
        Player player = playerRepository.findById(playerId)
            .orElseThrow(() -> new RuntimeException("Jugador no encontrado: " + playerId));
        int count = seatRepository.countByTable(table);
        if (count >= 6) throw new RuntimeException("Mesa llena");
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

    public void placeBet(UUID tableId, UUID playerId, Long bet) {
        GameTable table = tableRepository.findById(tableId).orElseThrow();
        if ("PLAYING".equals(table.getStatus())) {
            throw new RuntimeException("La mano sigue en juego");
        }
        if ("FINISHED".equals(table.getStatus())) {
            resetTable(tableId);
            table = tableRepository.findById(tableId).orElseThrow();
        }
        Player player = playerRepository.findById(playerId).orElseThrow();
        if (bet == null || bet < table.getMinBet()) throw new RuntimeException("Apuesta menor al minimo");
        if (bet > player.getBalance()) throw new RuntimeException("Saldo insuficiente");
        Seat seat = seatRepository.findByTableAndPlayer(table, player)
            .orElseThrow(() -> new RuntimeException("Jugador no encontrado en la mesa"));

        seat.setBet(bet);
        seat.setStatus("BETTING");
        seatRepository.save(seat);
        List<Seat> seats = seatRepository.findByTable(table);
        boolean allBet = seats.stream().allMatch(s -> s.getBet() > 0 && s.getStatus().equals("BETTING"));
        if (allBet) startRound(table);
        else messaging.convertAndSend("/topic/table/" + tableId, buildTableDTO(table));
    }

    private void startRound(GameTable table) {
        Shoe shoe = getShoe(table.getId());
        List<Seat> seats = seatRepository.findByTable(table);
        List<Card> dealerHand = new ArrayList<>();
        dealerHand.add(shoe.deal());
        dealerHand.add(shoe.deal());
        dealerHands.put(table.getId(), dealerHand);
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
        messaging.convertAndSend("/topic/table/" + table.getId(), buildTableDTO(table));
    }

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

    public void stand(UUID tableId, UUID playerId) {
        GameTable table = tableRepository.findById(tableId).orElseThrow();
        Player player = playerRepository.findById(playerId).orElseThrow();
        Seat seat = seatRepository.findByTableAndPlayer(table, player).orElseThrow();
        seat.setStatus("STAND");
        seatRepository.save(seat);
        checkRoundEnd(table);
    }

    public void doubleDown(UUID tableId, UUID playerId) {
        GameTable table = tableRepository.findById(tableId).orElseThrow();
        Player player = playerRepository.findById(playerId).orElseThrow();
        Seat seat = seatRepository.findByTableAndPlayer(table, player).orElseThrow();
        if (!seat.getStatus().equals("PLAYING")) return;
        List<Card> handBefore = jsonToCards(seat.getHand());
        if (handBefore.size() != 2) return;
        long originalBet = seat.getBet();
        if (originalBet * 2 > player.getBalance()) return;
        seat.setBet(originalBet * 2);
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

    private void checkRoundEnd(GameTable table) {
        List<Seat> seats = seatRepository.findByTable(table);
        boolean allDone = seats.stream().allMatch(s ->
            s.getStatus().equals("STAND") ||
            s.getStatus().equals("BUST") ||
            s.getStatus().equals("BLACKJACK"));
        messaging.convertAndSend("/topic/table/" + table.getId(), buildTableDTO(table));
        if (allDone) dealerPlay(table);
    }

    private void dealerPlay(GameTable table) {
        Shoe shoe = getShoe(table.getId());
        List<Card> dealerHand = dealerHands.getOrDefault(table.getId(), new ArrayList<>());
        while (calculateScore(dealerHand) <= 16) dealerHand.add(shoe.deal());
        dealerHands.put(table.getId(), dealerHand);
        int dealerScore = calculateScore(dealerHand);
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
                player.setBalance(player.getBalance() + (long)(bet * 1.5));
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
        schedulePostRoundReset(table.getId());
    }

    private void schedulePostRoundReset(UUID tableId) {
        ScheduledFuture<?> previous = pendingRoundResets.remove(tableId);
        if (previous != null) previous.cancel(false);
        ScheduledFuture<?> future = ROUND_RESET.schedule(
            () -> resetTable(tableId),
            ROUND_BREAK_SECONDS,
            TimeUnit.SECONDS
        );
        pendingRoundResets.put(tableId, future);
    }

    public void resetTable(UUID tableId) {
        ScheduledFuture<?> pending = pendingRoundResets.remove(tableId);
        if (pending != null && !pending.isDone()) pending.cancel(false);
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
            try {
                List<Card> playerCards = jsonToCards(seat.getHand());
                p.setHand(objectMapper.writeValueAsString(cardsToStringList(playerCards)));
            } catch (Exception e) {
                p.setHand("[]");
            }
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
        if ("PLAYING".equals(table.getStatus()) && dealerHand.size() >= 2) {
            List<String> hiddenHand = new ArrayList<>();
            hiddenHand.add(cardToCompact(dealerHand.get(0)));
            hiddenHand.add("XX");
            dto.setDealerHand(hiddenHand);
            dto.setDealerScore(dealerHand.get(0).getValue());
        } else {
            dto.setDealerHand(cardsToStringList(dealerHand));
            dto.setDealerScore(dealerHand.isEmpty() ? 0 : calculateScore(dealerHand));
        }
        return dto;
    }

    private String cardsToJson(List<Card> cards) {
        try { return objectMapper.writeValueAsString(cards); } catch (Exception e) { return "[]"; }
    }

    private List<Card> jsonToCards(String json) {
        try {
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Card.class));
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private String cardToCompact(Card card) {
        String r = switch (card.getRank()) {
            case ACE -> "A"; case TWO -> "2"; case THREE -> "3"; case FOUR -> "4";
            case FIVE -> "5"; case SIX -> "6"; case SEVEN -> "7"; case EIGHT -> "8";
            case NINE -> "9"; case TEN -> "10"; case JACK -> "J"; case QUEEN -> "Q"; case KING -> "K";
        };
        String s = switch (card.getSuit()) {
            case HEARTS -> "H"; case DIAMONDS -> "D"; case CLUBS -> "C"; case SPADES -> "S";
        };
        return r + s;
    }

    private List<String> cardsToStringList(List<Card> cards) {
        return cards.stream().map(this::cardToCompact).toList();
    }
}
