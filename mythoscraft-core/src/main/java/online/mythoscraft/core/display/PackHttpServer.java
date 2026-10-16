package online.mythoscraft.core.display;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/** Minimal read-only HTTP server that serves exactly one file: the resource pack. */
final class PackHttpServer {

    private final Logger logger;
    private HttpServer server;
    private ExecutorService executor;

    PackHttpServer(Logger logger) {
        this.logger = logger;
    }

    void start(String bind, int port, String path, byte[] pack) throws IOException {
        stop();
        server = HttpServer.create(new InetSocketAddress(bind, port), 16);
        executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "MythosCraft-PackHTTP");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/", exchange -> handle(exchange, path, pack));
        server.start();
    }

    private void handle(HttpExchange exchange, String path, byte[] pack) throws IOException {
        try (exchange) {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            if (!exchange.getRequestURI().getPath().equals(path)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, pack.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(pack);
            }
        } catch (IOException e) {
            logger.fine("Resource pack download aborted: " + e.getMessage());
        }
    }

    void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    boolean running() {
        return server != null;
    }
}
