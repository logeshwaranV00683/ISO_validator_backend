package com.verinite.rules.config;

import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    /**
     * cache.invalidation — FANOUT — routes to all bound queues.
     * Validation-engine binds its cache-invalidation queue here.
     */
    @Bean
    public FanoutExchange cacheInvalidationExchange() {
        return new FanoutExchange("cache.invalidation", true, false);
    }

    /**
     * audit.events — TOPIC — routing key: audit.rule.* / audit.field_definition.*
     * History-service binds its audit-logs queue to routingKey = audit.#
     */
    @Bean
    public TopicExchange auditEventsExchange() {
        return new TopicExchange("audit.events", true, false);
    }

    /** All AMQP messages are serialized as JSON. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** Wire the Jackson converter into the RabbitTemplate. */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter  jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}