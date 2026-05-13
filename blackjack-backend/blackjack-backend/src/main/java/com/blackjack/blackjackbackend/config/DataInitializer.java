package com.blackjack.blackjackbackend.config;

import com.blackjack.blackjackbackend.entity.Player;
import com.blackjack.blackjackbackend.repository.PlayerRepository;
import com.blackjack.blackjackbackend.util.PasswordUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final PlayerRepository playerRepository;

    public DataInitializer(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Override
    public void run(String... args) {
        if (!playerRepository.existsByUsername("admin")) {
            Player admin = new Player();
            admin.setUsername("admin");
            admin.setAvatarColor("#f59e0b");
            admin.setPasswordHash(PasswordUtil.hash("admin123"));
            admin.setRole("ADMIN");
            admin.setBalance(9_999_999L);
            playerRepository.save(admin);
            System.out.println("[DataInitializer] Admin user created: admin / admin123");
        }
    }
}
