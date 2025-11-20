package org.gridsuite.bddtests.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class NotificationWaiter {
    private NotificationWaiter() {
        throw new UnsupportedOperationException("NotificationWaiter is a utility class and cannot be instantiated");
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationWaiter.class);
    private static final String HEADER_USER_ID = "userId";

    private static final ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void executeAndWaitForLoadFlowSuccess(Runnable asyncRequest, TestContext.Node node, int timeout) {
        LOGGER.info("Wait for '{}' study loadflow completion (max: {} sec)", node.studyId, timeout);
        waitForNotification(
                asyncRequest,
                jsonNode -> checkNotificationMatchLoadFlowSuccess(jsonNode, node.studyId, node.nodeId),
                getStudyNotificationURI(node.studyId),
                timeout,
                1
        );
    }

    public static void executeAndWaitForStudyCreation(Runnable asyncRequest, String studyName, String directoryUuid, int timeout) {
        LOGGER.info("Wait for '{}' study creation completion (max: {} sec)", studyName, timeout);
        waitForNotification(
                asyncRequest,
                jsonNode -> checkNotificationMatchStudyCreation(jsonNode, studyName, directoryUuid),
                getDirectoryNotificationURI(),
                timeout,
                2
        );
    }

    public static void waitForNotification(
            Runnable asyncRequest,
            Predicate<JsonNode> notificationMatcher,
            URI notificationServerUri,
            int timeout,
            int expectedNotificationCount) {
        waitForNotification(() -> {
            asyncRequest.run();
            return null;
        }, notificationMatcher, notificationServerUri, timeout, expectedNotificationCount);
    }

    public static <T> T waitForNotification(Supplier<T> asyncRequest, Predicate<JsonNode> notificationMatcher, URI notificationServerUri, int timeout, int expectedNotificationCount) {
        CompletableFuture<Boolean> notificationReceived = new CompletableFuture<>();
        CompletableFuture<Boolean> wsReady = new CompletableFuture<>();
        AtomicInteger receivedNotificationCount = new AtomicInteger(0);

        client.execute(notificationServerUri, createUserIdHeader(), session -> {
            wsReady.complete(true);
            return session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .flatMap(NotificationWaiter::parseJson)
                    .filter(notificationMatcher)
                    .doOnNext(msg -> {
                        if (receivedNotificationCount.incrementAndGet() >= expectedNotificationCount) {
                            notificationReceived.complete(true);
                            session.close().subscribe();
                        }
                    }).then();
        }).subscribe();

        // Wait for web socket to be ready
        try {
            wsReady.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("WebSocket did not become ready in time", e);
        }

        // Execute the HTTP request
        T response = asyncRequest.get();

        // Wait for notifications with timeout
        try {
            notificationReceived.get(timeout, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException("Error while waiting for notification", e);
        }

        return response;
    }

    private static Mono<JsonNode> parseJson(String jsonAsString) {
        try {
            return Mono.just(mapper.readTree(jsonAsString));
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Invalid JSON: " + jsonAsString, e));
        }
    }

    private static boolean checkNotificationMatchStudyCreation(JsonNode json, String studyName, String directoryUuid) {
        JsonNode headers = json.get("headers");
        if (headers == null) {
            return false;
        }

        String jsonElementName = headers.path("elementName").asText("");
        String jsonDirectoryUuid = headers.path("directoryUuid").asText("");

        return jsonElementName.equals(studyName) && jsonDirectoryUuid.equals(directoryUuid);
    }

    private static boolean checkNotificationMatchLoadFlowSuccess(JsonNode json, String studyName, String nodeUuid) {
        JsonNode headers = json.get("headers");
        if (headers == null) {
            return false;
        }

        String jsonUpdateType = headers.path("updateType").asText("");
        String jsonStudyName = headers.path("studyUuid").asText("");
        String jsonNode = headers.path("node").asText("");

        return jsonUpdateType.equals("loadflowResult")
            && jsonStudyName.equals(studyName)
            && jsonNode.equals(nodeUuid);
    }

    private static URI getDirectoryNotificationURI() {
        return UriComponentsBuilder.fromUri(URI.create(EnvProperties.getInstance().getMicroServiceUrl(EnvProperties.MicroService.DIRECTORY_NOTIFICATION_SERVER) + "/notify"))
            .queryParam("updateType", "directories")
            .queryParam("userId", EnvProperties.getInstance().getUserName())
            .queryParam("access_token", EnvProperties.getInstance().getToken())
            .build()
            .toUri();
    }

    private static URI getStudyNotificationURI(String studyUuid) {
        return UriComponentsBuilder.fromUri(URI.create(EnvProperties.getInstance().getMicroServiceUrl(EnvProperties.MicroService.STUDY_NOTIFICATION_SERVER) + "/notify"))
            .queryParam("studyUuid", studyUuid)
            .queryParam("userId", EnvProperties.getInstance().getUserName())
            .queryParam("access_token", EnvProperties.getInstance().getToken())
            .build()
            .toUri();
    }

    private static HttpHeaders createUserIdHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, EnvProperties.getInstance().getUserName());
        return headers;
    }

    public static UUID executeAndWaitForExportNetworkFinished(Supplier<UUID> asyncRequest, String studyUuid, int timeout) {
        LOGGER.info("Waiting for export completion (max: {} sec)", timeout);
        return waitForNotification(
                asyncRequest,
                NotificationWaiter::checkNotificationMatchExportFinished,
                getStudyNotificationURI(studyUuid),
                timeout,
                1
        );
    }

    private static boolean checkNotificationMatchExportFinished(JsonNode json) {
        JsonNode headers = json.get("headers");
        if (headers == null) {
            return false;
        }

        String type = headers.path("updateType").asText("");
        return type.equals("networkExportFinished");
    }
}
