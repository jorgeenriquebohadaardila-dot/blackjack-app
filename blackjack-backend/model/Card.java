package com.blackjack.blackjackbackend.model;

public class Card {
    private Suit suit;
    private Rank rank;

    public Card() {}

    public Card(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public Suit getSuit() { return suit; }
    public void setSuit(Suit suit) { this.suit = suit; }
    public Rank getRank() { return rank; }
    public void setRank(Rank rank) { this.rank = rank; }

    public int getValue() { return rank.getValue(); }
    public boolean isAce() { return rank == Rank.ACE; }
}