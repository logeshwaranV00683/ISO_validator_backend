package com.verinite.history.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String AUDIT_EXCHANGE      = "audit.events";
    public static final String VALIDATION_EXCHANGE = "validation.events";

    // ── DLX names — must match rules-service's RabbitMQConfig exactly ────────
    public static final String AUDIT_EVENTS_DLX      = "audit.events.dlx";
    public static final String VALIDATION_EVENTS_DLX = "validation.events.dlx";

    public static final String AUDIT_QUEUE      = "history.audit-logs";
    public static final String VALIDATION_QUEUE = "history.validation-runs";

    // ── DLQs ──────────────────────────────────────────────────────────────────
    public static final String AUDIT_LOGS_DLQ      = "history.audit-logs.dlq";
    public static final String VALIDATION_RUNS_DLQ = "history.validation-runs.dlq";

    public static final String AUDIT_ROUTING_KEY      = "audit.#";
    public static final String VALIDATION_ROUTING_KEY = "run.completed";

    // ── Main Exchanges ─────────────────────────────────────────────────────────

    @Bean
    public TopicExchange auditExchange() {
        return ExchangeBuilder.topicExchange(AUDIT_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange validationExchange() {
        return ExchangeBuilder.directExchange(VALIDATION_EXCHANGE).durable(true).build();
    }

    // ── DLX Exchanges — RabbitMQ declaration is idempotent; safe to declare in both services ──

    @Bean
    public FanoutExchange auditEventsDlx() {
        return new FanoutExchange(AUDIT_EVENTS_DLX, true, false);
    }

    @Bean
    public FanoutExchange validationEventsDlx() {
        return new FanoutExchange(VALIDATION_EVENTS_DLX, true, false);
    }

    // ── Main Queues — x-dead-letter-exchange must match rules-service exactly ──

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE)
                .withArgument("x-dead-letter-exchange", AUDIT_EVENTS_DLX)
                .build();
    }

    // FIX: Added x-dead-letter-exchange to match validation-engine's declaration exactly.
    // Without this, whichever service starts first creates the queue without DLX,
    // and the other service crashes with PRECONDITION_FAILED on startup.
    @Bean
    public Queue validationQueue() {
        return QueueBuilder.durable(VALIDATION_QUEUE)
                .withArgument("x-dead-letter-exchange", VALIDATION_EVENTS_DLX)
                .build();
    }

    // ── DLQs ──────────────────────────────────────────────────────────────────

    @Bean
    public Queue auditLogsDlq() {
        return QueueBuilder.durable(AUDIT_LOGS_DLQ).build();
    }

    @Bean
    public Queue validationRunsDlq() {
        return QueueBuilder.durable(VALIDATION_RUNS_DLQ).build();
    }

    // ── Main Bindings ──────────────────────────────────────────────────────────

    @Bean
    public Binding auditBinding(Queue auditQueue, TopicExchange auditExchange) {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with(AUDIT_ROUTING_KEY);
    }

    @Bean
    public Binding validationBinding(Queue validationQueue,
                                     DirectExchange validationExchange) {
        return BindingBuilder.bind(validationQueue)
                .to(validationExchange)
                .with(VALIDATION_ROUTING_KEY);
    }

    // ── DLQ Bindings ──────────────────────────────────────────────────────────

    @Bean
    public Binding auditLogsDlqBinding(Queue auditLogsDlq,
                                       FanoutExchange auditEventsDlx) {
        return BindingBuilder.bind(auditLogsDlq).to(auditEventsDlx);
    }

    @Bean
    public Binding validationRunsDlqBinding(Queue validationRunsDlq,
                                            FanoutExchange validationEventsDlx) {
        return BindingBuilder.bind(validationRunsDlq).to(validationEventsDlx);
    }

    // ── Serialization ──────────────────────────────────────────────────────────

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