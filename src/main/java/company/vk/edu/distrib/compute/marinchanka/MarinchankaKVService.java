package company.vk.edu.distrib.compute.marinchanka;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.KVService;
import company.vk.edu.distrib.compute.Dao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

public class MarinchankaKVService implements KVService {
    private static final Logger log = LoggerFactory.getLogger(MarinchankaKVService.class);
    private static final String CONTENT_TYPE = "application/octet-stream";
    private static final int METHOD_NOT_ALLOWED = 405;
    private static final int BAD_REQUEST = 400;
    private static final int NOT_FOUND = 404;
    private static final int INTERNAL_ERROR = 500;
    private static final int STATUS_OK = 200;
    private static final int STATUS_CREATED = 201;
    private static final int STATUS_ACCEPTED = 202;
    private static final int SERVICE_UNAVAILABLE = 503;

    private final int port;
    private final Dao<byte[]> dao;
    private HttpServer server;
    private boolean running;

    public MarinchankaKVService(int port, Dao<byte[]> dao) {
        this.port = port;
        this.dao = dao;
    }

    @Override
    public void start() {
        if (running) {
            throw new IllegalStateException("Server already started");
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/v0/status", new StatusHandler());
            server.createContext("/v0/entity", new EntityHandler());

            server.setExecutor(null);
            server.start();
            running = true;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start server", e);
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        if (server != null) {
            server.stop(0);
        }
        try {
            dao.close();
        } catch (IOException e) {
            log.error("Error closing DAO", e);
        }
    }

    private final class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, -1);
                return;
            }

            if (running) {
                exchange.sendResponseHeaders(STATUS_OK, -1);
            } else {
                exchange.sendResponseHeaders(SERVICE_UNAVAILABLE, -1);
            }
        }
    }

    private final class EntityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String query = exchange.getRequestURI().getQuery();
            String id = extractId(query);

            if (id == null) {
                sendError(exchange, BAD_REQUEST, "Missing id parameter");
                return;
            }

            try {
                switch (method) {
                    case "GET":
                        handleGet(exchange, id);
                        break;
                    case "PUT":
                        handlePut(exchange, id);
                        break;
                    case "DELETE":
                        handleDelete(exchange, id);
                        break;
                    default:
                        exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, -1);
                        break;
                }
            } catch (IllegalArgumentException e) {
                sendError(exchange, BAD_REQUEST, e.getMessage());
            } catch (NoSuchElementException e) {
                sendError(exchange, NOT_FOUND, e.getMessage());
            } catch (IOException e) {
                sendError(exchange, INTERNAL_ERROR, "Internal server error");
            }
        }

        private void handleGet(HttpExchange exchange, String id) throws IOException {
            byte[] data = dao.get(id);
            exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE);
            exchange.sendResponseHeaders(STATUS_OK, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }

        private void handlePut(HttpExchange exchange, String id) throws IOException {
            byte[] data = exchange.getRequestBody().readAllBytes();
            dao.upsert(id, data);
            exchange.sendResponseHeaders(STATUS_CREATED, -1);
        }

        private void handleDelete(HttpExchange exchange, String id) throws IOException {
            dao.delete(id);
            exchange.sendResponseHeaders(STATUS_ACCEPTED, -1);
        }

        private String extractId(String query) {
            if (query == null) {
                return null;
            }
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2 && "id".equals(keyValue[0])) {
                    return keyValue[1];
                }
            }
            return null;
        }

        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            byte[] response = message.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}
