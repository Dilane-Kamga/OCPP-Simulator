package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.LiveEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveEventService {

    public static final String TOPIC = "/topic/events";

    private final SimpMessagingTemplate template;

    public void publish(LiveEventDto event) {
        try {
            template.convertAndSend(TOPIC, event);
        } catch (Exception e) {
            log.warn("Failed to publish live event {}: {}", event.type(), e.getMessage());
        }
    }
}
