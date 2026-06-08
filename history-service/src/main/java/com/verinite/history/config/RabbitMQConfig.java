package com.verinite.history.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for history-service.
 * History service only CONSUMES — it never publishes.
 *
 * FIX: VALIDATION_ROUTING_KEY was "run.completed" but validation-engine publishes
 *       with routing key "validation.run". Changed to match.
 */
@Configuration
public class RabbitMQConfig {

    public static final String AUDIT_EXCHANGE      = "audit.events";
    public static final String VALIDATION_EXCHANGE = "validation.events";

    public static final String AUDIT_QUEUE      = "history.audit-logs";
    public static final String VALIDATION_QUEUE = "history.validation-runs";

    // FIX: was "run.completed" — must match ValidationRunPublisher.VALIDATION_RUN_ROUTING_KEY
    public static final String AUDIT_ROUTING_KEY      = "audit.#";
    public static final String VALIDATION_ROUTING_KEY = "validation.run";

    @Bean public TopicExchange auditExchange() {
        return ExchangeBuilder.topicExchange(AUDIT_EXCHANGE).durable(true).build();
    }

    @Bean public DirectExchange validationExchange() {
        return ExchangeBuilder.directExchange(VALIDATION_EXCHANGE).durable(true).build();
    }

    @Bean public Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE).build();
    }

    @Bean public Queue validationQueue() {
        return QueueBuilder.durable(VALIDATION_QUEUE).build();
    }

    @Bean public Binding auditBinding(Queue auditQueue, TopicExchange auditExchange) {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with(AUDIT_ROUTING_KEY);
    }

    @Bean public Binding validationBinding(Queue validationQueue,
                                           DirectExchange validationExchange) {
        return BindingBuilder.bind(validationQueue)
                .to(validationExchange)
                .with(VALIDATION_ROUTING_KEY);   // FIX: now "validation.run"
    }

    @Bean public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter());
        return template;
    }
}