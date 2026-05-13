package com.blackjack.blackjackbackend.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 30)
    private String username;

    @Column(nullable = false, length = 10)
    private String avatarColor;

    @Column(nullable = false)
    private Long balance = 100000L;

    @Column(length = 64)
    private String passwordHash;

    @Column(length = 10, columnDefinition = "varchar(10) default 'PLAYER'")
    private String role = "PLAYER";

    @Column(length = 36)
    private String sessionToken;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getAvatarColor() { return avatarColor; }
    public void setAvatarColor(String avatarColor) { this.avatarColor = avatarColor; }
    public Long getBalance() { return balance; }
    public void setBalance(Long balance) { this.balance = balance; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role != null ? role : "PLAYER"; }
    public void setRole(String role) { this.role = role; }
    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
}
