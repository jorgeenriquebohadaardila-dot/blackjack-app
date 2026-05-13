package com.blackjack.blackjackbackend.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "table_id")
    private GameTable table;

    @ManyToOne
    @JoinColumn(name = "player_id")
    private Player player;

    @Column
    private Long bet = 0L;

    @Column(columnDefinition = "text")
    private String hand = "[]";

    @Column
    private Integer score = 0;

    @Column
    private String status = "WAITING";

    @Column
    private Integer seatNumber;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public GameTable getTable() { return table; }
    public void setTable(GameTable table) { this.table = table; }
    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }
    public Long getBet() { return bet; }
    public void setBet(Long bet) { this.bet = bet; }
    public String getHand() { return hand; }
    public void setHand(String hand) { this.hand = hand; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSeatNumber() { return seatNumber; }
    public void setSeatNumber(Integer seatNumber) { this.seatNumber = seatNumber; }
}