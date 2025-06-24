package com.example.elevatorbot.configuration;

import com.example.elevatorbot.Bot;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

@Slf4j
@Configuration
public class BotConfiguration {

    @Setter
    private String name;
    @Setter
    private String token;
    @Setter
    private String objectName = "этаж"; // По умолчанию "этаж", можно изменить на "ворота"

    @Autowired
    private RestTemplate restTemplate;

    @PostConstruct
    public void loadBotConfig() {
        Properties props = new Properties();
        File configFile = new File("bot-config.txt");

        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
            log.info("Загружен файл конфигурации бота: {}", configFile.getAbsolutePath());

            // Загружаем параметры из файла
            this.name = props.getProperty("bot.name");
            this.token = props.getProperty("bot.token");
            String loadedObjectName = props.getProperty("bot.object-name");
            if (loadedObjectName != null && !loadedObjectName.trim().isEmpty()) {
                this.objectName = loadedObjectName.trim();
            }

            log.info("Конфигурация бота загружена: name={}, object-name={}", name, objectName);

            // Валидация обязательных параметров
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("bot.name не указан в файле конфигурации");
            }
            if (token == null || token.trim().isEmpty()) {
                throw new IllegalArgumentException("bot.token не указан в файле конфигурации");
            }

        } catch (IOException e) {
            log.error("Ошибка чтения файла конфигурации бота: {}. Проверьте наличие файла bot-config.txt", e.getMessage());
            throw new RuntimeException("Не удалось загрузить конфигурацию бота из файла bot-config.txt", e);
        }
    }

    @Bean
    public Bot bot() {
        Bot bot = new Bot(token, name, restTemplate, objectName);
        bot.botConnect();
        return bot;
    }
}