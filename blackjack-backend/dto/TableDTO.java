package com.blackjack.blackjackbackend.dto;

import java.util.List;
import java.util.UUID;

public class TableDTO {
    private UUID id;
    private String name;
    private Long minBet;
    private String status;
    private int playerCount;
    private int maxPlayers = 6;
    private List<PlayerDTO> players;
    private List<String> dealerHand;
    private Integer dealerScore;
    private int remainingCards;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getMinBet() { return minBet; }
    public void setMinBet(Long minBet) { this.minBet = minBet; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getPlayerCount() { return playerCount; }
    public void setPlayerCount(int playerCount) { this.playerCount = playerCount; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public List<PlayerDTO> getPlayers() { return players; }
    public void setPlayers(List<PlayerDTO> players) { this.players = players; }
    public List<String> getDealerHand() { return dealerHand; }
    public void setDealerHand(List<String> dealerHand) { this.dealerHand = dealerHand; }
    public Integer getDealerScore() { return dealerScore; }
    public void setDealerScore(Integer dealerScore) { this.dealerScore = dealerScore; }
    public int getRemainingCards() { return remainingCards; }
    public void setRemainingCards(int remainingCards) { this.remainingCards = remainingCards; }
}