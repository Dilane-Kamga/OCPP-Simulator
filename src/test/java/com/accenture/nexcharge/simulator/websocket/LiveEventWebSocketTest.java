package com.accenture.nexcharge.simulator.websocket;

import com.accenture.nexcharge.simulator.model.dto.LiveEventDto;
import com.accenture.nexcharge.simulator.model.enums.LiveEventType;
import com.accenture.nexcharge.simulator.service.LiveEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LiveEventWebSocketTest {

    @LocalServerPort int port;
    @Autowired LiveEventService liveEventService;
    @Autowired ObjectMapper json;

    @Test
    void clientReceivesPublishedEvent() throws Exception {
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new MappingJackson2MessageConverter());

        BlockingQueue<String> received = new ArrayBlockingQueue<>(4);

        StompSession session = stomp.connectAsync("ws://localhost:" + port + "/ws/live",
                new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/events", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                received.offer(new String((byte[]) payload));
            }
        });

        // tiny pause to ensure subscription is processed
        Thread.sleep(200);
        liveEventService.publish(LiveEventDto.of(
                LiveEventType.STATUS_CHANGE, "BORNE_TEST",
                Map.of("status", "Charging")));

        String payload = received.poll(5, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        assertThat(payload).contains("STATUS_CHANGE");
        assertThat(payload).contains("BORNE_TEST");

        session.disconnect();
    }
}
