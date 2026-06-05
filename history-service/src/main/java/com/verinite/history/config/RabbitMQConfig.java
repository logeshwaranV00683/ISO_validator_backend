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
 *
 * History service only CONSUMES — it never publishes.
 *
 * Exchanges declared here (durable — survive broker restart):
 *   audit.events      (topic)  → consumed by: history.audit-logs
 *   validation.events (direct) → consumed by: history.validation-runs
 *
 * Queues (durable):
 *   history.audit-logs          bound to audit.events      with routing key audit.#
 *   history.validation-runs     bound to validation.events with routing key run.completed
 */
@Configuration
public class RabbitMQConfig {

    // ── Exchange names ───────────────────────────────────────────────────────
    public static final String AUDIT_EXCHANGE       = "audit.events";
    public static final String VALIDATION_EXCHANGE  = "validation.events";

    // ── Queue names ──────────────────────────────────────────────────────────
    public static final String AUDIT_QUEUE          = "history.audit-logs";
    public static final String VALIDATION_QUEUE     = "history.validation-runs";

    // ── Routing keys ─────────────────────────────────────────────────────────
    public static final String AUDIT_ROUTING_KEY       = "audit.#";          // topic wildcard
    public static final String VALIDATION_ROUTING_KEY  = "run.completed";

    // ── Exchanges ────────────────────────────────────────────────────────────

    @Bean
    public TopicExchange auditExchange() {
        return ExchangeBuilder.topicExchange(AUDIT_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange validationExchange() {
        return ExchangeBuilder.directExchange(VALIDATION_EXCHANGE)
                .durable(true)
                .build();
    }

    // ── Queues ───────────────────────────────────────────────────────────────

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE).build();
    }

    @Bean
    public Queue validationQueue() {
        return QueueBuilder.durable(VALIDATION_QUEUE).build();
    }

    // ── Bindings ─────────────────────────────────────────────────────────────

    /** history.audit-logs ← audit.events (topic) with routing key audit.# */
    @Bean
    public Binding auditBinding(Queue auditQueue, TopicExchange auditExchange) {
        return BindingBuilder.bind(auditQueue)
                .to(auditExchange)
                .with(AUDIT_ROUTING_KEY);
    }

    /** history.validation-runs ← validation.events (direct) with routing key run.completed */
    @Bean
    public Binding validationBinding(Queue validationQueue, DirectExchange validationExchange) {
        return BindingBuilder.bind(validationQueue)
                .to(validationExchange)
                .with(VALIDATION_ROUTING_KEY);
    }

    // ── Message Converter (JSON) ─────────────────────────────────────────────

    /** Use Jackson so messages are deserialized as Map / POJOs automatically */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter());
        return template;
    }
}