package com.blackjack.blackjackbackend.controller;

import com.blackjack.blackjackbackend.dto.ActionDTO;
import com.blackjack.blackjackbackend.dto.PlayerDTO;
import com.blackjack.blackjackbackend.dto.TableDTO;
import com.blackjack.blackjackbackend.entity.Player;
import com.blackjack.blackjackbackend.repository.GameTableRepository;
import com.blackjack.blackjackbackend.repository.PlayerRepository;
import com.blackjack.blackjackbackend.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LobbyController {

    private final PlayerRepository playerRepository;
    private final GameTableRepository tableRepository;
    private final GameService gameService;

    // login / registro
    @PostMapping("/login")
    public ResponseEntity<PlayerDTO> login(@RequestBody PlayerDTO request) {
        Player player = playerRepository.findByUsername(request.getUsername())
            .orElseGet(() -> {
                Player p = new Player();
                p.setUsername(request.getUsername());
                p.setAvatarColor(request.getAvatarColor());
                p.setBalance(100000L);
                return playerRepository.save(p);
            });

        PlayerDTO dto = new PlayerDTO();
        dto.setId(player.getId());
        dto.setUsername(player.getUsername());
        dto.setAvatarColor(player.getAvatarColor());
        dto.setBalance(player.getBalance());
        return ResponseEntity.ok(dto);
    }

    // listar mesas
    @GetMapping("/tables")
    public ResponseEntity<List<TableDTO>> getTables() {
        List<TableDTO> tables = tableRepository.findAll()
            .stream()
            .map(gameService::buildTableDTO)
            .toList();
        return ResponseEntity.ok(tables);
    }

    // unirse a mesa
    @PostMapping("/tables/{tableId}/join")
    public ResponseEntity<TableDTO> joinTable(
            @PathVariable UUID tableId,
            @RequestBody ActionDTO action) {
        TableDTO dto = gameService.joinTable(tableId, action.getPlayerId());
        return ResponseEntity.ok(dto);
    }
}