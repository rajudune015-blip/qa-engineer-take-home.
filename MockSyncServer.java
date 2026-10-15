package com.qa.assessment.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Local mock backend used by the take-home API tests; not a production sync service. */
public final class MockSyncServer {
    public static final int PORT = 4173;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Map<String, Object>> SUBMISSIONS = new ConcurrentHashMap<>();
    private static final Map<String, String> DEVICE_LOCAL_IDS = new ConcurrentHashMap<>();
    private static HttpServer server;

    private MockSyncServer() {}

    public static synchronized void start() throws IOException {
        if (server != null) return;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/health", exchange -> send(exchange, 200, Map.of("status", "ok")));
        server.createContext("/sync-survey", MockSyncServer::handleSync);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    public static synchronized void stop() {
        if (server != null) { server.stop(0); server = null; }
    }

    public static void clearData() { SUBMISSIONS.clear(); DEVICE_LOCAL_IDS.clear(); }

    private static void handleSync(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 404, Map.of("error", "not_found")); return;
        }
        Map<String, Object> body;
        try {
            body = MAPPER.readValue(exchange.getRequestBody(), new TypeReference<>() {});
        } catch (IOException e) {
            send(exchange, 400, Map.of("error", "invalid_json")); return;
        }
        for (String field : new String[]{"submissionId", "deviceId", "localId", "entityName", "answers"}) {
            Object value = body.get(field);
            if (value == null || (value instanceof String s && s.isBlank())) {
                send(exchange, 400, Map.of("error", "missing_required_field")); return;
            }
        }
        String submissionId = String.valueOf(body.get("submissionId"));
        Map<String, Object> old = SUBMISSIONS.get(submissionId);
        if (old != null) {
            if (old.equals(body)) send(exchange, 200, Map.of("status", "already_accepted", "submissionId", submissionId));
            else send(exchange, 409, Map.of("error", "idempotency_conflict", "submissionId", submissionId));
            return;
        }
        String localKey = body.get("deviceId") + "::" + body.get("localId");
        String existing = DEVICE_LOCAL_IDS.get(localKey);
        if (existing != null && !existing.equals(submissionId)) {
            send(exchange, 409, Map.of("error", "device_local_id_conflict", "localId", body.get("localId"))); return;
        }
        SUBMISSIONS.put(submissionId, body);
        DEVICE_LOCAL_IDS.put(localKey, submissionId);
        send(exchange, 201, Map.of("status", "accepted", "submissionId", submissionId));
    }

    private static void send(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = MAPPER.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }
}
