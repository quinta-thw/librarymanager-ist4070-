# 🔌 Library Management System - API Documentation

## 📡 API Overview

The Library Management System provides a comprehensive RESTful API for all library operations. This documentation covers all available endpoints, request/response formats, authentication requirements, and usage examples.

**Base URL**: `http://localhost:8080`  
**API Version**: 1.0  
**Content-Type**: `application/json` or `application/x-www-form-urlencoded`

---

## 🔐 Authentication

### **Session-Based Authentication**

The API uses session-based authentication with secure HTTP cookies.

#### **Login Process**

1. **POST** `/login` with credentials
2. **Receive session cookie** in response
3. **Include cookie** in subsequent requests
4. **Session expires** after 1 hour of inactivity

#### **Session Management**

```http
POST /login
Content-Type: application/x-www-form-urlencoded

username=your_username&password=your_password
```

**Response (Success)**:

```http
HTTP/1.1 302 Found
Set-Cookie: LIBRARY_SESSION=abc123xyz; Path=/; HttpOnly; Secure
Location: /
```

**Response (Failure)**:

```http
HTTP/1.1 401 Unauthorized
Content-Type: text/html

<html>Login failed. Please check your credentials.</html>
```

#### **Logout**

```http
POST /logout
```

**Response**:

```http
HTTP/1.1 302 Found
Set-Cookie: LIBRARY_SESSION=; Path=/; HttpOnly; Secure; Max-Age=0
Location: /login
```

---

## 📚 Book Management Endpoints

### **Get All Books**

Retrieve the complete library collection.

```http
GET /books
```

**Authentication**: Required  
**Role**: Any authenticated user

**Response**:

```json
{
  "success": true,
  "books": [
    {
      "title": "The Hobbit",
      "author": "J.R.R. Tolkien",
      "year": 1937,
      "genre": "Fantasy",
      "status": "Available",
      "rating": 5,
      "dateAdded": "2025-10-14"
    },
    {
      "title": "1984",
      "author": "George Orwell",
      "year": 1949,
      "genre": "Dystopian Fiction",
      "status": "Available",
      "rating": 5,
      "dateAdded": "2025-10-14"
    }
  ],
  "total": 31,
  "available": 30,
  "checkedOut": 1
}
```

### **Add New Book**

Add a new book to the library collection.

```http
POST /books/add
Content-Type: application/json
```

**Authentication**: Required  
**Role**: Librarian/Admin

**Request Body**:

```json
{
  "title": "The Midnight Library",
  "author": "Matt Haig",
  "year": "2020",
  "genre": "Philosophical Fiction",
  "status": "Available",
  "rating": "4"
}
```

**Response (Success)**:

```json
{
  "success": true,
  "message": "Book added successfully",
  "book": {
    "title": "The Midnight Library",
    "author": "Matt Haig",
    "year": 2020,
    "genre": "Philosophical Fiction",
    "status": "Available",
    "rating": 4,
    "dateAdded": "2025-10-14"
  }
}
```

**Response (Error)**:

```json
{
  "success": false,
  "error": "Invalid book data",
  "details": "Title and author are required fields"
}
```

### **Update Book**

Update existing book information.

```http
POST /books/update
Content-Type: application/json
```

**Authentication**: Required  
**Role**: Librarian/Admin

**Request Body**:

```json
{
  "originalTitle": "The Hobbit",
  "title": "The Hobbit: An Unexpected Journey",
  "author": "J.R.R. Tolkien",
  "year": "1937",
  "genre": "Fantasy",
  "status": "Available",
  "rating": "5"
}
```

**Response**:

```json
{
  "success": true,
  "message": "Book updated successfully",
  "updatedBook": {
    "title": "The Hobbit: An Unexpected Journey",
    "author": "J.R.R. Tolkien",
    "year": 1937,
    "genre": "Fantasy",
    "status": "Available",
    "rating": 5,
    "dateAdded": "2025-10-14"
  }
}
```

### **Remove Book**

Remove a book from the library collection.

```http
POST /books/remove
Content-Type: application/json
```

