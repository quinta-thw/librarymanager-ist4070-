import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.time.LocalDateTime;

public class AuthenticatedLibraryWebServer {
    private ArrayList<Book> books;
    private Map<String, SimpleUser> users;
    private Map<String, SimpleUser> sessions; // sessionId -> user
    private Map<String, LibraryChatbot> userChatbots; // sessionId -> chatbot
    private Map<String, Integer> loginAttempts; // IP -> attempt count
    private Map<String, LocalDateTime> lastLoginAttempt; // IP -> last attempt time
    private Map<String, LocalDateTime> sessionTimestamps; // sessionId -> creation time

    // MongoDB integration
    private MongoDBManager mongoManager;

    private final String DATA_FILE = "library_data.txt";
    private final String[] GENRES = {
            "Fiction", "Non-Fiction", "Mystery", "Romance", "Science Fiction",
            "Fantasy", "Biography", "History", "Self-Help", "Business", "Other"
    };

    public AuthenticatedLibraryWebServer() {
        books = new ArrayList<>();
        users = new HashMap<>();
        sessions = new HashMap<>();
        userChatbots = new HashMap<>();
        loginAttempts = new HashMap<>();
        lastLoginAttempt = new HashMap<>();
        sessionTimestamps = new HashMap<>();

        // Initialize MongoDB connection
        try {
            mongoManager = new MongoDBManager();
            System.out.println("🔗 MongoDB connection established - using database storage");

            // Load data from MongoDB
            loadBooksFromMongoDB();
            loadUsersFromMongoDB();
        } catch (Exception e) {
            System.err.println("⚠️  MongoDB connection failed, falling back to file storage");
            mongoManager = null;
            // Initialize default users and load from file as fallback
            initializeUsers();
            loadBooksFromFile();
        }
    }

    private void initializeUsers() {
        // Default librarian
        users.put("librarian", new SimpleUser("librarian", "admin123", SimpleUser.Role.LIBRARIAN, "Head Librarian"));
        users.put("admin", new SimpleUser("admin", "admin123", SimpleUser.Role.LIBRARIAN, "Library Administrator"));

        // Default users
        users.put("user1", new SimpleUser("user1", "user123", SimpleUser.Role.USER, "John Reader"));
        users.put("reader", new SimpleUser("reader", "read123", SimpleUser.Role.USER, "Book Reader"));
    }

    /**
     * Load books from MongoDB
     */
    private void loadBooksFromMongoDB() {
        if (mongoManager != null) {
            List<Book> loadedBooks = mongoManager.loadBooks();
            books.clear();
            books.addAll(loadedBooks);
        }
    }

    /**
     * Load users from MongoDB
     */
    private void loadUsersFromMongoDB() {
        if (mongoManager != null) {
            Map<String, SimpleUser> loadedUsers = mongoManager.loadUsers();
            users.clear();
            users.putAll(loadedUsers);
        }
    }

    /**
     * Save books to MongoDB (replaces saveBooksToFile)
     */
    private void saveBooksToMongoDB() {
        if (mongoManager != null) {
            mongoManager.saveBooks(books);
        } else {
            // Fallback to file storage
            saveBooksToFile();
        }
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Authentication routes
        server.createContext("/", new LoginPageHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/logout", new LogoutHandler());

        // Protected routes
        server.createContext("/dashboard", new DashboardHandler());
        server.createContext("/api/books", new BooksHandler());
        server.createContext("/api/add", new AddBookHandler());
        server.createContext("/api/remove", new RemoveBookHandler());
        server.createContext("/api/update", new UpdateBookHandler());
        server.createContext("/api/stats", new StatsHandler());
        server.createContext("/api/chat", new ChatHandler());
        server.createContext("/api/set-ai-key", new SetAIKeyHandler());
        server.createContext("/api/ai-status", new AIStatusHandler());
        server.createContext("/api/clear-ai-key", new ClearAIKeyHandler());
        server.createContext("/browse", new BookBrowseHandler());
        server.createContext("/api/book-details", new BookDetailsHandler());
        server.createContext("/api/update-reading", new UpdateReadingHandler());
        server.createContext("/api/search-books", new BookSearchHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("🌟 Authenticated Library Manager started on port " + port);
        System.out.println("🔗 Open: http://localhost:" + port);
        System.out.println("� Secure login system with password hashing and rate limiting");
        System.out.println("📚 Full featured library with role-based access and AI chatbot!");
    }

    class LoginPageHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String response = getLoginPage();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    class LoginHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String clientIP = getClientIP(exchange);

            // Check if IP is blocked due to failed attempts
            if (isIPBlocked(clientIP)) {
                String response = "{\"success\": false, \"message\": \"Too many failed attempts. Please try again in 15 minutes.\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(429, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseFormData(body);

            String username = params.get("username");
            String password = params.get("password");

            if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
                recordLoginAttempt(clientIP, false);
                String response = "{\"success\": false, \"message\": \"Username and password are required\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(400, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            SimpleUser user = users.get(username);
            boolean authenticated = false;

            if (user != null) {
                String salt = user.getSalt();

                // Check if password is already hashed (user has a salt) or plain text
                if (salt != null && !salt.isEmpty()) {
                    // Password is hashed - hash the input password and compare
                    String hashedInputPassword = hashPassword(password, salt);
                    authenticated = user.getPassword().equals(hashedInputPassword);
                } else {
                    // Plain text password - check directly and hash it for future logins
                    authenticated = user.getPassword().equals(password);

                    // Update to hashed password after successful login
                    if (authenticated) {
                        salt = generateSalt();
                        user.setSalt(salt);
                        String hashedPassword = hashPassword(password, salt);
                        user.setPassword(hashedPassword);

                        // Save updated user to MongoDB
                        if (mongoManager != null) {
                            mongoManager.saveUser(user);
                        }
                    }
                }
            }

            if (authenticated) {
                recordLoginAttempt(clientIP, true);

                String sessionId = UUID.randomUUID().toString();
                sessions.put(sessionId, user);
                sessionTimestamps.put(sessionId, LocalDateTime.now());

                // Initialize chatbot for this user session
                LibraryChatbot chatbot = new LibraryChatbot(books);
                chatbot.setCurrentUser(user);
                userChatbots.put(sessionId, chatbot);

                // Set secure session cookie with HttpOnly and SameSite
                exchange.getResponseHeaders().set("Set-Cookie",
                        "sessionId=" + sessionId + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=28800"); // 8 hours
                exchange.getResponseHeaders().set("Location", "/dashboard");
                exchange.sendResponseHeaders(302, -1);
            } else {
                recordLoginAttempt(clientIP, false);

                // Add delay to prevent brute force attacks
                try {
                    Thread.sleep(1000); // 1 second delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                String response = "{\"success\": false, \"message\": \"Invalid credentials\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    class LogoutHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String sessionId = getSessionId(exchange);
            if (sessionId != null) {
                sessions.remove(sessionId);
                userChatbots.remove(sessionId); // Clean up chatbot
            }

            exchange.getResponseHeaders().set("Set-Cookie", "sessionId=; Path=/; Max-Age=0");
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(302, -1);
        }
    }

    class DashboardHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            SimpleUser user = getAuthenticatedUser(exchange);
            if (user == null) {
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            String response = getDashboardPage(user);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    class BooksHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            SimpleUser user = getAuthenticatedUser(exchange);
            if (user == null) {
                sendUnauthorized(exchange);
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                String response = getBooksJson();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    class AddBookHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            SimpleUser user = getAuthenticatedUser(exchange);
            if (user == null) {
                sendUnauthorized(exchange);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("📚 ADD BOOK REQUEST - Body: " + body);
                Map<String, String> params = parseFormData(body);
                System.out.println("📚 ADD BOOK REQUEST - Parsed params: " + params);

                String title = params.get("title");
                String author = params.get("author");
                String yearStr = params.get("year");
                String genre = params.get("genre");
                String status = params.get("status");
                String rating = params.get("rating");
                String notes = params.get("notes");

                System.out.println("📚 ADD BOOK REQUEST - Fields: title=" + title + ", author=" + author + ", year="
                        + yearStr + ", genre=" + genre);

                if (title != null && author != null && !title.trim().isEmpty() && !author.trim().isEmpty()) {
                    int year = 0;
                    try {
                        if (yearStr != null && !yearStr.trim().isEmpty()) {
                            year = Integer.parseInt(yearStr);
                        }
                    } catch (NumberFormatException e) {
                        // Keep year as 0 if invalid
                    }

                    Book book = new Book(title.trim(), author.trim(), year);
                    if (genre != null && !genre.trim().isEmpty()) {
                        book.setGenre(genre);
                    }
                    if (status != null && !status.trim().isEmpty()) {
                        book.setStatus(status);
                    }
                    if (rating != null && !rating.trim().isEmpty() && !rating.equals("No Rating")) {
                        try {
                            book.setRating(Integer.parseInt(rating));
                        } catch (NumberFormatException e) {
                            // Keep default rating if invalid
                        }
                    }
                    if (notes != null && !notes.trim().isEmpty()) {
                        book.setNotes(notes.trim());
                    }

                    books.add(book);
                    saveBooksToMongoDB();

                    String response = "{\"success\": true, \"message\": \"Book added successfully!\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                } else {
                    String response = "{\"success\": false, \"message\": \"Title and Author are required\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(400, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                }
            }
        }
    }

    class StatsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            SimpleUser user = getAuthenticatedUser(exchange);
            if (user == null) {
                sendUnauthorized(exchange);
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                String response = getStatsJson();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    class RemoveBookHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            SimpleUser user = getAuthenticatedUser(exchange);
            if (user == null || !user.isLibrarian()) {
                sendUnauthorized(exchange);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);
                String title = params.get("title");

                if (title != null) {
                    boolean removed = books.removeIf(book -> book.getTitle().equalsIgnoreCase(title.trim()));
                    if (removed) {
                        saveBooksToMongoDB();
                        String response = "{\"success\": true, \"message\": \"Book removed successfully!\"}";
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, response.length());
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }
                    } else {
                        String response = "{\"success\": false, \"message\": \"Book not found\"}";
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(404, response.length());
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }
                    }
                }
            }
        }
    }

    class UpdateBookHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            SimpleUser user = getAuthenticatedUser(exchange);
            if (user == null) {
                sendUnauthorized(exchange);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);

                String title = params.get("title");
                String newStatus = params.get("status");
                String newRating = params.get("rating");

