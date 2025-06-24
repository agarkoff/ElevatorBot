package com.example.elevatorbot.configuration;

import com.example.elevatorbot.Bot;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConfigurationProperties(prefix = "bot")
public class BotConfiguration {

    @Setter
    private String name;
    @Setter
    private String token;
    @Setter
    private String objectName = "этаж"; // По умолчанию "этаж", можно изменить на "ворота"

    @Autowired
    private RestTemplate restTemplate;

    @Bean
    public Bot bot() {
        Bot bot = new Bot(token, name, restTemplate, objectName);
        bot.botConnect();
        return bot;
    }
}