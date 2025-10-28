# Library Book Manager Web App

A modern, responsive web application for managing your personal book collection. Built with HTML, CSS, and JavaScript with persistent storage using localStorage.

## Features

### ✅ Core Functionality
- **Add Books**: Enter title, author, and publication year
- **View All Books**: Display all books in a beautiful card layout
- **Search Books**: Real-time search by title or author
- **Remove Books**: Delete books with confirmation dialog
- **Data Persistence**: Books are saved in browser localStorage

### 🎨 Modern Design
- Beautiful gradient background and glassmorphism effects
- Responsive design that works on desktop, tablet, and mobile
- Smooth animations and hover effects
- Toast notifications for user feedback
- Clean, intuitive user interface

### 🚀 Enhanced Features
- Real-time search filtering as you type
- Keyboard shortcuts (Ctrl+N for new book, Ctrl+F for search)
- Book count display
- Empty state messages
- Input validation and error handling
- Duplicate book prevention

## How to Use

1. **Open the Web App**: Open `index.html` in any modern web browser
2. **Add Books**: Fill in the form with book details and click "Add Book"
3. **Search**: Use the search box to find books by title or author
4. **Remove Books**: Click the "Remove" button on any book card
5. **Data Persistence**: Your books are automatically saved and will be there when you return

## Files Structure

```
library manager/
├── index.html          # Main HTML structure
├── styles.css          # Modern CSS styling
├── script.js           # JavaScript functionality
├── src/               # Original Java console version
│   ├── Book.java
│   └── LibraryManager.java
└── README.md          # This file
```

## Technical Details

### JavaScript Classes
- **Book**: Represents a book with title, author, year, and unique ID
- **LibraryManager**: Handles all library operations and UI interactions

### Browser Compatibility
- Works in all modern browsers (Chrome, Firefox, Safari, Edge)
- Requires JavaScript enabled
- Uses localStorage for data persistence

### Responsive Breakpoints
- Desktop: 1200px+ (multi-column layout)
- Tablet: 768px-1199px (responsive grid)
- Mobile: 480px-767px (single column)
- Small Mobile: <480px (optimized for small screens)

## Getting Started

Simply open `index.html` in your web browser - no installation or server required!

## Keyboard Shortcuts

- **Ctrl/Cmd + N**: Focus on the title field to add a new book
- **Ctrl/Cmd + F**: Focus on the search field
- **Enter**: Submit form or search

## Future Enhancements

Potential features that could be added:
- Export/import book data
- Categories/genres for books
- Reading status (read, currently reading, want to read)
- Book ratings and reviews
- Cover image uploads
- Advanced search filters
- Book recommendations

---

*Upgraded from a Java console application to a modern web app!*
