package com.example.elevatorbot;

import lombok.NonNull;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;

public class ElevatorReplyKeyboard extends ReplyKeyboardMarkup {

    private List<KeyboardRow> keyboard;

    @Override
    public @NonNull List<KeyboardRow> getKeyboard() {
        if (keyboard == null) {
            keyboard = List.of(new KeyboardRow(List.of(new KeyboardButton("8"), new KeyboardButton("13"))));
            super.setKeyboard(keyboard);
        }
        return keyboard;
    }
}
