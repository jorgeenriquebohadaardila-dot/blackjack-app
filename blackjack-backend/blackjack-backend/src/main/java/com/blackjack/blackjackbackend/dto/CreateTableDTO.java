package com.blackjack.blackjackbackend.dto;

import java.util.UUID;

public class CreateTableDTO {
    private String name;
    private Long minBet;
    private UUID playerId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getMinBet() { return minBet; }
    public void setMinBet(Long minBet) { this.minBet = minBet; }
    public UUID getPlayerId() { return playerId; }
    public void setPlayerId(UUID playerId) { this.playerId = playerId; }
}
