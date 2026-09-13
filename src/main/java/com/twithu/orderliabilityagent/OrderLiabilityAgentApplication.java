package com.twithu.orderliabilityagent;

import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {ChatMemoryAutoConfiguration.class})
public class OrderLiabilityAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderLiabilityAgentApplication.class, args);
    }

}
