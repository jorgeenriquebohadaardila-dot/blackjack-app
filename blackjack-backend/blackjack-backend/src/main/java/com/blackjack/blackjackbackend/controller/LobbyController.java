package com.blackjack.blackjackbackend.controller;

import com.blackjack.blackjackbackend.dto.*;
import com.blackjack.blackjackbackend.entity.GameTable;
import com.blackjack.blackjackbackend.entity.Player;
import com.blackjack.blackjackbackend.entity.Seat;
import com.blackjack.blackjackbackend.repository.GameTableRepository;
import com.blackjack.blackjackbackend.repository.PlayerRepository;
import com.blackjack.blackjackbackend.repository.SeatRepository;
import com.blackjack.blackjackbackend.service.GameService;
import com.blackjack.blackjackbackend.util.PasswordUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class LobbyController {

    private final PlayerRepository playerRepository;
    private final GameTableRepository tableRepository;
    private final SeatRepository seatRepository;
    private final GameService gameService;

    public LobbyController(PlayerRepository playerRepository,
                           GameTableRepository tableRepository,
                           SeatRepository seatRepository,
                           GameService gameService) {
        this.playerRepository = playerRepository;
        this.tableRepository = tableRepository;
        this.seatRepository = seatRepository;
        this.gameService = gameService;
    }

    /* ── Auth ──────────────────────────────────────────────── */

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDTO request) {
        if (request.getUsername() == null || request.getUsername().trim().length() < 3) {
            return ResponseEntity.badRequest().body(Map.of("error", "El usuario debe tener al menos 3 caracteres"));
        }
        if (request.getPassword() == null || request.getPassword().length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contraseña debe tener al menos 4 caracteres"));
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Las contraseñas no coinciden"));
        }
        if (playerRepository.existsByUsername(request.getUsername().trim())) {
            return ResponseEntity.status(409).body(Map.of("error", "El nombre de usuario ya está en uso"));
        }
        Player p = new Player();
        p.setUsername(request.getUsername().trim());
        p.setAvatarColor(request.getAvatarColor() != null ? request.getAvatarColor() : "#00e5a0");
        p.setPasswordHash(PasswordUtil.hash(request.getPassword()));
        p.setBalance(100000L);
        p.setRole("PLAYER");
        playerRepository.save(p);
        return ResponseEntity.status(201).body(Map.of("message", "Registro exitoso"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Usuario y contraseña requeridos"));
        }
        Optional<Player> opt = playerRepository.findByUsername(request.getUsername().trim());
        if (opt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuario o contraseña incorrectos"));
        }
        Player player = opt.get();
        if (player.getPasswordHash() == null ||
                !PasswordUtil.hash(request.getPassword()).equals(player.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuario o contraseña incorrectos"));
        }
        String token = UUID.randomUUID().toString();
        player.setSessionToken(token);
        playerRepository.save(player);

        PlayerDTO dto = toDTO(player);
        dto.setToken(token);
        return ResponseEntity.ok(dto);
    }

    /* ── Tables ────────────────────────────────────────────── */

    @GetMapping("/tables")
    public ResponseEntity<List<TableDTO>> getTables() {
        List<TableDTO> tables = tableRepository.findAll()
                .stream()
                .map(gameService::buildTableDTO)
                .toList();
        return ResponseEntity.ok(tables);
    }

    @GetMapping("/tables/{tableId}")
    public ResponseEntity<TableDTO> getTable(@PathVariable UUID tableId) {
        return tableRepository.findById(tableId)
                .map(t -> ResponseEntity.ok(gameService.buildTableDTO(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/tables/{tableId}/reset")
    public ResponseEntity<TableDTO> resetTableRest(@PathVariable UUID tableId) {
        return tableRepository.findById(tableId)
                .map(t -> {
                    gameService.resetTable(tableId);
                    GameTable fresh = tableRepository.findById(tableId).orElseThrow();
                    return ResponseEntity.ok(gameService.buildTableDTO(fresh));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/tables")
    public ResponseEntity<?> createTable(@RequestBody CreateTableDTO request) {
        if (request.getPlayerId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "playerId requerido"));
        }
        Player requester = playerRepository.findById(request.getPlayerId()).orElse(null);
        if (requester == null || !"ADMIN".equals(requester.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "Solo el administrador puede crear mesas"));
        }
        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre de la mesa es requerido"));
        }
        String name = request.getName().toUpperCase().trim();
        if (tableRepository.existsByNameIgnoreCase(name)) {
            return ResponseEntity.status(409).body(Map.of("error", "Ya existe una mesa con ese nombre"));
        }
        GameTable table = new GameTable();
        table.setName(name);
        table.setMinBet(request.getMinBet() != null ? request.getMinBet() : 1000L);
        tableRepository.save(table);
        return ResponseEntity.status(201).body(gameService.buildTableDTO(table));
    }

    @DeleteMapping("/tables/{tableId}")
    public ResponseEntity<?> deleteTable(
            @PathVariable UUID tableId,
            @RequestParam UUID playerId) {
        Player requester = playerRepository.findById(playerId).orElse(null);
        if (requester == null || !"ADMIN".equals(requester.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "Solo el administrador puede eliminar mesas"));
        }
        GameTable table = tableRepository.findById(tableId).orElse(null);
        if (table == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Mesa no encontrada"));
        }
        List<Seat> seats = seatRepository.findByTable(table);
        seatRepository.deleteAll(seats);
        tableRepository.delete(table);
        return ResponseEntity.ok(Map.of("message", "Mesa eliminada correctamente"));
    }

    @PostMapping("/tables/{tableId}/join")
    public ResponseEntity<?> joinTable(
            @PathVariable UUID tableId,
            @RequestBody ActionDTO action) {
        try {
            TableDTO dto = gameService.joinTable(tableId, action.getPlayerId());
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            System.out.println("[joinTable] Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /* ── Helpers ───────────────────────────────────────────── */

    private PlayerDTO toDTO(Player player) {
        PlayerDTO dto = new PlayerDTO();
        dto.setId(player.getId());
        dto.setUsername(player.getUsername());
        dto.setAvatarColor(player.getAvatarColor());
        dto.setBalance(player.getBalance());
        dto.setRole(player.getRole());
        return dto;
    }
}
