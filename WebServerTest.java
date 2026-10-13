package com.lelentandane.synthesis.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lelentandane.synthesis.db.Database;
import com.lelentandane.synthesis.service.GraphService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Starts the real server on a free port and talks to it over HTTP. */
class WebServerTest {

    private Database database;
    private WebServer server;
    private HttpClient client;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        database = Database.inMemory();
        server = new WebServer(new GraphService(database), "127.0.0.1", 0);
        server.start();
        base = "http://127.0.0.1:" + server.port();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        database.close();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String json, String contentType) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> json(String method, String path, String json) throws Exception {
        return send(method, path, json, "application/json");
    }

    private static JsonObject body(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private long createConcept(String name, String discipline) throws Exception {
        HttpResponse<String> response = json("POST", "/api/concepts",
                "{\"name\":\"" + name + "\",\"discipline\":\"" + discipline + "\",\"description\":\"\"}");
        assertEquals(201, response.statusCode());
        return body(response).get("id").getAsLong();
    }

    // ---------------------------------------------------------------- static files

    @Test
    void servesTheAppAndItsAssetsWithSecurityHeaders() throws Exception {
        HttpResponse<String> page = get("/");

        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Synthesis"));
        assertTrue(page.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(page.headers().firstValue("Content-Security-Policy").orElse("").contains("default-src 'self'"));
        assertEquals("nosniff", page.headers().firstValue("X-Content-Type-Options").orElse(""));

        assertEquals(200, get("/js/app.js").statusCode());
        assertTrue(get("/css/style.css").headers().firstValue("Content-Type").orElse("").startsWith("text/css"));
    }

    @Test
    void doesNotServeUnknownFilesOrInternalResources() throws Exception {
        assertEquals(404, get("/nope.html").statusCode());
        assertEquals(404, get("/schema.sql").statusCode());
        assertEquals(404, get("/css").statusCode());
        assertEquals(404, get("/..%2Fschema.sql").statusCode());
        assertEquals(404, get("/css/..%2F..%2Fschema.sql").statusCode());
        assertEquals(405, send("POST", "/", "{}", "application/json").statusCode());
    }

    // ---------------------------------------------------------------- concepts and relations

    @Test
    void createsListsUpdatesAndDeletesConcepts() throws Exception {
        long id = createConcept("Feedback loop", "Biology");

        JsonObject graph = body(get("/api/graph"));
        assertEquals(1, graph.getAsJsonArray("concepts").size());
        assertEquals("Feedback loop", graph.getAsJsonArray("concepts").get(0).getAsJsonObject().get("name").getAsString());

        HttpResponse<String> updated = json("PUT", "/api/concepts/" + id,
                "{\"name\":\"Feedback\",\"discipline\":\"Systems\",\"description\":\"x\"}");
        assertEquals(200, updated.statusCode());
        assertEquals("Systems", body(updated).get("discipline").getAsString());

        assertEquals(200, send("DELETE", "/api/concepts/" + id, null, null).statusCode());
        assertEquals(404, send("DELETE", "/api/concepts/" + id, null, null).statusCode());
        assertEquals(0, body(get("/api/graph")).getAsJsonArray("concepts").size());
    }

    @Test
    void validationProblemsComeBackAsJsonErrors() throws Exception {
        HttpResponse<String> blank = json("POST", "/api/concepts", "{\"name\":\"  \",\"discipline\":\"Biology\"}");
        assertEquals(400, blank.statusCode());
        assertEquals("Give the concept a name.", body(blank).get("error").getAsString());

        createConcept("Habit loop", "Psychology");
        HttpResponse<String> duplicate = json("POST", "/api/concepts", "{\"name\":\"habit loop\",\"discipline\":\"Psychology\"}");
        assertEquals(409, duplicate.statusCode());
        assertTrue(body(duplicate).get("error").getAsString().contains("already exists"));

        assertEquals(400, json("POST", "/api/concepts", "{\"name\":5,\"discipline\":\"x\"}").statusCode());
    }

    @Test
    void rejectsMalformedBodiesAndWrongContentTypes() throws Exception {
        assertEquals(400, json("POST", "/api/concepts", "{not json").statusCode());
        assertEquals(400, json("POST", "/api/concepts", "[1,2,3]").statusCode());
        assertEquals(415, send("POST", "/api/concepts", "{\"name\":\"A\",\"discipline\":\"B\"}", "text/plain").statusCode());
        assertEquals(415, send("POST", "/api/concepts", "{\"name\":\"A\",\"discipline\":\"B\"}", null).statusCode());
        assertEquals(413, json("POST", "/api/concepts", "{\"name\":\"" + "a".repeat(1_100_000) + "\"}").statusCode());
    }

    @Test
    void createsAndDeletesRelations() throws Exception {
        long a = createConcept("A", "Biology");
        long b = createConcept("B", "Writing");

        HttpResponse<String> created = json("POST", "/api/relations",
                "{\"fromId\":" + a + ",\"toId\":" + b + ",\"label\":\"inspires\",\"strength\":4}");
        assertEquals(201, created.statusCode());
        long relationId = body(created).get("id").getAsLong();

        assertEquals(409, json("POST", "/api/relations",
                "{\"fromId\":" + a + ",\"toId\":" + b + ",\"label\":\"inspires\",\"strength\":2}").statusCode());
        assertEquals(400, json("POST", "/api/relations",
                "{\"fromId\":" + a + ",\"toId\":" + b + ",\"label\":\"x\",\"strength\":9}").statusCode());
        assertEquals(400, json("POST", "/api/relations",
                "{\"fromId\":\"one\",\"toId\":" + b + ",\"label\":\"x\"}").statusCode());
        assertEquals(400, json("POST", "/api/relations", "{\"toId\":" + b + ",\"label\":\"x\"}").statusCode());

        assertEquals(200, send("DELETE", "/api/relations/" + relationId, null, null).statusCode());
        assertEquals(404, send("DELETE", "/api/relations/" + relationId, null, null).statusCode());
    }

    // ---------------------------------------------------------------- queries

    @Test
    void findsPathsAndExplainsThem() throws Exception {
        long a = createConcept("Habit loop", "Psychology");
        long b = createConcept("Feedback loop", "Biology");
        long c = createConcept("Compounding", "Business");
        json("POST", "/api/relations", "{\"fromId\":" + a + ",\"toId\":" + b + ",\"label\":\"is an example of\",\"strength\":4}");
        json("POST", "/api/relations", "{\"fromId\":" + b + ",\"toId\":" + c + ",\"label\":\"drives\",\"strength\":3}");

        JsonObject result = body(get("/api/path?from=" + c + "&to=" + a + "&mode=fewest"));

        assertTrue(result.get("found").getAsBoolean());
        assertEquals(2, result.get("length").getAsInt());
        assertEquals("Compounding reaches Habit loop in 2 steps.", result.get("summary").getAsString());
        JsonArray steps = result.getAsJsonArray("steps");
        assertEquals("Feedback loop drives Compounding.", steps.get(0).getAsJsonObject().get("sentence").getAsString());
        assertEquals("Habit loop is an example of Feedback loop.", steps.get(1).getAsJsonObject().get("sentence").getAsString());
    }

    @Test
    void reportsWhenConceptsAreNotConnectedAndRejectsBadQueries() throws Exception {
        long a = createConcept("A", "X");
        long b = createConcept("B", "X");

        JsonObject result = body(get("/api/path?from=" + a + "&to=" + b));
        assertFalse(result.get("found").getAsBoolean());
        assertTrue(result.get("summary").getAsString().contains("not connected yet"));

        assertEquals(400, get("/api/path?from=abc&to=" + b).statusCode());
        assertEquals(400, get("/api/path?from=" + a).statusCode());
        assertEquals(400, get("/api/path?from=" + a + "&to=" + a).statusCode());
        assertEquals(400, get("/api/path?from=" + a + "&to=" + b + "&mode=teleport").statusCode());
        assertEquals(404, get("/api/path?from=" + a + "&to=9999").statusCode());
    }

    @Test
    void demoGraphProducesInsights() throws Exception {
        assertEquals(201, send("POST", "/api/demo", null, null).statusCode());
        assertEquals(409, send("POST", "/api/demo", null, null).statusCode());

        JsonObject insights = body(get("/api/insights"));
        assertTrue(insights.getAsJsonObject("counts").get("concepts").getAsInt() > 30);
        assertFalse(insights.getAsJsonArray("keystones").isEmpty());
        assertFalse(insights.getAsJsonArray("suggestions").isEmpty());
        assertFalse(insights.getAsJsonArray("disciplineLinks").isEmpty());
        assertEquals(1, insights.getAsJsonArray("clusters").size());
    }

    // ---------------------------------------------------------------- export / import

    @Test
    void exportedGraphsCanBeImportedAgain() throws Exception {
        send("POST", "/api/demo", null, null);
        HttpResponse<String> export = get("/api/export");
        assertEquals(200, export.statusCode());
        assertTrue(export.headers().firstValue("Content-Disposition").orElse("").contains("synthesis-export.json"));

        long a = createFreshAfterWipe();
        assertNotNull(a);

        HttpResponse<String> imported = json("POST", "/api/import", export.body());
        assertEquals(200, imported.statusCode());
        JsonObject graph = body(get("/api/graph"));
        JsonObject original = JsonParser.parseString(export.body()).getAsJsonObject();
        assertEquals(original.getAsJsonArray("concepts").size(), graph.getAsJsonArray("concepts").size());
        assertEquals(original.getAsJsonArray("relations").size(), graph.getAsJsonArray("relations").size());
    }

    private long createFreshAfterWipe() throws Exception {
        // Import replaces everything, so adding an extra concept first proves it is discarded.
        return createConcept("Temporary extra", "Scratch");
    }

    @Test
    void importRejectsBrokenFilesWithoutTouchingTheGraph() throws Exception {
        createConcept("Keep me", "Biology");

        HttpResponse<String> bad = json("POST", "/api/import",
                "{\"concepts\":[{\"name\":\"A\",\"discipline\":\"X\"}],\"relations\":[{\"from\":\"A\",\"to\":\"Ghost\",\"label\":\"x\"}]}");
        assertEquals(400, bad.statusCode());
        assertEquals(400, json("POST", "/api/import", "{\"concepts\":\"nope\"}").statusCode());
        assertEquals(400, json("POST", "/api/import", "{\"concepts\":[1]}").statusCode());

        JsonArray concepts = body(get("/api/graph")).getAsJsonArray("concepts");
        assertEquals(1, concepts.size());
        assertEquals("Keep me", concepts.get(0).getAsJsonObject().get("name").getAsString());
    }

    // ---------------------------------------------------------------- routing

    @Test
    void unknownEndpointsAndWrongMethodsGetClearAnswers() throws Exception {
        assertEquals(404, get("/api/nothing").statusCode());
        assertEquals(405, get("/api/concepts").statusCode());
        assertEquals(405, send("DELETE", "/api/graph", null, null).statusCode());
        assertEquals(405, get("/api/concepts/1").statusCode());
        assertEquals(404, get("/api/concepts/abc").statusCode());
    }
}
