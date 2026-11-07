package in.ac.iitm.slotbook.server;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The service end to end over HTTP, with the real solvers on data/feasible (the portal's catalog). */
class SolverServerTest {
    private static final String KEY = "test-key";
    private static final String EMPTY_PORTAL = "\"student_preferences_csv\":\"student_id,course_id,rank\\n\","
            + "\"instructor_preferences_csv\":\"instructor,slot,penalty,reason\\n\","
            + "\"prerequisites_csv\":\"course_id,prereq_course_id\\n\","
            + "\"completed_courses_csv\":\"student_id,course_id,grade,term,source\\n\"";

    private static HttpServer server;
    private static URI base;
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws IOException {
        server = SolverServer.start(0, new SolveEndpoint(Path.of("data/feasible"), 10), KEY);
        base = URI.create("http://localhost:" + server.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private static HttpResponse<String> post(String body, String key) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve("/solve"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (key != null) request.header("Authorization", "Bearer " + key);
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Map<?, ?> json(HttpResponse<String> response) {
        return assertInstanceOf(Map.class, Json.parse(response.body()), response.body());
    }

    private static String slotOf(Map<?, ?> reply, String courseId) {
        for (Object row : (List<?>) reply.get("slotbook")) {
            Map<?, ?> map = (Map<?, ?>) row;
            if (courseId.equals(map.get("course_id"))) return (String) map.get("assigned_slot");
        }
        throw new AssertionError(courseId + " missing from " + reply.get("slotbook"));
    }

    @Test
    void healthNeedsNoKey() throws Exception {
        HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder(base.resolve("/health")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals(Boolean.TRUE, json(response).get("ok"));
    }

    @Test
    void hostProbesWithHeadGetAnEmptyOk() throws Exception {
        for (String path : List.of("/", "/health")) {
            HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder(base.resolve(path))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), path);
            assertEquals("", response.body(), path);
        }
    }

    @Test
    void solvingNeedsTheKey() throws Exception {
        assertEquals(401, post("{" + EMPTY_PORTAL + "}", null).statusCode());
        assertEquals(401, post("{" + EMPTY_PORTAL + "}", "wrong").statusCode());
    }

    @Test
    void withNoPortalRowsTheReplyIsTheCliResultOnTheSampleData() throws Exception {
        HttpResponse<String> response = post("{\"solver\":\"cpsat\"," + EMPTY_PORTAL + "}", KEY);
        assertEquals(200, response.statusCode(), response.body());
        Map<?, ?> reply = json(response);
        // 20 is the optimum CP-SAT proves on data/feasible from the CLI (docs_v2, CI demo runs).
        assertEquals(20.0, reply.get("objective"));
        List<?> slotbook = (List<?>) reply.get("slotbook");
        assertEquals(slotbook.size(), ((List<?>) reply.get("explanations")).size());
        Map<?, ?> first = (Map<?, ?>) slotbook.get(0);
        for (String column : List.of("course_id", "title", "semester_group", "course_type", "instructor",
                "assigned_slot", "additional_slot", "room", "class_size")) {
            assertTrue(first.containsKey(column), column);
        }
        Map<?, ?> why = (Map<?, ?>) ((List<?>) reply.get("explanations")).get(0);
        assertFalse(((String) why.get("reasons")).isBlank());
    }

    @Test
    void aProfessorsNewPenaltyMovesTheirCourse() throws Exception {
        String before = slotOf(json(post("{\"solver\":\"cpsat\"," + EMPTY_PORTAL + "}", KEY)), "EE1101");
        String body = "{\"solver\":\"cpsat\",\"instructor_preferences_csv\":"
                + "\"instructor,slot,penalty,reason\\nPradeep K," + before + ",100,Department meeting\\n\"}";
        HttpResponse<String> response = post(body, KEY);
        assertEquals(200, response.statusCode(), response.body());
        Map<?, ?> reply = json(response);
        assertEquals(1.0, reply.get("merged_instructor_rows"));
        assertNotEquals(before, slotOf(reply, "EE1101"), "EE1101 should leave the slot its instructor now penalises");
    }

    @Test
    void theDefaultSolverIsUniTime() throws Exception {
        HttpResponse<String> response = post("{" + EMPTY_PORTAL + "}", KEY);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("unitime", json(response).get("solver"));
    }

    @Test
    void invalidSubmissionsAreReportedNotSolved() throws Exception {
        HttpResponse<String> response = post(
                "{\"student_preferences_csv\":\"student_id,course_id,rank\\nX1,ZZ9999,1\\n\"}", KEY);
        assertEquals(422, response.statusCode(), response.body());
        Map<?, ?> reply = json(response);
        assertEquals(Boolean.FALSE, reply.get("ok"));
        assertTrue(((String) reply.get("error")).contains("ZZ9999"), response.body());
    }

    @Test
    void malformedRequestsAre400() throws Exception {
        assertEquals(400, post("{\"student_preferences_csv\":", KEY).statusCode());
        assertEquals(400, post("[1,2]", KEY).statusCode());
        assertEquals(400, post("{\"student_preferences_csv\":\"student,course,rank\\nX,EE1101,1\\n\"}", KEY).statusCode());
    }

    @Test
    void theKeyIsComparedExactly() {
        assertTrue(SolverServer.authorised(null, null));
        assertTrue(SolverServer.authorised("Bearer k", "k"));
        assertFalse(SolverServer.authorised("Bearer k ", "k"));
        assertFalse(SolverServer.authorised("k", "k"));
        assertFalse(SolverServer.authorised(null, "k"));
    }
}
