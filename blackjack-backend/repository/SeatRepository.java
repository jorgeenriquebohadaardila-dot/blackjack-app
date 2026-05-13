package com.blackjack.blackjackbackend.repository;

import com.blackjack.blackjackbackend.entity.Seat;
import com.blackjack.blackjackbackend.entity.GameTable;
import com.blackjack.blackjackbackend.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
    List<Seat> findByTable(GameTable table);
    Optional<Seat> findByTableAndPlayer(GameTable table, Player player);
    int countByTable(GameTable table);
    Optional<Seat> findByTableAndSeatNumber(GameTable table, Integer seatNumber);
}