**Authentication**: Required  
**Role**: Librarian/Admin

**Request Body**:

```json
{
  "title": "Book Title to Remove"
}
```

**Response**:

```json
{
  "success": true,
  "message": "Book removed successfully"
}
```

### **Get Book Details**

Get detailed information about a specific book.

```http
GET /books/details?title=The+Hobbit
```

**Authentication**: Required  
**Role**: Any authenticated user

**Response**:

```json
{
  "success": true,
  "book": {
    "title": "The Hobbit",
    "author": "J.R.R. Tolkien",
    "year": 1937,
    "genre": "Fantasy",
    "status": "Available",
    "rating": 5,
    "dateAdded": "2025-10-14",
    "description": "A classic fantasy adventure...",
    "isbn": "978-0547928227",
    "pages": 366
  }
}
```

### **Browse Books**

Browse books with filtering and pagination.

```http
GET /books/browse?genre=Fantasy&status=Available&page=1&limit=10
```

**Authentication**: Required  
**Role**: Any authenticated user

**Query Parameters**:

- `genre` (optional): Filter by genre
- `author` (optional): Filter by author
- `status` (optional): Filter by status
- `rating` (optional): Filter by minimum rating
- `page` (optional): Page number (default: 1)
- `limit` (optional): Items per page (default: 20)

**Response**:

```json
{
  "success": true,
  "books": [...],
  "pagination": {
    "currentPage": 1,
    "totalPages": 3,
    "totalItems": 45,
    "itemsPerPage": 20
  },
  "filters": {
    "genre": "Fantasy",
    "status": "Available"
  }
}
```

### **Search Books**

Search books using text queries.

```http
GET /books/search?q=tolkien&type=author
```

**Authentication**: Required  
**Role**: Any authenticated user

**Query Parameters**:

- `q` (required): Search query
- `type` (optional): Search type (`title`, `author`, `genre`, `all`)

**Response**:

```json
{
  "success": true,
  "query": "tolkien",
  "searchType": "author",
  "results": [
    {
      "title": "The Hobbit",
      "author": "J.R.R. Tolkien",
      "year": 1937,
      "genre": "Fantasy",
      "status": "Available",
      "rating": 5,
      "relevanceScore": 1.0
    },
    {
      "title": "The Lord of the Rings",
      "author": "J.R.R. Tolkien",
      "year": 1954,
      "genre": "Fantasy",
      "status": "Available",
      "rating": 5,
      "relevanceScore": 1.0
    }
  ],
  "totalResults": 2
}
```

---

## 🤖 AI Chatbot Endpoints

### **Send Chat Message**

Send a message to the AI chatbot and receive a response.

```http
POST /chat
Content-Type: application/json
```

**Authentication**: Required  
**Role**: Any authenticated user

**Request Body**:

```json
{
  "message": "Do you have books by Tolkien?"
}
```

**Response**:

```json
{
  "success": true,
  "message": "📚 **Found 2 books by J.R.R. Tolkien:**\n\n• **The Hobbit** (1937, Fantasy) - Available ⭐⭐⭐⭐⭐\n• **The Lord of the Rings** (1954, Fantasy) - Available ⭐⭐⭐⭐⭐\n\nBoth are highly-rated fantasy classics! Would you like more information about either book?",
  "timestamp": "2025-10-14T10:30:00Z",
  "conversationId": "conv_abc123",
  "aiMode": "enhanced_local"
}
```

### **Get AI Status**

Check the current AI configuration and capabilities.

```http
GET /ai/status
```

**Authentication**: Required  
**Role**: Any authenticated user

**Response**:

```json
{
  "success": true,
  "aiEnabled": true,
  "mode": "enhanced_local",
  "capabilities": [
    "book_search",
    "recommendations",
    "information_queries",
    "natural_language_processing"
  ],
  "features": {
    "contextAware": true,
    "roleBasedResponses": true,
    "conversationHistory": true,
    "libraryIntegration": true
  }
}
```

### **Set AI Configuration** _(Librarian Only)_

Configure AI settings and API keys.

