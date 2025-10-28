class Book {
    constructor(title, author, year, genre = 'other', status = 'want-to-read', rating = 0) {
        this.title = title;
        this.author = author;
        this.year = parseInt(year);
        this.genre = genre;
        this.status = status;
        this.rating = parseInt(rating);
        this.id = this.generateId();
        this.dateAdded = new Date().toISOString();
    }

    generateId() {
        return Date.now() + Math.random().toString(36).substr(2, 9);
    }

    equals(otherBook) {
        return this.title.toLowerCase() === otherBook.title.toLowerCase();
    }

    toJSON() {
        return {
            id: this.id,
            title: this.title,
            author: this.author,
            year: this.year,
            genre: this.genre,
            status: this.status,
            rating: this.rating,
            dateAdded: this.dateAdded
        };
    }

    static fromJSON(json) {
        const book = new Book(json.title, json.author, json.year, json.genre, json.status, json.rating);
        book.id = json.id;
        book.dateAdded = json.dateAdded || new Date().toISOString();
        return book;
    }
}

class LibraryManager {
    constructor() {
        this.books = [];
        this.currentFilter = {
            search: '',
            genre: '',
            status: '',
            sortBy: 'title',
            sortOrder: 'asc'
        };
        this.loadFromStorage();
        this.initializeEventListeners();
        this.renderBooks();
        this.updateStatistics();
    }

    initializeEventListeners() {
        // Add book form
        document.getElementById('addBookForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.addBook();
        });

        // Star rating
        this.initializeStarRating();

        // Search functionality
        document.getElementById('searchBtn').addEventListener('click', () => {
            this.applyFilters();
        });

