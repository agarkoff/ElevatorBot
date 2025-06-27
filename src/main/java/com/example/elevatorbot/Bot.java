package com.example.elevatorbot;

import com.example.elevatorbot.enums.State;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class Bot extends TelegramLongPollingBot {

    private final int RECONNECT_PAUSE = 10000;

    private final String botUsername;
    private final RestTemplate restTemplate;

    private final Map<Long, State> chatStates = new ConcurrentHashMap<>();
    private final Map<Long, String> chatPhones = new ConcurrentHashMap<>();

    private final Map<String, String> floorMapping;
    private final String objectName; // Название объекта управления (этаж/ворота)

    public Bot(String botToken, String botUsername, RestTemplate restTemplate, String objectName) {
        super(botToken);
        this.botUsername = botUsername;
        this.restTemplate = restTemplate;
        this.objectName = objectName != null ? objectName : "этаж"; // По умолчанию "этаж"
        this.floorMapping = loadFloorMapping();
    }

    private Map<String, String> loadFloorMapping() {
        Properties props = new Properties();
        File floorsFile = new File("floors.txt");

        try (FileInputStream fis = new FileInputStream(floorsFile);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            props.load(reader);
            log.info("Загружен файл конфигурации: {}", floorsFile.getAbsolutePath());

            Map<String, String> mapping = new HashMap<>();
            for (String key : props.stringPropertyNames()) {
                mapping.put(key, props.getProperty(key));
                log.info("Загружен мапинг: {} {} -> реле {}", objectName, key, props.getProperty(key));
            }

            if (mapping.isEmpty()) {
                log.error("Файл конфигурации пуст!");
                // Возвращаем дефолтный мапинг
                return Map.of("8", "0", "13", "1");
            }

            return mapping;
        } catch (IOException e) {
            log.error("Ошибка чтения файла конфигурации: {}. Используется дефолтный мапинг.", e.getMessage());
            // Возвращаем дефолтный мапинг в случае ошибки
            return Map.of("8", "0", "13", "1");
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        final Long chatId = update.getMessage().getChatId();
        final State state = chatStates.computeIfAbsent(chatId, k -> State.WAIT_AUTHED);
        switch (state) {
            case WAIT_AUTHED:
                sendAuth(chatId);
                break;
            case AUTH:
                auth(chatId, update.getMessage().getContact().getPhoneNumber());
                break;
            case WAIT_SELECT:
                sendSelect(chatId);
                break;
            case SELECT:
                elevator(chatId, update.getMessage().getText());
        }
    }

    private void sendAuth(Long chatId) {
        log.info("send auth for chatId = {}", chatId);

        SendMessage message = new SendMessage(chatId + "", "Отправьте номер вашего телефона для авторизации");

        ReplyKeyboardMarkup replyKeyboardMarkup = getAuthReplyKeyboardMarkup();
        message.setReplyMarkup(replyKeyboardMarkup);

        chatStates.put(chatId, State.AUTH);

        send(chatId, message);
    }

    private void auth(Long chatId, String phone) {
        log.info("auth for chatId = {}, {}", chatId, phone);
        if (!checkCode(phone)) {
            log.error("Ошибка авторизации для chatId = {}", chatId);

            SendMessage message = new SendMessage(chatId + "", "Вы не подключены к системе");
            send(chatId, message);
            sendAuth(chatId);

            chatStates.put(chatId, State.AUTH);
            return;
        }
        chatStates.put(chatId, State.WAIT_SELECT);
        chatPhones.put(chatId, phone);
        sendSelect(chatId);
    }

    private void sendSelect(Long chatId) {
        log.info("send select for chatId = {}", chatId);

        String messageText = String.format("Выберите %s", objectName);
        SendMessage message = new SendMessage(chatId + "", messageText);

        ReplyKeyboardMarkup replyKeyboardMarkup = getFloorsReplyKeyboardMarkup();
        message.setReplyMarkup(replyKeyboardMarkup);

        chatStates.put(chatId, State.SELECT);

        send(chatId, message);
    }

    private void send(Long chatId, SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения в чат {}: {}", chatId, e.getMessage());
        }
    }

    private void elevator(Long chatId, String floor) {
        log.info("select for chatId = {}, {} = {}", chatId, objectName, floor);
        String relay = floorMapping.get(floor);

        if (relay == null) {
            log.error("{} {} не найден в конфигурации", objectName, floor);
            String errorMessage = String.format("%s не найден", capitalize(objectName));
            SendMessage message = new SendMessage(chatId + "", errorMessage);
            send(chatId, message);
            sendSelect(chatId);
            return;
        }

        Long response = restTemplate.postForObject(
                "/post?code={code}&relay={relay}",
                null,
                Long.class,
                Map.of("code", chatPhones.get(chatId), "relay", relay));

        Objects.requireNonNull(response, "Ошибка отправки запроса");

        String text;
        if (response == 2) {
            text = "Команда отправлена";
        } else if (response >= 300) {
            long delta = response - 300L;
            text = String.format("Слишком часто, ждите %d секунд", delta);
        } else {
            text = "Команда не отправлена";
        }

        SendMessage message = new SendMessage(chatId + "", text);
        send(chatId, message);
        sendSelect(chatId);

        chatStates.put(chatId, State.SELECT);
    }

    private static ReplyKeyboardMarkup getAuthReplyKeyboardMarkup() {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup(new ArrayList<>());

        KeyboardButton keyboardButton = new KeyboardButton("Авторизация");
        keyboardButton.setRequestContact(true);
        replyKeyboardMarkup.getKeyboard().add(new KeyboardRow(List.of(keyboardButton)));

        return replyKeyboardMarkup;
    }

    private ReplyKeyboardMarkup getFloorsReplyKeyboardMarkup() {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup(new ArrayList<>());

        // Создаем кнопки динамически на основе загруженного мапинга
        List<String> floors = new ArrayList<>(floorMapping.keySet());

        List<KeyboardButton> buttons = floors.stream()
                .map(KeyboardButton::new)
                .collect(Collectors.toList());

        // Группируем кнопки по 2-3 в ряд
        for (int i = 0; i < buttons.size(); i += 3) {
            int end = Math.min(i + 3, buttons.size());
            replyKeyboardMarkup.getKeyboard().add(new KeyboardRow(buttons.subList(i, end)));
        }

        return replyKeyboardMarkup;
    }

    private boolean checkCode(String code) {
        try {
            List<String> codes = FileUtils.readLines(new File("ids.txt"), "UTF-8");
            return codes.contains(code);
        } catch (IOException e) {
            log.error("Error while reading file: {}", e.getMessage());
            return false;
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    public void botConnect() {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(this);
            log.info("TelegramAPI started. Look for messages");
        } catch (TelegramApiException e) {
            log.error("Cant Connect. Pause " + RECONNECT_PAUSE / 1000 + " sec and try again. Error: " + e.getMessage());
            try {
                Thread.sleep(RECONNECT_PAUSE);
            } catch (InterruptedException e1) {
                log.warn("InterruptedException", e1);
                return;
            }
            botConnect();
        }
    }
}