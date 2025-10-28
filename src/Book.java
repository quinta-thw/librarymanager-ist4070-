import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Book {
    private String title;
    private String author;
    private int year;
    private String genre;
    private String status; // "want-to-read", "currently-reading", "read"
    private int rating; // 1-5 stars, 0 for unrated
    private LocalDate dateAdded;
    private String notes;

    // Constructors
    public Book(String title, String author, int year) {
        this(title, author, year, "Other", "want-to-read", 0, "");
    }

    public Book(String title, String author, int year, String genre, String status, int rating, String notes) {
        this.title = title;
        this.author = author;
        this.year = year;
        this.genre = genre;
        this.status = status;
        this.rating = Math.max(0, Math.min(5, rating)); // Ensure rating is between 0-5
        this.notes = notes;
        this.dateAdded = LocalDate.now();
    }

    // Getters
    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public int getYear() {
        return year;
    }

    public String getGenre() {
        return genre;
    }

    public String getStatus() {
        return status;
    }

    public int getRating() {
        return rating;
    }

    public LocalDate getDateAdded() {
        return dateAdded;
    }

    public String getNotes() {
        return notes;
    }

    // Setters
    public void setStatus(String status) {
        this.status = status;
    }

    public void setRating(int rating) {
        this.rating = Math.max(0, Math.min(5, rating));
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    // Utility methods
    public String getRatingStars() {
        if (rating == 0) return "Not Rated";
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < rating; i++) {
            stars.append("*");
        }
        for (int i = rating; i < 5; i++) {
            stars.append("-");
        }
        return stars.toString() + " (" + rating + "/5)";
    }

    // FIXED: Traditional switch statement instead of switch expression
    public String getFormattedStatus() {
        switch (status) {
            case "want-to-read":
                return "Want to Read";
            case "currently-reading":
                return "Currently Reading";
            case "read":
                return "Read";
            default:
                return status;
        }
    }

    // File format methods for persistence
    public String toFileString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return String.join("|", 
            title, author, String.valueOf(year), genre, status, 
            String.valueOf(rating), dateAdded.format(formatter), notes.replace("|", "\\|"));
    }

    public static Book fromFileString(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length >= 7) {
            Book book = new Book(parts[0], parts[1], Integer.parseInt(parts[2]), 
                               parts[3], parts[4], Integer.parseInt(parts[5]), 
                               parts.length > 7 ? parts[7].replace("\\|", "|") : "");
            try {
                book.dateAdded = LocalDate.parse(parts[6]);
            } catch (Exception e) {
                book.dateAdded = LocalDate.now();
            }
            return book;
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("+-- ").append(title).append(" --+\n");
        sb.append("| Author: ").append(author).append("\n");
        sb.append("| Year: ").append(year).append("\n");
        sb.append("| Genre: ").append(genre).append("\n");
        sb.append("| Status: ").append(getFormattedStatus()).append("\n");
        sb.append("| Rating: ").append(getRatingStars()).append("\n");
        sb.append("| Added: ").append(dateAdded.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))).append("\n");
        if (!notes.isEmpty()) {
            sb.append("| Notes: ").append(notes).append("\n");
        }
        
        // FIXED: Replace String.repeat() with manual string building
        int dashCount = Math.max(30, title.length() + 8);
        StringBuilder dashes = new StringBuilder(dashCount);
        for (int i = 0; i < dashCount; i++) {
            dashes.append("-");
        }
        sb.append("+").append(dashes).append("+");
        
        return sb.toString();
    }

    public String toShortString() {
        return String.format("%-30s | %-20s | %4d | %-15s | %s", 
            title.length() > 30 ? title.substring(0, 27) + "..." : title,
            author.length() > 20 ? author.substring(0, 17) + "..." : author,
            year, getFormattedStatus(), getRatingStars());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Book book = (Book) obj;
        return title.equalsIgnoreCase(book.title) && author.equalsIgnoreCase(book.author);
    }

    @Override
    public int hashCode() {
        return (title.toLowerCase() + author.toLowerCase()).hashCode();
    }
}
