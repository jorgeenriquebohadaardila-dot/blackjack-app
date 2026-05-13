package com.blackjack.blackjackbackend.repository;

import com.blackjack.blackjackbackend.entity.GameTable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface GameTableRepository extends JpaRepository<GameTable, UUID> {
    boolean existsByNameIgnoreCase(String name);
}
