import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OpenAI API Wrapper for Library Management Chatbot
 * Provides intelligent responses using GPT-4 focused on library operations
 */
public class OpenAIWrapper {
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private String apiKey;
    private String model = "gpt-4o-mini"; // Cost-effective model

    public OpenAIWrapper(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Generate AI response for library management queries
     */
    public String generateResponse(String userMessage, SimpleUser currentUser, List<Book> books) {
        try {
            String systemPrompt = buildSystemPrompt(currentUser, books);
            String requestBody = buildRequestBody(systemPrompt, userMessage);

            HttpURLConnection connection = createConnection();

            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read response
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                return parseResponse(connection);
            } else {
                String errorMessage = "OpenAI API Error: " + responseCode;
                if (responseCode == 429) {
                    errorMessage += " (Rate limit exceeded - temporarily switching to local AI mode)";
                } else if (responseCode == 401) {
                    errorMessage += " (Invalid API key)";
                } else if (responseCode == 403) {
                    errorMessage += " (Access forbidden)";
                }
                System.err.println(errorMessage);
                // Throw exception so chatbot can fall back to enhanced responses
                throw new RuntimeException(errorMessage);
            }

        } catch (Exception e) {
            System.err.println("OpenAI API Exception: " + e.getMessage());
            // Always throw exception to let LibraryChatbot handle fallback properly
            throw new RuntimeException("OpenAI API temporarily unavailable: " + e.getMessage());
        }
    }

    private String buildSystemPrompt(SimpleUser currentUser, List<Book> books) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are LibraryBot, an intelligent assistant for a Library Management System. ");
        prompt.append("You help with book discovery, recommendations, library statistics, and management tasks.\n\n");

        // User context
        if (currentUser != null) {
            if (currentUser.isLibrarian()) {
                prompt.append("USER ROLE: Librarian (").append(currentUser.getFullName()).append(")\n");
                prompt.append("PERMISSIONS: Full library management, statistics, user insights, book operations\n");
            } else {
                prompt.append("USER ROLE: Library User (").append(currentUser.getFullName()).append(")\n");
                prompt.append("PERMISSIONS: Book browsing, rating, status updates, personal statistics\n");
            }
        }

        // Complete library inventory for accurate responses
        prompt.append("\nCOMPLETE LIBRARY INVENTORY:\n");
        prompt.append("Total Books: ").append(books.size()).append("\n\n");

        if (!books.isEmpty()) {
            prompt.append("EXACT BOOK LIST (use this for all queries):\n");
            for (Book book : books) {
                prompt.append("• \"").append(book.getTitle()).append("\" by ").append(book.getAuthor())
                        .append(" (").append(book.getYear()).append(", ").append(book.getGenre())
                        .append(", Status: ").append(book.getStatus())
                        .append(", Rating: ").append(book.getRating()).append("/5)\n");
            }

            prompt.append("\nLIBRARY STATISTICS:\n");
            // Popular genres
            long fictionCount = books.stream().filter(b -> "Fiction".equalsIgnoreCase(b.getGenre())).count();
            long nonFictionCount = books.stream().filter(b -> "Non-Fiction".equalsIgnoreCase(b.getGenre())).count();
            prompt.append("- Fiction Books: ").append(fictionCount).append("\n");
            prompt.append("- Non-Fiction Books: ").append(nonFictionCount).append("\n");

            // Ratings overview
            double avgRating = books.stream().filter(b -> b.getRating() > 0).mapToInt(Book::getRating).average()
                    .orElse(0.0);
            prompt.append("- Average Rating: ").append(String.format("%.1f", avgRating)).append("/5\n");
        }

        prompt.append("\nYOUR CAPABILITIES:\n");
        prompt.append("📚 Book Search & Discovery: Help find books by title, author, genre, year\n");
        prompt.append("⭐ Smart Recommendations: Suggest books based on preferences, ratings, reading history\n");
        prompt.append("📊 Statistics & Analytics: Provide reading insights, library metrics, progress tracking\n");
        prompt.append("➕ Library Management: Guide users through adding, updating, removing books\n");
        prompt.append("🎯 Personalized Help: Adapt responses to user role and library context\n");

