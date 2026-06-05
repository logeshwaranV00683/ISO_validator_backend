package com.verinite.profile.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the exchanges this service publishes to.
 * Spring AMQP declarations are idempotent — safe to redeclare
 * even if rules-service already created the same exchange.
 */
@Configuration
public class RabbitMQConfig {

    /** Fanout — profile publishes FORMAT_UPDATED / FORMAT_ROLLED_BACK here */
    @Bean
    public FanoutExchange cacheInvalidationExchange() {
        return new FanoutExchange("cache.invalidation", true, false);
    }

    /** Topic — profile publishes audit events here */
    @Bean
    public TopicExchange auditEventsExchange() {
        return new TopicExchange("audit.events", true, false);
    }

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