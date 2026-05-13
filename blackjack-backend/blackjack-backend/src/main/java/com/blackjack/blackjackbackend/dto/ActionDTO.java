package com.blackjack.blackjackbackend.dto;
import java.util.UUID;
public class ActionDTO {
    private UUID playerId;
    private UUID tableId;
    private String action;
    private Long bet;
    public UUID getPlayerId() { return playerId; }
    public void setPlayerId(UUID playerId) { this.playerId = playerId; }
    public UUID getTableId() { return tableId; }
    public void setTableId(UUID tableId) { this.tableId = tableId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getBet() { return bet; }
    public void setBet(Long bet) { this.bet = bet; }
}
