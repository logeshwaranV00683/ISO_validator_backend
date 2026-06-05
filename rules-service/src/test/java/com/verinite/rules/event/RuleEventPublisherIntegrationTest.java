package com.verinite.rules.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.rules.service.FieldDefinitionService;
import com.verinite.rules.service.RuleService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // disable everything except AMQP
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.import-check.enabled=false",
                "spring.cloud.config.fail-fast=false",
                "eureka.client.enabled=false"
        }
)
@Testcontainers
class RuleEventPublisherIntegrationTest {

    @Container
    static RabbitMQContainer rabbit =
            new RabbitMQContainer("rabbitmq:3.12-management");

    @DynamicPropertySource
    static void rabbitProps(DynamicPropertyRegistry r) {
        r.add("spring.rabbitmq.host", rabbit::getHost);
        r.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    }

    // mock the JPA-dependent services so the context loads without a DB
    @MockBean RuleService             ruleService;
    @MockBean FieldDefinitionService  fieldDefinitionService;

    @Autowired RuleEventPublisher publisher;
    @Autowired RabbitTemplate     rabbitTemplate;
    @Autowired AmqpAdmin          amqpAdmin;

    @Test
    void publishRuleUpdated_messageArrivesOnFanoutExchange() throws Exception {
        // bind a temp queue to the fanout exchange to receive the message
        Queue q = QueueBuilder.nonDurable().autoDelete().build();
        amqpAdmin.declareQueue(q);
        amqpAdmin.declareBinding(
                BindingBuilder.bind(q).to(new FanoutExchange("cache.invalidation")));

        publisher.publishRuleUpdated(1L, "0200");

        Message raw = rabbitTemplate.receive(q.getName(), 5_000);
        assertThat(raw).as("message must arrive within 5 s").isNotNull();

        CacheInvalidationEvent event = new ObjectMapper()
                .readValue(raw.getBody(), CacheInvalidationEvent.class);

        assertThat(event.getPayload().getType()).isEqualTo("RULE_UPDATED");
        assertThat(event.getPayload().getProfileId()).isEqualTo(1L);
        assertThat(event.getPayload().getMti()).isEqualTo("0200");
    }
}