```http
POST /ai/set-key
Content-Type: application/json
```

**Authentication**: Required  
**Role**: Librarian/Admin

**Request Body**:

```json
{
  "apiKey": "sk-your-openai-api-key-here",
  "model": "gpt-4o-mini",
  "mode": "ai_powered"
}
```

**Response**:

```json
{
  "success": true,
  "message": "AI configuration updated successfully",
  "mode": "ai_powered",
  "model": "gpt-4o-mini"
}
```

### **Clear AI Configuration** _(Librarian Only)_

Reset AI to local mode and clear API keys.

```http
POST /ai/clear-key
```

**Authentication**: Required  
**Role**: Librarian/Admin

**Response**:

```json
{
  "success": true,
  "message": "AI configuration cleared. Switched to Enhanced Local Mode.",
  "mode": "enhanced_local"
}
```

---

## 👤 User Management Endpoints

### **Get Current User Info**

Get information about the currently logged-in user.

```http
GET /user/profile
```

**Authentication**: Required  
**Role**: Any authenticated user

**Response**:

```json
{
  "success": true,
  "user": {
    "username": "john_doe",
    "fullName": "John Doe",
    "email": "john@example.com",
    "role": "USER",
    "isLibrarian": false,
    "lastLogin": "2025-10-14T09:15:00Z",
    "memberSince": "2024-01-15",
    "preferences": {
      "favoriteGenres": ["Fantasy", "Science Fiction"],
      "notificationsEnabled": true
    }
  }
}
```

### **Update Reading Status**

Update your reading status for a specific book.

```http
POST /reading/update
Content-Type: application/json
```

**Authentication**: Required  
**Role**: Any authenticated user

**Request Body**:

```json
{
  "title": "The Hobbit",
  "status": "currently-reading",
  "rating": 5,
  "notes": "Absolutely loving this adventure!"
}
```

**Status Values**:

- `want-to-read`
- `currently-reading`
- `read`
- `available` (remove from personal lists)

**Response**:

```json
{
  "success": true,
  "message": "Reading status updated successfully",
  "book": {
    "title": "The Hobbit",
    "status": "currently-reading",
    "rating": 5,
    "personalNotes": "Absolutely loving this adventure!",
    "dateUpdated": "2025-10-14T10:45:00Z"
  }
}
```

---

## 📊 Statistics & Analytics Endpoints

### **Get Library Statistics**

Get comprehensive library statistics and analytics.

```http
GET /stats
```

**Authentication**: Required  
**Role**: Any authenticated user (detailed stats require Librarian role)

**Response (User)**:

```json
{
  "success": true,
  "userStats": {
    "totalBooks": 31,
    "availableBooks": 30,
    "booksRead": 5,
    "currentlyReading": 2,
    "wantToRead": 8,
    "averageRating": 4.2,
    "favoriteGenre": "Fantasy"
  },
  "recommendations": [
    {
      "title": "Project Hail Mary",
      "reason": "Highly rated Science Fiction"
    }
  ]
}
```

**Response (Librarian)**:

```json
{
  "success": true,
  "libraryStats": {
    "totalBooks": 31,
    "totalUsers": 15,
    "activeUsers": 12,
    "booksAdded": {
      "thisMonth": 5,
      "thisYear": 31
    },
    "popularGenres": [
      { "genre": "Fantasy", "count": 8 },
      { "genre": "Fiction", "count": 6 },
      { "genre": "Science Fiction", "count": 4 }
    ],
    "topRatedBooks": [
      { "title": "The Hobbit", "rating": 5.0 },
      { "title": "1984", "rating": 5.0 }
    ],
    "userActivity": {
      "dailyActiveUsers": 8,
      "weeklyActiveUsers": 12,
      "monthlyActiveUsers": 15
    }
  },
  "insights": [
    "Fantasy is your most popular genre",
    "Consider adding more Science Fiction titles",
    "User engagement is up 15% this month"
  ]
}
```

### **Get Reading Analytics**

Get detailed reading analytics for users.

```http
GET /stats/reading
```

