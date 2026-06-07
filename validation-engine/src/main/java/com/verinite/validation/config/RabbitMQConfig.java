package com.verinite.validation.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ── Exchange names ──────────────────────────────────────────────────────
    public static final String VALIDATION_EVENTS_EXCHANGE  = "validation.events";
    public static final String CACHE_INVALIDATION_EXCHANGE = "cache.invalidation";

    public static final String VALIDATION_EVENTS_DLX       = "validation.events.dlx";
    public static final String CACHE_INVALIDATION_DLX      = "cache.invalidation.dlx";

    // ── Queue names ─────────────────────────────────────────────────────────
    public static final String HISTORY_VALIDATION_RUNS_QUEUE   = "history.validation-runs";
    public static final String VALIDATION_ENGINE_CACHE_QUEUE   = "validation-engine.cache-invalidation";

    public static final String HISTORY_VALIDATION_RUNS_DLQ     = "history.validation-runs.dlq";
    public static final String VALIDATION_ENGINE_CACHE_DLQ     = "validation-engine.cache-invalidation.dlq";

    // ── Routing key ─────────────────────────────────────────────────────────
    public static final String VALIDATION_RUN_ROUTING_KEY = "validation.run";

    // ── Main exchanges ──────────────────────────────────────────────────────

    @Bean
    public DirectExchange validationEventsExchange() {
        return new DirectExchange(VALIDATION_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange cacheInvalidationExchange() {
        return new FanoutExchange(CACHE_INVALIDATION_EXCHANGE, true, false);
    }

    // ── DLX (fanout) ────────────────────────────────────────────────────────

    @Bean
    public FanoutExchange validationEventsDlx() {
        return new FanoutExchange(VALIDATION_EVENTS_DLX, true, false);
    }

    @Bean
    public FanoutExchange cacheInvalidationDlx() {
        return new FanoutExchange(CACHE_INVALIDATION_DLX, true, false);
    }

    // ── Main queues ─────────────────────────────────────────────────────────

    @Bean
    public Queue historyValidationRunsQueue() {
        return QueueBuilder.durable(HISTORY_VALIDATION_RUNS_QUEUE)
                .withArgument("x-dead-letter-exchange", VALIDATION_EVENTS_DLX)
                .build();
    }

    @Bean
    public Queue validationEngineCacheQueue() {
        return QueueBuilder.durable(VALIDATION_ENGINE_CACHE_QUEUE)
                .withArgument("x-dead-letter-exchange", CACHE_INVALIDATION_DLX)
                .build();
    }

    // ── DLQs ────────────────────────────────────────────────────────────────

    @Bean
    public Queue historyValidationRunsDlq() {
        return QueueBuilder.durable(HISTORY_VALIDATION_RUNS_DLQ).build();
    }

    @Bean
    public Queue validationEngineCacheDlq() {
        return QueueBuilder.durable(VALIDATION_ENGINE_CACHE_DLQ).build();
    }

    // ── Bindings ────────────────────────────────────────────────────────────

    @Bean
    public Binding historyValidationRunsBinding(Queue historyValidationRunsQueue,
                                                DirectExchange validationEventsExchange) {
        return BindingBuilder.bind(historyValidationRunsQueue)
                .to(validationEventsExchange)
                .with(VALIDATION_RUN_ROUTING_KEY);
    }

    @Bean
    public Binding validationEngineCacheBinding(Queue validationEngineCacheQueue,
                                                FanoutExchange cacheInvalidationExchange) {
        return BindingBuilder.bind(validationEngineCacheQueue).to(cacheInvalidationExchange);
    }

    @Bean
    public Binding historyRunsDlqBinding(Queue historyValidationRunsDlq,
                                         FanoutExchange validationEventsDlx) {
        return BindingBuilder.bind(historyValidationRunsDlq).to(validationEventsDlx);
    }

    @Bean
    public Binding cacheDlqBinding(Queue validationEngineCacheDlq,
                                   FanoutExchange cacheInvalidationDlx) {
        return BindingBuilder.bind(validationEngineCacheDlq).to(cacheInvalidationDlx);
    }

    // ── JSON serialization ──────────────────────────────────────────────────

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