package com.verinite.rules.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ═══════════════════════════════════════════════════════════════
    // EXCHANGE NAMES
    // ═══════════════════════════════════════════════════════════════

    public static final String VALIDATION_EVENTS_EXCHANGE    = "validation.events";
    public static final String AUDIT_EVENTS_EXCHANGE         = "audit.events";
    public static final String CACHE_INVALIDATION_EXCHANGE   = "cache.invalidation";

    // Dead letter exchanges (all fanout — dead letters route unconditionally)
    public static final String VALIDATION_EVENTS_DLX         = "validation.events.dlx";
    public static final String AUDIT_EVENTS_DLX              = "audit.events.dlx";
    public static final String CACHE_INVALIDATION_DLX        = "cache.invalidation.dlx";

    // ═══════════════════════════════════════════════════════════════
    // QUEUE NAMES
    // ═══════════════════════════════════════════════════════════════

    public static final String HISTORY_VALIDATION_RUNS_QUEUE        = "history.validation-runs";
    public static final String HISTORY_AUDIT_LOGS_QUEUE             = "history.audit-logs";
    public static final String VALIDATION_ENGINE_CACHE_QUEUE        = "validation-engine.cache-invalidation";

    public static final String HISTORY_VALIDATION_RUNS_DLQ          = "history.validation-runs.dlq";
    public static final String HISTORY_AUDIT_LOGS_DLQ               = "history.audit-logs.dlq";
    public static final String VALIDATION_ENGINE_CACHE_DLQ          = "validation-engine.cache-invalidation.dlq";

    // ═══════════════════════════════════════════════════════════════
    // BINDING KEYS
    // ═══════════════════════════════════════════════════════════════

    /** Direct exchange — validation-engine publishes with this key */
    public static final String VALIDATION_RUN_ROUTING_KEY    = "validation.run";

    /** Topic exchange — history-service consumes everything: audit.rule.*, audit.field_definition.*, etc. */
    public static final String AUDIT_WILDCARD_KEY            = "audit.#";

    // ═══════════════════════════════════════════════════════════════
    // MAIN EXCHANGES
    // ═══════════════════════════════════════════════════════════════

    /** Direct — validation-engine publishes completed runs here → history-service */
    @Bean
    public DirectExchange validationEventsExchange() {
        return new DirectExchange(VALIDATION_EVENTS_EXCHANGE, true, false);
    }

    /** Topic — rules/profile/auth publish audit events here → history-service */
    @Bean
    public TopicExchange auditEventsExchange() {
        return new TopicExchange(AUDIT_EVENTS_EXCHANGE, true, false);
    }

    /** Fanout — rules/profile publish cache-bust events here → validation-engine */
    @Bean
    public FanoutExchange cacheInvalidationExchange() {
        return new FanoutExchange(CACHE_INVALIDATION_EXCHANGE, true, false);
    }

    // ═══════════════════════════════════════════════════════════════
    // DEAD LETTER EXCHANGES (all fanout — routes unconditionally to DLQ)
    // ═══════════════════════════════════════════════════════════════

    @Bean
    public FanoutExchange validationEventsDlx() {
        return new FanoutExchange(VALIDATION_EVENTS_DLX, true, false);
    }

    @Bean
    public FanoutExchange auditEventsDlx() {
        return new FanoutExchange(AUDIT_EVENTS_DLX, true, false);
    }

    @Bean
    public FanoutExchange cacheInvalidationDlx() {
        return new FanoutExchange(CACHE_INVALIDATION_DLX, true, false);
    }

    // ═══════════════════════════════════════════════════════════════
    // MAIN QUEUES — durable, with DLX argument
    // ═══════════════════════════════════════════════════════════════

    @Bean
    public Queue historyValidationRunsQueue() {
        return QueueBuilder.durable(HISTORY_VALIDATION_RUNS_QUEUE)
                .withArgument("x-dead-letter-exchange", VALIDATION_EVENTS_DLX)
                .build();
    }

    @Bean
    public Queue historyAuditLogsQueue() {
        return QueueBuilder.durable(HISTORY_AUDIT_LOGS_QUEUE)
                .withArgument("x-dead-letter-exchange", AUDIT_EVENTS_DLX)
                .build();
    }

    @Bean
    public Queue validationEngineCacheQueue() {
        return QueueBuilder.durable(VALIDATION_ENGINE_CACHE_QUEUE)
                .withArgument("x-dead-letter-exchange", CACHE_INVALIDATION_DLX)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // DEAD LETTER QUEUES — durable, no DLX (dead letters stop here)
    // ═══════════════════════════════════════════════════════════════

    @Bean
    public Queue historyValidationRunsDlq() {
        return QueueBuilder.durable(HISTORY_VALIDATION_RUNS_DLQ).build();
    }

    @Bean
    public Queue historyAuditLogsDlq() {
        return QueueBuilder.durable(HISTORY_AUDIT_LOGS_DLQ).build();
    }

    @Bean
    public Queue validationEngineCacheDlq() {
        return QueueBuilder.durable(VALIDATION_ENGINE_CACHE_DLQ).build();
    }

    // ═══════════════════════════════════════════════════════════════
    // MAIN BINDINGS
    // ═══════════════════════════════════════════════════════════════

    /** history.validation-runs ← validation.events (direct, key=validation.run) */
    @Bean
    public Binding historyValidationRunsBinding(Queue historyValidationRunsQueue,
                                                DirectExchange validationEventsExchange) {
        return BindingBuilder
                .bind(historyValidationRunsQueue)
                .to(validationEventsExchange)
                .with(VALIDATION_RUN_ROUTING_KEY);
    }

    /** history.audit-logs ← audit.events (topic, pattern=audit.#) */
    @Bean
    public Binding historyAuditLogsBinding(Queue historyAuditLogsQueue,
                                           TopicExchange auditEventsExchange) {
        return BindingBuilder
                .bind(historyAuditLogsQueue)
                .to(auditEventsExchange)
                .with(AUDIT_WILDCARD_KEY);
    }

    /** validation-engine.cache-invalidation ← cache.invalidation (fanout, no routing key) */
    @Bean
    public Binding validationEngineCacheBinding(Queue validationEngineCacheQueue,
                                                FanoutExchange cacheInvalidationExchange) {
        return BindingBuilder
                .bind(validationEngineCacheQueue)
                .to(cacheInvalidationExchange);
    }

    // ═══════════════════════════════════════════════════════════════
    // DLQ BINDINGS — each DLQ binds to its fanout DLX
    // ═══════════════════════════════════════════════════════════════

    @Bean
    public Binding historyValidationRunsDlqBinding(Queue historyValidationRunsDlq,
                                                   FanoutExchange validationEventsDlx) {
        return BindingBuilder.bind(historyValidationRunsDlq).to(validationEventsDlx);
    }

    @Bean
    public Binding historyAuditLogsDlqBinding(Queue historyAuditLogsDlq,
                                              FanoutExchange auditEventsDlx) {
        return BindingBuilder.bind(historyAuditLogsDlq).to(auditEventsDlx);
    }

    @Bean
    public Binding validationEngineCacheDlqBinding(Queue validationEngineCacheDlq,
                                                   FanoutExchange cacheInvalidationDlx) {
        return BindingBuilder.bind(validationEngineCacheDlq).to(cacheInvalidationDlx);
    }

    // ═══════════════════════════════════════════════════════════════
    // SERIALIZATION + TEMPLATE
    // ═══════════════════════════════════════════════════════════════

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter  jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}