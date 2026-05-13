package com.blackjack.blackjackbackend.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tables")
public class GameTable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false)
    private Long minBet;

    @Column(nullable = false)
    private String status = "WAITING";

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getMinBet() { return minBet; }
    public void setMinBet(Long minBet) { this.minBet = minBet; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
