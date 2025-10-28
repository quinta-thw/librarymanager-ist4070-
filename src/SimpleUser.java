/**
 * Simple User class for authentication with enhanced security
 */
public class SimpleUser {
    public enum Role {
        LIBRARIAN, USER
    }
    
    private String username;
    private String password;
    private Role role;
    private String fullName;
    private String salt; // Salt for password hashing
    
    public SimpleUser(String username, String password, Role role, String fullName) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.fullName = fullName;
        this.salt = null; // Will be generated when needed
    }
    
    // Getters
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public Role getRole() { return role; }
    public String getFullName() { return fullName; }
    public String getSalt() { return salt; }
    
    // Setters for security updates
    public void setPassword(String password) { this.password = password; }
    public void setSalt(String salt) { this.salt = salt; }
    
    public boolean isLibrarian() { return role == Role.LIBRARIAN; }
    public boolean isUser() { return role == Role.USER; }
}