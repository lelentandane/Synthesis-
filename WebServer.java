package com.lelentandane.synthesis.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.lelentandane.synthesis.service.ApiException;
import com.lelentandane.synthesis.service.GraphService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small JSON API plus static files, built on the HTTP server that ships with the JDK.
 * No framework: routing, validation and error handling are all visible in this file.
 */
public final class WebServer {

    private static final Logger LOG = Logger.getLogger(WebServer.class.getName());
    private static final int MAX_BODY_BYTES = 1_000_000;
    private static final Pattern CONCEPT_ID = Pattern.compile("^/api/concepts/(\\d{1,18})$");
    private static final Pattern RELATION_ID = Pattern.compile("^/api/relations/(\\d{1,18})$");

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".html", "text/html; charset=utf-8",
            ".css", "text/css; charset=utf-8",
            ".js", "text/javascript; charset=utf-8",
            ".woff2", "font/woff2",
            ".svg", "image/svg+xml",
            ".png", "image/png",
            ".ico", "image/x-icon",
            ".txt", "text/plain; charset=utf-8");

    private record Reply(int status, Object body, Map<String, String> headers) {
        Reply(int status, Object body) {
            this(status, body, Map.of());
        }
    }

    private final GraphService service;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public WebServer(GraphService service, String host, int port) throws IOException {
        this.service = service;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.createContext("/api/", this::handleApi);
        this.server.createContext("/", this::handleStatic);
        this.server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    // ------------------------------------------------------------------ API

    private void handleApi(HttpExchange exchange) throws IOException {
        try {
            Reply reply = route(exchange);
            sendJson(exchange, reply.status(), reply.body(), reply.headers());
        } catch (ApiException e) {
            sendJson(exchange, e.status(), Map.of("error", e.getMessage()), Map.of());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unexpected error handling " + exchange.getRequestURI(), e);
            sendJson(exchange, 500, Map.of("error", "Something went wrong on the server."), Map.of());
        } finally {
            exchange.close();
        }
    }

    private Reply route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        switch (path) {
            case "/api/graph" -> {
                allow(method, "GET");
                return new Reply(200, ApiMapper.graph(service.snapshot()));
            }
            case "/api/concepts" -> {
                allow(method, "POST");
                JsonObject body = readJson(exchange);
                var created = service.createConcept(text(body, "name"), text(body, "discipline"), text(body, "description"));
                return new Reply(201, ApiMapper.concept(created));
            }
            case "/api/relations" -> {
                allow(method, "POST");
                JsonObject body = readJson(exchange);
                var created = service.createRelation(whole(body, "fromId"), whole(body, "toId"),
                        text(body, "label"), (int) wholeOr(body, "strength", 3));
                return new Reply(201, ApiMapper.relation(created));
            }
            case "/api/path" -> {
                allow(method, "GET");
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                long from = queryId(query, "from");
                long to = queryId(query, "to");
                String mode = query.getOrDefault("mode", "fewest");
                return new Reply(200, ApiMapper.pathResult(service.findPaths(from, to, mode)));
            }
            case "/api/insights" -> {
                allow(method, "GET");
                return new Reply(200, ApiMapper.insights(service.insights()));
            }
            case "/api/demo" -> {
                allow(method, "POST");
                return new Reply(201, ApiMapper.graph(service.loadDemo()));
            }
            case "/api/export" -> {
                allow(method, "GET");
                return new Reply(200, ApiMapper.export(service.snapshot()),
                        Map.of("Content-Disposition", "attachment; filename=\"synthesis-export.json\""));
            }
            case "/api/import" -> {
                allow(method, "POST");
                return new Reply(200, ApiMapper.graph(importGraph(readJson(exchange))));
            }
            default -> {
                // fall through to the id-based routes below
            }
        }

        Matcher concept = CONCEPT_ID.matcher(path);
        if (concept.matches()) {
            long id = Long.parseLong(concept.group(1));
            if ("PUT".equals(method)) {
                JsonObject body = readJson(exchange);
                var updated = service.updateConcept(id, text(body, "name"), text(body, "discipline"), text(body, "description"));
                return new Reply(200, ApiMapper.concept(updated));
            }
            allow(method, "DELETE");
            service.deleteConcept(id);
            return new Reply(200, Map.of("deleted", true));
        }

        Matcher relation = RELATION_ID.matcher(path);
        if (relation.matches()) {
            allow(method, "DELETE");
            service.deleteRelation(Long.parseLong(relation.group(1)));
            return new Reply(200, Map.of("deleted", true));
        }

        throw ApiException.notFound("There is no such API endpoint.");
    }

    private GraphService.Snapshot importGraph(JsonObject body) {
        List<GraphService.ConceptDraft> concepts = new ArrayList<>();
        for (JsonObject item : objects(body, "concepts")) {
            concepts.add(new GraphService.ConceptDraft(text(item, "name"), text(item, "discipline"), text(item, "description")));
        }
        List<GraphService.RelationDraft> relations = new ArrayList<>();
        for (JsonObject item : objects(body, "relations")) {
            relations.add(new GraphService.RelationDraft(text(item, "from"), text(item, "to"),
                    text(item, "label"), (int) wholeOr(item, "strength", 3)));
        }
        return service.replaceAll(concepts, relations);
    }

    // ------------------------------------------------------------------ request helpers

    private static void allow(String method, String expected) {
        if (!expected.equals(method)) {
            throw new ApiException(405, "This endpoint only accepts " + expected + ".");
        }
    }

    /** Reads a JSON object body. Requires the JSON content type, which also blocks cross-site form posts. */
    private JsonObject readJson(HttpExchange exchange) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase().startsWith("application/json")) {
            throw new ApiException(415, "Send the data as JSON with Content-Type: application/json.");
        }
        // The request stream is deliberately not closed here: closing a half-read body makes the
        // server drop the connection, and the client would never see our answer.
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            drain(exchange);
            throw new ApiException(413, "That request is too large.");
        }
        try {
            JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw ApiException.badRequest("The request body must be a JSON object.");
            }
            return parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw ApiException.badRequest("The request body is not valid JSON.");
        }
    }

    /**
     * Reads and discards the rest of an oversized body (up to a cap), so the client finishes sending
     * and receives our 413 answer instead of a dropped connection.
     */
    private static void drain(HttpExchange exchange) {
        byte[] buffer = new byte[8192];
        long remaining = 8L * MAX_BODY_BYTES;
        try {
            InputStream in = exchange.getRequestBody();
            int read;
            while (remaining > 0 && (read = in.read(buffer)) != -1) {
                remaining -= read;
            }
        } catch (IOException ignored) {
            // the client went away; nothing more to do
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw ApiException.badRequest("\"" + key + "\" must be text.");
        }
        return element.getAsString();
    }

    private static long whole(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            throw ApiException.badRequest("\"" + key + "\" is required.");
        }
        return toWhole(element, key);
    }

    private static long wholeOr(JsonObject object, String key, long fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : toWhole(element, key);
    }

    private static long toWhole(JsonElement element, String key) {
        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new NumberFormatException();
            }
            return new BigDecimal(element.getAsString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw ApiException.badRequest("\"" + key + "\" must be a whole number.");
        }
    }

    private static List<JsonObject> objects(JsonObject body, String key) {
        JsonElement element = body.get(key);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw ApiException.badRequest("\"" + key + "\" must be a list.");
        }
        List<JsonObject> result = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (JsonElement item : array) {
            if (!item.isJsonObject()) {
                throw ApiException.badRequest("Every entry in \"" + key + "\" must be an object.");
            }
            result.add(item.getAsJsonObject());
        }
        return result;
    }

    private static long queryId(Map<String, String> query, String key) {
        try {
            return Long.parseLong(query.getOrDefault(key, ""));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("\"" + key + "\" must be a concept id.");
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        for (String pair : raw.split("&")) {
            int split = pair.indexOf('=');
            String key = split < 0 ? pair : pair.substring(0, split);
            String value = split < 0 ? "" : pair.substring(split + 1);
            result.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return result;
    }

    // ------------------------------------------------------------------ responses

    private void sendJson(HttpExchange exchange, int status, Object body, Map<String, String> extra) throws IOException {
        byte[] bytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        extra.forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ------------------------------------------------------------------ static files

    private void handleStatic(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                sendText(exchange, 405, "Method not allowed");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) {
                path = "/index.html";
            }
            String type = contentType(path);
            if (type == null || path.contains("..") || path.contains("\\") || path.contains("//") || path.indexOf('\0') >= 0) {
                sendText(exchange, 404, "Not found");
                return;
            }
            try (InputStream in = WebServer.class.getResourceAsStream("/static" + path)) {
                if (in == null) {
                    sendText(exchange, 404, "Not found");
                    return;
                }
                byte[] bytes = in.readAllBytes();
                var headers = exchange.getResponseHeaders();
                headers.set("Content-Type", type);
                headers.set("Cache-Control", path.endsWith(".woff2") ? "public, max-age=86400" : "no-cache");
                headers.set("X-Content-Type-Options", "nosniff");
                headers.set("Content-Security-Policy",
                        "default-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
                boolean head = "HEAD".equals(method);
                exchange.sendResponseHeaders(200, head ? -1 : bytes.length);
                if (!head) {
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                }
            }
        } finally {
            exchange.close();
        }
    }

    private static String contentType(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? null : CONTENT_TYPES.get(path.substring(dot).toLowerCase());
    }

    private void sendText(HttpExchange exchange, int status, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
