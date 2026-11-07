package in.ac.iitm.slotbook.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP front end for the portal's "Generate slotbook" button ({@code portal/api/solver.js}).
 *
 * <pre>
 * GET  /health   {"ok":true,...}      no key needed; used to wake a sleeping free host
 * POST /solve    portal payload  -&gt;  {"ok":true,"slotbook":[...],"explanations":[...],...}
 * </pre>
 *
 * Environment: {@code PORT} (default 8080), {@code SOLVER_API_KEY} (required bearer token when set),
 * {@code SLOTBOOK_BASE} (default {@code data/feasible}), {@code SOLVER_TIME_LIMIT} seconds (default 8).
 */
public final class SolverServer {
    static final int MAX_BODY_BYTES = 5 * 1024 * 1024;

    private SolverServer() {}

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(env("PORT", "8080"));
        Path base = Path.of(env("SLOTBOOK_BASE", "data/feasible"));
        double timeLimit = Double.parseDouble(env("SOLVER_TIME_LIMIT", "8"));
        String key = System.getenv("SOLVER_API_KEY");
        if (!Files.isRegularFile(base.resolve("courses.csv"))) {
            throw new IOException("SLOTBOOK_BASE " + base.toAbsolutePath() + " has no courses.csv");
        }
        if (key == null || key.isBlank()) {
            System.err.println("WARNING: SOLVER_API_KEY is not set; anyone who finds this address can run solves.");
        }
        start(port, new SolveEndpoint(base, timeLimit), key);
        System.out.println("Slotbook solver service on port " + port + ", base dataset " + base.toAbsolutePath());
    }

    /** Starts the server; port 0 picks a free port (see {@code server.getAddress()}). */
    public static HttpServer start(int port, SolveEndpoint endpoint, String apiKey) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        // Two threads, so /health answers while a solve runs; solves themselves take turns.
        // Daemon threads: the server's own dispatcher thread keeps the process alive, and stop() in tests ends it.
        ExecutorService executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "slotbook-solver");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                route(exchange, endpoint, apiKey);
            } catch (RuntimeException | IOException fault) {
                fault.printStackTrace(System.err);
                send(exchange, SolveEndpoint.error(500, "Internal error"));
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private static void route(HttpExchange exchange, SolveEndpoint endpoint, String apiKey) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if ("/health".equals(path) || "/".equals(path) && "GET".equals(method)) {
            send(exchange, new SolveEndpoint.Reply(200, "{\"ok\":true,\"service\":\"slotbook-solver\"}"));
            return;
        }
        if (!"/solve".equals(path) && !"/".equals(path)) {
            send(exchange, SolveEndpoint.error(404, "Not found; POST /solve or GET /health"));
            return;
        }
        if (!"POST".equals(method)) {
            exchange.getResponseHeaders().add("Allow", "POST");
            send(exchange, SolveEndpoint.error(405, "Use POST"));
            return;
        }
        if (!authorised(exchange.getRequestHeaders().getFirst("Authorization"), apiKey)) {
            send(exchange, SolveEndpoint.error(401, "Missing or wrong Authorization: Bearer key"));
            return;
        }
        byte[] body;
        try (InputStream in = exchange.getRequestBody()) {
            body = in.readNBytes(MAX_BODY_BYTES + 1);
        }
        if (body.length > MAX_BODY_BYTES) {
            send(exchange, SolveEndpoint.error(413, "Request body is larger than 5 MB"));
            return;
        }
        send(exchange, endpoint.solve(new String(body, StandardCharsets.UTF_8)));
    }

    static boolean authorised(String header, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return true;
        byte[] expected = ("Bearer " + apiKey).getBytes(StandardCharsets.UTF_8);
        byte[] actual = (header == null ? "" : header).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static void send(HttpExchange exchange, SolveEndpoint.Reply reply) throws IOException {
        byte[] bytes = reply.json().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
