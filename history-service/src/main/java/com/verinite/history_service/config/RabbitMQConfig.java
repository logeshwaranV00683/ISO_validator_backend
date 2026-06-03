package com.verinite.history.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String VALIDATION_EVENTS_EXCHANGE = "validation.events";
    public static final String VALIDATION_RUNS_QUEUE = "history.validation-runs";
    public static final String VALIDATION_RUN_ROUTING_KEY = "run.completed";

    @Bean
    public DirectExchange validationEventsExchange() {
        return ExchangeBuilder
                .directExchange(VALIDATION_EVENTS_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue validationRunsQueue() {
        return QueueBuilder
                .durable(VALIDATION_RUNS_QUEUE)
                .build();
    }

    @Bean
    public Binding validationRunsBinding() {
        return BindingBuilder
                .bind(validationRunsQueue())
                .to(validationEventsExchange())
                .with(VALIDATION_RUN_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}