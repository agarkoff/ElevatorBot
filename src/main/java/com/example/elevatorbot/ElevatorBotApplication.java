package com.example.elevatorbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class ElevatorBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElevatorBotApplication.class, args);
    }

}