**Authentication**: Required  
**Role**: Any authenticated user

**Response**:

```json
{
  "success": true,
  "readingStats": {
    "booksRead": {
      "total": 12,
      "thisYear": 8,
      "thisMonth": 2
    },
    "readingGoal": {
      "yearly": 20,
      "progress": 8,
      "percentage": 40
    },
    "genreBreakdown": [
      { "genre": "Fantasy", "count": 4 },
      { "genre": "Fiction", "count": 3 },
      { "genre": "Non-Fiction", "count": 1 }
    ],
    "averageRating": 4.3,
    "readingStreak": {
      "current": 15,
      "longest": 32,
      "unit": "days"
    }
  }
}
```

---

## 🏠 Dashboard & Navigation Endpoints

### **Get Dashboard Data**

Get personalized dashboard information.

```http
GET /
GET /dashboard
```

**Authentication**: Required  
**Role**: Any authenticated user

**Response**:

```json
{
  "success": true,
  "dashboard": {
    "welcome": "Welcome back, John!",
    "quickStats": {
      "booksRead": 5,
      "currentlyReading": 2,
      "wantToRead": 8
    },
    "recentActivity": [
      {
        "type": "book_added",
        "title": "The Midnight Library",
        "timestamp": "2025-10-14T09:30:00Z"
      },
      {
        "type": "book_rated",
        "title": "Dune",
        "rating": 5,
        "timestamp": "2025-10-13T14:20:00Z"
      }
    ],
    "recommendations": [
      {
        "title": "Klara and the Sun",
        "author": "Kazuo Ishiguro",
        "reason": "Based on your interest in philosophical fiction"
      }
    ],
    "notifications": [
      {
        "type": "info",
        "message": "2 new books added to the Fantasy section",
        "timestamp": "2025-10-14T08:00:00Z"
      }
    ]
  }
}
```

---

## 🔍 Error Handling

### **Standard Error Response Format**

All API endpoints return errors in a consistent format:

```json
{
  "success": false,
  "error": "Error type",
  "message": "Human-readable error description",
  "code": "ERROR_CODE",
  "timestamp": "2025-10-14T10:30:00Z",
  "details": {
    "field": "Additional error details"
  }
}
```

### **HTTP Status Codes**

| Code | Meaning               | Description                          |
| ---- | --------------------- | ------------------------------------ |
| 200  | OK                    | Request successful                   |
| 201  | Created               | Resource created successfully        |
| 400  | Bad Request           | Invalid request format or parameters |
| 401  | Unauthorized          | Authentication required or failed    |
| 403  | Forbidden             | Insufficient permissions             |
| 404  | Not Found             | Resource not found                   |
| 409  | Conflict              | Resource already exists              |
| 429  | Too Many Requests     | Rate limit exceeded                  |
| 500  | Internal Server Error | Server error                         |

### **Common Error Codes**

| Error Code                 | Description                             |
| -------------------------- | --------------------------------------- |
| `AUTH_REQUIRED`            | Authentication required                 |
| `INVALID_CREDENTIALS`      | Invalid username or password            |
| `INSUFFICIENT_PERMISSIONS` | User role lacks required permissions    |
| `BOOK_NOT_FOUND`           | Specified book does not exist           |
| `BOOK_ALREADY_EXISTS`      | Book with same title and author exists  |
| `INVALID_BOOK_DATA`        | Required book fields missing or invalid |
| `RATE_LIMIT_EXCEEDED`      | Too many requests in time window        |
| `AI_SERVICE_UNAVAILABLE`   | AI chatbot temporarily unavailable      |
| `INVALID_SEARCH_QUERY`     | Search query format is invalid          |

### **Error Examples**

**Authentication Error**:

```json
{
  "success": false,
  "error": "Authentication required",
  "message": "Please log in to access this resource",
  "code": "AUTH_REQUIRED",
  "timestamp": "2025-10-14T10:30:00Z"
}
```

**Validation Error**:

