package com.verinite.ai.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchange names — must match what rules-service declared
    public static final String AUDIT_EVENTS_EXCHANGE = "audit.events";

    // Routing key for ai config changes → consumed by history-service
    public static final String AUDIT_AI_CONFIG_ROUTING_KEY  = "audit.ai.config-change";
    public static final String AUDIT_AI_PROMPT_ROUTING_KEY  = "audit.ai.prompt-change";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate tpl = new RabbitTemplate(connectionFactory);
        tpl.setMessageConverter(jsonMessageConverter());
        return tpl;
    }
}