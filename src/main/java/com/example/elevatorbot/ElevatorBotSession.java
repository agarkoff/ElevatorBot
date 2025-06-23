package com.example.elevatorbot;

import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.generics.BotOptions;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.meta.generics.LongPollingBot;

@Slf4j
public class ElevatorBotSession implements BotSession {

    public ElevatorBotSession() {
        System.out.println();
    }

    @Override
    public void setOptions(BotOptions options) {
        log.info("options setted = {}", options);
    }

    @Override
    public void setToken(String token) {
        log.info("token setted = {}", token);
    }

    @Override
    public void setCallback(LongPollingBot callback) {
        log.info("callback setted");
    }

    @Override
    public void start() {
        log.info("TelegramAPI started. Look for messages");
    }

    @Override
    public void stop() {
        log.info("TelegramAPI stopped");
    }

    @Override
    public boolean isRunning() {
        return false;
    }
}