```json
{
  "success": false,
  "error": "Invalid book data",
  "message": "Title and author are required fields",
  "code": "INVALID_BOOK_DATA",
  "timestamp": "2025-10-14T10:30:00Z",
  "details": {
    "missingFields": ["title", "author"],
    "providedFields": ["year", "genre"]
  }
}
```

**Permission Error**:

```json
{
  "success": false,
  "error": "Insufficient permissions",
  "message": "Librarian role required for this operation",
  "code": "INSUFFICIENT_PERMISSIONS",
  "timestamp": "2025-10-14T10:30:00Z",
  "details": {
    "requiredRole": "LIBRARIAN",
    "currentRole": "USER"
  }
}
```

---

## 🚀 Rate Limiting

### **Rate Limit Policies**

| Endpoint Category | Limit        | Window   |
| ----------------- | ------------ | -------- |
| Authentication    | 5 requests   | 1 minute |
| Book Operations   | 60 requests  | 1 minute |
| Search Queries    | 100 requests | 1 minute |
| AI Chat           | 30 requests  | 1 minute |
| General API       | 200 requests | 1 minute |

### **Rate Limit Headers**

Response headers include rate limit information:

```http
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 45
X-RateLimit-Reset: 1697276400
X-RateLimit-Window: 60
```

### **Rate Limit Exceeded Response**

```json
{
  "success": false,
  "error": "Rate limit exceeded",
  "message": "Too many requests. Please try again in 30 seconds.",
  "code": "RATE_LIMIT_EXCEEDED",
  "timestamp": "2025-10-14T10:30:00Z",
  "details": {
    "limit": 60,
    "window": 60,
    "resetTime": "2025-10-14T10:31:00Z"
  }
}
```

---

## 📝 Data Models

### **Book Model**

```json
{
  "title": "string (required, max 200 chars)",
  "author": "string (required, max 100 chars)",
  "year": "integer (required, 1000-current year)",
  "genre": "string (required, max 50 chars)",
  "status": "enum: 'Available', 'Checked Out', 'Want to Read', 'Currently Reading', 'Read'",
  "rating": "integer (optional, 1-5)",
  "dateAdded": "ISO 8601 date string",
  "isbn": "string (optional, ISBN-10 or ISBN-13)",
  "pages": "integer (optional, positive number)",
  "description": "string (optional, max 1000 chars)",
  "personalNotes": "string (optional, max 500 chars, user-specific)"
}
```

### **User Model**

```json
{
  "username": "string (required, unique, 3-30 chars)",
  "fullName": "string (required, max 100 chars)",
  "email": "string (required, valid email format)",
  "role": "enum: 'ADMIN', 'LIBRARIAN', 'USER'",
  "isLibrarian": "boolean (computed from role)",
  "lastLogin": "ISO 8601 timestamp",
  "memberSince": "ISO 8601 date",
  "isActive": "boolean",
  "preferences": {
    "favoriteGenres": "array of strings",
    "notificationsEnabled": "boolean",
    "theme": "enum: 'light', 'dark', 'auto'"
  }
}
```

### **Chat Message Model**

```json
{
  "message": "string (required, max 1000 chars)",
  "timestamp": "ISO 8601 timestamp",
  "type": "enum: 'user', 'bot', 'system'",
  "conversationId": "string (session-based)",
  "metadata": {
    "aiMode": "enum: 'enhanced_local', 'ai_powered'",
    "responseTime": "integer (milliseconds)",
    "confidence": "number (0-1, for AI responses)"
  }
}
```

---

## 🔧 SDK Examples

### **JavaScript/Node.js**