                if (title != null) {
                    Book book = books.stream()
                            .filter(b -> b.getTitle().equalsIgnoreCase(title.trim()))
                            .findFirst()
                            .orElse(null);

                    if (book != null) {
                        if (newStatus != null && !newStatus.trim().isEmpty()) {
                            book.setStatus(newStatus);
                        }
                        if (newRating != null && !newRating.trim().isEmpty() && !newRating.equals("No Rating")) {
                            try {
                                book.setRating(Integer.parseInt(newRating));
                            } catch (NumberFormatException e) {
                                // Keep existing rating if invalid
                            }
                        }
                        saveBooksToMongoDB();

                        String response = "{\"success\": true, \"message\": \"Book updated successfully!\"}";
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, response.length());
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }
                    } else {
                        String response = "{\"success\": false, \"message\": \"Book not found\"}";
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(404, response.length());
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }
                    }
                }
            }
        }
    }

    class ChatHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            try {
                SimpleUser user = getAuthenticatedUser(exchange);
                if (user == null) {
                    sendUnauthorized(exchange);
                    return;
                }

                if ("POST".equals(exchange.getRequestMethod())) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, String> params = parseFormData(body);
                    String message = params.get("message");

                    if (message != null && !message.trim().isEmpty()) {
                        String sessionId = getSessionId(exchange);
                        LibraryChatbot chatbot = userChatbots.get(sessionId);

                        if (chatbot == null) {
                            // Create new chatbot if none exists
                            chatbot = new LibraryChatbot(books);
                            chatbot.setCurrentUser(user);
                            userChatbots.put(sessionId, chatbot);
                        }

                        String response = chatbot.processMessage(message.trim());

                        String jsonResponse = "{\"success\": true, \"response\": \"" + escapeJson(response) + "\"}";
                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(responseBytes);
                        }
                    } else {
                        String response = "{\"success\": false, \"message\": \"Message cannot be empty\"}";
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(400, responseBytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(responseBytes);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Chat error: " + e.getMessage());
                e.printStackTrace();
                String errorResponse = "{\"success\": false, \"response\": \"Sorry, I'm having trouble connecting. Please try again.\"}";
                byte[] responseBytes = errorResponse.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        }
    }

    class SetAIKeyHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            SimpleUser user = getAuthenticatedUser(exchange);
            if (user == null || !user.isLibrarian()) {
                sendUnauthorized(exchange);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);
                String apiKey = params.get("apiKey");

                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    // Update all chatbot instances with the new API key
                    for (LibraryChatbot chatbot : userChatbots.values()) {
                        chatbot.setOpenAIKey(apiKey);
                    }

                    String response = "{\"success\": true, \"message\": \"AI chatbot activated successfully! 🤖\"}";
                    byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                    }
                } else {
                    String response = "{\"success\": false, \"message\": \"API key cannot be empty\"}";
                    byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(400, responseBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                    }
                }
            }
        }
    }

    class AIStatusHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            SimpleUser user = getAuthenticatedUser(exchange);
            if (user == null || !user.isLibrarian()) {
                sendUnauthorized(exchange);
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                // Check if any chatbot has AI enabled
                boolean aiEnabled = false;
                String status = "AI features are disabled. Configure OpenAI API key to enable intelligent responses.";

                for (LibraryChatbot chatbot : userChatbots.values()) {
                    if (chatbot.isAIEnabled()) {
                        aiEnabled = true;
                        status = "OpenAI GPT-4 is active and providing intelligent responses.";
                        break;
                    }
                }

                String response = String.format("{\"enabled\": %b, \"status\": \"%s\"}", aiEnabled, escapeJson(status));
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        }
    }

    class ClearAIKeyHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            SimpleUser user = getAuthenticatedUser(exchange);
            if (user == null || !user.isLibrarian()) {
                sendUnauthorized(exchange);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                // Clear AI configuration for all chatbot instances
                for (LibraryChatbot chatbot : userChatbots.values()) {
                    chatbot.setOpenAIKey(""); // This will disable AI
                }

                String response = "{\"success\": true, \"message\": \"AI configuration cleared. Switched to standard mode.\"}";
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        }
    }

    // Helper methods
    private SimpleUser getAuthenticatedUser(HttpExchange exchange) {
        String sessionId = getSessionId(exchange);
        if (sessionId != null) {
            // Check if session is expired
            if (isSessionExpired(sessionId)) {
                // Clean up expired session
                sessions.remove(sessionId);
                sessionTimestamps.remove(sessionId);
                userChatbots.remove(sessionId);
                return null;
            }

            // Update session timestamp on activity
            sessionTimestamps.put(sessionId, LocalDateTime.now());
            return sessions.get(sessionId);
        }
        return null;
    }

    private String getSessionId(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie != null) {
            String[] cookies = cookie.split(";");
            for (String c : cookies) {
                String[] parts = c.trim().split("=");
                if (parts.length == 2 && "sessionId".equals(parts[0])) {
                    return parts[1];
                }
            }
        }
        return null;
    }

    private void sendUnauthorized(HttpExchange exchange) throws IOException {
        String response = "{\"success\": false, \"message\": \"Unauthorized\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(401, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body != null && !body.isEmpty()) {
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    try {
                        String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                        String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                        params.put(key, value);
                    } catch (Exception e) {
                        // Skip invalid pairs
                    }
                }
            }
        }
        return params;
    }

    private String getLoginPage() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Library Login</title>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body {\n" +
                "            font-family: 'Georgia', 'Times New Roman', serif;\n" +
                "            background: linear-gradient(135deg, #2c3e50 0%, #34495e 25%, #1a252f 50%, #2c3e50 75%, #34495e 100%);\n"
                +
                "            background-size: 400% 400%;\n" +
                "            animation: gradientShift 15s ease infinite;\n" +
                "            min-height: 100vh;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            position: relative;\n" +
                "        }\n" +
                "        .login-container {\n" +
                "            background: linear-gradient(135deg, rgba(52, 73, 94, 0.95) 0%, rgba(44, 62, 80, 0.98) 100%);\n"
                +
                "            backdrop-filter: blur(15px);\n" +
                "            border: 2px solid #d4af37;\n" +
                "            padding: 40px;\n" +
                "            border-radius: 20px;\n" +
                "            box-shadow: 0 25px 50px rgba(0, 0, 0, 0.4);\n" +
                "            width: 100%;\n" +
                "            max-width: 400px;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        h1 {\n" +
                "            color: #d4af37;\n" +
                "            margin-bottom: 30px;\n" +
                "            font-size: 28px;\n" +
                "        }\n" +
                "        .form-group {\n" +
                "            margin-bottom: 20px;\n" +
                "            text-align: left;\n" +
                "        }\n" +
                "        label {\n" +
                "            display: block;\n" +
                "            margin-bottom: 5px;\n" +
                "            font-weight: 600;\n" +
                "            color: #ecf0f1;\n" +
                "            text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.5);\n" +
                "        }\n" +
                "        input[type=\"text\"], input[type=\"password\"] {\n" +
                "            width: 100%;\n" +
                "            padding: 12px;\n" +
                "            border: 2px solid #ddd;\n" +
                "            border-radius: 8px;\n" +
                "            font-size: 16px;\n" +
                "            transition: border-color 0.3s;\n" +
                "        }\n" +
                "        input[type=\"text\"]:focus, input[type=\"password\"]:focus {\n" +
                "            outline: none;\n" +
                "            border-color: #667eea;\n" +
                "        }\n" +
                "        .login-btn {\n" +
                "            width: 100%;\n" +
                "            padding: 12px;\n" +
                "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            border-radius: 8px;\n" +
                "            font-size: 16px;\n" +
                "            font-weight: 600;\n" +
                "            cursor: pointer;\n" +
                "            transition: transform 0.2s;\n" +
                "        }\n" +
                "        .login-btn:hover {\n" +
                "            transform: translateY(-2px);\n" +
                "        }\n" +
                "        .security-notice {\n" +
                "            margin-top: 30px;\n" +
                "            padding: 20px;\n" +
                "            background: #e8f5e8;\n" +
                "            border: 1px solid #4caf50;\n" +
                "            border-radius: 10px;\n" +
                "            text-align: center;\n" +
                "            color: #2e7d32;\n" +
                "        }\n" +
                "        .security-notice p {\n" +
                "            margin: 5px 0;\n" +
                "        }\n" +
                "        .security-notice small {\n" +
                "            color: #666;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"login-container\">\n" +
                "        <h1>📚 Library System</h1>\n" +
                "        <form method=\"POST\" action=\"/api/login\">\n" +
                "            <div class=\"form-group\">\n" +
                "                <label for=\"username\">Username:</label>\n" +
                "                <input type=\"text\" id=\"username\" name=\"username\" required>\n" +
                "            </div>\n" +
                "            <div class=\"form-group\">\n" +
                "                <label for=\"password\">Password:</label>\n" +
                "                <input type=\"password\" id=\"password\" name=\"password\" required>\n" +
                "            </div>\n" +
                "            <button type=\"submit\" class=\"login-btn\">� Secure Login</button>\n" +
                "        </form>\n" +
                "        \n" +
                "        <div class=\"security-notice\">\n" +
                "            <p>🔒 Your connection is secure and protected.</p>\n" +
                "            <p><small>Contact your administrator for account access.</small></p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private String getDashboardPage(SimpleUser user) {
        if (user.isLibrarian()) {
            return getLibrarianDashboard(user);
        } else {
            return getUserDashboard(user);
        }
    }

    private String getLibrarianDashboard(SimpleUser user) {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Librarian Dashboard</title>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body {\n" +
                "            font-family: 'Georgia', 'Times New Roman', serif;\n" +
                "            background: linear-gradient(135deg, #2c3e50 0%, #34495e 25%, #1a252f 50%, #2c3e50 75%, #34495e 100%);\n"
                +
                "            background-size: 400% 400%;\n" +
                "            animation: gradientShift 15s ease infinite;\n" +
                "            min-height: 100vh;\n" +
                "            color: #ecf0f1;\n" +
                "            position: relative;\n" +
                "        }\n" +
                "        .header {\n" +
                "            padding: 20px;\n" +
                "            text-align: center;\n" +
                "            border-bottom: 1px solid rgba(255,255,255,0.2);\n" +
                "        }\n" +
                "        .header h1 {\n" +
                "            font-size: 32px;\n" +
                "            margin-bottom: 10px;\n" +
                "        }\n" +
                "        .user-info {\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            align-items: center;\n" +
                "            opacity: 0.9;\n" +
                "        }\n" +
                "        .logout-btn {\n" +
                "            background: rgba(255,255,255,0.2);\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            padding: 10px 20px;\n" +
                "            border-radius: 20px;\n" +
                "            cursor: pointer;\n" +
                "            text-decoration: none;\n" +
                "        }\n" +
                "        .stats-container {\n" +
                "            display: grid;\n" +
                "            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));\n" +
                "            gap: 20px;\n" +
                "            padding: 30px;\n" +
                "            max-width: 1200px;\n" +
                "            margin: 0 auto;\n" +
                "        }\n" +
                "        .stat-card {\n" +
                "            background: rgba(255,255,255,0.15);\n" +
                "            backdrop-filter: blur(10px);\n" +
                "            border-radius: 20px;\n" +
                "            padding: 30px;\n" +
                "            text-align: center;\n" +
                "            border: 1px solid rgba(255,255,255,0.2);\n" +
                "        }\n" +
                "        .stat-number {\n" +
                "            font-size: 48px;\n" +
                "            font-weight: bold;\n" +
                "            margin-bottom: 10px;\n" +
                "        }\n" +
                "        .stat-label {\n" +
                "            font-size: 18px;\n" +
                "            opacity: 0.9;\n" +
                "        }\n" +
                "        .management-section {\n" +
                "            background: rgba(255,255,255,0.1);\n" +
                "            margin: 20px;\n" +
                "            border-radius: 20px;\n" +
                "            padding: 30px;\n" +
                "        }\n" +
                "        .management-section h2 {\n" +
                "            margin-bottom: 20px;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        .form-group {\n" +
                "            margin-bottom: 15px;\n" +
                "        }\n" +
                "        .form-group label {\n" +
                "            display: block;\n" +
                "            margin-bottom: 5px;\n" +
                "            font-weight: 600;\n" +
                "        }\n" +
                "        .form-group input, .form-group select {\n" +
                "            width: 100%;\n" +
                "            padding: 10px;\n" +
                "            border: 1px solid rgba(255,255,255,0.3);\n" +
                "            border-radius: 8px;\n" +
                "            background: rgba(255,255,255,0.1);\n" +
                "            color: white;\n" +
                "        }\n" +
                "        .form-group input::placeholder {\n" +
                "            color: rgba(255,255,255,0.7);\n" +
                "        }\n" +
                "        .btn {\n" +
                "            background: rgba(255,255,255,0.2);\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            padding: 12px 24px;\n" +
                "            border-radius: 8px;\n" +
                "            cursor: pointer;\n" +
                "            font-weight: 600;\n" +
                "            margin-right: 10px;\n" +
                "        }\n" +
                "        .btn:hover {\n" +
                "            background: rgba(255,255,255,0.3);\n" +
                "        }\n" +
                "        .books-list {\n" +
                "            max-height: 300px;\n" +
                "            overflow-y: auto;\n" +
                "            background: rgba(255,255,255,0.1);\n" +
                "            border-radius: 10px;\n" +
                "            padding: 15px;\n" +
                "        }\n" +
                "        .book-item {\n" +
                "            background: rgba(255,255,255,0.1);\n" +
                "            margin-bottom: 10px;\n" +
                "            padding: 15px;\n" +
                "            border-radius: 8px;\n" +
                "        }\n" +
                "        .chatbot-widget {\n" +
                "            position: fixed;\n" +
                "            bottom: 20px;\n" +
                "            right: 20px;\n" +
                "            width: 60px;\n" +
                "            height: 60px;\n" +
                "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
                "            border-radius: 50%;\n" +
                "            cursor: pointer;\n" +
                "            box-shadow: 0 4px 20px rgba(102, 126, 234, 0.4);\n" +
                "            z-index: 1000;\n" +
                "            transition: all 0.3s ease;\n" +
                "        }\n" +
                "        .chatbot-widget:hover {\n" +
                "            transform: scale(1.1);\n" +
                "            box-shadow: 0 6px 25px rgba(102, 126, 234, 0.6);\n" +
                "        }\n" +
                "        .chatbot-toggle {\n" +
                "            color: white;\n" +
                "            font-size: 24px;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            width: 100%;\n" +
                "            height: 100%;\n" +
                "        }\n" +
                "        .chatbot-panel {\n" +
                "            position: fixed;\n" +
                "            bottom: 90px;\n" +
                "            right: 20px;\n" +
                "            width: 350px;\n" +
                "            height: 400px;\n" +
                "            background: white;\n" +
                "            border-radius: 15px;\n" +
                "            box-shadow: 0 10px 40px rgba(0,0,0,0.2);\n" +
                "            z-index: 999;\n" +
                "            display: none;\n" +
                "            flex-direction: column;\n" +
                "        }\n" +
                "        .chatbot-header {\n" +
                "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
                "            color: white;\n" +
                "            padding: 15px;\n" +
                "            border-radius: 15px 15px 0 0;\n" +
                "            font-weight: bold;\n" +
                "        }\n" +
                "        .chatbot-messages {\n" +
                "            flex: 1;\n" +
                "            padding: 15px;\n" +
                "            overflow-y: auto;\n" +
                "            max-height: 300px;\n" +
                "        }\n" +
                "        .chatbot-input {\n" +
                "            display: flex;\n" +
                "            padding: 15px;\n" +
                "            border-top: 1px solid #eee;\n" +
                "        }\n" +
                "        .chatbot-input input {\n" +
                "            flex: 1;\n" +
                "            padding: 10px;\n" +
                "            border: 1px solid #ddd;\n" +
                "            border-radius: 20px;\n" +
                "            margin-right: 10px;\n" +
                "        }\n" +
                "        .chatbot-send {\n" +
                "            background: #667eea;\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            padding: 10px 15px;\n" +
                "            border-radius: 20px;\n" +
                "            cursor: pointer;\n" +
                "        }\n" +
                "        .chat-message {\n" +
                "            margin-bottom: 10px;\n" +
                "            padding: 8px 12px;\n" +
                "            border-radius: 15px;\n" +
                "            max-width: 80%;\n" +
                "        }\n" +
                "        .user-message {\n" +
                "            background: #667eea;\n" +
                "            color: white;\n" +
                "            margin-left: auto;\n" +
                "            text-align: right;\n" +
                "        }\n" +
                "        .bot-message {\n" +
                "            background: #f1f3f5;\n" +
                "            color: #333;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"header\">\n" +
                "        <div class=\"user-info\">\n" +
                "            <span>📊 Welcome, " + user.getFullName() + "</span>\n" +
                "            <a href=\"/api/logout\" class=\"logout-btn\">🚪 Logout</a>\n" +
                "        </div>\n" +
                "        <h1>📚 Library Manager</h1>\n" +
                "    </div>\n" +
                "\n" +
                "    <div class=\"stats-container\" id=\"stats\">\n" +
                "        <!-- Stats will be loaded here -->\n" +
                "    </div>\n" +
                "\n" +
                "    <div class=\"management-section\">\n" +
                "        <h2>📖 Library Management</h2>\n" +
                "        \n" +
                "        <div style=\"display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 20px; margin-bottom: 20px;\">\n"
                +
                "            <button class=\"btn\" onclick=\"loadBooksDetailed()\">📚 View All Books & Ratings</button>\n"
                +
                "            <button class=\"btn\" onclick=\"loadStats()\">📊 Refresh Stats</button>\n" +
                "            <button class=\"btn\" onclick=\"loadRemoveBookInterface()\" style=\"background: #dc3545;\">🗑️ Remove Book</button>\n"
                +
                "        </div>\n" +
                "        \n" +
                "        <div style=\"max-width: 600px;\">\n" +
                "            <div>\n" +
                "                <h3>Add New Book</h3>\n" +
                "                <form id=\"addBookForm\">\n" +
                "                    <div class=\"form-group\">\n" +
                "                        <input type=\"text\" id=\"addTitle\" name=\"title\" placeholder=\"Book title\" required>\n"
                +
                "                    </div>\n" +
                "                    <div class=\"form-group\">\n" +
                "                        <input type=\"text\" id=\"addAuthor\" name=\"author\" placeholder=\"Author\" required>\n"
                +
                "                    </div>\n" +
                "                    <div class=\"form-group\">\n" +
                "                        <input type=\"text\" id=\"addGenre\" name=\"genre\" placeholder=\"Genre\" required>\n"
                +
                "                    </div>\n" +
                "                    <div class=\"form-group\">\n" +
                "                        <input type=\"number\" id=\"addYear\" name=\"year\" placeholder=\"Publication year\" min=\"1000\" max=\"2030\" required>\n"
                +
                "                    </div>\n" +
                "                    <button type=\"button\" class=\"btn\" onclick=\"submitAddBookForm()\" style=\"background: #28a745;\">� Add Book</button>\n"
                +
                "                </form>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"books-list\" id=\"booksList\" style=\"margin-top: 20px;\"></div>\n" +
                "    </div>\n" +
                "\n" +
                "    <div class=\"management-section\">\n" +
                "        <div style=\"display: flex; align-items: center; gap: 15px; margin-bottom: 20px;\">\n" +
                "            <h2>🤖 AI Chatbot Configuration</h2>\n" +
                "            <button type=\"button\" class=\"btn\" onclick=\"toggleAIConfig()\" id=\"aiConfigToggle\" style=\"background: rgba(0,123,255,0.8);\">\n"
                +
                "                🔧 Configure AI Settings\n" +
                "            </button>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div id=\"aiConfigPanel\" style=\"display: none; max-width: 600px;\">\n" +
                "            <p style=\"margin-bottom: 20px; opacity: 0.9;\">Configure OpenAI API to enable intelligent chatbot responses</p>\n"
                +
                "        \n" +
                "            <!-- AI Status Display -->\n" +
                "            <div id=\"currentAIStatus\" style=\"margin-bottom: 20px; padding: 15px; border-radius: 10px; background: rgba(255,193,7,0.2); border: 1px solid rgba(255,193,7,0.5);\">\n"
                +
                "                <h4 style=\"margin: 0 0 10px 0; color: #ffc107;\">🔧 Current Status: Standard Mode</h4>\n"
                +
                "                <p style=\"margin: 0; opacity: 0.9;\">AI features are disabled. Configure OpenAI API key below to enable intelligent responses.</p>\n"
                +
                "                <button type=\"button\" class=\"btn\" onclick=\"checkAIStatus()\" style=\"margin-top: 10px; background: rgba(255,193,7,0.3);\">📊 Check AI Status</button>\n"
                +
                "            </div>\n" +
                "            \n" +
                "            <form id=\"aiKeyForm\">\n" +
                "                <div class=\"form-group\">\n" +
                "                    <label for=\"apiKey\">🔑 OpenAI API Key:</label>\n" +
                "                    <input type=\"password\" id=\"apiKey\" placeholder=\"sk-proj-...\" required>\n" +
                "                    <small style=\"opacity: 0.8; margin-top: 5px; display: block;\">Get your API key from: https://platform.openai.com/api-keys</small>\n"
                +
                "                </div>\n" +
                "                <div style=\"display: flex; gap: 10px; align-items: center;\">\n" +
                "                    <button type=\"submit\" class=\"btn\">🚀 Activate AI Chatbot</button>\n" +
                "                    <button type=\"button\" class=\"btn\" onclick=\"clearAIConfig()\" style=\"background: rgba(220,53,69,0.8);\">🗑️ Clear Config</button>\n"
                +
                "                </div>\n" +
                "                <div id=\"aiStatus\" style=\"margin-top: 10px; padding: 10px; border-radius: 8px; display: none;\"></div>\n"
                +
                "            </form>\n" +
                "            \n" +
                "            <div style=\"margin-top: 20px; padding: 15px; background: rgba(255,255,255,0.1); border-radius: 10px;\">\n"
                +
                "                <h4>💡 AI-Powered Assistants:</h4>\n" +
                "                <div style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-top: 15px;\">\n"
                +
                "                    <div style=\"background: rgba(0,123,255,0.1); padding: 12px; border-radius: 8px; border: 1px solid rgba(0,123,255,0.3);\">\n"
                +
                "                        <h5 style=\"margin: 0 0 8px 0; color: #007bff;\">📚 Library Assistant</h5>\n" +
                "                        <ul style=\"margin: 0; padding-left: 15px; font-size: 0.9em; opacity: 0.9;\">\n"
                +
                "                            <li>Book search and filtering</li>\n" +
                "                            <li>Library statistics and analytics</li>\n" +
                "                            <li>Collection management help</li>\n" +
                "                            <li>Book cataloging assistance</li>\n" +
                "                        </ul>\n" +
                "                    </div>\n" +
                "                    <div style=\"background: rgba(40,167,69,0.1); padding: 12px; border-radius: 8px; border: 1px solid rgba(40,167,69,0.3);\">\n"
                +
                "                        <h5 style=\"margin: 0 0 8px 0; color: #28a745;\">📖 Reading Assistant</h5>\n" +
                "                        <ul style=\"margin: 0; padding-left: 15px; font-size: 0.9em; opacity: 0.9;\">\n"
                +
                "                            <li>Personalized book recommendations</li>\n" +
                "                            <li>Reading progress tracking</li>\n" +
                "                            <li>Genre-based suggestions</li>\n" +
                "                            <li>Reading goals and motivation</li>\n" +
                "                        </ul>\n" +
                "                    </div>\n" +
                "                </div>\n" +
                "                <p style=\"margin-top: 15px; opacity: 0.8; font-size: 0.9em; text-align: center;\">🧠 Powered by OpenAI GPT for natural language understanding and intelligent responses</p>\n"
                +
                "            </div>\n" +
                "        </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        function loadStats() {\n" +
                "            console.log('Loading stats...');\n" +
                "            fetch('/api/stats')\n" +
                "                .then(response => {\n" +
                "                    console.log('Stats response:', response.status);\n" +
                "                    return response.json();\n" +
                "                })\n" +
                "                .then(data => {\n" +
                "                    console.log('Stats data received:', data);\n" +
                "                    const statsElement = document.getElementById('stats');\n" +
                "                    if (!statsElement) {\n" +
                "                        console.error('Stats element not found!');\n" +
                "                        return;\n" +
                "                    }\n" +
                "                    statsElement.innerHTML = `\n" +
                "                        <div class=\"stat-card\">\n" +
                "                            <div class=\"stat-number\">${data.totalBooks}</div>\n" +
                "                            <div class=\"stat-label\">Total Books</div>\n" +
                "                        </div>\n" +
                "                        <div class=\"stat-card\">\n" +
                "                            <div class=\"stat-number\">${data.booksRead}</div>\n" +
                "                            <div class=\"stat-label\">Books Read</div>\n" +
                "                        </div>\n" +
                "                        <div class=\"stat-card\">\n" +
                "                            <div class=\"stat-number\">${data.completionRate}%</div>\n" +
                "                            <div class=\"stat-label\">Completion Rate</div>\n" +
                "                        </div>\n" +
                "                        <div class=\"stat-card\">\n" +
                "                            <div class=\"stat-number\">${data.averageRating}</div>\n" +
                "                            <div class=\"stat-label\">Average Rating</div>\n" +
                "                        </div>\n" +
                "                    `;\n" +
                "                    console.log('Stats updated successfully');\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    console.error('Error loading stats:', error);\n" +
                "                    document.getElementById('stats').innerHTML = '<div class=\"stat-card\"><div class=\"stat-label\">Error loading stats</div></div>';\n"
                +
                "                });\n" +
                "        }\n" +
                "\n" +
                "        function loadBooks() {\n" +
                "            fetch('/api/books')\n" +
                "                .then(response => response.json())\n" +
                "                .then(books => {\n" +
                "                    const booksList = document.getElementById('booksList');\n" +
                "                    if (books.length === 0) {\n" +
                "                        booksList.innerHTML = '<p>No books found.</p>';\n" +
                "                        return;\n" +
                "                    }\n" +
                "                    \n" +
                "                    booksList.innerHTML = books.map(book => `\n" +
                "                        <div class=\"book-item\">\n" +
                "                            <strong>${book.title}</strong> by ${book.author}<br>\n" +
                "                            <small>Year: ${book.year || 'Unknown'} | Genre: ${book.genre || 'Unknown'} | Status: ${book.status || 'Unknown'} | Rating: ${book.rating > 0 ? book.rating : 'No rating'}</small>\n"
                +
                "                            ${book.notes ? '<br><em>' + book.notes + '</em>' : ''}\n" +
                "                        </div>\n" +
                "                    `).join('');\n" +
                "                })\n" +
                "                .catch(error => console.error('Error loading books:', error));\n" +
                "        }\n" +
                "        \n" +
                "        function loadBooksDetailed() {\n" +
                "            fetch('/api/books')\n" +
                "                .then(response => response.json())\n" +
                "                .then(books => {\n" +
                "                    const booksList = document.getElementById('booksList');\n" +
                "                    if (books.length === 0) {\n" +
                "                        booksList.innerHTML = '<p>No books found.</p>';\n" +
                "                        return;\n" +
                "                    }\n" +
                "                    \n" +
                "                    booksList.innerHTML = '<h3>Library Collection - Detailed View</h3>' + books.map(book => `\n"
                +
                "                        <div class=\"book-item\" style=\"border-left: 4px solid ${book.rating >= 4 ? '#4CAF50' : book.rating >= 2 ? '#FF9800' : '#F44336'}; margin-bottom: 15px; padding: 15px; background: rgba(255,255,255,0.1); border-radius: 8px;\">\n"
                +
                "                            <div style=\"display: grid; grid-template-columns: 2fr 1fr 1fr; gap: 15px; align-items: start;\">\n"
                +
                "                                <div>\n" +
                "                                    <strong style=\"font-size: 1.1em;\">${book.title}</strong><br>\n" +
                "                                    <span>by ${book.author} (${book.year || 'Unknown year'})</span><br>\n"
                +
                "                                    <span style=\"background: rgba(255,255,255,0.2); padding: 2px 8px; border-radius: 12px; font-size: 0.8em;\">${book.genre || 'Unknown genre'}</span>\n"
                +
                "                                </div>\n" +
                "                                <div>\n" +
                "                                    <strong>Rating:</strong> ${book.rating > 0 ? '★'.repeat(book.rating) + ' (' + book.rating + '/5)' : 'No rating'}<br>\n"
                +
                "                                    <strong>Status:</strong> ${book.status || 'Available'}<br>\n" +
                "                                    ${book.rating <= 2 && book.rating > 0 ? '<span style=\"color: #ffcccb;\">⚠ Poor rating - consider removal</span>' : ''}\n"
                +
                "                                </div>\n" +
                "                                <div>\n" +
                "                                    ${book.notes ? '<strong>User Notes:</strong><br><em style=\"font-size: 0.9em;\">' + book.notes + '</em>' : '<span style=\"opacity: 0.7;\">No user notes</span>'}\n"
                +
                "                                </div>\n" +
                "                            </div>\n" +
                "                        </div>\n" +
                "                    `).join('');\n" +
                "                })\n" +
                "                .catch(error => console.error('Error loading detailed books:', error));\n" +
                "        }\n" +
                "        \n" +
                "        function loadUserRecommendations() {\n" +
                "            fetch('/api/book-recommendations')\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    const booksList = document.getElementById('booksList');\n" +
                "                    if (!data.success || data.recommendations.length === 0) {\n" +
                "                        booksList.innerHTML = '<h3>📚 User Book Recommendations</h3><p style=\"text-align: center; opacity: 0.7;\">No user book recommendations yet.</p>';\n"
                +
                "                        return;\n" +
                "                    }\n" +
                "                    \n" +
                "                    const recommendations = data.recommendations;\n" +
                "                    booksList.innerHTML = '<h3>📚 User Book Recommendations (' + recommendations.length + ')</h3>' + \n"
                +
                "                        recommendations.map(req => {\n" +
                "                            const statusColor = req.status === 'pending' ? '#ff9800' : \n" +
                "                                              req.status === 'approved' ? '#4caf50' : '#f44336';\n" +
                "                            const statusIcon = req.status === 'pending' ? '⏳' : \n" +
                "                                             req.status === 'approved' ? '✅' : '❌';\n" +
                "                            \n" +
                "                            return `\n" +
                "                                <div class=\"book-item\" style=\"border-left: 4px solid ${statusColor}; margin-bottom: 20px; padding: 20px; background: rgba(255, 255, 255, 0.05); border-radius: 10px;\">\n"
                +
                "                                    <div style=\"display: grid; grid-template-columns: 2fr 1fr; gap: 20px;\">\n"
                +
                "                                        <div>\n" +
                "                                            <div style=\"display: flex; justify-content: space-between; align-items: start; margin-bottom: 10px;\">\n"
                +
                "                                                <div>\n" +
                "                                                    <strong style=\"font-size: 1.2em; color: #fff;\">${req.title}</strong><br>\n"
                +
                "                                                    <span style=\"color: rgba(255,255,255,0.8);\">by ${req.author}</span>\n"
                +
                "                                                    ${req.publicationYear ? ' (' + req.publicationYear + ')' : ''}\n"
                +
                "                                                </div>\n" +
                "                                                <div style=\"text-align: right;\">\n" +
                "                                                    <span style=\"background: ${statusColor}; color: white; padding: 4px 8px; border-radius: 12px; font-size: 0.8em;\">${statusIcon} ${req.status.toUpperCase()}</span><br>\n"
                +
                "                                                    <strong style=\"color: #4CAF50; font-size: 1.1em;\">$${req.estimatedPrice}</strong>\n"
                +
                "                                                </div>\n" +
                "                                            </div>\n" +
                "                                            \n" +
                "                                            <div style=\"margin: 10px 0;\">\n" +
                "                                                ${req.genre ? '<span style=\"background: rgba(33, 150, 243, 0.3); padding: 2px 8px; border-radius: 12px; font-size: 0.8em; margin-right: 5px;\">' + req.genre + '</span>' : ''}\n"
                +
                "                                                <span style=\"color: rgba(255,255,255,0.6); font-size: 0.9em;\">Requested by: <strong>${req.requestedBy}</strong></span>\n"
                +
                "                                            </div>\n" +
                "                                            \n" +
                "                                            ${req.reason ? '<p style=\"background: rgba(255,255,255,0.1); padding: 10px; border-radius: 8px; margin: 10px 0; font-style: italic;\">\"' + req.reason + '\"</p>' : ''}\n"
                +
                "                                            \n" +
                "                                            ${req.librarianFeedback ? '<div style=\"background: rgba(76, 175, 80, 0.2); padding: 10px; border-radius: 8px; margin-top: 10px;\"><strong>📝 Librarian Feedback:</strong><br>' + req.librarianFeedback + '</div>' : ''}\n"
                +
                "                                        </div>\n" +
                "                                        \n" +
                "                                        <div>\n" +
                "                                            ${req.status === 'pending' ? `\n" +
                "                                                <button onclick=\"approveRequest('${req.id}')\" style=\"width: 100%; background: #4CAF50; color: white; border: none; padding: 10px; border-radius: 8px; cursor: pointer; margin-bottom: 10px; font-weight: 600;\">✅ Add to Library</button>\n"
                +
                "                                                <button onclick=\"showRejectModal('${req.id}')\" style=\"width: 100%; background: #f44336; color: white; border: none; padding: 10px; border-radius: 8px; cursor: pointer; font-weight: 600;\">❌ Reject Request</button>\n"
                +
                "                                            ` : `\n" +
                "                                                <div style=\"text-align: center; color: rgba(255,255,255,0.6); font-size: 0.9em;\">\n"
                +
                "                                                    <p>Request ${req.status}</p>\n" +
                "                                                    <p style=\"font-size: 0.8em;\">${new Date(req.requestDate).toLocaleDateString()}</p>\n"
                +
                "                                                </div>\n" +
                "                                            `}\n" +
                "                                        </div>\n" +
                "                                    </div>\n" +
                "                                </div>\n" +
                "                            `;\n" +
                "                        }).join('');\n" +
                "                })\n" +
                "                .catch(error => console.error('Error loading recommendations:', error));\n" +
                "        }\n" +
                "        \n" +
                "        function approveRequest(requestId) {\n" +
                "            if (confirm('Add this book to the library collection?')) {\n" +
                "                fetch('/api/approve-request', {\n" +
                "                    method: 'POST',\n" +
                "                    headers: { 'Content-Type': 'application/json' },\n" +
                "                    body: JSON.stringify({ requestId: requestId })\n" +
                "                })\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    alert(data.message);\n" +
                "                    if (data.success) {\n" +
                "                        loadUserRecommendations();\n" +
                "                    }\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    alert('Error approving request');\n" +
                "                    console.error('Error:', error);\n" +
                "                });\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        function showRejectModal(requestId) {\n" +
                "            const feedback = prompt('Please provide feedback for rejecting this request (optional):');\n"
                +
                "            if (feedback !== null) {\n" +
                "                fetch('/api/reject-request', {\n" +
                "                    method: 'POST',\n" +
                "                    headers: { 'Content-Type': 'application/json' },\n" +
                "                    body: JSON.stringify({ \n" +
                "                        requestId: requestId,\n" +
                "                        feedback: feedback || 'Request was not approved at this time.'\n" +
                "                    })\n" +
                "                })\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    alert(data.message);\n" +
                "                    if (data.success) {\n" +
                "                        loadUserRecommendations();\n" +
                "                    }\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    alert('Error rejecting request');\n" +
                "                    console.error('Error:', error);\n" +
                "                });\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        // Remove book functionality removed\n" +
                "        \n" +
                "        // orderForm handler removed - now using addBookForm handler below\n" +
                "\n" +
                "        // AI Key Configuration\n" +
                "        document.getElementById('aiKeyForm').addEventListener('submit', function(e) {\n" +
                "            e.preventDefault();\n" +
                "            const apiKey = document.getElementById('apiKey').value;\n" +
                "            const statusDiv = document.getElementById('aiStatus');\n" +
                "            \n" +
                "            statusDiv.style.display = 'block';\n" +
                "            statusDiv.style.background = 'rgba(255, 193, 7, 0.2)';\n" +
                "            statusDiv.style.color = 'white';\n" +
                "            statusDiv.innerHTML = '🔄 Activating AI chatbot...';\n" +
                "            \n" +
                "            fetch('/api/set-ai-key', {\n" +
                "                method: 'POST',\n" +
                "                headers: {'Content-Type': 'application/x-www-form-urlencoded'},\n" +
                "                body: 'apiKey=' + encodeURIComponent(apiKey)\n" +
                "            })\n" +
                "            .then(response => response.json())\n" +
                "            .then(data => {\n" +
                "                if (data.success) {\n" +
                "                    statusDiv.style.background = 'rgba(40, 167, 69, 0.2)';\n" +
                "                    statusDiv.innerHTML = '✅ ' + data.message;\n" +
                "                    document.getElementById('apiKey').value = '';\n" +
                "                    \n" +
                "                    // Update AI status display\n" +
                "                    updateAIStatusDisplay(true);\n" +
                "                    \n" +
                "                    // Update chatbot header to show AI status\n" +
                "                    const chatHeader = document.querySelector('.chatbot-header');\n" +
                "                    if (chatHeader) {\n" +
                "                        chatHeader.innerHTML = '🤖 AI Library Assistant (GPT-4 Powered)';\n" +
                "                    }\n" +
                "                } else {\n" +
                "                    statusDiv.style.background = 'rgba(220, 53, 69, 0.2)';\n" +
                "                    statusDiv.innerHTML = '❌ ' + data.message;\n" +
                "                }\n" +
                "            })\n" +
                "            .catch(error => {\n" +
                "                statusDiv.style.background = 'rgba(220, 53, 69, 0.2)';\n" +
                "                statusDiv.innerHTML = '❌ Error: ' + error.message;\n" +
                "                console.error('Error:', error);\n" +
                "            });\n" +
                "        });\n" +
                "\n" +
                "        // Chatbot functions\n" +
                "        function sendMessage() {\n" +
                "            const input = document.getElementById('chatInput');\n" +
                "            const message = input.value.trim();\n" +
                "            if (!message) return;\n" +
                "\n" +
                "            // Add user message to chat\n" +
                "            addMessageToChat('user', message);\n" +
                "            input.value = '';\n" +
                "\n" +
                "            // Send to server\n" +
                "            fetch('/api/chat', {\n" +
                "                method: 'POST',\n" +
                "                headers: {'Content-Type': 'application/x-www-form-urlencoded'},\n" +
                "                body: 'message=' + encodeURIComponent(message)\n" +
                "            })\n" +
                "            .then(response => response.json())\n" +
                "            .then(data => {\n" +
                "                if (data.success) {\n" +
                "                    addMessageToChat('bot', data.response);\n" +
                "                } else {\n" +
                "                    addMessageToChat('bot', 'Sorry, I encountered an error. Please try again.');\n" +
                "                }\n" +
                "            })\n" +
                "            .catch(error => {\n" +
                "                console.error('Chat error:', error);\n" +
                "                addMessageToChat('bot', 'Sorry, I\\'m having trouble connecting. Please try again.');\n"
                +
                "            });\n" +
                "        }\n" +
                "\n" +
                "        function addMessageToChat(sender, message) {\n" +
                "            const chatMessages = document.getElementById('chatMessages');\n" +
                "            const messageDiv = document.createElement('div');\n" +
                "            messageDiv.className = 'chat-message ' + sender + '-message';\n" +
                "            messageDiv.style.cssText = 'margin-bottom: 10px; padding: 10px; border-radius: 10px; ' +\n"
                +
                "                (sender === 'user' ? 'background: rgba(255,255,255,0.2); margin-left: 20%; text-align: right;' : \n"
                +
                "                                     'background: rgba(255,255,255,0.1); margin-right: 20%;');\n" +
                "            \n" +
                "            // Format message (convert \\n to <br> for line breaks)\n" +
                "            const formattedMessage = message.replace(/\\\\n/g, '<br>').replace(/\\*\\*(.*?)\\*\\*/g, '<strong>$1</strong>');\n"
                +
                "            messageDiv.innerHTML = (sender === 'user' ? '👤 ' : '🤖 ') + formattedMessage;\n" +
                "            \n" +
                "            chatMessages.appendChild(messageDiv);\n" +
                "            chatMessages.scrollTop = chatMessages.scrollHeight;\n" +
                "        }\n" +
                "\n" +
                "        // AI Configuration Functions\n" +
                "        function checkAIStatus() {\n" +
                "            fetch('/api/ai-status')\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    updateAIStatusDisplay(data.enabled, data.status);\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    console.error('Error checking AI status:', error);\n" +
                "                    updateAIStatusDisplay(false, 'Error checking AI status');\n" +
                "                });\n" +
                "        }\n" +
                "        \n" +
                "        function updateAIStatusDisplay(enabled, statusMessage) {\n" +
                "            const statusDiv = document.getElementById('currentAIStatus');\n" +
                "            if (enabled) {\n" +
                "                statusDiv.style.background = 'rgba(40, 167, 69, 0.2)';\n" +
                "                statusDiv.style.borderColor = 'rgba(40, 167, 69, 0.5)';\n" +
                "                statusDiv.innerHTML = `\n" +
                "                    <h4 style=\"margin: 0 0 10px 0; color: #28a745;\">🤖 Current Status: AI Enabled</h4>\n"
                +
                "                    <p style=\"margin: 0; opacity: 0.9;\">OpenAI GPT-4 is active and providing intelligent responses for both Library Assistant and Reading Assistant.</p>\n"
                +
                "                    <button type=\"button\" class=\"btn\" onclick=\"checkAIStatus()\" style=\"margin-top: 10px; background: rgba(40, 167, 69, 0.3);\">🔄 Refresh Status</button>\n"
                +
                "                `;\n" +
                "            } else {\n" +
                "                statusDiv.style.background = 'rgba(255,193,7,0.2)';\n" +
                "                statusDiv.style.borderColor = 'rgba(255,193,7,0.5)';\n" +
                "                statusDiv.innerHTML = `\n" +
                "                    <h4 style=\"margin: 0 0 10px 0; color: #ffc107;\">🔧 Current Status: Standard Mode</h4>\n"
                +
                "                    <p style=\"margin: 0; opacity: 0.9;\">${statusMessage || 'AI features are disabled. Configure OpenAI API key below to enable intelligent responses.'}</p>\n"
                +
                "                    <button type=\"button\" class=\"btn\" onclick=\"checkAIStatus()\" style=\"margin-top: 10px; background: rgba(255,193,7,0.3);\">📊 Check AI Status</button>\n"
                +
                "                `;\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        function toggleAIConfig() {\n" +
                "            const panel = document.getElementById('aiConfigPanel');\n" +
                "            const button = document.getElementById('aiConfigToggle');\n" +
                "            \n" +
                "            if (panel.style.display === 'none' || panel.style.display === '') {\n" +
                "                panel.style.display = 'block';\n" +
                "                button.innerHTML = '🔼 Hide AI Settings';\n" +
                "                button.style.background = 'rgba(220,53,69,0.8)';\n" +
                "            } else {\n" +
                "                panel.style.display = 'none';\n" +
                "                button.innerHTML = '🔧 Configure AI Settings';\n" +
                "                button.style.background = 'rgba(0,123,255,0.8)';\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        function clearAIConfig() {\n" +
                "            if (confirm('Are you sure you want to clear the AI configuration? This will disable AI features.')) {\n"
                +
                "                fetch('/api/clear-ai-key', {\n" +
                "                    method: 'POST',\n" +
                "                    headers: {'Content-Type': 'application/x-www-form-urlencoded'}\n" +
                "                })\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    const statusDiv = document.getElementById('aiStatus');\n" +
                "                    if (data.success) {\n" +
                "                        statusDiv.style.display = 'block';\n" +
                "                        statusDiv.style.background = 'rgba(255, 193, 7, 0.2)';\n" +
                "                        statusDiv.innerHTML = '🔧 ' + data.message;\n" +
                "                        updateAIStatusDisplay(false);\n" +
                "                        \n" +
                "                        // Update chatbot header\n" +
                "                        const chatHeader = document.querySelector('.chatbot-header');\n" +
                "                        if (chatHeader) {\n" +
                "                            chatHeader.innerHTML = '🤖 Library Assistant';\n" +
                "                        }\n" +
                "                    } else {\n" +
                "                        statusDiv.style.display = 'block';\n" +
                "                        statusDiv.style.background = 'rgba(220, 53, 69, 0.2)';\n" +
                "                        statusDiv.innerHTML = '❌ ' + data.message;\n" +
                "                    }\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    console.error('Error:', error);\n" +
                "                });\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        // Load initial data\n" +
                "        loadStats();\n" +
                "        loadBooks();\n" +
                "        checkAIStatus(); // Check AI status on page load\n" +
                "        \n" +
                "        // Global Add Book function (as backup)\n" +
                "        window.submitAddBookForm = function() {\n" +
                "            console.log('🔥 Manual Add Book form submission triggered');\n" +
                "            \n" +
                "            const title = document.getElementById('addTitle').value;\n" +
                "            const author = document.getElementById('addAuthor').value;\n" +
                "            const genre = document.getElementById('addGenre').value;\n" +
                "            const year = document.getElementById('addYear').value;\n" +
                "            \n" +
                "            console.log('Form values:', {title, author, genre, year});\n" +
                "            \n" +
                "            if (!title || !author || !genre || !year) {\n" +
                "                alert('Please fill in all required fields!');\n" +
                "                return;\n" +
                "            }\n" +
                "            \n" +
                "            const formData = `title=${encodeURIComponent(title)}&author=${encodeURIComponent(author)}&genre=${encodeURIComponent(genre)}&year=${encodeURIComponent(year)}`;\n"
                +
                "            console.log('Sending form data:', formData);\n" +
                "            \n" +
                "            fetch('/api/add', {\n" +
                "                method: 'POST',\n" +
                "                headers: {'Content-Type': 'application/x-www-form-urlencoded'},\n" +
                "                body: formData\n" +
                "            })\n" +
                "            .then(response => {\n" +
                "                console.log('Form response status:', response.status);\n" +
                "                return response.json();\n" +
                "            })\n" +
                "            .then(data => {\n" +
                "                console.log('Form response data:', data);\n" +
                "                alert(data.message);\n" +
                "                if (data.success) {\n" +
                "                    document.getElementById('addTitle').value = '';\n" +
                "                    document.getElementById('addAuthor').value = '';\n" +
                "                    document.getElementById('addGenre').value = '';\n" +
                "                    document.getElementById('addYear').value = '';\n" +
                "                    loadStats();\n" +
                "                    loadBooks();\n" +
                "                }\n" +
                "            })\n" +
                "            .catch(error => {\n" +
                "                console.error('Form error:', error);\n" +
                "                alert('Error adding book: ' + error.message);\n" +
                "            });\n" +
                "        };\n" +
                "        \n" +
                "        // Global Remove Book functions\n" +
                "        window.loadRemoveBookInterface = function() {\n" +
                "            console.log('Loading Remove Book interface...');\n" +
                "            fetch('/api/books')\n" +
                "                .then(response => response.json())\n" +
                "                .then(books => {\n" +
                "                    const booksList = document.getElementById('booksList');\n" +
                "                    let html = '<div style=\"max-width: 800px;\"><h3>🗑️ Remove Books</h3>';\n" +
                "                    \n" +
                "                    if (books.length === 0) {\n" +
                "                        html += '<p>No books available to remove.</p>';\n" +
                "                    } else {\n" +
                "                        html += '<div style=\"margin-bottom: 20px; background: rgba(220, 53, 69, 0.1); padding: 15px; border-radius: 10px; border: 1px solid rgba(220, 53, 69, 0.3);\"><p><strong>⚠️ Rating Warning:</strong> Books with ratings 2 or below are highlighted in red as they may need removal due to poor quality.</p></div>';\n"
                +
                "                        html += '<div style=\"display: grid; gap: 15px;\">';\n" +
                "                        \n" +
                "                        books.forEach(book => {\n" +
                "                            const rating = book.rating || 0;\n" +
                "                            const isPoorRating = rating <= 2 && rating > 0;\n" +
                "                            const cardStyle = isPoorRating ? 'background: rgba(220, 53, 69, 0.15); border: 2px solid #dc3545;' : 'background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.2);';\n"
                +
                "                            const warningText = isPoorRating ? '<div style=\"color: #dc3545; font-weight: bold; margin-top: 8px; font-size: 0.9em;\">⚠️ Poor Rating - Consider Removal</div>' : '';\n"
                +
                "                            \n" +
                "                            html += `<div style=\"${cardStyle} padding: 15px; border-radius: 12px; display: flex; justify-content: space-between; align-items: center;\">`;\n"
                +
                "                            html += `<div style=\"flex: 1;\">`;\n" +
                "                            html += `<div style=\"font-size: 1.1em; font-weight: bold; margin-bottom: 5px;\">${book.title}</div>`;\n"
                +
                "                            html += `<div style=\"opacity: 0.9; margin-bottom: 3px;\">by ${book.author}</div>`;\n"
                +
                "                            html += `<div style=\"opacity: 0.8; font-size: 0.9em;\">Genre: ${book.genre || 'N/A'} | Year: ${book.year || 'N/A'} | Rating: ${rating > 0 ? rating + '/5 ★' : 'No Rating'}</div>`;\n"
                +
                "                            html += warningText;\n" +
                "                            html += `</div>`;\n" +
                "                            html += `<button class=\"btn\" onclick=\"removeBook('${book.title.replace(/'/g, \"\\\\\\'\")}', '${book.author.replace(/'/g, \"\\\\\\'\")}', ${rating})\" style=\"background: #dc3545; padding: 10px 15px; margin-left: 15px;\">🗑️ Remove</button>`;\n"
                +
                "                            html += `</div>`;\n" +
                "                        });\n" +
                "                        \n" +
                "                        html += '</div>';\n" +
                "                    }\n" +
                "                    \n" +
                "                    html += '<div style=\"margin-top: 20px;\"><button class=\"btn\" onclick=\"loadBooks()\" style=\"background: #6c757d;\">📚 Back to Books List</button></div></div>';\n"
                +
                "                    booksList.innerHTML = html;\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    console.error('Error loading books for removal:', error);\n" +
                "                    document.getElementById('booksList').innerHTML = '<p>Error loading books for removal.</p>';\n"
                +
                "                });\n" +
                "        };\n" +
                "        \n" +
                "        window.removeBook = function(title, author, rating) {\n" +
                "            const ratingWarning = rating <= 2 && rating > 0 ? \n" +
                "                '\\n\\n⚠️ WARNING: This book has a poor rating (' + rating + '/5). Removing it may improve your library quality!' : '';\n"
                +
                "            \n" +
                "            const confirmMessage = `Are you sure you want to remove:\\n\\n\"${title}\" by ${author}?${ratingWarning}`;\n"
                +
                "            \n" +
                "            if (confirm(confirmMessage)) {\n" +
                "                console.log('Removing book:', title, 'by', author, 'Rating:', rating);\n" +
                "                \n" +
                "                fetch('/api/remove', {\n" +
                "                    method: 'POST',\n" +
                "                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},\n" +
                "                    body: `title=${encodeURIComponent(title)}&author=${encodeURIComponent(author)}`\n"
                +
                "                })\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => {\n" +
                "                    console.log('Remove response:', data);\n" +
                "                    if (data.success) {\n" +
                "                        alert('✅ Book removed successfully!');\n" +
                "                        loadRemoveBookInterface(); // Refresh the remove interface\n" +
                "                        loadStats(); // Update stats\n" +
                "                    } else {\n" +
                "                        alert('❌ ' + data.message);\n" +
                "                    }\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    console.error('Error removing book:', error);\n" +
                "                    alert('❌ Error removing book: ' + error.message);\n" +
                "                });\n" +
                "            }\n" +
                "        };\n" +
                "    </script>\n" +
                "\n" +
                "    <!-- Floating Chatbot Widget -->\n" +
                "    <div class=\"chatbot-widget\" onclick=\"toggleChatbot()\" id=\"chatToggle\">\n" +
                "        <div class=\"chatbot-toggle\">🤖</div>\n" +
                "    </div>\n" +
                "    <div class=\"chatbot-panel\" id=\"chatPanel\">\n" +
                "        <div class=\"chatbot-header\">\n" +
                "            <span>🤖 Library Assistant</span>\n" +
                "            <span onclick=\"toggleChatbot()\" style=\"cursor: pointer; font-size: 18px; float: right;\">✕</span>\n"
                +
                "        </div>\n" +
                "        <div class=\"chatbot-messages\" id=\"chatbotMessages\">\n" +
                "            <div class=\"chat-message bot-message\" id=\"initialGreeting\">👋 Hello! I'm your library assistant. Ask me about books, statistics, or anything else!</div>\n"
                +
                "        </div>\n" +
                "        <div class=\"chatbot-input\">\n" +
                "            <input type=\"text\" id=\"chatbotInput\" placeholder=\"Type your message...\" onkeypress=\"if(event.key==='Enter') sendChatMessage()\">\n"
                +
                "            <button class=\"chatbot-send\" onclick=\"sendChatMessage()\">Send</button>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        function toggleChatbot() {\n" +
                "            const panel = document.getElementById('chatPanel');\n" +
                "            \n" +
                "            if (panel.style.display === 'none' || panel.style.display === '') {\n" +
                "                panel.style.display = 'flex';\n" +
                "                \n" +
                "            } else {\n" +
                "                panel.style.display = 'none';\n" +
                "                \n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        function sendChatMessage() {\n" +
                "            const input = document.getElementById('chatbotInput');\n" +
                "            const message = input.value.trim();\n" +
                "            if (!message) return;\n" +
                "\n" +
                "            addChatMessage('user', message);\n" +
                "            input.value = '';\n" +
                "\n" +
                "            fetch('/api/chat', {\n" +
                "                method: 'POST',\n" +
                "                headers: {'Content-Type': 'application/x-www-form-urlencoded'},\n" +
                "                body: 'message=' + encodeURIComponent(message)\n" +
                "            })\n" +
                "            .then(response => response.json())\n" +
                "            .then(data => {\n" +
                "                if (data.success) {\n" +
                "                    addChatMessage('bot', data.response);\n" +
                "                } else {\n" +
                "                    addChatMessage('bot', 'Sorry, I encountered an error.');\n" +
                "                }\n" +
                "            })\n" +
                "            .catch(error => {\n" +
                "                console.error('Chat error:', error);\n" +
                "                addChatMessage('bot', 'Sorry, I\\'m having trouble connecting.');\n" +
                "            });\n" +
                "        }\n" +
                "\n" +
                "        function addChatMessage(sender, message) {\n" +
                "            const messages = document.getElementById('chatbotMessages');\n" +
                "            const messageDiv = document.createElement('div');\n" +
                "            messageDiv.className = 'chat-message ' + sender + '-message';\n" +
                "            const formattedMessage = message.replace(/\\\\n/g, '<br>').replace(/\\*\\*(.*?)\\*\\*/g, '<strong>$1</strong>');\n"
                +
                "            messageDiv.innerHTML = formattedMessage;\n" +
                "            messages.appendChild(messageDiv);\n" +
                "            messages.scrollTop = messages.scrollHeight;\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }

    private String getUserDashboard(SimpleUser user) {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>User Dashboard</title>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body {\n" +
                "            font-family: 'Georgia', 'Times New Roman', serif;\n" +
                "            background: linear-gradient(135deg, #2c3e50 0%, #34495e 25%, #1a252f 50%, #2c3e50 75%, #34495e 100%);\n"
                +
                "            background-size: 400% 400%;\n" +
                "            animation: gradientShift 15s ease infinite;\n" +
                "            min-height: 100vh;\n" +
                "            color: #ecf0f1;\n" +
                "            position: relative;\n" +
                "        }\n" +
                "        .header {\n" +
                "            padding: 20px;\n" +
                "            text-align: center;\n" +
                "            border-bottom: 1px solid rgba(255,255,255,0.2);\n" +
                "        }\n" +
                "        .header h1 {\n" +
                "            font-size: 32px;\n" +
                "            margin-bottom: 10px;\n" +
                "        }\n" +
                "        .user-info {\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            align-items: center;\n" +
                "            opacity: 0.9;\n" +
                "        }\n" +
                "        .logout-btn {\n" +
                "            background: rgba(255,255,255,0.2);\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            padding: 10px 20px;\n" +
                "            border-radius: 20px;\n" +
                "            cursor: pointer;\n" +
                "            text-decoration: none;\n" +
                "        }\n" +
                "        .main-content {\n" +
                "            max-width: 800px;\n" +
                "            margin: 30px auto;\n" +
                "            padding: 0 20px;\n" +
                "        }\n" +
                "        .add-book-section {\n" +
                "            background: rgba(255,255,255,0.15);\n" +
                "            backdrop-filter: blur(10px);\n" +
                "            border-radius: 20px;\n" +
                "            padding: 30px;\n" +
                "            border: 1px solid rgba(255,255,255,0.2);\n" +
                "            margin-bottom: 30px;\n" +
                "        }\n" +
                "        .add-book-section h2 {\n" +
                "            margin-bottom: 25px;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        .form-row {\n" +
                "            display: grid;\n" +
                "            grid-template-columns: 1fr 1fr;\n" +
                "            gap: 20px;\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        .form-group {\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        .form-group label {\n" +
                "            display: block;\n" +
                "            margin-bottom: 8px;\n" +
                "            font-weight: 600;\n" +
                "        }\n" +
                "        .form-group input, .form-group select, .form-group textarea {\n" +
                "            width: 100%;\n" +
                "            padding: 12px;\n" +
                "            border: 1px solid rgba(255,255,255,0.3);\n" +
                "            border-radius: 8px;\n" +
                "            background: rgba(255,255,255,0.1);\n" +
                "            color: white;\n" +
                "            font-size: 16px;\n" +
                "        }\n" +
                "        .form-group input::placeholder, .form-group textarea::placeholder {\n" +
                "            color: rgba(255,255,255,0.7);\n" +
                "        }\n" +
                "        .form-group option {\n" +
                "            background: #333;\n" +
                "            color: white;\n" +
                "        }\n" +
                "        .add-btn {\n" +
                "            width: 100%;\n" +
                "            background: rgba(255,255,255,0.2);\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            padding: 15px;\n" +
                "            border-radius: 10px;\n" +
                "            cursor: pointer;\n" +
                "            font-weight: 600;\n" +
                "            font-size: 16px;\n" +
                "            transition: all 0.3s;\n" +
                "        }\n" +
                "        .add-btn:hover {\n" +
                "            background: rgba(255,255,255,0.3);\n" +
                "            transform: translateY(-2px);\n" +
                "        }\n" +
                "        .books-section {\n" +
                "            background: rgba(255,255,255,0.15);\n" +
                "            backdrop-filter: blur(10px);\n" +
                "            border-radius: 20px;\n" +
                "            padding: 30px;\n" +
                "            border: 1px solid rgba(255,255,255,0.2);\n" +
                "        }\n" +
                "        .books-section h2 {\n" +
                "            margin-bottom: 20px;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        .view-books-btn {\n" +
                "            background: rgba(255,255,255,0.2);\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            padding: 12px 24px;\n" +
                "            border-radius: 8px;\n" +
                "            cursor: pointer;\n" +
                "            font-weight: 600;\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        .books-list {\n" +
                "            max-height: 400px;\n" +
                "            overflow-y: auto;\n" +
                "        }\n" +
                "        .book-item {\n" +
                "            background: rgba(255,255,255,0.1);\n" +
                "            margin-bottom: 15px;\n" +
                "            padding: 20px;\n" +
                "            border-radius: 10px;\n" +
                "            border-left: 4px solid rgba(255,255,255,0.3);\n" +
                "        }\n" +
                "        .book-title {\n" +
                "            font-size: 18px;\n" +
                "            font-weight: bold;\n" +
                "            margin-bottom: 5px;\n" +
                "        }\n" +
                "        .book-author {\n" +
                "            color: rgba(255,255,255,0.8);\n" +
                "            margin-bottom: 10px;\n" +
                "        }\n" +
                "        .book-details {\n" +
                "            font-size: 14px;\n" +
                "            color: rgba(255,255,255,0.7);\n" +
                "        }\n" +
                "        @media (max-width: 768px) {\n" +
                "            .form-row {\n" +
                "                grid-template-columns: 1fr;\n" +
                "            }\n" +
                "        }\n" +
                "        .chatbot-widget {\n" +
                "            position: fixed;\n" +
                "            bottom: 20px;\n" +
                "            right: 20px;\n" +
                "            width: 60px;\n" +
                "            height: 60px;\n" +
                "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
                "            border-radius: 50%;\n" +
                "            cursor: pointer;\n" +
                "            box-shadow: 0 4px 20px rgba(102, 126, 234, 0.4);\n" +
                "            z-index: 1000;\n" +
                "            transition: all 0.3s ease;\n" +
                "        }\n" +
                "        .chatbot-widget:hover {\n" +
                "            transform: scale(1.1);\n" +
                "            box-shadow: 0 6px 25px rgba(102, 126, 234, 0.6);\n" +
                "        }\n" +
                "        .chatbot-toggle {\n" +
                "            color: white;\n" +
                "            font-size: 24px;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            width: 100%;\n" +
                "            height: 100%;\n" +
                "        }\n" +
                "        .chatbot-panel {\n" +
                "            position: fixed;\n" +
                "            bottom: 90px;\n" +
                "            right: 20px;\n" +
                "            width: 350px;\n" +
                "            height: 400px;\n" +
                "            background: white;\n" +
                "            border-radius: 15px;\n" +
                "            box-shadow: 0 10px 40px rgba(0,0,0,0.2);\n" +
                "            z-index: 999;\n" +
                "            display: none;\n" +
                "            flex-direction: column;\n" +
                "        }\n" +
                "        .chatbot-header {\n" +
                "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
                "            color: white;\n" +
                "            padding: 15px;\n" +
                "            border-radius: 15px 15px 0 0;\n" +
                "            font-weight: bold;\n" +
                "        }\n" +
                "        .chatbot-messages {\n" +
                "            flex: 1;\n" +
                "            padding: 15px;\n" +
                "            overflow-y: auto;\n" +
                "            max-height: 300px;\n" +
                "        }\n" +
                "        .chatbot-input {\n" +
                "            display: flex;\n" +
                "            padding: 15px;\n" +
                "            border-top: 1px solid #eee;\n" +
                "        }\n" +
                "        .chatbot-input input {\n" +
                "            flex: 1;\n" +
                "            padding: 10px;\n" +
                "            border: 1px solid #ddd;\n" +
                "            border-radius: 20px;\n" +
                "            margin-right: 10px;\n" +
                "        }\n" +
                "        .chatbot-send {\n" +
                "            background: #667eea;\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            padding: 10px 15px;\n" +
                "            border-radius: 20px;\n" +
                "            cursor: pointer;\n" +
                "        }\n" +
                "        .chat-message {\n" +
                "            margin-bottom: 10px;\n" +
                "            padding: 8px 12px;\n" +
                "            border-radius: 15px;\n" +
                "            max-width: 80%;\n" +
                "        }\n" +
                "        .user-message {\n" +
                "            background: #667eea;\n" +
                "            color: white;\n" +
                "            margin-left: auto;\n" +
                "            text-align: right;\n" +
                "        }\n" +
                "        .bot-message {\n" +
                "            background: #f1f3f5;\n" +
                "            color: #333;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"header\">\n" +
                "        <div class=\"user-info\">\n" +
                "            <span>📖 Welcome, " + user.getFullName() + "</span>\n" +
                "            <a href=\"/api/logout\" class=\"logout-btn\">🚪 Logout</a>\n" +
                "        </div>\n" +
                "        <h1>📚 Library Manager</h1>\n" +
                "        <p>Your personal reading companion</p>\n" +
                "    </div>\n" +
                "\n" +
                "    <div class=\"main-content\">\n" +
                "        <div class=\"navigation-section\">\n" +
                "            <h2>📖 Discover Books</h2>\n" +
                "            <div style=\"text-align: center; margin: 30px 0;\">\n" +
                "                <a href=\"/browse\" style=\"background: rgba(255,255,255,0.2); color: white; padding: 15px 30px; text-decoration: none; border-radius: 25px; font-size: 18px; display: inline-block; margin: 10px; transition: all 0.3s;\">📚 Browse All Books</a>\n"
                +
                "                <p style=\"margin-top: 20px; opacity: 0.8;\">Explore our collection of " + books.size()
                + " books</p>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"search-section\" style=\"background: rgba(255,255,255,0.1); padding: 20px; border-radius: 15px; margin-bottom: 20px;\">\n"
                +
                "            <h2>🔍 Search Books</h2>\n" +
                "            <div style=\"display: grid; grid-template-columns: 2fr 1fr; gap: 20px; margin-bottom: 20px;\">\n"
                +
                "                <div>\n" +
                "                    <input type=\"text\" id=\"searchInput\" placeholder=\"Search by title, author, or genre...\" style=\"width: 100%; padding: 12px; border: 1px solid rgba(255,255,255,0.3); border-radius: 8px; background: rgba(255,255,255,0.1); color: white; font-size: 16px;\">\n"
                +
                "                </div>\n" +
                "                <div>\n" +
                "                    <button onclick=\"searchBooks()\" style=\"width: 100%; background: rgba(255,255,255,0.2); color: white; border: none; padding: 12px; border-radius: 8px; cursor: pointer; font-weight: 600;\">Search Library</button>\n"
                +
                "                </div>\n" +
                "            </div>\n" +
                "            <div id=\"searchResults\" style=\"margin-top: 20px;\"></div>\n" +
                "        </div>\n" +
                "        <div class=\"quick-stats-section\" style=\"background: rgba(255,255,255,0.1); padding: 20px; border-radius: 15px; margin-bottom: 20px;\">\n"
                +
                "            <h2>📊 Library Overview</h2>\n" +
                "            <div style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px;\">\n"
                +
                "                <div style=\"text-align: center; padding: 20px; background: rgba(255,255,255,0.05); border-radius: 10px;\">\n"
                +
                "                    <h3>📚 Total Books</h3>\n" +
                "                    <div style=\"font-size: 2em; margin: 10px 0;\">" + books.size() + "</div>\n" +
                "                </div>\n" +
                "                <div style=\"text-align: center; padding: 20px; background: rgba(255,255,255,0.05); border-radius: 10px;\">\n"
                +
                "                    <h3>🎭 Genres Available</h3>\n" +
                "                    <div style=\"font-size: 2em; margin: 10px 0;\">" + getGenreCount() + "</div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            <div style=\"text-align: center;\">\n" +
                "                <h3>🌟 Popular Genres</h3>\n" +
                "                <p>" + getTopGenres() + "</p>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"books-section\">\n" +
                "            <h2>📚 My Books</h2>\n" +
                "            <button class=\"view-books-btn\" onclick=\"loadBooks()\">👀 View My Books</button>\n" +
                "            <div class=\"books-list\" id=\"booksList\"></div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        // Set up Add Book form listener with delay to ensure DOM is ready\n" +
                "        setTimeout(function() {\n" +
                "            console.log('Setting up Add Book form...');\n" +
                "            const addBookForm = document.getElementById('addBookForm');\n" +
                "            if (addBookForm) {\n" +
                "                console.log('✅ Add Book form found, setting up listener');\n" +
                "                addBookForm.addEventListener('submit', function(e) {\n" +
                "                console.log('🔥 Add Book form submitted!');\n" +
                "                e.preventDefault();\n" +
                "                \n" +
                "                // Check if all required fields are filled\n" +
                "                const title = document.getElementById('addTitle').value;\n" +
                "                const author = document.getElementById('addAuthor').value;\n" +
                "                const genre = document.getElementById('addGenre').value;\n" +
                "                const year = document.getElementById('addYear').value;\n" +
                "                \n" +
                "                console.log('Form values:', {title, author, genre, year});\n" +
                "                \n" +
                "                if (!title || !author || !genre || !year) {\n" +
                "                    alert('Please fill in all required fields!');\n" +
                "                    return;\n" +
                "                }\n" +
                "                \n" +
                "                const formData = new FormData(this);\n" +
                "                const params = new URLSearchParams();\n" +
                "                for (let [key, value] of formData.entries()) {\n" +
                "                    console.log('Form data:', key, '=', value);\n" +
                "                    params.append(key, value);\n" +
                "                }\n" +
                "                \n" +
                "                console.log('Sending request to /api/add');\n" +
                "                \n" +
                "                fetch('/api/add', {\n" +
                "                    method: 'POST',\n" +
                "                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},\n" +
                "                    body: params.toString()\n" +
                "                })\n" +
                "                .then(response => {\n" +
                "                    console.log('Response received:', response.status);\n" +
                "                    return response.json();\n" +
                "                })\n" +
                "                .then(data => {\n" +
                "                    console.log('Response data:', data);\n" +
                "                    alert(data.message);\n" +
                "                    if (data.success) {\n" +
                "                        this.reset();\n" +
                "                        loadBooks();\n" +
                "                        // Also refresh stats for user dashboard\n" +
                "                        if (typeof loadStats === 'function') {\n" +
                "                            loadStats();\n" +
                "                        }\n" +
                "                    }\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    console.error('Error:', error);\n" +
                "                    alert('Error adding book: ' + error.message);\n" +
                "                });\n" +
                "                });\n" +
                "            } else {\n" +
                "                console.log('Add Book form not found - probably on User Dashboard (this is normal)');\n"
                +
                "            }\n" +
                "        }, 500);\n" +
                "\n" +

                "\n" +
                "        function loadBooks() {\n" +
                "            fetch('/api/books')\n" +
                "                .then(response => response.json())\n" +
                "                .then(books => {\n" +
                "                    const booksList = document.getElementById('booksList');\n" +
                "                    if (books.length === 0) {\n" +
                "                        booksList.innerHTML = '<p style=\"text-align: center; opacity: 0.7;\">📚 No books currently being read or completed yet.<br><br>Browse the library and start reading some books!</p>';\n"
                +
                "                        return;\n" +
                "                    }\n" +
                "                    \n" +
                "                    booksList.innerHTML = books.map(book => `\n" +
                "                        <div class=\"book-item\">\n" +
                "                            <div class=\"book-title\">${book.title}</div>\n" +
                "                            <div class=\"book-author\">by ${book.author}</div>\n" +
                "                            <div class=\"book-details\">\n" +
                "                                📅 ${book.year || 'Unknown year'} | \n" +
                "                                🎭 ${book.genre || 'No genre'} | \n" +
                "                                📊 ${book.status || 'No status'} | \n" +
                "                                ⭐ ${book.rating > 0 ? book.rating + ' stars' : 'No rating'}\n" +
                "                                ${book.notes ? '<br>📝 <em>' + book.notes + '</em>' : ''}\n" +
                "                            </div>\n" +
                "                        </div>\n" +
                "                    `).join('');\n" +
                "                })\n" +
                "                .catch(error => {\n" +
                "                    console.error('Error loading books:', error);\n" +
                "                    document.getElementById('booksList').innerHTML = '<p style=\"color: #ff6b6b;\">Error loading books</p>';\n"
                +
                "                });\n" +
                "        }\n" +
                "    </script>\n" +
                "\n" +
                "    <!-- Floating Chatbot Widget -->\n" +
                "    <div class=\"chatbot-widget\" onclick=\"toggleChatbot()\" id=\"chatToggle\">\n" +
                "        <div class=\"chatbot-toggle\">🤖</div>\n" +
                "    </div>\n" +
                "    <div class=\"chatbot-panel\" id=\"chatPanel\">\n" +
                "        <div class=\"chatbot-header\">\n" +
                "            <span>🤖 Reading Assistant</span>\n" +
                "            <span onclick=\"toggleChatbot()\" style=\"cursor: pointer; font-size: 18px; float: right;\">✕</span>\n"
                +
                "        </div>\n" +
                "        <div class=\"chatbot-messages\" id=\"chatbotMessages\">\n" +
                "            <div class=\"chat-message bot-message\">👋 Hello! I'm your reading assistant. Ask me for book recommendations, help with reading goals, or anything else!</div>\n"
                +
                "        </div>\n" +
                "        <div class=\"chatbot-input\">\n" +
                "            <input type=\"text\" id=\"chatbotInput\" placeholder=\"Type your message...\" onkeypress=\"if(event.key==='Enter') sendChatMessage()\">\n"
                +
                "            <button class=\"chatbot-send\" onclick=\"sendChatMessage()\">Send</button>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        function toggleChatbot() {\n" +
                "            const panel = document.getElementById('chatPanel');\n" +
                "            \n" +
                "            if (panel.style.display === 'none' || panel.style.display === '') {\n" +
                "                panel.style.display = 'flex';\n" +
                "                \n" +
                "            } else {\n" +
                "                panel.style.display = 'none';\n" +
                "                \n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        function sendChatMessage() {\n" +
                "            const input = document.getElementById('chatbotInput');\n" +
                "            const message = input.value.trim();\n" +
                "            if (!message) return;\n" +
                "\n" +
                "            addChatMessage('user', message);\n" +
                "            input.value = '';\n" +
                "\n" +
                "            fetch('/api/chat', {\n" +
                "                method: 'POST',\n" +
                "                headers: {'Content-Type': 'application/x-www-form-urlencoded'},\n" +
                "                body: 'message=' + encodeURIComponent(message)\n" +
                "            })\n" +
                "            .then(response => response.json())\n" +
                "            .then(data => {\n" +
                "                if (data.success) {\n" +
                "                    addChatMessage('bot', data.response);\n" +
                "                } else {\n" +
                "                    addChatMessage('bot', 'Sorry, I encountered an error.');\n" +
                "                }\n" +
                "            })\n" +
                "            .catch(error => {\n" +
                "                console.error('Chat error:', error);\n" +
                "                addChatMessage('bot', 'Sorry, I\\'m having trouble connecting.');\n" +
                "            });\n" +
                "        }\n" +
                "\n" +
                "        function addChatMessage(sender, message) {\n" +
                "            const messages = document.getElementById('chatbotMessages');\n" +
                "            const messageDiv = document.createElement('div');\n" +
                "            messageDiv.className = 'chat-message ' + sender + '-message';\n" +
                "            const formattedMessage = message.replace(/\\\\n/g, '<br>').replace(/\\*\\*(.*?)\\*\\*/g, '<strong>$1</strong>');\n"
                +
                "            messageDiv.innerHTML = formattedMessage;\n" +
                "            messages.appendChild(messageDiv);\n" +
                "            messages.scrollTop = messages.scrollHeight;\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }

    private String getBooksJson() {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        // Only show books that are currently being read or have been read
        for (Book book : books) {
            String status = book.getStatus();
            if (status != null && (status.equalsIgnoreCase("currently-reading") ||
                    status.equalsIgnoreCase("read") ||
                    status.equalsIgnoreCase("currently reading") ||
                    status.equals("read"))) {
                if (!first)
                    json.append(",");
                first = false;

                json.append("{")
                        .append("\"title\":\"").append(escapeJson(book.getTitle())).append("\",")
                        .append("\"author\":\"").append(escapeJson(book.getAuthor())).append("\",")
                        .append("\"year\":").append(book.getYear()).append(",")
                        .append("\"genre\":\"").append(escapeJson(book.getGenre())).append("\",")
                        .append("\"status\":\"").append(escapeJson(book.getStatus())).append("\",")
                        .append("\"rating\":").append(book.getRating()).append(",")
                        .append("\"notes\":\"").append(escapeJson(book.getNotes())).append("\"")
                        .append("}");
            }
        }
        json.append("]");
        return json.toString();
    }

    private String getStatsJson() {
        int totalBooks = books.size();
        int booksRead = (int) books.stream().filter(book -> "Read".equalsIgnoreCase(book.getStatus())).count();
        int completionRate = totalBooks > 0 ? (booksRead * 100) / totalBooks : 0;
        double averageRating = books.stream()
                .filter(book -> book.getRating() > 0)
                .mapToDouble(Book::getRating)
                .average()
                .orElse(0.0);

        return String.format("{\"totalBooks\":%d,\"booksRead\":%d,\"completionRate\":%d,\"averageRating\":%.1f}",
                totalBooks, booksRead, completionRate, averageRating);
    }

    private String escapeJson(String str) {
        if (str == null)
            return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void loadBooksFromFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(DATA_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    String title = parts[0];
                    String author = parts[1];
                    int year = 0;
                    try {
                        year = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException e) {
                        // Keep year as 0 if invalid
                    }

                    Book book = new Book(title, author, year);

                    if (parts.length > 3)
                        book.setGenre(parts[3]);
                    if (parts.length > 4)
                        book.setStatus(parts[4]);
                    if (parts.length > 5) {
                        try {
                            book.setRating(Integer.parseInt(parts[5]));
                        } catch (NumberFormatException e) {
                            // Keep default rating if invalid
                        }
                    }
                    if (parts.length > 6)
                        book.setNotes(parts[6]);

                    books.add(book);
                }
            }
            System.out.println("📚 Loaded " + books.size() + " books from file");
        } catch (IOException e) {
            System.out.println("📚 No existing library data found. Initializing with sample books...");
            initializeSampleBooks();
        }
    }

    private void initializeSampleBooks() {
        // Add diverse collection of 15 real, popular, and acclaimed books
        Book book1 = new Book("The Lord of the Rings", "J.R.R. Tolkien", 1954);
        book1.setGenre("Fantasy");
        book1.setRating(5);
        book1.setStatus("Available");
        books.add(book1);

        Book book2 = new Book("Harry Potter and the Philosopher's Stone", "J.K. Rowling", 1997);
        book2.setGenre("Fantasy");
        book2.setRating(5);
        book2.setStatus("Available");
        books.add(book2);

        Book book3 = new Book("The Great Gatsby", "F. Scott Fitzgerald", 1925);
        book3.setGenre("Fiction");
        book3.setRating(4);
        book3.setStatus("Available");
        books.add(book3);

        Book book4 = new Book("To Kill a Mockingbird", "Harper Lee", 1960);
        book4.setGenre("Fiction");
        book4.setRating(5);
        book4.setStatus("Available");
        books.add(book4);

        Book book5 = new Book("1984", "George Orwell", 1949);
        book5.setGenre("Dystopian Fiction");
        book5.setRating(5);
        book5.setStatus("Available");
        books.add(book5);

        Book book6 = new Book("Pride and Prejudice", "Jane Austen", 1813);
        book6.setGenre("Romance");
        book6.setRating(4);
        book6.setStatus("Available");
        books.add(book6);

        Book book7 = new Book("The Catcher in the Rye", "J.D. Salinger", 1951);
        book7.setGenre("Coming-of-Age Fiction");
        book7.setRating(4);
        book7.setStatus("Available");
        books.add(book7);

        Book book8 = new Book("Dune", "Frank Herbert", 1965);
        book8.setGenre("Science Fiction");
        book8.setRating(5);
        book8.setStatus("Available");
        books.add(book8);

        Book book9 = new Book("The Da Vinci Code", "Dan Brown", 2003);
        book9.setGenre("Mystery Thriller");
        book9.setRating(4);
        book9.setStatus("Available");
        books.add(book9);

        Book book10 = new Book("The Hunger Games", "Suzanne Collins", 2008);
        book10.setGenre("Dystopian Fiction");
        book10.setRating(4);
        book10.setStatus("Available");
        books.add(book10);

        Book book11 = new Book("Gone Girl", "Gillian Flynn", 2012);
        book11.setGenre("Psychological Thriller");
        book11.setRating(4);
        book11.setStatus("Available");
        books.add(book11);

        Book book12 = new Book("The Alchemist", "Paulo Coelho", 1988);
        book12.setGenre("Philosophical Fiction");
        book12.setRating(4);
        book12.setStatus("Available");
        books.add(book12);

        Book book13 = new Book("Sapiens", "Yuval Noah Harari", 2011);
        book13.setGenre("Non-Fiction");
        book13.setRating(5);
        book13.setStatus("Available");
        books.add(book13);

        Book book14 = new Book("Educated", "Tara Westover", 2018);
        book14.setGenre("Memoir");
        book14.setRating(5);
        book14.setStatus("Available");
        books.add(book14);

        Book book15 = new Book("Where the Crawdads Sing", "Delia Owens", 2018);
        book15.setGenre("Literary Fiction");
        book15.setRating(4);
        book15.setStatus("Available");
        books.add(book15);

        System.out.println("📚 Initialized library with " + books.size() + " diverse books");
        saveBooksToMongoDB(); // Save sample books to MongoDB
    }

    private void saveBooksToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(DATA_FILE))) {
            for (Book book : books) {
                writer.println(book.getTitle() + "|" + book.getAuthor() + "|" + book.getYear() + "|" +
                        (book.getGenre() != null ? book.getGenre() : "") + "|" +
                        (book.getStatus() != null ? book.getStatus() : "") + "|" +
                        book.getRating() + "|" +
                        (book.getNotes() != null ? book.getNotes() : ""));
            }
        } catch (IOException e) {
            System.err.println("Error saving books to file: " + e.getMessage());
        }
    }

    // Security utility methods
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes());
            byte[] hashedPassword = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hashedPassword);
        } catch (Exception e) {
            System.err.println("Error hashing password: " + e.getMessage());
            return password; // Fallback to plain text for development
        }
    }

    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private boolean isSessionExpired(String sessionId) {
        LocalDateTime timestamp = sessionTimestamps.get(sessionId);
        if (timestamp == null)
            return true;
        return timestamp.isBefore(LocalDateTime.now().minusHours(8)); // 8-hour session timeout
    }

    private boolean isIPBlocked(String clientIP) {
        Integer attempts = loginAttempts.getOrDefault(clientIP, 0);
        LocalDateTime lastAttempt = lastLoginAttempt.get(clientIP);

        if (attempts >= 5) {
            if (lastAttempt != null && lastAttempt.isAfter(LocalDateTime.now().minusMinutes(15))) {
                return true; // Blocked for 15 minutes after 5 failed attempts
            } else {
                // Reset attempts after 15 minutes
                loginAttempts.put(clientIP, 0);
                return false;
            }
        }
        return false;
    }

    private void recordLoginAttempt(String clientIP, boolean success) {
        if (success) {
            loginAttempts.put(clientIP, 0); // Reset on successful login
        } else {
            int attempts = loginAttempts.getOrDefault(clientIP, 0) + 1;
            loginAttempts.put(clientIP, attempts);
        }
        lastLoginAttempt.put(clientIP, LocalDateTime.now());
    }

    private String getClientIP(HttpExchange exchange) {
        String xForwardedFor = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    // Book Browse Handler - Shows all books for selection
    class BookBrowseHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            SimpleUser user = getAuthenticatedUser(exchange);
            if (user == null) {
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                String response = getBookBrowsePage(user);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    // Book Details Handler - Gets detailed info for a specific book
    class BookDetailsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            SimpleUser user = getAuthenticatedUser(exchange);
            if (user == null) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                String title = "";
                if (query != null && query.startsWith("title=")) {
                    title = java.net.URLDecoder.decode(query.substring(6), StandardCharsets.UTF_8);
                }

                Book book = findBookByTitle(title);
                if (book != null) {
                    String jsonResponse = bookToJson(book);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, jsonResponse.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    String response = "{\"success\": false, \"message\": \"Book not found\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(404, response.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }
    }

    // Update Reading Handler - Updates rating, notes, and status
    class UpdateReadingHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            SimpleUser user = getAuthenticatedUser(exchange);
            if (user == null) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);

                String title = params.get("title");
                String rating = params.get("rating");
                String notes = params.get("notes");
                String status = params.get("status");

                Book book = findBookByTitle(title);
                if (book != null) {
                    // Update book details
                    if (rating != null && !rating.isEmpty()) {
                        try {
                            int ratingValue = Integer.parseInt(rating);
                            if (ratingValue >= 1 && ratingValue <= 5) {
                                book.setRating(ratingValue);
                            }
                        } catch (NumberFormatException e) {
                            // Invalid rating, ignore
                        }
                    }

                    if (notes != null) {
                        book.setNotes(notes);
                    }

                    if (status != null && !status.isEmpty()) {
                        book.setStatus(status);
                    }

                    saveBooksToMongoDB(); // Save changes

                    String response = "{\"success\": true, \"message\": \"Book updated successfully!\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    String response = "{\"success\": false, \"message\": \"Book not found\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(404, response.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }
    }

    private Book findBookByTitle(String title) {
        return books.stream()
                .filter(book -> book.getTitle().equals(title))
                .findFirst()
                .orElse(null);
    }

    private String bookToJson(Book book) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"title\":\"").append(escapeJson(book.getTitle())).append("\",");
        json.append("\"author\":\"").append(escapeJson(book.getAuthor())).append("\",");
        json.append("\"year\":").append(book.getYear()).append(",");
        json.append("\"genre\":\"").append(escapeJson(book.getGenre() != null ? book.getGenre() : "")).append("\",");
        json.append("\"rating\":").append(book.getRating()).append(",");
        json.append("\"status\":\"").append(escapeJson(book.getStatus() != null ? book.getStatus() : "Available"))
                .append("\",");
        json.append("\"notes\":\"").append(escapeJson(book.getNotes() != null ? book.getNotes() : "")).append("\"");
        json.append("}");
        return json.toString();
    }

    private String getBookBrowsePage(SimpleUser user) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Browse Books - Library Manager</title>\n");
        html.append("    <style>\n");
        html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append(
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; }\n");
        html.append("        .container { max-width: 1200px; margin: 0 auto; padding: 20px; }\n");
        html.append(
                "        .header { background: white; border-radius: 15px; padding: 20px; margin-bottom: 20px; text-align: center; box-shadow: 0 8px 32px rgba(0,0,0,0.1); }\n");
        html.append(
                "        .book-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; }\n");
        html.append(
                "        .book-card { background: white; border-radius: 15px; padding: 20px; box-shadow: 0 8px 32px rgba(0,0,0,0.1); transition: transform 0.3s; cursor: pointer; }\n");
        html.append("        .book-card:hover { transform: translateY(-5px); }\n");
        html.append("        .book-title { font-size: 1.2em; font-weight: bold; color: #333; margin-bottom: 5px; }\n");
        html.append("        .book-author { color: #666; margin-bottom: 5px; }\n");
        html.append(
                "        .book-genre { background: #667eea; color: white; padding: 5px 10px; border-radius: 20px; font-size: 0.8em; display: inline-block; margin-bottom: 10px; }\n");
        html.append("        .book-year { color: #888; font-size: 0.9em; }\n");
        html.append("        .rating { color: #ffa500; margin-top: 10px; }\n");
        html.append("        .nav-buttons { text-align: center; margin-bottom: 20px; }\n");
        html.append(
                "        .nav-buttons a { background: white; color: #667eea; padding: 10px 20px; text-decoration: none; border-radius: 25px; margin: 0 10px; display: inline-block; }\n");
        html.append(
                "        .modal { display: none; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); }\n");
        html.append(
                "        .modal-content { background-color: white; margin: 5% auto; padding: 20px; border-radius: 15px; width: 80%; max-width: 500px; }\n");
        html.append(
                "        .close { color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor: pointer; }\n");
        html.append("        .form-group { margin-bottom: 15px; }\n");
        html.append("        .form-group label { display: block; margin-bottom: 5px; font-weight: bold; }\n");
        html.append(
                "        .form-group input, .form-group select, .form-group textarea { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 5px; }\n");
        html.append(
                "        .btn { background: #667eea; color: white; padding: 10px 20px; border: none; border-radius: 5px; cursor: pointer; }\n");
        html.append("        .btn:hover { background: #764ba2; }\n");
        html.append("    </style>\n");
        html.append("</head>\n<body>\n");

        html.append("    <div class=\"container\">\n");
        html.append("        <div class=\"header\">\n");
        html.append("            <h1>📚 Browse Library Collection</h1>\n");
        html.append("            <p>Select any book to rate, add notes, or update your reading status</p>\n");
        html.append("        </div>\n");

        html.append("        <div class=\"nav-buttons\">\n");
        html.append("            <a href=\"/dashboard\">🏠 Dashboard</a>\n");
        html.append("            <a href=\"/api/logout\">🚪 Logout</a>\n");
        html.append("        </div>\n");

        html.append("        <div class=\"book-grid\">\n");

        // Add all books to the grid
        for (Book book : books) {
            html.append("            <div class=\"book-card\" onclick=\"selectBook('")
                    .append(escapeHtml(book.getTitle())).append("')\">\n");
            html.append("                <div class=\"book-title\">").append(escapeHtml(book.getTitle()))
                    .append("</div>\n");
            html.append("                <div class=\"book-author\">by ").append(escapeHtml(book.getAuthor()))
                    .append("</div>\n");
            if (book.getGenre() != null && !book.getGenre().isEmpty()) {
                html.append("                <div class=\"book-genre\">").append(escapeHtml(book.getGenre()))
                        .append("</div>\n");
            }
            html.append("                <div class=\"book-year\">").append(book.getYear()).append("</div>\n");
            if (book.getRating() > 0) {
                html.append("                <div class=\"rating\">");
                for (int i = 0; i < book.getRating(); i++) {
                    html.append("⭐");
                }
                html.append(" (").append(book.getRating()).append("/5)</div>\n");
            }
            html.append("            </div>\n");
        }

        html.append("        </div>\n");
        html.append("    </div>\n");

        // Modal for book details
        html.append("    <div id=\"bookModal\" class=\"modal\">\n");
        html.append("        <div class=\"modal-content\">\n");
        html.append("            <span class=\"close\" onclick=\"closeModal()\">&times;</span>\n");
        html.append("            <h2 id=\"modalTitle\">Book Details</h2>\n");
        html.append("            <form id=\"bookForm\">\n");
        html.append("                <input type=\"hidden\" id=\"bookTitle\" name=\"title\">\n");
        html.append("                <div class=\"form-group\">\n");
        html.append("                    <label for=\"rating\">Your Rating (1-5 stars):</label>\n");
        html.append("                    <select id=\"rating\" name=\"rating\">\n");
        html.append("                        <option value=\"\">Select rating...</option>\n");
        html.append("                        <option value=\"1\">⭐ 1 Star</option>\n");
        html.append("                        <option value=\"2\">⭐⭐ 2 Stars</option>\n");
        html.append("                        <option value=\"3\">⭐⭐⭐ 3 Stars</option>\n");
        html.append("                        <option value=\"4\">⭐⭐⭐⭐ 4 Stars</option>\n");
        html.append("                        <option value=\"5\">⭐⭐⭐⭐⭐ 5 Stars</option>\n");
        html.append("                    </select>\n");
        html.append("                </div>\n");
        html.append("                <div class=\"form-group\">\n");
        html.append("                    <label for=\"status\">Reading Status:</label>\n");
        html.append("                    <select id=\"status\" name=\"status\">\n");
        html.append("                        <option value=\"Available\">Available</option>\n");
        html.append("                        <option value=\"Want to Read\">Want to Read</option>\n");
        html.append("                        <option value=\"Currently Reading\">Currently Reading</option>\n");
        html.append("                        <option value=\"Read\">Finished Reading</option>\n");
        html.append("                    </select>\n");
        html.append("                </div>\n");
        html.append("                <div class=\"form-group\">\n");
        html.append("                    <label for=\"notes\">Personal Notes:</label>\n");
        html.append(
                "                    <textarea id=\"notes\" name=\"notes\" rows=\"4\" placeholder=\"Your thoughts about this book...\"></textarea>\n");
        html.append("                </div>\n");
        html.append(
                "                <button type=\"button\" class=\"btn\" onclick=\"updateBook()\">💾 Save Changes</button>\n");
        html.append("            </form>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");

        // JavaScript
        html.append("    <script>\n");
        html.append("        function selectBook(title) {\n");
        html.append("            fetch('/api/book-details?title=' + encodeURIComponent(title))\n");
        html.append("                .then(response => response.json())\n");
        html.append("                .then(book => {\n");
        html.append("                    document.getElementById('modalTitle').textContent = book.title;\n");
        html.append("                    document.getElementById('bookTitle').value = book.title;\n");
        html.append("                    document.getElementById('rating').value = book.rating || '';\n");
        html.append("                    document.getElementById('status').value = book.status || 'Available';\n");
        html.append("                    document.getElementById('notes').value = book.notes || '';\n");
        html.append("                    document.getElementById('bookModal').style.display = 'block';\n");
        html.append("                })\n");
        html.append("                .catch(error => alert('Error loading book details'));\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        function closeModal() {\n");
        html.append("            document.getElementById('bookModal').style.display = 'none';\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        function updateBook() {\n");
        html.append("            const formData = new FormData(document.getElementById('bookForm'));\n");
        html.append("            fetch('/api/update-reading', {\n");
        html.append("                method: 'POST',\n");
        html.append("                body: new URLSearchParams(formData)\n");
        html.append("            })\n");
        html.append("            .then(response => response.json())\n");
        html.append("            .then(data => {\n");
        html.append("                if (data.success) {\n");
        html.append("                    alert('Book updated successfully!');\n");
        html.append("                    closeModal();\n");
        html.append("                    location.reload();\n");
        html.append("                } else {\n");
        html.append("                    alert('Error: ' + data.message);\n");
        html.append("                }\n");
        html.append("            })\n");
        html.append("            .catch(error => alert('Error updating book'));\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        window.onclick = function(event) {\n");
        html.append("            if (event.target == document.getElementById('bookModal')) {\n");
        html.append("                closeModal();\n");
        html.append("            }\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        function searchBooks() {\n");
        html.append("            const query = document.getElementById('searchInput').value.trim();\n");
        html.append("            const resultsDiv = document.getElementById('searchResults');\n");
        html.append("            \n");
        html.append("            if (!query) {\n");
        html.append(
                "                resultsDiv.innerHTML = '<p style=\"color: rgba(255,255,255,0.7);\">Please enter a search term</p>';\n");
        html.append("                return;\n");
        html.append("            }\n");
        html.append("            \n");
        html.append("            fetch('/api/search-books?q=' + encodeURIComponent(query))\n");
        html.append("                .then(response => response.json())\n");
        html.append("                .then(data => {\n");
        html.append("                    if (data.success && data.books.length > 0) {\n");
        html.append("                        let html = '<div style=\"display: grid; gap: 15px;\">';\n");
        html.append("                        data.books.forEach(book => {\n");
        html.append("                            const status = book.readingStatus || 'Not Started';\n");
        html.append("                            const rating = book.rating || 'No rating';\n");
        html.append("                            html += `\n");
        html.append(
                "                                <div style=\"background: rgba(255,255,255,0.1); padding: 15px; border-radius: 10px; border-left: 4px solid rgba(76, 175, 80, 0.8);\">\n");
        html.append(
                "                                    <h4 style=\"margin: 0 0 5px 0; color: #4CAF50;\">${book.title}</h4>\n");
        html.append(
                "                                    <p style=\"margin: 0 0 5px 0; color: rgba(255,255,255,0.9);\">by ${book.author} (${book.publicationYear}) - ${book.genre}</p>\n");
        html.append(
                "                                    <div style=\"display: flex; justify-content: space-between; align-items: center; margin-top: 10px;\">\n");
        html.append(
                "                                        <span style=\"color: rgba(255,255,255,0.7); font-size: 14px;\">Status: ${status} | Rating: ${rating}</span>\n");
        html.append(
                "                                        <button onclick=\"selectBookFromSearch('${book.title}')\" style=\"background: rgba(33, 150, 243, 0.8); color: white; border: none; padding: 6px 12px; border-radius: 5px; cursor: pointer; font-size: 12px;\">Select Book</button>\n");
        html.append("                                    </div>\n");
        html.append("                                </div>\n");
        html.append("                            `;\n");
        html.append("                        });\n");
        html.append("                        html += '</div>';\n");
        html.append("                        resultsDiv.innerHTML = html;\n");
        html.append("                    } else {\n");
        html.append("                        resultsDiv.innerHTML = `\n");
        html.append(
                "                            <div style=\"background: rgba(255, 152, 0, 0.1); padding: 15px; border-radius: 10px; border-left: 4px solid rgba(255, 152, 0, 0.8);\">\n");
        html.append(
                "                                <p style=\"margin: 0; color: rgba(255, 152, 0, 0.9);\">📚 Book not found in our collection.</p>\n");
        html.append(
                "                                <p style=\"margin: 5px 0 0 0; color: rgba(255,255,255,0.7); font-size: 14px;\">Consider requesting it using the form below!</p>\n");
        html.append("                            </div>\n");
        html.append("                        `;\n");
        html.append("                    }\n");
        html.append("                })\n");
        html.append("                .catch(error => {\n");
        html.append(
                "                    resultsDiv.innerHTML = '<p style=\"color: rgba(244, 67, 54, 0.9);\">Error searching books</p>';\n");
        html.append("                });\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        function selectBookFromSearch(title) {\n");
        html.append("            fetch('/api/book-details?title=' + encodeURIComponent(title))\n");
        html.append("                .then(response => response.json())\n");
        html.append("                .then(data => {\n");
        html.append("                    if (data.success) {\n");
        html.append("                        showBookModal(data.book);\n");
        html.append("                    }\n");
        html.append("                });\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("    </script>\n");
        html.append("</body>\n</html>");

        return html.toString();
    }

    private int getGenreCount() {
        return (int) books.stream()
                .map(Book::getGenre)
                .filter(genre -> genre != null && !genre.isEmpty())
                .distinct()
                .count();
    }

    private String getTopGenres() {
        return books.stream()
                .filter(book -> book.getGenre() != null)
                .collect(Collectors.groupingBy(Book::getGenre, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));
    }

    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // Handler for book search functionality
    class BookSearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("🔍 BookSearchHandler called!");

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method not allowed");
                return;
            }

            String query = getQueryParameter(exchange.getRequestURI().getQuery(), "q");
            System.out.println("📝 Search query: '" + query + "'");

            if (query == null || query.trim().isEmpty()) {
                sendJsonResponse(exchange, "{\"success\": false, \"message\": \"No search query provided\"}");
                return;
            }

            String searchTerm = query.toLowerCase().trim();
            System.out.println("🔍 Searching for: '" + searchTerm + "' in " + books.size() + " books");

            List<Book> matchingBooks = books.stream()
                    .filter(book -> book.getTitle().toLowerCase().contains(searchTerm) ||
                            book.getAuthor().toLowerCase().contains(searchTerm) ||
                            (book.getGenre() != null && book.getGenre().toLowerCase().contains(searchTerm)))
                    .collect(Collectors.toList());

            System.out.println("✅ Found " + matchingBooks.size() + " matching books");

            StringBuilder json = new StringBuilder();
            json.append("{\"success\": true, \"books\": [");
            for (int i = 0; i < matchingBooks.size(); i++) {
                Book book = matchingBooks.get(i);
                if (i > 0)
                    json.append(",");
                json.append("{")
                        .append("\"title\": \"").append(escapeJson(book.getTitle())).append("\",")
                        .append("\"author\": \"").append(escapeJson(book.getAuthor())).append("\",")
                        .append("\"publicationYear\": ").append(book.getYear()).append(",")
                        .append("\"genre\": \"").append(escapeJson(book.getGenre())).append("\",")
                        .append("\"rating\": \"")
                        .append(book.getRating() > 0 ? book.getRating() + " stars" : "No rating").append("\",")
                        .append("\"readingStatus\": \"")
                        .append(book.getStatus() != null ? book.getStatus() : "Not Started").append("\"")
                        .append("}");
            }
            json.append("]}");

            sendJsonResponse(exchange, json.toString());
        }
    }

    private void sendJsonResponse(HttpExchange exchange, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, json.getBytes().length);
        exchange.getResponseBody().write(json.getBytes());
        exchange.getResponseBody().close();
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        exchange.getResponseBody().write(response.getBytes());
        exchange.getResponseBody().close();
    }

    private String getQueryParameter(String query, String param) {
        if (query == null)
            return null;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && keyValue[0].equals(param)) {
                try {
                    return java.net.URLDecoder.decode(keyValue[1], "UTF-8");
                } catch (Exception e) {
                    return keyValue[1];
                }
            }
        }
        return null;
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1)
            return null;

        startIndex = json.indexOf(":", startIndex) + 1;
        startIndex = json.indexOf("\"", startIndex) + 1;
        int endIndex = json.indexOf("\"", startIndex);

        if (startIndex > 0 && endIndex > startIndex) {
            return json.substring(startIndex, endIndex);
        }
        return null;
    }

    public static void main(String[] args) {
        try {
            AuthenticatedLibraryWebServer server = new AuthenticatedLibraryWebServer();
            server.start(8080);

            // Keep server running
            System.out.println("Press Ctrl+C to stop the server");
            Thread.currentThread().join();
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("Server stopped");
        }
    }
}
