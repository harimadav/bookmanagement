// Server.java
import com.sun.net.httpserver.*;
import java.net.InetSocketAddress;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

public class Server {
    static final String DB = "database.json";
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        // ✅ Use PORT env variable (for Render) or default to 5000 (for Docker/local)
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "5000"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // ✅ Root endpoint to show server running status
        server.createContext("/", (exchange) -> {
            addCORS(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                List<Book> books = readBooks();
                writeJson(exchange, books);
            } else if ("POST".equalsIgnoreCase(method)) {
                Book incoming = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), Book.class);
                List<Book> books = readBooks();
                int nextId = books.stream().mapToInt(b -> b.id).max().orElse(0) + 1;
                incoming.id = nextId;
                incoming.status = "Available";
                incoming.borrower = "";
                incoming.dueDate = "";
                books.add(incoming);
                writeBooks(books);
                writeJson(exchange, incoming);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }

            exchange.close();
        });

        // ✅ Borrow / Return / Renew routes
        server.createContext("/action", (exchange) -> {
            addCORS(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath(); // /action/{id}/{operation}
            String[] parts = path.split("/");
            if (parts.length < 4) { exchange.sendResponseHeaders(404, -1); return; }

            int id = Integer.parseInt(parts[2]);
            String action = parts[3];

            List<Book> books = readBooks();
            Optional<Book> ob = books.stream().filter(b -> b.id == id).findFirst();
            if (!ob.isPresent()) { exchange.sendResponseHeaders(404, -1); return; }
            Book book = ob.get();

            if ("borrow".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
                Map<String, String> body = gson.fromJson(
                    new InputStreamReader(exchange.getRequestBody()),
                    new TypeToken<Map<String, String>>() {}.getType()
                );
                String borrower = body.getOrDefault("borrower", "unknown");
                if (!"Available".equalsIgnoreCase(book.status)) {
                    sendText(exchange, 400, "Book not available");
                } else {
                    book.status = "Borrowed";
                    book.borrower = borrower;
                    book.dueDate = java.time.LocalDate.now().plusDays(7).toString();
                    writeBooks(books);
                    writeJson(exchange, book);
                }
            } else if ("return".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
                book.status = "Available";
                book.borrower = "";
                book.dueDate = "";
                writeBooks(books);
                writeJson(exchange, book);
            } else if ("renew".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
                if (!"Borrowed".equalsIgnoreCase(book.status)) {
                    sendText(exchange, 400, "Book is not borrowed");
                } else {
                    book.dueDate = java.time.LocalDate.parse(book.dueDate).plusDays(7).toString();
                    writeBooks(books);
                    writeJson(exchange, book);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }

            exchange.close();
        });

        server.setExecutor(null);
        server.start();
        System.out.println("✅ Book Management Server running at http://localhost:" + port);
    }

    // ---------- Utility Methods ----------
    static void addCORS(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    static List<Book> readBooks() throws IOException {
        Path p = Paths.get(DB);
        if (!Files.exists(p)) return new ArrayList<>();
        String json = new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8);
        return gson.fromJson(json, new TypeToken<List<Book>>() {}.getType());
    }

    static void writeBooks(List<Book> books) throws IOException {
        String json = gson.toJson(books);
        Files.write(Paths.get(DB), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static void writeJson(HttpExchange ex, Object obj) throws IOException {
        String s = gson.toJson(obj);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
    }

    static void sendText(HttpExchange ex, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/plain");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
    }
}

class Book {
    int id;
    String name;
    String author;
    String status;
    String borrower;
    String dueDate;
}
