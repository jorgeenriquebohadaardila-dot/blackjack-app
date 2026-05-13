package com.blackjack.blackjackbackend.dto;
import java.util.List;
public class GameResultDTO {
    private String message;
    private Long payout;
    private List<PlayerDTO> players;
    private List<String> dealerHand;
    private Integer dealerScore;
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getPayout() { return payout; }
    public void setPayout(Long payout) { this.payout = payout; }
    public List<PlayerDTO> getPlayers() { return players; }
    public void setPlayers(List<PlayerDTO> players) { this.players = players; }
    public List<String> getDealerHand() { return dealerHand; }
    public void setDealerHand(List<String> dealerHand) { this.dealerHand = dealerHand; }
    public Integer getDealerScore() { return dealerScore; }
    public void setDealerScore(Integer dealerScore) { this.dealerScore = dealerScore; }
}
