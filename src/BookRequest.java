import java.time.LocalDateTime;
import java.util.UUID;

public class BookRequest {
    private String id;
    private String title;
    private String author;
    private String genre;
    private String publicationYear;
    private String reason;
    private String requestedBy;
    private LocalDateTime requestDate;
    private String status; // "pending", "approved", "rejected"
    private String librarianFeedback;
    private double estimatedPrice;
    
    public BookRequest(String title, String author, String genre, String publicationYear, 
                      String reason, String requestedBy) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.author = author;
        this.genre = genre;
        this.publicationYear = publicationYear;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.requestDate = LocalDateTime.now();
        this.status = "pending";
        this.librarianFeedback = "";
        this.estimatedPrice = generateEstimatedPrice();
    }
    
    private double generateEstimatedPrice() {
        // Generate a realistic book price between $8-35
        return Math.round((8 + Math.random() * 27) * 100.0) / 100.0;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getGenre() { return genre; }
    public String getPublicationYear() { return publicationYear; }
    public String getReason() { return reason; }
    public String getRequestedBy() { return requestedBy; }
    public LocalDateTime getRequestDate() { return requestDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLibrarianFeedback() { return librarianFeedback; }
    public void setLibrarianFeedback(String feedback) { this.librarianFeedback = feedback; }
    public double getEstimatedPrice() { return estimatedPrice; }
    public void setEstimatedPrice(double price) { this.estimatedPrice = price; }
}