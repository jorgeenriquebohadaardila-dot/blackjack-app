package com.blackjack.blackjackbackend.dto;

import java.util.UUID;

public class PlayerDTO {
    private UUID id;
    private String username;
    private String avatarColor;
    private Long balance;
    private String status;
    private Long bet;
    private Integer score;
    private String hand;
    private Integer seatNumber;
    private String role;
    private String token;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getAvatarColor() { return avatarColor; }
    public void setAvatarColor(String avatarColor) { this.avatarColor = avatarColor; }
    public Long getBalance() { return balance; }
    public void setBalance(Long balance) { this.balance = balance; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getBet() { return bet; }
    public void setBet(Long bet) { this.bet = bet; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getHand() { return hand; }
    public void setHand(String hand) { this.hand = hand; }
    public Integer getSeatNumber() { return seatNumber; }
    public void setSeatNumber(Integer seatNumber) { this.seatNumber = seatNumber; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