        prompt.append("\nCRITICAL ACCURACY RULES:\n");
        prompt.append("1. ONLY reference books that are listed in the EXACT BOOK LIST above\n");
        prompt.append("2. Use EXACT titles and authors as shown in the inventory\n");
        prompt.append("3. If a book is not in the list, say 'We don't have that book'\n");
        prompt.append("4. Always check Status and Rating information from the inventory\n");
        prompt.append("5. Give direct, specific answers based on the actual data\n");
        prompt.append("6. Never make up or assume information about books not in the list\n\n");

        prompt.append("RESPONSE GUIDELINES:\n");
        prompt.append("- Be helpful, friendly, and book-focused\n");
        prompt.append("- Keep responses concise and direct\n");
        prompt.append("- Reference actual library data from the inventory above\n");
        prompt.append("- Use exact book titles and author names\n");
        prompt.append("- Include status and rating when relevant\n");

        if (currentUser != null && currentUser.isLibrarian()) {
            prompt.append("- Provide management insights and operational suggestions\n");
        } else {
            prompt.append("- Focus on reading recommendations and personal library experience\n");
        }

        return prompt.toString();
    }

    private String buildRequestBody(String systemPrompt, String userMessage) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\":\"").append(model).append("\",");
        json.append("\"messages\":[");
        json.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(systemPrompt)).append("\"},");
        json.append("{\"role\":\"user\",\"content\":\"").append(escapeJson(userMessage)).append("\"}");
        json.append("],");
        json.append("\"max_tokens\":500,");
        json.append("\"temperature\":0.7");
        json.append("}");
        return json.toString();
    }

    private HttpURLConnection createConnection() throws IOException {
        try {
            URI uri = URI.create(OPENAI_API_URL);
            URL url = uri.toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            return connection;
        } catch (Exception e) {
            throw new IOException("Failed to create connection: " + e.getMessage(), e);
        }
    }

    private String parseResponse(HttpURLConnection connection) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }

            String jsonResponse = response.toString();

            // Simple JSON parsing to extract the message content
            int contentStart = jsonResponse.indexOf("\"content\":\"") + 11;
            if (contentStart > 10) {
                int contentEnd = jsonResponse.indexOf("\"", contentStart);
                while (contentEnd > 0 && jsonResponse.charAt(contentEnd - 1) == '\\') {
                    contentEnd = jsonResponse.indexOf("\"", contentEnd + 1);
                }
                if (contentEnd > contentStart) {
                    return unescapeJson(jsonResponse.substring(contentStart, contentEnd));
                }
            }

            return "I'm having trouble processing that request. Could you try again?";
        }
    }

    private String getFallbackResponse(String message, SimpleUser user) {
        message = message.toLowerCase();

        if (message.contains("hello") || message.contains("hi")) {
            return "👋 Hello! I'm your AI library assistant. How can I help you with books today?";
        }

        if (message.contains("recommend") || message.contains("suggest")) {
            return "📚 I'd love to recommend books! What genre interests you? Or tell me about books you've enjoyed recently.";
        }

        if (message.contains("search") || message.contains("find")) {
            return "🔍 I can help you find books! Try searching by title, author, or genre using the search features above.";
        }

        if (message.contains("help")) {
            if (user != null && user.isLibrarian()) {
                return "🤖 I'm here to help with library management! I can assist with book operations, user insights, and system analytics.";
            } else {
                return "🤖 I'm here to help you discover great books! I can recommend titles, help you search, and track your reading progress.";
            }
        }

        return "🤔 I'm currently experiencing some technical difficulties, but I'm still here to help! Try asking about book recommendations or library features.";
    }

    private String escapeJson(String text) {
        if (text == null)
            return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String text) {
        if (text == null)
            return "";
        return text.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getModel() {
        return model;
    }
}