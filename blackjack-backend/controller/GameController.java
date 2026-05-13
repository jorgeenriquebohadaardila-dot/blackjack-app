package com.blackjack.blackjackbackend.controller;

import com.blackjack.blackjackbackend.dto.ActionDTO;
import com.blackjack.blackjackbackend.dto.TableDTO;
import com.blackjack.blackjackbackend.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @MessageMapping("/place-bet")
    public void placeBet(ActionDTO action) {
        gameService.placeBet(action.getTableId(), action.getPlayerId(), action.getBet());
    }

    @MessageMapping("/hit")
    public void hit(ActionDTO action) {
        gameService.hit(action.getTableId(), action.getPlayerId());
    }

    @MessageMapping("/stand")
    public void stand(ActionDTO action) {
        gameService.stand(action.getTableId(), action.getPlayerId());
    }

    @MessageMapping("/double")
    public void doubleDown(ActionDTO action) {
        gameService.doubleDown(action.getTableId(), action.getPlayerId());
    }

    @MessageMapping("/reset")
    public void reset(ActionDTO action) {
        gameService.resetTable(action.getTableId());
    }
}
