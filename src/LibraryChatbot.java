import java.util.*;
import java.util.stream.Collectors;

/**
 * Intelligent Chatbot for Library Management System
 * Powered by OpenAI GPT for advanced natural language understanding
 * Provides intelligent responses focused on library management and book
 * recommendations
 */
public class LibraryChatbot {
    private ArrayList<Book> books;
    private SimpleUser currentUser;
    private List<String> conversationHistory;
    private Random random;
    private OpenAIWrapper openAI;
    private boolean useAI = true;

    // Intent patterns for fallback natural language processing
    private Map<String, List<String>> intentPatterns;

    public LibraryChatbot(ArrayList<Book> books) {
        this.books = books;
        this.conversationHistory = new ArrayList<>();
        this.random = new Random();
        initializeIntentPatterns();
        initializeOpenAI();
    }

    private void initializeOpenAI() {
        // Initialize without AI - can be enabled later with API key
        this.openAI = null;
        this.useAI = false;
        System.out.println("⚙️  Chatbot initialized in Enhanced Local Mode with full library intelligence.");
        System.out.println("🤖 Built-in AI active - all features available without external API dependencies!");
    }

    public void disableAI() {
        this.useAI = false;
        System.out.println("🔄 AI mode disabled - using Enhanced Local Mode for optimal performance.");
    }

    public void setCurrentUser(SimpleUser user) {
        this.currentUser = user;
    }

    private void initializeIntentPatterns() {
        intentPatterns = new HashMap<>();

        // Book search patterns
        intentPatterns.put("search", Arrays.asList(
                "find", "search", "look for", "show me", "list", "get books"));

        // Recommendation patterns
        intentPatterns.put("recommend", Arrays.asList(
                "recommend", "suggest", "what should i read", "book recommendation",
                "good book", "next book", "similar to"));

        // Statistics patterns
        intentPatterns.put("stats", Arrays.asList(
                "how many", "statistics", "stats", "progress", "count", "total"));

        // Add book patterns
        intentPatterns.put("add", Arrays.asList(
                "add book", "new book", "add a book", "create book", "insert book"));

        // Help patterns
        intentPatterns.put("help", Arrays.asList(
                "help", "how to", "what can you do", "commands", "assistance"));

        // Greeting patterns
        intentPatterns.put("greeting", Arrays.asList(
                "hello", "hi", "hey", "good morning", "good afternoon", "good evening"));
    }

    public String processMessage(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "I didn't catch that. Could you please try again?";
        }

        String originalMessage = userMessage.trim();
        conversationHistory.add("User: " + originalMessage);

        String response;

        System.out.println("DEBUG: useAI=" + useAI + ", openAI=" + (openAI != null ? "exists" : "null"));

        // Try AI-powered response first
        if (useAI && openAI != null) {
            try {
                response = openAI.generateResponse(originalMessage, currentUser, books);
                // Check if response indicates an error or technical difficulty
                if (response != null && !response.trim().isEmpty() &&
                        !response.contains("technical difficulties") &&
                        !response.contains("experiencing some") &&
                        !response.contains("try again")) {
                    conversationHistory.add("AI Bot: " + response);
                    System.out.println("DEBUG: Returning AI response: "
                            + response.substring(0, Math.min(50, response.length())) + "...");
                    return "🤖 " + response;
                } else {
                    System.out.println("DEBUG: AI returned error response, falling back to enhanced mode");
                    // Temporarily disable AI for this session due to errors
                    this.useAI = false;
                }
            } catch (Exception e) {
                System.err.println("AI response failed, using fallback: " + e.getMessage());
                // Temporarily disable AI for this session due to errors
                this.useAI = false;
                // Notify user about the switch to local mode (only once)
                if (e.getMessage().contains("429")) {
                    System.out.println("🔄 Switched to Enhanced Local Mode due to API rate limits");
                }
            }
        }

        // Enhanced fallback responses focused on your library
        response = generateEnhancedResponse(userMessage.toLowerCase().trim());
        conversationHistory.add("Library Bot: " + response);

        System.out.println("DEBUG: Returning enhanced response: "
                + response.substring(0, Math.min(50, response.length())) + "...");