```javascript
class LibraryAPI {
  constructor(baseURL = "http://localhost:8080") {
    this.baseURL = baseURL;
    this.sessionCookie = null;
  }

  async login(username, password) {
    const response = await fetch(`${this.baseURL}/login`, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: `username=${encodeURIComponent(
        username
      )}&password=${encodeURIComponent(password)}`,
      credentials: "include",
    });

    if (response.ok) {
      this.sessionCookie = response.headers.get("set-cookie");
      return true;
    }
    throw new Error("Login failed");
  }

  async getBooks() {
    const response = await fetch(`${this.baseURL}/books`, {
      credentials: "include",
    });
    return response.json();
  }

  async addBook(book) {
    const response = await fetch(`${this.baseURL}/books/add`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(book),
      credentials: "include",
    });
    return response.json();
  }

  async chatWithAI(message) {
    const response = await fetch(`${this.baseURL}/chat`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ message }),
      credentials: "include",
    });
    return response.json();
  }
}

// Usage example
const api = new LibraryAPI();
await api.login("admin", "library123");

const books = await api.getBooks();
console.log(`Found ${books.total} books`);

const aiResponse = await api.chatWithAI("Recommend me a fantasy book");
console.log(aiResponse.message);
```

### **Python**

```python
import requests
import json

class LibraryAPI:
    def __init__(self, base_url='http://localhost:8080'):
        self.base_url = base_url
        self.session = requests.Session()

    def login(self, username, password):
        data = {
            'username': username,
            'password': password
        }
        response = self.session.post(f'{self.base_url}/login', data=data)
        if response.status_code != 200:
            raise Exception('Login failed')
        return True

    def get_books(self):
        response = self.session.get(f'{self.base_url}/books')
        response.raise_for_status()
        return response.json()

    def add_book(self, book):
        response = self.session.post(
            f'{self.base_url}/books/add',
            json=book
        )
        response.raise_for_status()
        return response.json()

    def chat_with_ai(self, message):
        response = self.session.post(
            f'{self.base_url}/chat',
            json={'message': message}
        )
        response.raise_for_status()
        return response.json()

# Usage example
api = LibraryAPI()
api.login('admin', 'library123')

books = api.get_books()
print(f"Found {books['total']} books")

new_book = {
    'title': 'The Martian',
    'author': 'Andy Weir',
    'year': '2011',
    'genre': 'Science Fiction',
    'status': 'Available',
    'rating': '5'
}
result = api.add_book(new_book)
print(f"Added book: {result['book']['title']}")

ai_response = api.chat_with_ai('What science fiction books do you have?')
print(ai_response['message'])
```

---

## 🧪 Testing

### **API Testing with curl**

**Login**:

```bash
curl -X POST http://localhost:8080/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=library123" \
  -c cookies.txt \
  -v
```

**Get Books**:

```bash
curl -X GET http://localhost:8080/books \
  -b cookies.txt \
  -H "Accept: application/json"
```

**Add Book**:

```bash
curl -X POST http://localhost:8080/books/add \
  -b cookies.txt \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Test Book",
    "author": "Test Author",
    "year": "2023",
    "genre": "Fiction",
    "status": "Available",
    "rating": "4"
  }'
```

**Chat with AI**:

```bash
curl -X POST http://localhost:8080/chat \
  -b cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"message": "Do you have books by Tolkien?"}'
```

### **Postman Collection**

Import this collection into Postman for easy API testing:

```json
{
  "info": {
    "name": "Library Management API",
    "description": "Complete API collection for the Library Management System"
  },
  "variable": [
    {
      "key": "base_url",
      "value": "http://localhost:8080"
    }
  ],
  "item": [
    {
      "name": "Authentication",
      "item": [
        {
          "name": "Login",
          "request": {
            "method": "POST",
            "header": [
              {
                "key": "Content-Type",
                "value": "application/x-www-form-urlencoded"
              }
            ],
            "body": {
              "mode": "urlencoded",
              "urlencoded": [
                {
                  "key": "username",
                  "value": "admin"
                },
                {
                  "key": "password",
                  "value": "library123"
                }
              ]
            },
            "url": {
              "raw": "{{base_url}}/login",
              "host": ["{{base_url}}"],
              "path": ["login"]
            }
          }
        }
      ]
    }
  ]
}
```

---

This comprehensive API documentation provides all the information needed to interact with the Library Management System programmatically. Whether you're building a mobile app, integrating with other systems, or creating custom tools, this API reference will help you get started quickly and efficiently.

_For the latest API updates and additional examples, please refer to the system documentation or contact the development team._
