package com.verinite.rules.config;

import org.springframework.amqp.core.FanoutExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public FanoutExchange cacheInvalidationExchange() {
        return new FanoutExchange("cache.invalidation");
    }
}