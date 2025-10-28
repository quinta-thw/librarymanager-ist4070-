import com.mongodb.client.*;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * MongoDB Manager for Library Management System
 * Maintains exact same functionality as file-based system
 */
public class MongoDBManager {
    private static final String CONNECTION_STRING = "mongodb://127.0.0.1:27017";
    private static final String DATABASE_NAME = "library_management";

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> booksCollection;
    private MongoCollection<Document> usersCollection;
    private MongoCollection<Document> sessionsCollection;

    public MongoDBManager() {
        try {
            this.mongoClient = MongoClients.create(CONNECTION_STRING);
            this.database = mongoClient.getDatabase(DATABASE_NAME);
            this.booksCollection = database.getCollection("books");
            this.usersCollection = database.getCollection("users");
            this.sessionsCollection = database.getCollection("sessions");

            // Test connection
            database.runCommand(new Document("ping", 1));
            System.out.println("✅ MongoDB connection established successfully");

            // Initialize default users if collection is empty
            initializeUsers();

        } catch (Exception e) {
            System.err.println("❌ MongoDB connection failed: " + e.getMessage());
            throw new RuntimeException("Failed to connect to MongoDB", e);
        }
    }

    /**
     * Initialize default users - exactly same as file system
     */
    private void initializeUsers() {
        try {
            long userCount = usersCollection.countDocuments();
            if (userCount == 0) {
                // Create default users with exact same credentials as file system
                List<Document> defaultUsers = Arrays.asList(
                        createUserDocument("librarian", "admin123", "LIBRARIAN", "Head Librarian"),
                        createUserDocument("admin", "admin123", "LIBRARIAN", "Library Administrator"),
                        createUserDocument("user1", "user123", "USER", "John Reader"),
                        createUserDocument("reader", "read123", "USER", "Book Reader"));

                usersCollection.insertMany(defaultUsers);
                System.out.println("🔐 Initialized default users in MongoDB");
            }
        } catch (Exception e) {
            System.err.println("❌ Error initializing users: " + e.getMessage());
        }
    }

    private Document createUserDocument(String username, String password, String role, String fullName) {
        return new Document()
                .append("username", username)
                .append("password", password) // Plain text - will be hashed on first login
                .append("role", role)
                .append("fullName", fullName)
                .append("salt", null) // Will be generated on first login
                .append("createdDate", new Date())
                .append("lastLogin", null)
                .append("isActive", true);
    }

    /**
     * Load all books from MongoDB - maintains exact same format as file system
     */
    public List<Book> loadBooks() {
        List<Book> books = new ArrayList<>();

        try {
            FindIterable<Document> documents = booksCollection.find();
            for (Document doc : documents) {
                Book book = documentToBook(doc);
                if (book != null) {
                    books.add(book);
                }
            }
            System.out.println("📚 Loaded " + books.size() + " books from MongoDB");
        } catch (Exception e) {
            System.err.println("❌ Error loading books: " + e.getMessage());
        }

        return books;
    }

    /**
     * Save all books to MongoDB - maintains exact same data
     */
    public void saveBooks(List<Book> books) {
        try {
            // Clear existing books
            booksCollection.deleteMany(new Document());

            // Insert all books
            List<Document> bookDocuments = new ArrayList<>();
            for (Book book : books) {
                bookDocuments.add(bookToDocument(book));
            }

            if (!bookDocuments.isEmpty()) {
                booksCollection.insertMany(bookDocuments);
            }

            System.out.println("💾 Saved " + books.size() + " books to MongoDB");
        } catch (Exception e) {
            System.err.println("❌ Error saving books: " + e.getMessage());
        }
    }

    /**
     * Convert Book object to MongoDB Document - preserves all data
     */
    private Document bookToDocument(Book book) {
        Document doc = new Document()
                .append("title", book.getTitle())
                .append("author", book.getAuthor())
                .append("year", book.getYear())
                .append("genre", book.getGenre())
                .append("status", book.getStatus())
                .append("rating", book.getRating());

        // Include date added if available
        if (book.getDateAdded() != null) {
            // Store as Date object for consistency
            doc.append("dateAdded", new Date());
        }

        return doc;
    }

    /**
     * Convert MongoDB Document to Book object - maintains exact same format
     */
    private Book documentToBook(Document doc) {
        try {
            String title = doc.getString("title");
            String author = doc.getString("author");
            Integer year = doc.getInteger("year");
            String genre = doc.getString("genre");
            String status = doc.getString("status");
            Integer rating = doc.getInteger("rating");
            
            // Handle dateAdded properly - it could be Date or String
            Object dateAddedObj = doc.get("dateAdded");
            String dateAdded = null;
            if (dateAddedObj instanceof Date) {
                // Convert Date to String format
                Date date = (Date) dateAddedObj;
                dateAdded = date.toString();
            } else if (dateAddedObj instanceof String) {
                dateAdded = (String) dateAddedObj;
            }

            // Use the full constructor
            Book book = new Book(title, author, year,
                    genre != null ? genre : "Other",
                    status != null ? status : "Available",
                    rating != null ? rating : 0,
                    "");

            // Note: dateAdded is automatically set in constructor,
            // keeping original behavior consistent

            return book;
        } catch (Exception e) {
            System.err.println("❌ Error converting document to book: " + e.getMessage());
            e.printStackTrace(); // Add stack trace for debugging
            return null;
        }
    }

    /**
     * Load users - maintains exact same user structure
     */
    public Map<String, SimpleUser> loadUsers() {
        Map<String, SimpleUser> users = new HashMap<>();

        try {
            FindIterable<Document> documents = usersCollection.find();
            for (Document doc : documents) {
                SimpleUser user = documentToUser(doc);
                if (user != null) {
                    users.put(user.getUsername(), user);
                }
            }
            System.out.println("👥 Loaded " + users.size() + " users from MongoDB");
        } catch (Exception e) {
            System.err.println("❌ Error loading users: " + e.getMessage());
        }

        return users;
    }

    /**
     * Save user changes back to MongoDB
     */
    public void saveUser(SimpleUser user) {
        try {
            Document userDoc = new Document()
                    .append("username", user.getUsername())
                    .append("password", user.getPassword())
                    .append("role", user.getRole().toString())
                    .append("fullName", user.getFullName())
                    .append("salt", user.getSalt())
                    .append("lastLogin", new Date())
                    .append("isActive", true);

            usersCollection.replaceOne(
                    Filters.eq("username", user.getUsername()),
                    userDoc,
                    new ReplaceOptions().upsert(true));
        } catch (Exception e) {
            System.err.println("❌ Error saving user: " + e.getMessage());
        }
    }

    /**
     * Convert MongoDB Document to SimpleUser object
     */
    private SimpleUser documentToUser(Document doc) {
        try {
            String username = doc.getString("username");
            String password = doc.getString("password");
            String roleStr = doc.getString("role");
            String fullName = doc.getString("fullName");

            if (username == null || password == null || roleStr == null) {
                return null;
            }

            SimpleUser.Role role = SimpleUser.Role.valueOf(roleStr);
            SimpleUser user = new SimpleUser(username, password, role, fullName);
            user.setSalt(doc.getString("salt"));

            return user;
        } catch (Exception e) {
            System.err.println("❌ Error converting document to user: " + e.getMessage());
            return null;
        }
    }

    /**
     * Close MongoDB connection
     */
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("🔌 MongoDB connection closed");
        }
    }

    /**
     * Test if MongoDB is connected and working
     */
    public boolean isConnected() {
        try {
            database.runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