        document.getElementById('searchInput').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.applyFilters();
            }
        });

        document.getElementById('clearSearchBtn').addEventListener('click', () => {
            this.clearFilters();
        });

        // Real-time search
        document.getElementById('searchInput').addEventListener('input', () => {
            this.currentFilter.search = document.getElementById('searchInput').value.trim().toLowerCase();
            this.applyFilters();
        });

        // Filter dropdowns
        document.getElementById('filterGenre').addEventListener('change', () => {
            this.currentFilter.genre = document.getElementById('filterGenre').value;
            this.applyFilters();
        });

        document.getElementById('filterStatus').addEventListener('change', () => {
            this.currentFilter.status = document.getElementById('filterStatus').value;
            this.applyFilters();
        });

        document.getElementById('sortBy').addEventListener('change', () => {
            this.currentFilter.sortBy = document.getElementById('sortBy').value;
            this.applyFilters();
        });

        document.getElementById('sortOrderBtn').addEventListener('click', () => {
            this.toggleSortOrder();
        });

        // Export/Import
        document.getElementById('exportBtn').addEventListener('click', () => {
            this.exportBooks();
        });

        document.getElementById('importBtn').addEventListener('click', () => {
            document.getElementById('importFile').click();
        });

        document.getElementById('importFile').addEventListener('change', (e) => {
            this.importBooks(e.target.files[0]);
        });
    }

    initializeStarRating() {
        const stars = document.querySelectorAll('.star');
        const ratingInput = document.getElementById('rating');
        
        stars.forEach(star => {
            star.addEventListener('click', () => {
                const rating = parseInt(star.dataset.rating);
                ratingInput.value = rating;
                this.updateStarDisplay(rating);
            });
            
            star.addEventListener('mouseenter', () => {
                const rating = parseInt(star.dataset.rating);
                this.updateStarDisplay(rating);
            });
        });
        
        document.getElementById('starRating').addEventListener('mouseleave', () => {
            this.updateStarDisplay(parseInt(ratingInput.value));
        });
    }

    updateStarDisplay(rating) {
        const stars = document.querySelectorAll('.star');
        stars.forEach((star, index) => {
            if (index < rating) {
                star.classList.add('active');
            } else {
                star.classList.remove('active');
            }
        });
    }

    addBook() {
        const title = document.getElementById('title').value.trim();
        const author = document.getElementById('author').value.trim();
        const year = document.getElementById('year').value.trim();
        const genre = document.getElementById('genre').value;
        const status = document.getElementById('status').value;
        const rating = document.getElementById('rating').value;

        if (!title || !author || !year || !genre) {
            this.showToast('Please fill in all required fields', 'error');
            return;
        }

        const newBook = new Book(title, author, year, genre, status, rating);
        
        // Check if book already exists
        if (this.books.some(book => book.equals(newBook))) {
            this.showToast('A book with this title already exists', 'error');
            return;
        }

        this.books.push(newBook);
        this.saveToStorage();
        this.renderBooks();
        this.updateStatistics();
        this.clearForm();
        this.showToast('Book added successfully!');
    }

    removeBook(bookId) {
        const bookIndex = this.books.findIndex(book => book.id === bookId);
        if (bookIndex !== -1) {
            const book = this.books[bookIndex];
            this.books.splice(bookIndex, 1);
            this.saveToStorage();
            this.renderBooks();
            this.updateStatistics();
            this.showToast(`"${book.title}" removed successfully!`);
        }
    }

    applyFilters() {
        let filteredBooks = [...this.books];

        // Search filter
        if (this.currentFilter.search) {
            filteredBooks = filteredBooks.filter(book => 
                book.title.toLowerCase().includes(this.currentFilter.search) ||
                book.author.toLowerCase().includes(this.currentFilter.search) ||
                book.genre.toLowerCase().includes(this.currentFilter.search)
            );
        }

        // Genre filter
        if (this.currentFilter.genre) {
            filteredBooks = filteredBooks.filter(book => book.genre === this.currentFilter.genre);
        }

        // Status filter
        if (this.currentFilter.status) {
            filteredBooks = filteredBooks.filter(book => book.status === this.currentFilter.status);
        }

        // Sort books
        filteredBooks = this.sortBooks(filteredBooks);

        this.renderBooks(filteredBooks);
    }

    sortBooks(books) {
        const { sortBy, sortOrder } = this.currentFilter;
        
        return books.sort((a, b) => {
            let aValue, bValue;
            
            switch (sortBy) {
                case 'title':
                    aValue = a.title.toLowerCase();
                    bValue = b.title.toLowerCase();
                    break;
                case 'author':
                    aValue = a.author.toLowerCase();
                    bValue = b.author.toLowerCase();
                    break;
                case 'year':
                    aValue = a.year;
                    bValue = b.year;
                    break;
                case 'rating':
                    aValue = a.rating;
                    bValue = b.rating;
                    break;
                case 'status':
                    aValue = a.status;
                    bValue = b.status;
                    break;
                case 'genre':
                    aValue = a.genre;
                    bValue = b.genre;
                    break;
                default:
                    aValue = a.title.toLowerCase();
                    bValue = b.title.toLowerCase();
            }

            if (sortOrder === 'asc') {
                return aValue < bValue ? -1 : aValue > bValue ? 1 : 0;
            } else {
                return aValue > bValue ? -1 : aValue < bValue ? 1 : 0;
            }
        });
    }

    toggleSortOrder() {
        const btn = document.getElementById('sortOrderBtn');
        if (this.currentFilter.sortOrder === 'asc') {
            this.currentFilter.sortOrder = 'desc';
            btn.textContent = '↓ Descending';
            btn.dataset.order = 'desc';
        } else {
            this.currentFilter.sortOrder = 'asc';
            btn.textContent = '↑ Ascending';
            btn.dataset.order = 'asc';
        }
        this.applyFilters();
    }

    clearFilters() {
        document.getElementById('searchInput').value = '';
        document.getElementById('filterGenre').value = '';
        document.getElementById('filterStatus').value = '';
        document.getElementById('sortBy').value = 'title';
        
        this.currentFilter = {
            search: '',
            genre: '',
            status: '',
            sortBy: 'title',
            sortOrder: 'asc'
        };
        
        const btn = document.getElementById('sortOrderBtn');
        btn.textContent = '↑ Ascending';
        btn.dataset.order = 'asc';
        
        this.renderBooks();
    }

    renderBooks(booksToRender = null) {
        const container = document.getElementById('booksContainer');
        const books = booksToRender || this.books;
        
        // Update book count
        document.getElementById('bookCount').textContent = this.books.length;

        if (books.length === 0) {
            if (this.books.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <p>No books in your library yet. Add your first book above!</p>
                    </div>
                `;
            } else {
                container.innerHTML = `
                    <div class="empty-state">
                        <p>No books match your search criteria.</p>
                    </div>
                `;
            }
            return;
        }

        container.innerHTML = books.map(book => `
            <div class="book-card" data-book-id="${book.id}">
                <div class="book-title">${this.escapeHtml(book.title)}</div>
                <div class="book-author">by ${this.escapeHtml(book.author)}</div>
                <div class="book-meta">
                    <div class="book-year">Published: ${book.year}</div>
                    <div class="book-genre">${this.formatGenre(book.genre)}</div>
                    <div class="book-status status-${book.status}">${this.formatStatus(book.status)}</div>
                    ${book.rating > 0 ? `<div class="book-rating">${this.renderStars(book.rating)}</div>` : ''}
                </div>
                <div class="book-actions">
                    <button class="btn btn-outline" onclick="libraryManager.updateBookStatus('${book.id}')">
                        Update Status
                    </button>
                    <button class="btn btn-danger" onclick="libraryManager.confirmRemoveBook('${book.id}', '${this.escapeHtml(book.title)}')">
                        Remove
                    </button>
                </div>
            </div>
        `).join('');
    }

    renderStars(rating) {
        return '★'.repeat(rating) + '☆'.repeat(5 - rating);
    }

    formatGenre(genre) {
        return genre.split('-').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
    }

    formatStatus(status) {
        return status.split('-').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
    }

    updateBookStatus(bookId) {
        const book = this.books.find(b => b.id === bookId);
        if (!book) return;

        const statuses = ['want-to-read', 'currently-reading', 'read'];
        const currentIndex = statuses.indexOf(book.status);
        const nextIndex = (currentIndex + 1) % statuses.length;
        
        book.status = statuses[nextIndex];
        this.saveToStorage();
        this.renderBooks();
        this.updateStatistics();
        this.showToast(`Book status updated to: ${this.formatStatus(book.status)}`);
    }

    confirmRemoveBook(bookId, bookTitle) {
        if (confirm(`Are you sure you want to remove "${bookTitle}" from your library?`)) {
            this.removeBook(bookId);
        }
    }

    clearForm() {
        document.getElementById('addBookForm').reset();
        document.getElementById('rating').value = '0';
        this.updateStarDisplay(0);
    }

    updateStatistics() {
        const totalBooks = this.books.length;
        const booksRead = this.books.filter(book => book.status === 'read').length;
        const currentlyReading = this.books.filter(book => book.status === 'currently-reading').length;
        
        const ratedBooks = this.books.filter(book => book.rating > 0);
        const averageRating = ratedBooks.length > 0 
            ? (ratedBooks.reduce((sum, book) => sum + book.rating, 0) / ratedBooks.length).toFixed(1)
            : '0.0';

        const genreCounts = {};
        this.books.forEach(book => {
            genreCounts[book.genre] = (genreCounts[book.genre] || 0) + 1;
        });
        
        const favoriteGenre = Object.keys(genreCounts).length > 0
            ? this.formatGenre(Object.keys(genreCounts).reduce((a, b) => genreCounts[a] > genreCounts[b] ? a : b))
            : '-';

        document.getElementById('totalBooks').textContent = totalBooks;
        document.getElementById('booksRead').textContent = booksRead;
        document.getElementById('currentlyReading').textContent = currentlyReading;
        document.getElementById('averageRating').textContent = averageRating;
        document.getElementById('favoriteGenre').textContent = favoriteGenre;
    }

    exportBooks() {
        if (this.books.length === 0) {
            this.showToast('No books to export', 'error');
            return;
        }

        const dataStr = JSON.stringify(this.books.map(book => book.toJSON()), null, 2);
        const dataBlob = new Blob([dataStr], {type: 'application/json'});
        
        const link = document.createElement('a');
        link.href = URL.createObjectURL(dataBlob);
        link.download = `library-books-${new Date().toISOString().split('T')[0]}.json`;
        link.click();
        
        this.showToast('Books exported successfully!');
    }

    importBooks(file) {
        if (!file) return;

        const reader = new FileReader();
        reader.onload = (e) => {
            try {
                const importedData = JSON.parse(e.target.result);
                
                if (!Array.isArray(importedData)) {
                    this.showToast('Invalid file format', 'error');
                    return;
                }

                let importedCount = 0;
                let skippedCount = 0;

                importedData.forEach(bookData => {
                    const book = Book.fromJSON(bookData);
                    if (!this.books.some(existingBook => existingBook.equals(book))) {
                        this.books.push(book);
                        importedCount++;
                    } else {
                        skippedCount++;
                    }
                });

                this.saveToStorage();
                this.renderBooks();
                this.updateStatistics();
                
                let message = `Imported ${importedCount} books`;
                if (skippedCount > 0) {
                    message += `, skipped ${skippedCount} duplicates`;
                }
                this.showToast(message);

            } catch (error) {
                this.showToast('Error reading file', 'error');
            }
        };
        
        reader.readAsText(file);
        // Reset file input
        document.getElementById('importFile').value = '';
    }

    saveToStorage() {
        const booksData = this.books.map(book => book.toJSON());
        localStorage.setItem('libraryBooks', JSON.stringify(booksData));
    }

    loadFromStorage() {
        const savedBooks = localStorage.getItem('libraryBooks');
        if (savedBooks) {
            try {
                const booksData = JSON.parse(savedBooks);
                this.books = booksData.map(bookData => Book.fromJSON(bookData));
            } catch (error) {
                console.error('Error loading books from storage:', error);
                this.books = [];
            }
        }
    }

    showToast(message, type = 'success') {
        const toast = document.getElementById('toast');
        toast.textContent = message;
        toast.className = `toast ${type}`;
        toast.classList.add('show');

        setTimeout(() => {
            toast.classList.remove('show');
        }, 3000);
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Additional utility methods
    getTotalBooks() {
        return this.books.length;
    }

    getBooksByYear(year) {
        return this.books.filter(book => book.year === parseInt(year));
    }

    getBooksByAuthor(author) {
        return this.books.filter(book => 
            book.author.toLowerCase().includes(author.toLowerCase())
        );
    }

    getBooksByGenre(genre) {
        return this.books.filter(book => book.genre === genre);
    }

    getBooksByStatus(status) {
        return this.books.filter(book => book.status === status);
    }

    getReadingProgress() {
        const total = this.books.length;
        const read = this.books.filter(book => book.status === 'read').length;
        return total > 0 ? Math.round((read / total) * 100) : 0;
    }
}

// Initialize the library manager when the page loads
let libraryManager;

document.addEventListener('DOMContentLoaded', () => {
    libraryManager = new LibraryManager();
});

// Add keyboard shortcuts
document.addEventListener('keydown', (e) => {
    // Ctrl/Cmd + N to focus on title input (new book)
    if ((e.ctrlKey || e.metaKey) && e.key === 'n') {
        e.preventDefault();
        document.getElementById('title').focus();
    }
    
    // Ctrl/Cmd + F to focus on search
    if ((e.ctrlKey || e.metaKey) && e.key === 'f') {
        e.preventDefault();
        document.getElementById('searchInput').focus();
    }
});