        return response;
    }

    private String generateEnhancedResponse(String message) {
        System.out.println("DEBUG: Processing message: '" + message + "'");

        // Handle direct questions with short, specific answers
        if (isDirectQuestion(message)) {
            return handleDirectQuestion(message);
        }

        // Handle casual conversation patterns first
        if (isCasualQuestion(message)) {
            return handleCasualConversation(message);
        }

        // First show AI status if user asks about capabilities
        if (message.contains("what can you do") || message.contains("help") || message.contains("capabilities")) {
            String status = isAIEnabled() ? "🤖 **AI-Powered Mode Active**\\n\\n"
                    : "⚙️ **Standard Mode** (Configure OpenAI API key for advanced AI features)\\n\\n";
            return status + handleHelp();
        }

        return generateOriginalEnhancedResponse(message);
    }

    private String generateOriginalEnhancedResponse(String message) {
        // Enhanced library-specific responses
        if (message.contains("hello") || message.contains("hi") || message.contains("hey") ||
                message.contains("good morning") || message.contains("good afternoon")
                || message.contains("good evening")) {
            return handleEnhancedGreeting();
        }

        // Check for statistics requests first (before general search)
        if (message.contains("stats") || message.contains("statistics") ||
                message.contains("how many") || message.contains("analytics") ||
                (message.contains("show") && (message.contains("statistics") || message.contains("stats"))) ||
                message.contains("library statistics") || message.contains("library stats")) {
            System.out.println("DEBUG: Detected statistics request");
            return handleEnhancedStatistics(message);
        }

        // Handle natural recommendation requests
        if (isRecommendationQuery(message)) {
            return handleEnhancedRecommendation(message);
        }

        // Handle natural search queries
        if (isSearchQuery(message)) {
            System.out.println("DEBUG: Detected search request");
            return handleEnhancedSearch(message);
        }

        if (message.contains("add") && message.contains("book")) {
            return handleEnhancedAddBook(message);
        }

        // Library-specific queries
        if (message.contains("genre") || message.contains("category")) {
            return handleGenreInquiry();
        }

        if (message.contains("rating") || message.contains("rate")) {
            return handleRatingInquiry();
        }

        if (message.contains("status") || message.contains("available")) {
            return handleStatusInquiry();
        }

        // Enhanced general conversation handling
        return handleNaturalLanguageQuery(message);
    }

    private String handleEnhancedGreeting() {
        String[] greetings = {
                "Hi! I'm here to help with your library. What are you looking for?",
                "Hello! I can help you find books, get recommendations, or answer questions about our collection.",
                "Hey there! Ask me about books, authors, or anything library-related.",
                "Hi! I'm your library assistant. How can I help you today?"
        };

        String greeting = greetings[random.nextInt(greetings.length)];

        // Add brief library status
        if (!books.isEmpty()) {
            greeting += " We have " + books.size() + " books available.";
        }

        // Add role-specific example questions
        greeting += "\\n\\n💡 **Try asking me:**";

        if (currentUser != null && currentUser.isLibrarian()) {
            // Librarian-specific questions focusing on management and statistics
            greeting += "\\n• \"How many books are available?\"";
            greeting += "\\n• \"How many books are currently reading?\"";
            greeting += "\\n• \"Show me books with 5-star ratings\"";
            greeting += "\\n• \"Which books need review?\"";
            greeting += "\\n• \"Do we have books by popular authors like Tolkien?\"";
            greeting += "\\n• \"What's our library's average rating?\"";
            greeting += "\\n• \"How many fiction vs non-fiction books?\"";
        } else {
            // Reader-specific questions focusing on discovery and recommendations
            greeting += "\\n• \"Do you have books by Tolkien?\"";
            greeting += "\\n• \"Do you have anything by J.K. Rowling?\"";
            greeting += "\\n• \"Show me books by Harari\"";
            greeting += "\\n• \"Do you have 1984?\"";
            greeting += "\\n• \"Who wrote Harry Potter?\"";
            greeting += "\\n• \"What's the rating of Dune?\"";
            greeting += "\\n• \"Recommend something good to read\"";
        }

        return greeting;
    }

    private String handleEnhancedRecommendation(String message) {
        if (books.isEmpty()) {
            return "📚 I'd love to give recommendations, but your library is currently empty! " +
                    (currentUser != null && currentUser.isLibrarian()
                            ? "Use the management tools to add some books first."
                            : "Ask your librarian to add some books to get started.");
        }

        // Different recommendation approaches based on user role
        if (currentUser != null && currentUser.isLibrarian()) {
            return handleLibrarianRecommendations(message);
        } else {
            return handleReaderRecommendations(message);
        }
    }

    private String handleLibrarianRecommendations(String message) {
        // Librarian gets collection management insights and popular book data
        String result = "📊 **Collection Recommendations (Library Management Perspective):**\\n\\n";

        // Most popular books for patron recommendations
        List<Book> highRated = books.stream()
                .filter(book -> book.getRating() >= 4)
                .sorted((b1, b2) -> Integer.compare(b2.getRating(), b1.getRating()))
                .limit(3)
                .collect(Collectors.toList());

        if (!highRated.isEmpty()) {
            result += "⭐ **Highest Rated Books to Recommend to Patrons:**\\n";
            result += formatBookList(highRated);
        }

        // Books that might need promotion
        List<Book> available = books.stream()
                .filter(book -> "Available".equalsIgnoreCase(book.getStatus()))
                .filter(book -> book.getRating() >= 3)
                .limit(3)
                .collect(Collectors.toList());

        if (!available.isEmpty()) {
            result += "\\n📚 **Available Books Worth Promoting:**\\n";
            result += formatBookList(available);
        }

        // Collection insights
        Map<String, Long> genreCount = books.stream()
                .filter(book -> book.getGenre() != null)
                .collect(Collectors.groupingBy(Book::getGenre, Collectors.counting()));

        if (!genreCount.isEmpty()) {
            String mostPopular = genreCount.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("Unknown");

            result += "\\n🎭 **Collection Insights:**\\n";
            result += "• Most represented genre: " + mostPopular + "\\n";
            result += "• Consider promoting diverse genres to attract different readers\\n";
            result += "• Focus on highly-rated books for patron satisfaction";
        }

        if (!isAIEnabled()) {
            result += "\\n\\n🤖 *Enable AI for advanced collection analysis and acquisition recommendations!*";
        }

        return result;
    }

    private String handleReaderRecommendations(String message) {
        // Extract genre preference for readers
        String genre = extractGenre(message);
        List<Book> recommendations;

        if (!genre.isEmpty()) {
            recommendations = books.stream()
                    .filter(book -> book.getGenre() != null &&
                            book.getGenre().toLowerCase().contains(genre))
                    .filter(book -> "Available".equalsIgnoreCase(book.getStatus()))
                    .collect(Collectors.toList());

            if (recommendations.isEmpty()) {
                return "🤔 I don't see any available " + genre + " books right now. " +
                        "Here are some highly-rated books from other genres:\\n\\n" +
                        formatBookList(getTopRatedAvailableBooks());
            }
        } else {
            recommendations = getTopRatedAvailableBooks();
        }

        Collections.shuffle(recommendations);
        String result = "📖 **Personal Book Recommendations:**\\n\\n" +
                formatBookList(recommendations.stream().limit(3).collect(Collectors.toList()));

        // Add reading tips for users
        result += "\\n💡 **Reading Tips:**\\n";
        result += "• All recommended books are currently available\\n";
        result += "• Based on high ratings from other readers\\n";
        result += "• Try different genres to expand your reading horizons";

        if (!isAIEnabled()) {
            result += "\\n\\n🤖 *Enable AI for personalized recommendations based on your reading history!*";
        }

        return result;
    }

    private List<Book> getTopRatedAvailableBooks() {
        return books.stream()
                .filter(book -> "Available".equalsIgnoreCase(book.getStatus()))
                .filter(book -> book.getRating() >= 4)
                .sorted((b1, b2) -> Integer.compare(b2.getRating(), b1.getRating()))
                .limit(5)
                .collect(Collectors.toList());
    }

    private String handleEnhancedSearch(String message) {
        if (books.isEmpty()) {
            return "📚 Your library is currently empty. " +
                    (currentUser != null && currentUser.isLibrarian() ? "Add some books using the management interface!"
                            : "Ask your librarian to populate the library first.");
        }

        String searchTerm = extractSearchTerm(message);

        if (searchTerm.isEmpty()) {
            return "📖 **Your Complete Library Collection:**\\n\\n" +
                    formatBookList(books.stream().limit(10).collect(Collectors.toList())) +
                    (books.size() > 10 ? "\\n... and " + (books.size() - 10) + " more books!" : "");
        }

        // Enhanced search with intelligent matching
        List<Book> matches = findMatchingBooks(searchTerm, message);

        if (matches.isEmpty()) {
            return generateNoMatchResponse(searchTerm, message);
        }

        return generateMatchResponse(matches, searchTerm, message);
    }

    private List<Book> findMatchingBooks(String searchTerm, String originalMessage) {
        String lowerSearchTerm = searchTerm.toLowerCase();
        String lowerMessage = originalMessage.toLowerCase();

        return books.stream()
                .filter(book -> {
                    // Exact title match (highest priority)
                    if (book.getTitle().toLowerCase().equals(lowerSearchTerm)) {
                        return true;
                    }

                    // Exact author match
                    if (book.getAuthor().toLowerCase().equals(lowerSearchTerm)) {
                        return true;
                    }

                    // Title contains search term
                    if (book.getTitle().toLowerCase().contains(lowerSearchTerm)) {
                        return true;
                    }

                    // Author contains search term
                    if (book.getAuthor().toLowerCase().contains(lowerSearchTerm)) {
                        return true;
                    }

                    // Genre match
                    if (book.getGenre() != null && book.getGenre().toLowerCase().contains(lowerSearchTerm)) {
                        return true;
                    }

                    // Advanced matching for partial author names
                    String[] searchWords = lowerSearchTerm.split("\\s+");
                    String[] authorWords = book.getAuthor().toLowerCase().split("\\s+");

                    // Check if search contains author's last name
                    if (searchWords.length > 0 && authorWords.length > 1) {
                        String authorLastName = authorWords[authorWords.length - 1];
                        if (Arrays.asList(searchWords).contains(authorLastName)) {
                            return true;
                        }
                    }

                    // Check if any significant title words match
                    String[] titleWords = book.getTitle().toLowerCase().split("\\s+");
                    if (titleWords.length > 1) {
                        return Arrays.stream(titleWords)
                                .filter(word -> word.length() > 3)
                                .anyMatch(word -> lowerSearchTerm.contains(word) || word.contains(lowerSearchTerm));
                    }

                    return false;
                })
                .collect(Collectors.toList());
    }

    private String generateNoMatchResponse(String searchTerm, String originalMessage) {
        // Provide helpful suggestions based on what we have
        StringBuilder response = new StringBuilder();
        response.append("🔍 I couldn't find any books matching '").append(searchTerm).append("'.\\n\\n");

        // Suggest similar authors if search looks like an author name
        if (looksLikeAuthorName(searchTerm)) {
            response.append("**📚 Available Authors in our library:**\\n");
            Set<String> authors = books.stream()
                    .map(Book::getAuthor)
                    .collect(Collectors.toSet());

            authors.stream()
                    .sorted()
                    .forEach(author -> response.append("• ").append(author).append("\\n"));
        } else {
            response.append("**Available in your library:**\\n").append(getAvailableGenres());
        }

        response.append("\\n\\n💡 **Try asking:**\\n");
        response.append("• 'Do you have books by Tolkien?'\\n");
        response.append("• 'Show me Harry Potter'\\n");
        response.append("• 'Find fantasy books'\\n");
        response.append("• 'What books by J.K. Rowling do you have?'");

        return response.toString();
    }

    private String generateMatchResponse(List<Book> matches, String searchTerm, String originalMessage) {
        StringBuilder response = new StringBuilder();

        // Determine the type of match for better response
        boolean isAuthorSearch = originalMessage.toLowerCase().contains("by ") ||
                looksLikeAuthorName(searchTerm) ||
                matches.stream().anyMatch(book -> book.getAuthor().toLowerCase().contains(searchTerm.toLowerCase()));

        boolean isTitleSearch = matches.stream()
                .anyMatch(book -> book.getTitle().toLowerCase().contains(searchTerm.toLowerCase()));

        if (isAuthorSearch && matches.size() > 1) {
            String author = matches.get(0).getAuthor();
            response.append("📚 **Books by ").append(author).append(":**\\n\\n");
        } else if (isTitleSearch && matches.size() == 1) {
            response.append("📖 **Found the book you're looking for:**\\n\\n");
        } else {
            response.append("🎯 **Found ").append(matches.size()).append(" book(s) matching '").append(searchTerm)
                    .append("':**\\n\\n");
        }

        response.append(formatBookList(matches));

        // Add helpful context
        if (matches.size() == 1) {
            Book book = matches.get(0);
            response.append("\\n💡 **About this book:**\\n");
            response.append("📅 Published: ").append(book.getYear()).append("\\n");
            response.append("🎭 Genre: ").append(book.getGenre()).append("\\n");
            response.append("⭐ Rating: ").append(book.getRating()).append("/5\\n");
            response.append("📋 Status: ").append(book.getStatus());
        }

        return response.toString();
    }

    private boolean looksLikeAuthorName(String term) {
        // Simple heuristic: contains 2+ words, at least one starts with capital
        String[] words = term.split("\\s+");
        return words.length >= 2 ||
                (words.length == 1 && words[0].length() > 3 && Character.isUpperCase(words[0].charAt(0)));
    }

    private boolean isDirectQuestion(String message) {
        // Check for patterns that expect direct, short answers
        return message.contains("do you have") ||
                message.contains("how many") ||
                message.contains("what is") ||
                message.contains("who wrote") ||
                message.contains("when was") ||
                message.matches(".*\\b(is|are|was|were|does|did|can|will|would)\\b.*\\?") ||
                message.endsWith("?");
    }

    private String handleDirectQuestion(String message) {
        // Handle "do you have" questions
        if (message.contains("do you have")) {
            return handleDoYouHaveQuestion(message);
        }

        // Handle "how many" questions
        if (message.contains("how many")) {
            return handleHowManyQuestion(message);
        }

        // Handle "what is" questions
        if (message.contains("what is") || message.contains("what's")) {
            return handleWhatIsQuestion(message);
        }

        // Handle "who wrote" questions
        if (message.contains("who wrote") || message.contains("who is the author")) {
            return handleWhoWroteQuestion(message);
        }

        // Handle simple yes/no questions
        if (message.matches(".*\\b(is|are|was|were|does|did|can|will|would)\\b.*\\?")) {
            return handleYesNoQuestion(message);
        }

        // Default direct response
        return "I'll help you with that. " + generateEnhancedResponseDefault(message);
    }

    private String handleDoYouHaveQuestion(String message) {
        String searchTerm = extractDoYouHaveSearchTerm(message);
        if (searchTerm.isEmpty()) {
            return "Yes, we have " + books.size() + " books in our library.";
        }

        List<Book> matches = findMatchingBooks(searchTerm, message);
        if (matches.isEmpty()) {
            // Check if it's an author search that might have partial name
            if (looksLikeAuthorName(searchTerm)) {
                List<Book> authorMatches = books.stream()
                        .filter(book -> book.getAuthor().toLowerCase().contains(searchTerm.toLowerCase()))
                        .collect(Collectors.toList());

                if (!authorMatches.isEmpty()) {
                    if (authorMatches.size() == 1) {
                        Book book = authorMatches.get(0);
                        return "Yes! We have \"" + book.getTitle() + "\" by " + book.getAuthor() +
                                " (" + book.getStatus() + ", rated " + book.getRating() + "/5).";
                    } else {
                        String author = authorMatches.get(0).getAuthor();
                        return "Yes! We have " + authorMatches.size() + " books by " + author + ": " +
                                authorMatches.stream().map(Book::getTitle).collect(Collectors.joining(", ")) + ".";
                    }
                }
            }
            return "No, we don't have any books matching '" + searchTerm + "' in our library.";
        } else if (matches.size() == 1) {
            Book book = matches.get(0);
            return "Yes! We have \"" + book.getTitle() + "\" by " + book.getAuthor() +
                    " (" + book.getStatus() + ", rated " + book.getRating() + "/5).";
        } else {
            // Check if all matches are by the same author
            String firstAuthor = matches.get(0).getAuthor();
            boolean sameAuthor = matches.stream().allMatch(book -> book.getAuthor().equals(firstAuthor));

            if (sameAuthor) {
                return "Yes! We have " + matches.size() + " books by " + firstAuthor + ": " +
                        matches.stream().map(Book::getTitle).collect(Collectors.joining(", ")) + ".";
            } else {
                return "Yes! We have " + matches.size() + " books matching '" + searchTerm + "': " +
                        matches.stream().limit(3).map(Book::getTitle).collect(Collectors.joining(", ")) +
                        (matches.size() > 3 ? " and " + (matches.size() - 3) + " more." : ".");
            }
        }
    }

    private String handleHowManyQuestion(String message) {
        String lowerMessage = message.toLowerCase();

        // Handle status-specific counts
        if (lowerMessage.contains("read") && !lowerMessage.contains("currently reading")) {
            long readCount = books.stream()
                    .filter(book -> "Read".equalsIgnoreCase(book.getStatus()))
                    .count();
            return "We have " + readCount + " books marked as 'Read'.";
        }

        if (lowerMessage.contains("currently reading") || lowerMessage.contains("reading now")) {
            long readingCount = books.stream()
                    .filter(book -> "Currently Reading".equalsIgnoreCase(book.getStatus()))
                    .count();
            return "We have " + readingCount + " books currently being read.";
        }

        if (lowerMessage.contains("available")) {
            long availableCount = books.stream()
                    .filter(book -> "Available".equalsIgnoreCase(book.getStatus()))
                    .count();
            return "We have " + availableCount + " books available to read.";
        }

        if (lowerMessage.contains("want to read") || lowerMessage.contains("wishlist")) {
            long wantToReadCount = books.stream()
                    .filter(book -> "want-to-read".equalsIgnoreCase(book.getStatus()))
                    .count();
            return "We have " + wantToReadCount + " books on the want-to-read list.";
        }

        // Check for genre-specific counts
        String genre = extractGenre(message);
        if (!genre.isEmpty()) {
            long count = books.stream()
                    .filter(book -> book.getGenre() != null &&
                            book.getGenre().toLowerCase().contains(genre))
                    .count();
            return "We have " + count + " " + genre + " books.";
        }

        // Check for rating-specific counts
        if (lowerMessage.contains("5 star") || lowerMessage.contains("rated 5")) {
            long fiveStarCount = books.stream()
                    .filter(book -> book.getRating() == 5)
                    .count();
            return "We have " + fiveStarCount + " books rated 5 stars.";
        }

        if (lowerMessage.contains("4 star") || lowerMessage.contains("rated 4")) {
            long fourStarCount = books.stream()
                    .filter(book -> book.getRating() == 4)
                    .count();
            return "We have " + fourStarCount + " books rated 4 stars.";
        }

        // Default book count
        if (lowerMessage.contains("book")) {
            return "We have " + books.size() + " books in our library.";
        }

        return "We have " + books.size() + " books total in our library.";
    }

    private String handleWhatIsQuestion(String message) {
        if (message.contains("rating") || message.contains("rated")) {
            return handleRatingQuestion(message);
        }
        return "I can help with book information. What specific details do you need?";
    }

    private String handleWhoWroteQuestion(String message) {
        String bookTitle = extractBookTitleFromWhoWrote(message);
        if (!bookTitle.isEmpty()) {
            Optional<Book> book = books.stream()
                    .filter(b -> b.getTitle().toLowerCase().contains(bookTitle.toLowerCase()))
                    .findFirst();

            if (book.isPresent()) {
                return book.get().getAuthor() + " wrote \"" + book.get().getTitle() + "\".";
            } else {
                return "I don't have that book in our library to tell you the author.";
            }
        }
        return "Which book are you asking about?";
    }

    private String handleYesNoQuestion(String message) {
        // Try to extract book/author from the question
        List<Book> matches = findMatchingBooks(extractSearchTerm(message), message);
        if (!matches.isEmpty()) {
            return "Yes, we have that in our library.";
        }
        return "Could you be more specific about what you're looking for?";
    }

    private String extractDoYouHaveSearchTerm(String message) {
        // Extract the search term after "do you have"
        String[] patterns = { "do you have", "got", "have you got" };
        for (String pattern : patterns) {
            int index = message.toLowerCase().indexOf(pattern);
            if (index != -1) {
                String afterPattern = message.substring(index + pattern.length()).trim();
                // Remove common words and punctuation
                afterPattern = afterPattern.replaceAll("\\b(any|a|an|the|book|books|by)\\b", "").trim();
                afterPattern = afterPattern.replaceAll("[?.,!]", "").trim();
                return afterPattern;
            }
        }
        return "";
    }

    private String extractBookTitleFromWhoWrote(String message) {
        String[] patterns = { "who wrote", "who is the author of", "who's the author of" };
        for (String pattern : patterns) {
            int index = message.toLowerCase().indexOf(pattern);
            if (index != -1) {
                String afterPattern = message.substring(index + pattern.length()).trim();
                afterPattern = afterPattern.replaceAll("\\b(the|a|an)\\b", "").trim();
                afterPattern = afterPattern.replaceAll("[?.,!]", "").trim();
                return afterPattern;
            }
        }
        return "";
    }

    private String handleRatingQuestion(String message) {
        String searchTerm = extractSearchTerm(message);
        if (!searchTerm.isEmpty()) {
            List<Book> matches = findMatchingBooks(searchTerm, message);
            if (!matches.isEmpty()) {
                Book book = matches.get(0);
                return "\"" + book.getTitle() + "\" is rated " + book.getRating() + " out of 5 stars.";
            }
        }

        // General rating info
        double avgRating = books.stream()
                .filter(b -> b.getRating() > 0)
                .mapToInt(Book::getRating)
                .average()
                .orElse(0.0);
        return "Our library's average book rating is " + String.format("%.1f", avgRating) + " out of 5 stars.";
    }

    private String generateEnhancedResponseDefault(String message) {
        // This is the fallback to the original verbose response system
        return generateOriginalEnhancedResponse(message);
    }

    private String handleEnhancedStatistics(String message) {
        if (currentUser != null && !currentUser.isLibrarian() &&
                (message.contains("library") || message.contains("all books"))) {
            return "🔒 Library-wide statistics are restricted to librarians only.\\n\\n" +
                    "**Your Personal Reading Stats:**\\n" + getPersonalStats();
        }

        int totalBooks = books.size();
        if (totalBooks == 0) {
            String emptyResponse = "📊 **Library Statistics:**\\n\\n📚 Total Books: 0\\n\\n";
            if (currentUser != null && currentUser.isLibrarian()) {
                emptyResponse += "🔧 **Librarian Recommendation:** Start building your collection! Add diverse books across multiple genres to attract readers.";
            } else {
                emptyResponse += "📖 Your library is just getting started! Ask your librarian to add some books.";
            }
            return emptyResponse;
        }

        long booksRead = books.stream().filter(book -> "Read".equalsIgnoreCase(book.getStatus())).count();
        long currentlyReading = books.stream().filter(book -> "Currently Reading".equalsIgnoreCase(book.getStatus()))
                .count();
        long available = books.stream().filter(book -> "Available".equalsIgnoreCase(book.getStatus())).count();
        double avgRating = books.stream().filter(b -> b.getRating() > 0).mapToInt(Book::getRating).average()
                .orElse(0.0);

        String stats;

        if (currentUser != null && currentUser.isLibrarian()) {
            // Librarian-focused statistics
            stats = "📊 **Library Management Analytics:**\\n\\n";
            stats += "📚 **Collection Overview:**\\n";
            stats += "  • Total Books: " + totalBooks + "\\n";
            stats += "  • Available: " + available + "\\n";
            stats += "  • Currently Reading: " + currentlyReading + "\\n";
            stats += "  • Completed: " + booksRead + "\\n";
            stats += "⭐ **Quality Metrics:**\\n";
            stats += "  • Average Rating: " + String.format("%.1f", avgRating) + "/5\\n";
            stats += "  • High-rated books (4+ stars): " + books.stream().filter(b -> b.getRating() >= 4).count()
                    + "\\n";

            // Librarian-specific insights
            stats += "\\n📈 **Collection Insights:**\\n";
            if (totalBooks > 0) {
                int utilizationRate = (int) (((booksRead + currentlyReading) * 100) / totalBooks);
                stats += "  • Collection Utilization: " + utilizationRate + "%\\n";
            }

            // Books needing attention
            long lowRated = books.stream().filter(b -> b.getRating() > 0 && b.getRating() <= 2).count();
            long unrated = books.stream().filter(b -> b.getRating() == 0).count();
            if (lowRated > 0) {
                stats += "  • Books needing review: " + lowRated + "\\n";
            }
            if (unrated > 0) {
                stats += "  • Unrated books: " + unrated + "\\n";
            }

            stats += "\\n" + getLibrarianGenreAnalysis();

        } else {
            // User-focused statistics
            stats = "� **Your Reading Journey:**\\n\\n";
            stats += "📚 Books Available: " + available + "\\n";
            stats += "📖 You're Reading: " + currentlyReading + "\\n";
            stats += "✅ Books Completed: " + booksRead + "\\n";
            stats += "⭐ Average Rating: " + String.format("%.1f", avgRating) + "/5\\n";

            if (totalBooks > 0) {
                int completionRate = (int) ((booksRead * 100) / totalBooks);
                stats += "📈 Reading Progress: " + completionRate + "%\\n\\n";
            }

            stats += getPopularGenres();
        }

        if (!isAIEnabled()) {
            stats += "\\n\\n🤖 *Enable AI for advanced analytics and insights!*";
        }

        return stats;
    }

    private String getLibrarianGenreAnalysis() {
        Map<String, Long> genreCount = books.stream()
                .filter(book -> book.getGenre() != null)
                .collect(Collectors.groupingBy(Book::getGenre, Collectors.counting()));

        if (genreCount.isEmpty()) {
            return "🎭 **Genre Analysis:** No genre data available - consider adding genre information for better collection management.";
        }

        StringBuilder analysis = new StringBuilder("🎭 **Genre Distribution:**\\n");

        // Most represented genres
        genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> analysis.append("  • ").append(entry.getKey()).append(": ").append(entry.getValue())
                        .append(" books\\n"));

        // Recommendations for collection development
        if (genreCount.size() < 5) {
            analysis.append(
                    "\\n💡 **Collection Development:** Consider adding more genre diversity to attract different reader preferences.");
        }

        // Find underrepresented genres
        long maxCount = Collections.max(genreCount.values());
        long minCount = Collections.min(genreCount.values());
        if (maxCount > minCount * 3) {
            analysis.append(
                    "\\n📈 **Recommendation:** Some genres are underrepresented - consider balancing the collection.");
        }

        return analysis.toString();
    }

    private String handleEnhancedAddBook(String message) {
        if (currentUser == null) {
            return "🔐 Please log in to manage books in the library.";
        }

        if (!currentUser.isLibrarian()) {
            return "📚 Only librarians can add books to the library.\\n\\n" +
                    "You can rate and update existing books, or ask your librarian to add new titles!";
        }

        return "📝 **Add New Books to Your Library:**\\n\\n" +
                "Use the management interface above to add books with details like:\\n" +
                "• 📖 Title and Author\\n" +
                "• 🎭 Genre (Fiction, Mystery, Sci-Fi, etc.)\\n" +
                "• 📅 Publication Year\\n" +
                "• ⭐ Initial Rating\\n\\n" +
                "💡 *Pro tip: Add diverse genres to give users more options!*";
    }

    private List<Book> getTopRatedBooks(int limit) {
        return books.stream()
                .filter(book -> book.getRating() >= 4)
                .sorted((b1, b2) -> Integer.compare(b2.getRating(), b1.getRating()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<Book> getTopRatedBooks() {
        return getTopRatedBooks(3);
    }

    private String getAvailableGenres() {
        return books.stream()
                .map(Book::getGenre)
                .filter(genre -> genre != null && !genre.isEmpty())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private String getPopularGenres() {
        Map<String, Long> genreCount = books.stream()
                .filter(book -> book.getGenre() != null)
                .collect(Collectors.groupingBy(Book::getGenre, Collectors.counting()));

        if (genreCount.isEmpty()) {
            return "🎭 No genre data available yet.";
        }

        StringBuilder result = new StringBuilder("🎭 **Popular Genres:**\\n");
        genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> result.append("  • ").append(entry.getKey()).append(": ").append(entry.getValue())
                        .append(" books\\n"));

        return result.toString();
    }

    private String getPersonalStats() {
        // This would be enhanced with user-specific data in a full implementation
        return "📊 Feature coming soon! Track your personal reading journey.";
    }

    private String handleGenreInquiry() {
        Set<String> availableGenres = books.stream()
                .map(Book::getGenre)
                .filter(genre -> genre != null && !genre.isEmpty())
                .collect(Collectors.toSet());

        if (availableGenres.isEmpty()) {
            return "🎭 **Available Genres:**\\n\\nYour library doesn't have genre information yet. " +
                    "Popular genres include: Fiction, Mystery, Romance, Sci-Fi, Fantasy, Biography, History, Self-Help, Business.";
        }

        return "🎭 **Genres in Your Library:**\\n\\n" +
                availableGenres.stream().sorted().collect(Collectors.joining(", ")) +
                "\\n\\nTell me which genre interests you for personalized recommendations!";
    }

    private String handleRatingInquiry() {
        return "⭐ **About Book Ratings:**\\n\\n" +
                "• Rate books 1-5 stars (5 being excellent)\\n" +
                "• Your ratings help improve recommendations\\n" +
                "• Use the book management interface to rate books\\n" +
                "• Highly rated books (4+ stars) appear in top recommendations\\n\\n" +
                "💡 *Tip: Be honest with ratings - it helps everyone discover great books!*";
    }

    private String handleStatusInquiry() {
        long available = books.stream().filter(book -> "Available".equalsIgnoreCase(book.getStatus())).count();
        long checkedOut = books.stream().filter(book -> "Checked Out".equalsIgnoreCase(book.getStatus())).count();
        long reserved = books.stream().filter(book -> "Reserved".equalsIgnoreCase(book.getStatus())).count();

        return "📋 **Library Status Overview:**\\n\\n" +
                "✅ Available: " + available + " books\\n" +
                "📤 Checked Out: " + checkedOut + " books\\n" +
                "🔖 Reserved: " + reserved + " books\\n\\n" +
                "Use the book list to see specific availability!";
    }

    private String generateResponse(String message) {
        String intent = detectIntent(message);

        switch (intent) {
            case "greeting":
                return handleGreeting();
            case "search":
                return handleSearch(message);
            case "recommend":
                return handleRecommendation(message);
            case "stats":
                return handleStatistics(message);
            case "add":
                return handleAddBook(message);
            case "help":
                return handleHelp();
            default:
                return handleGeneral(message);
        }
    }

    private String detectIntent(String message) {
        for (Map.Entry<String, List<String>> entry : intentPatterns.entrySet()) {
            for (String pattern : entry.getValue()) {
                if (message.contains(pattern)) {
                    return entry.getKey();
                }
            }
        }
        return "general";
    }

    private String handleGreeting() {
        String[] greetings = {
                "Hello! I'm your library assistant. How can I help you today?",
                "Hi there! Ready to explore some books?",
                "Welcome to the library! What would you like to know?",
                "Hey! I'm here to help with all your book needs!"
        };

        String greeting = greetings[random.nextInt(greetings.length)];

        if (currentUser != null) {
            if (currentUser.isLibrarian()) {
                greeting += " As a librarian, I can help you with library management, statistics, and user insights.";
            } else {
                greeting += " I can help you find books, get recommendations, and manage your reading list.";
            }
        }

        return greeting;
    }

    private String handleSearch(String message) {
        if (books.isEmpty()) {
            return "📚 The library is currently empty. Would you like to add some books first?";
        }

        // Extract search terms
        String searchTerm = extractSearchTerm(message);

        if (searchTerm.isEmpty()) {
            return "📖 Here are all the books in the library:\\n" + formatBookList(books);
        }

        // Search books by title, author, or genre
        List<Book> matchingBooks = books.stream()
                .filter(book -> book.getTitle().toLowerCase().contains(searchTerm) ||
                        book.getAuthor().toLowerCase().contains(searchTerm) ||
                        (book.getGenre() != null && book.getGenre().toLowerCase().contains(searchTerm)))
                .collect(Collectors.toList());

        if (matchingBooks.isEmpty()) {
            return "🔍 I couldn't find any books matching '" + searchTerm
                    + "'. Try a different search term or ask for recommendations!";
        }

        return "📚 Found " + matchingBooks.size() + " book(s) matching '" + searchTerm + "':\\n" +
                formatBookList(matchingBooks);
    }

    private String handleRecommendation(String message) {
        if (books.isEmpty()) {
            return "📚 I'd love to recommend books, but the library is empty! Add some books first.";
        }

        // Extract genre preference if mentioned
        String preferredGenre = extractGenre(message);

        List<Book> recommendations;

        if (!preferredGenre.isEmpty()) {
            recommendations = books.stream()
                    .filter(book -> book.getGenre() != null &&
                            book.getGenre().toLowerCase().contains(preferredGenre))
                    .collect(Collectors.toList());
        } else {
            // Recommend highly rated books or random selection
            recommendations = books.stream()
                    .filter(book -> book.getRating() >= 4)
                    .collect(Collectors.toList());

            if (recommendations.isEmpty()) {
                recommendations = new ArrayList<>(books);
            }
        }

        if (recommendations.isEmpty()) {
            return "🤔 I couldn't find books in that genre. How about exploring something new?";
        }

        // Shuffle and limit to 3 recommendations
        Collections.shuffle(recommendations);
        recommendations = recommendations.stream().limit(3).collect(Collectors.toList());

        String response = "📖 Here are my recommendations for you:\\n";
        response += formatBookList(recommendations);
        response += "\\n💡 Would you like more recommendations or help with something else?";

        return response;
    }

    private String handleStatistics(String message) {
        if (currentUser != null && !currentUser.isLibrarian() &&
                (message.contains("library") || message.contains("all books"))) {
            return "🔒 Sorry, only librarians can access overall library statistics. I can show you your personal reading stats though!";
        }

        int totalBooks = books.size();
        long booksRead = books.stream().filter(book -> "Read".equalsIgnoreCase(book.getStatus())).count();
        long currentlyReading = books.stream().filter(book -> "Currently Reading".equalsIgnoreCase(book.getStatus()))
                .count();
        double averageRating = books.stream()
                .filter(book -> book.getRating() > 0)
                .mapToInt(Book::getRating)
                .average()
                .orElse(0.0);

        String stats = "📊 Library Statistics:\\n";
        stats += "📚 Total Books: " + totalBooks + "\\n";
        stats += "✅ Books Read: " + booksRead + "\\n";
        stats += "📖 Currently Reading: " + currentlyReading + "\\n";
        stats += "⭐ Average Rating: " + String.format("%.1f", averageRating) + "/5\\n";

        if (totalBooks > 0) {
            int completionRate = (int) ((booksRead * 100) / totalBooks);
            stats += "📈 Completion Rate: " + completionRate + "%\\n";
        }

        // Genre breakdown
        Map<String, Long> genreCount = books.stream()
                .filter(book -> book.getGenre() != null)
                .collect(Collectors.groupingBy(Book::getGenre, Collectors.counting()));

        if (!genreCount.isEmpty()) {
            stats += "\\n🎭 Popular Genres:\\n";
            StringBuilder genreStats = new StringBuilder(stats);
            genreCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(3)
                    .forEach(entry -> genreStats.append("  • ").append(entry.getKey()).append(": ")
                            .append(entry.getValue()).append(" books\\n"));
            stats = genreStats.toString();
        }

        return stats;
    }

    private String handleAddBook(String message) {
        if (currentUser == null) {
            return "🔐 Please log in to add books to the library.";
        }

        return "📝 To add a book, I'll need some information. You can either:\\n" +
                "• Use the 'Add Book' form on the main page\\n" +
                "• Tell me: 'Add [Title] by [Author]'\\n" +
                "• Or just say 'I want to add a book' and I'll guide you through it!\\n\\n" +
                "What would you prefer?";
    }

    private String handleHelp() {
        String help = "";

        if (currentUser != null && currentUser.isLibrarian()) {
            help += "� **Library Assistant Mode** - I can help you manage the library!\\n\\n";
            help += "📊 **Accurate Status Questions:**\\n";
            help += "• \"How many books are available?\" - Count available books\\n";
            help += "• \"How many books are read?\" - Count completed books\\n";
            help += "• \"How many books are currently reading?\" - Count in-progress books\\n";
            help += "• \"How many fiction vs non-fiction books?\" - Genre breakdown\\n\\n";

            help += "� **Author & Title Searches:**\\n";
            help += "• \"Do we have books by Tolkien?\" - Find by author\\n";
            help += "• \"Show me books by Harari\" - List author's works\\n";
            help += "• \"Do we have 1984?\" - Check specific titles\\n";
            help += "• \"What's the rating of Dune?\" - Get book ratings\\n\\n";

            help += "📚 **Collection Analysis:**\\n";
            help += "• \"Show me books with 5-star ratings\" - Quality analysis\\n";
            help += "• \"Which books need review?\" - Unrated books\\n";
            help += "• \"What's our library's average rating?\" - Overall quality\\n";
        } else {
            help += "� **Reading Assistant Mode** - I'm your personal reading companion!\\n\\n";
            help += "ℹ️ **Get Book Information:**\\n";
            help += "• \"Who wrote Harry Potter?\" - Author information\\n";
            help += "• \"What's the rating of Dune?\" - Book ratings and reviews\\n";
            help += "• \"Is The Great Gatsby available?\" - Check availability\\n\\n";

            help += "🔍 **Find Books You'll Love:**\\n";
            help += "• \"Do you have books by Tolkien?\" - Author searches\\n";
            help += "• \"Do you have anything by J.K. Rowling?\" - Find your favorites\\n";
            help += "• \"Show me books by Harari\" - Discover new authors\\n";
            help += "• \"Do you have 1984?\" - Check for specific titles\\n\\n";

            help += "� **Reading Progress:**\\n";
            help += "• \"Recommend something good to read\" - Personalized suggestions\\n";
            help += "• \"I like fantasy books\" - Genre-based recommendations\\n";
            help += "• \"Show me highly rated books\" - Quality recommendations\\n";
        }

        if (currentUser != null && currentUser.isLibrarian()) {
            help += "💬 **Librarian Natural Language Examples:**\\n";
            help += "• 'What are our most popular books this month?'\\n";
            help += "• 'Show me books that need cataloging'\\n";
            help += "• 'Which authors have multiple books in our collection?'\\n";
            help += "• 'Find books with low ratings for review'\\n";
            help += "• 'What genres are underrepresented in our library?'\\n";
            help += "• 'Help me identify duplicate entries'\\n";
            help += "• 'Show me statistics for collection development'\\n";
            help += "• 'Which books haven't been checked out recently?'\\n\\n";
            help += "🔧 **Library Management Focus:** I help you maintain, analyze, and improve the library collection with professional insights and administrative tools!";
        } else {
            help += "💬 **Reader Natural Language Examples:**\\n";
            help += "• 'I'm bored, suggest something good'\\n";
            help += "• 'Do you have any books by Tolkien?'\\n";
            help += "• 'I'm looking for Harry Potter'\\n";
            help += "• 'What do you think about George Orwell?'\\n";
            help += "• 'I love The Hunger Games, got similar books?'\\n";
            help += "• 'Can you find me something by J.K. Rowling?'\\n";
            help += "• 'I want something light and funny to read'\\n";
            help += "• 'Recommend me a page-turner thriller'\\n\\n";
            help += "📖 **Personal Reading Focus:** I help you discover amazing books, track your reading journey, and find your next favorite story!";
        }

        return help;
    }

    private boolean isCasualQuestion(String message) {
        return message.contains("what do you think") || message.contains("i'm bored") ||
                message.contains("i'm looking for") || message.contains("can you") ||
                message.contains("do you have") || message.contains("i need") ||
                message.contains("i want") || message.contains("tell me about") ||
                message.contains("i like") || message.contains("i love") ||
                message.contains("what's") || message.contains("whats") ||
                message.contains("how about") || message.contains("maybe");
    }

    private boolean isRecommendationQuery(String message) {
        return message.contains("recommend") || message.contains("suggest") ||
                message.contains("what should i read") || message.contains("good book") ||
                message.contains("next book") || message.contains("similar to") ||
                message.contains("i'm bored") || message.contains("something new") ||
                message.contains("i need something") || message.contains("what about") ||
                message.contains("any ideas") || message.contains("pick for me");
    }

    private boolean isSearchQuery(String message) {
        return message.contains("search") || message.contains("find") ||
                message.contains("show me") || message.contains("list") ||
                message.contains("do you have") || message.contains("books by") ||
                message.contains("anything by") || message.contains("look for") ||
                message.contains("got any") || message.contains("where is") ||
                message.contains("looking for");
    }

    private String handleCasualConversation(String message) {
        // Handle "I'm bored" or similar casual expressions
        if (message.contains("i'm bored") || message.contains("im bored") || message.contains("bored")) {
            if (currentUser != null && currentUser.isLibrarian()) {
                return "� Need something to do? How about analyzing our collection? Let me show you some interesting stats...\\n\\n"
                        +
                        handleEnhancedStatistics("show library statistics");
            } else {
                return "�😴 Feeling bored? Perfect time for a good book! Let me suggest something exciting...\\n\\n" +
                        handleEnhancedRecommendation("recommend something exciting");
            }
        }

        // Handle "I'm looking for..."
        if (message.contains("i'm looking for") || message.contains("im looking for")
                || message.contains("looking for")) {
            String searchTerm = extractAfterPhrase(message, "looking for");
            if (!searchTerm.isEmpty()) {
                // Check if it's a specific author or title
                String recognizedItem = findAuthorOrTitleInText(searchTerm);
                if (!recognizedItem.isEmpty()) {
                    return "🎯 I found " + recognizedItem + " in our library!\\n\\n"
                            + handleEnhancedSearch("find " + searchTerm);
                }
                return "🔍 Let me help you find " + searchTerm + "!\\n\\n" + handleEnhancedSearch("find " + searchTerm);
            }
            return "🔍 What are you looking for? I can help you find books by title, author, or genre!";
        }

        // Handle "I like..." or "I love..."
        if (message.contains("i like") || message.contains("i love")) {
            String preference = extractAfterPhrase(message, message.contains("i like") ? "i like" : "i love");
            if (!preference.isEmpty()) {
                String recognizedItem = findAuthorOrTitleInText(preference);
                if (!recognizedItem.isEmpty()) {
                    return "😊 Excellent choice! I see you enjoy " + recognizedItem + ". Here's what we have:\\n\\n" +
                            handleEnhancedSearch("find " + preference);
                }
                return "😊 Great taste! Since you enjoy " + preference + ", here are some similar books:\\n\\n" +
                        handleEnhancedSearch("find " + preference);
            }
        }

        // Handle "What do you think about..."
        if (message.contains("what do you think")) {
            String topic = extractAfterPhrase(message, "what do you think");
            if (!topic.isEmpty()) {
                String recognizedItem = findAuthorOrTitleInText(topic);
                if (!recognizedItem.isEmpty()) {
                    return "🤔 " + recognizedItem + " is fantastic! Here's what we have in our collection:\\n\\n" +
                            handleEnhancedSearch("find " + topic);
                }
                return "🤔 Interesting question about " + topic + "! Let me see what books we have on that topic:\\n\\n"
                        +
                        handleEnhancedSearch("find " + topic);
            }
        }

        // Handle "Can you..."
        if (message.contains("can you")) {
            if (message.contains("help") || message.contains("find") || message.contains("show")) {
                if (currentUser != null && currentUser.isLibrarian()) {
                    return "✨ Absolutely! I'm here to help with library management tasks. What do you need?\\n\\n" +
                            "🔧 **Librarian Services:**\\n" +
                            "• 'Show me collection statistics'\\n" +
                            "• 'Which books need review or cataloging?'\\n" +
                            "• 'What are our most popular titles?'\\n" +
                            "• 'Help me analyze genre distribution'\\n" +
                            "• 'Find books with low ratings'\\n" +
                            "• 'Show me underutilized books'";
                } else {
                    return "✨ Absolutely! I'd be happy to help you discover great books. What are you interested in?\\n\\n"
                            +
                            "� **Reader Services:**\\n" +
                            "• 'Do you have books by J.K. Rowling?'\\n" +
                            "• 'Find me Harry Potter'\\n" +
                            "• 'Show me books by Tolkien'\\n" +
                            "• 'I'm looking for The Hunger Games'\\n" +
                            "• 'Recommend something exciting'\\n" +
                            "• 'What's a good sci-fi book?'";
                }
            }
        }

        // Handle "Do you have..."
        if (message.contains("do you have")) {
            String item = extractAfterPhrase(message, "do you have");
            if (!item.isEmpty()) {
                String recognizedItem = findAuthorOrTitleInText(item);
                if (!recognizedItem.isEmpty()) {
                    return "📚 Yes! Let me show you what we have by/from " + recognizedItem + ":\\n\\n" +
                            handleEnhancedSearch("find " + item);
                }
                return "📚 Let me check if we have " + item + " in our collection...\\n\\n" +
                        handleEnhancedSearch("find " + item);
            }
        }

        // Handle author-specific questions
        if (message.contains("books by") || message.contains("written by") || message.contains("anything by")) {
            return "📚 I'd be happy to help you find books by that author! Let me search our collection...\\n\\n" +
                    handleEnhancedSearch(message);
        }

        return handleNaturalLanguageQuery(message);
    }

    private String findAuthorOrTitleInText(String text) {
        // Check if the text contains any known authors or titles
        String authorFound = findAuthorInMessage(text);
        if (!authorFound.isEmpty()) {
            return authorFound;
        }

        String titleFound = findBookTitleInMessage(text);
        if (!titleFound.isEmpty()) {
            return "\"" + titleFound + "\"";
        }

        return "";
    }

    private String handleNaturalLanguageQuery(String message) {
        // Handle specific book-related queries
        if (message.contains("how") && message.contains("rate")) {
            return "⭐ To rate a book:\\n1. Find the book in your list\\n2. Select a rating from 1-5 stars\\n3. Click update!\\n\\nRatings help me give better recommendations!";
        }

        if (message.contains("genre") || message.contains("category")) {
            return "🎭 Available genres: Fiction, Non-Fiction, Mystery, Romance, Science Fiction, Fantasy, Biography, History, Self-Help, Business, and Other.\\n\\nTell me which genre interests you!";
        }

        if (message.contains("thank")) {
            return "You're absolutely welcome! Happy reading! 📚✨ Feel free to ask me anything else about books!";
        }

        // Handle reading preferences and moods
        if (message.contains("something") && (message.contains("light") || message.contains("easy"))) {
            return "😌 Looking for something light and easy? Here are some great options:\\n\\n" +
                    handleEnhancedRecommendation("recommend light reading");
        }

        if (message.contains("something") && (message.contains("exciting") || message.contains("thrilling"))) {
            return "🎯 Want something exciting? Let me find some thrilling reads for you:\\n\\n" +
                    handleEnhancedRecommendation("recommend thrilling books");
        }

        // Default friendly response with natural conversation
        String[] responses = {
                "🤔 I'd love to help! Could you tell me more about what you're looking for? I'm great with book recommendations, searches, and library questions!",
                "💭 Hmm, let me think... Are you looking for a specific book, want a recommendation, or need help with something else? Just ask naturally!",
                "🔍 I understand casual conversation! Try asking me things like 'I'm bored, suggest something' or 'Do you have any good mysteries?' - I'm here to help!",
                "📚 I'm not quite sure what you mean, but I love helping with books! Feel free to ask me anything in your own words - no need for formal commands!"
        };

        return responses[random.nextInt(responses.length)];
    }

    private String extractAfterPhrase(String message, String phrase) {
        int index = message.toLowerCase().indexOf(phrase.toLowerCase());
        if (index != -1 && index + phrase.length() < message.length()) {
            return message.substring(index + phrase.length()).trim();
        }
        return "";
    }

    private String handleGeneral(String message) {
        return handleNaturalLanguageQuery(message);
    }

    private String extractSearchTerm(String message) {
        // Enhanced natural language search term extraction with author/title
        // recognition
        String[] commonWords = { "find", "search", "show", "me", "books", "by", "the", "a", "an", "for",
                "library", "statistics", "stats", "analytics", "how", "many", "total",
                "do", "you", "have", "any", "got", "looking", "i'm", "im", "i", "am",
                "can", "could", "would", "please", "want", "need", "like", "love",
                "book", "novel", "story", "read", "reading", "written" };

        // Handle specific author queries
        if (message.contains("books by ")) {
            return extractAuthorFromPhrase(message, "books by ");
        }
        if (message.contains("anything by ")) {
            return extractAuthorFromPhrase(message, "anything by ");
        }
        if (message.contains("written by ")) {
            return extractAuthorFromPhrase(message, "written by ");
        }
        if (message.contains("author ")) {
            return extractAuthorFromPhrase(message, "author ");
        }

        // Handle specific book title queries
        String bookTitle = findBookTitleInMessage(message);
        if (!bookTitle.isEmpty()) {
            return bookTitle;
        }

        // Handle author name recognition
        String authorName = findAuthorInMessage(message);
        if (!authorName.isEmpty()) {
            return authorName;
        }

        // Handle specific phrases
        if (message.contains("looking for ")) {
            return message.substring(message.indexOf("looking for ") + 12).trim();
        }
        if (message.contains("do you have ")) {
            String term = message.substring(message.indexOf("do you have ") + 12).trim();
            // Clean up common endings
            term = term.replaceAll("\\?+$", "").trim();
            return term;
        }

        String[] words = message.toLowerCase().split("\\s+");

        List<String> keywords = Arrays.stream(words)
                .filter(word -> !Arrays.asList(commonWords).contains(word.toLowerCase()) &&
                        word.length() > 2 &&
                        !word.matches("\\d+")) // Exclude numbers
                .collect(Collectors.toList());

        return String.join(" ", keywords);
    }

    private String extractAuthorFromPhrase(String message, String phrase) {
        int index = message.toLowerCase().indexOf(phrase.toLowerCase());
        if (index != -1 && index + phrase.length() < message.length()) {
            String authorPart = message.substring(index + phrase.length()).trim();
            // Clean up punctuation
            authorPart = authorPart.replaceAll("[\\?!]+$", "").trim();
            return authorPart;
        }
        return "";
    }

    private String findBookTitleInMessage(String message) {
        String lowerMessage = message.toLowerCase();

        // Check for exact book title matches (case insensitive)
        for (Book book : books) {
            String bookTitle = book.getTitle().toLowerCase();

            // Exact match
            if (lowerMessage.contains(bookTitle)) {
                return book.getTitle();
            }

            // Partial matches for long titles
            String[] titleWords = bookTitle.split("\\s+");
            if (titleWords.length > 2) {
                // Check if message contains most significant words from title
                String[] significantWords = Arrays.stream(titleWords)
                        .filter(word -> !Arrays.asList("the", "a", "an", "and", "of", "to", "in", "on", "at", "by")
                                .contains(word))
                        .toArray(String[]::new);

                if (significantWords.length >= 2) {
                    boolean foundMajorWords = Arrays.stream(significantWords)
                            .limit(2)
                            .allMatch(word -> lowerMessage.contains(word));

                    if (foundMajorWords) {
                        return book.getTitle();
                    }
                }
            }
        }

        return "";
    }

    private String findAuthorInMessage(String message) {
        String lowerMessage = message.toLowerCase();

        // Check for author name matches
        for (Book book : books) {
            String authorName = book.getAuthor().toLowerCase();

            // Full name match
            if (lowerMessage.contains(authorName)) {
                return book.getAuthor();
            }

            // Last name match
            String[] nameParts = authorName.split("\\s+");
            if (nameParts.length > 1) {
                String lastName = nameParts[nameParts.length - 1];
                if (lastName.length() > 3 && lowerMessage.contains(lastName)) {
                    return book.getAuthor();
                }
            }

            // Handle common name variations
            if (authorName.contains("j.r.r. tolkien") &&
                    (lowerMessage.contains("tolkien") || lowerMessage.contains("j.r.r"))) {
                return book.getAuthor();
            }
            if (authorName.contains("j.k. rowling") &&
                    (lowerMessage.contains("rowling") || lowerMessage.contains("j.k") || lowerMessage.contains("jk"))) {
                return book.getAuthor();
            }
            if (authorName.contains("j.d. salinger") &&
                    (lowerMessage.contains("salinger") || lowerMessage.contains("j.d"))) {
                return book.getAuthor();
            }
        }

        return "";
    }

    private String extractGenre(String message) {
        String[] genres = { "fiction", "non-fiction", "mystery", "romance", "science fiction",
                "sci-fi", "fantasy", "biography", "history", "self-help", "business" };

        for (String genre : genres) {
            if (message.contains(genre)) {
                return genre;
            }
        }
        return "";
    }

    private String formatBookList(List<Book> bookList) {
        if (bookList.isEmpty()) {
            return "No books found.";
        }

        StringBuilder result = new StringBuilder();
        int count = 0;
        for (Book book : bookList) {
            if (count >= 5) { // Limit to 5 books to avoid long responses
                result.append("... and ").append(bookList.size() - 5).append(" more books.\\n");
                break;
            }

            result.append("📖 **").append(book.getTitle()).append("**\\n");
            result.append("   by ").append(book.getAuthor());

            if (book.getYear() > 0) {
                result.append(" (").append(book.getYear()).append(")");
            }

            if (book.getGenre() != null && !book.getGenre().isEmpty()) {
                result.append(" - ").append(book.getGenre());
            }

            if (book.getRating() > 0) {
                result.append(" ⭐").append(book.getRating()).append("/5");
            }

            result.append("\\n\\n");
            count++;
        }

        return result.toString();
    }

    public List<String> getConversationHistory() {
        return new ArrayList<>(conversationHistory);
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    /**
     * Set OpenAI API key and enable AI responses
     */
    public void setOpenAIKey(String apiKey) {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                this.openAI = new OpenAIWrapper(apiKey.trim());
                this.useAI = true;
                System.out.println("🤖 AI Enabled: OpenAI API key configured successfully!");
                System.out.println("🧠 Advanced AI responses now available for library assistance.");
            } catch (Exception e) {
                System.err.println("❌ Failed to initialize OpenAI: " + e.getMessage());
                this.useAI = false;
                this.openAI = null;
            }
        } else {
            this.useAI = false;
            this.openAI = null;
            System.out.println("⚙️ AI features disabled - using standard mode responses.");
        }
    }

    /**
     * Check if AI is enabled
     */
    public boolean isAIEnabled() {
        return useAI && openAI != null;
    }

    /**
     * Get AI status message
     */
    public String getAIStatus() {
        if (isAIEnabled()) {
            return "🤖 AI-Powered Mode: Advanced responses using OpenAI GPT-4";
        } else {
            return "🔧 Fallback Mode: Rule-based responses (Set OpenAI API key to enable AI)";
        }
    }
}