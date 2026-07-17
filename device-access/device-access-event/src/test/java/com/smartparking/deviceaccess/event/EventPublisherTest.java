package com.smartparking.deviceaccess.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.deviceaccess.common.entity.EventOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublisher Unit Tests")
class EventPublisherTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @Mock
    private EventOutboxRepository outboxRepository;

    private WebhookProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private EventPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new WebhookProperties();
        properties.setUrl("https://example.com/webhooks/events");
        properties.setTimeoutSeconds(5);
        properties.setRetryCount(2);
        properties.setRetryIntervalSeconds(0); // no delay in tests
        publisher = new EventPublisher(properties, objectMapper, httpClient, outboxRepository);
    }

    @Test
    @DisplayName("Should skip publishing when webhook is disabled")
    void shouldSkipWhenDisabled() {
        properties.setUrl("");

        publisher.publish(createTestEvent());

        verifyNoInteractions(httpClient, outboxRepository);
    }

    @Test
    @DisplayName("Should publish successfully on first attempt")
    void shouldPublishSuccessfully() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        publisher.publish(createTestEvent());

        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(outboxRepository, never()).savePending(any(), anyString());
    }

    @Test
    @DisplayName("Should retry on failure and succeed on second attempt")
    void shouldRetryOnFailure() throws Exception {
        when(httpResponse.statusCode())
                .thenReturn(500)
                .thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        publisher.publish(createTestEvent());

        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(outboxRepository, never()).savePending(any(), anyString());
    }

    @Test
    @DisplayName("Should save to outbox when all retries exhausted")
    void shouldSaveToOutboxWhenExhausted() throws Exception {
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        DeviceEvent event = createTestEvent();
        publisher.publish(event);

        // retryCount=2, so total attempts = 3 (1 + 2 retries)
        verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(outboxRepository).savePending(eq(event), anyString());
    }

    @Test
    @DisplayName("Should handle network exception gracefully")
    void shouldHandleNetworkException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        publisher.publish(createTestEvent()); // should not throw

        verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(outboxRepository).savePending(any(), anyString());
    }

    @Test
    @DisplayName("Should include X-Signature when secretKey is configured")
    void shouldIncludeSignatureWhenSecretKeyConfigured() throws Exception {
        properties.setSecretKey("my-secret");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        publisher.publish(createTestEvent());

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest request = requestCaptor.getValue();
        assertThat(request.headers().firstValue("X-Signature")).isPresent();
        assertThat(request.headers().firstValue("X-Timestamp")).isPresent();
    }

    @Test
    @DisplayName("Should not include X-Signature when secretKey is absent")
    void shouldNotIncludeSignatureWhenSecretKeyAbsent() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        publisher.publish(createTestEvent());

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest request = requestCaptor.getValue();
        assertThat(request.headers().firstValue("X-Signature")).isEmpty();
    }

    @Test
    @DisplayName("Should save pending outbox with correct event")
    void shouldSavePendingOutboxWithCorrectEvent() throws Exception {
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        DeviceEvent event = createTestEvent();
        publisher.publish(event);

        ArgumentCaptor<DeviceEvent> eventCaptor = ArgumentCaptor.forClass(DeviceEvent.class);
        verify(outboxRepository).savePending(eventCaptor.capture(), anyString());

        assertThat(eventCaptor.getValue().getEventId()).isEqualTo(event.getEventId());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(event.getEventType());
    }

    private DeviceEvent createTestEvent() {
        return DeviceEvent.builder()
                .eventId("evt_" + UUID.randomUUID())
                .eventType("PLATE_RECOGNIZED")
                .deviceSn("test-sn-001")
                .vendor("ZHENSHI")
                .occurredAt("2026-07-13T10:30:00Z")
                .receivedAt("2026-07-13T10:30:00.100Z")
                .payload(Map.of("plateNo", "A12345", "confidence", 98))
                .build();
    }
}
