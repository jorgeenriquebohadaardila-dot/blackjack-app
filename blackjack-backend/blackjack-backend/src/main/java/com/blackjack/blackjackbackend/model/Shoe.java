package com.blackjack.blackjackbackend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Shoe {

    private List<Card> cards = new ArrayList<>();

    public Shoe() {
        shuffle();
    }

    public void shuffle() {
        cards.clear();
        // 6 barajas
        for (int i = 0; i < 6; i++) {
            for (Suit suit : Suit.values()) {
                for (Rank rank : Rank.values()) {
                    cards.add(new Card(suit, rank));
                }
            }
        }
        Collections.shuffle(cards);
    }

    public Card deal() {
        if (cards.isEmpty() || needsReshuffle()) {
            shuffle();
        }
        return cards.remove(0);
    }

    // reshuffle cuando queda menos del 25%
    public boolean needsReshuffle() {
        return cards.size() < 78;
    }

    public int remaining() {
        return cards.size();
    }
}