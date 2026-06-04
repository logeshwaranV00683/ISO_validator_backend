package com.verinite.rules.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RuleEventPublisherTest {

    @Mock    RabbitTemplate     rabbitTemplate;
    @InjectMocks RuleEventPublisher publisher;

    @Test
    void publishRuleUpdated_sendsToCorrectExchangeWithPayload() {
        publisher.publishRuleUpdated(1L, "0200");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        // fanout exchange → routing key is always "" (ignored by broker)
        verify(rabbitTemplate).convertAndSend(eq("cache.invalidation"), eq(""), captor.capture());

        CacheInvalidationEvent event = (CacheInvalidationEvent) captor.getValue();
        assertThat(event.getPayload().getType()).isEqualTo("RULE_UPDATED");
        assertThat(event.getPayload().getProfileId()).isEqualTo(1L);
        assertThat(event.getPayload().getMti()).isEqualTo("0200");
        assertThat(event.getPayload().getFormatId()).isNull();   // null on rule events
    }

    @Test
    void publishRuleDeleted_sendsCorrectTypeAndCoords() {
        publisher.publishRuleDeleted(2L, "0210");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq("cache.invalidation"), eq(""), captor.capture());

        CacheInvalidationEvent event = (CacheInvalidationEvent) captor.getValue();
        assertThat(event.getPayload().getType()).isEqualTo("RULE_DELETED");
        assertThat(event.getPayload().getProfileId()).isEqualTo(2L);
        assertThat(event.getPayload().getMti()).isEqualTo("0210");
    }
}