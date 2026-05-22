package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.LiveEventDto;
import com.accenture.nexcharge.simulator.model.enums.LiveEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveEventServiceTest {

    @Mock
    SimpMessagingTemplate template;

    @InjectMocks
    LiveEventService service;

    @Test
    void publishesToTopicEvents() {
        LiveEventDto event = LiveEventDto.of(
                LiveEventType.STATUS_CHANGE, "BORNE_A", Map.of("status", "Charging"));

        service.publish(event);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(destinationCaptor.capture(), payloadCaptor.capture());

        assertThat(destinationCaptor.getValue()).isEqualTo("/topic/events");
        assertThat(payloadCaptor.getValue()).isSameAs(event);
    }
}
