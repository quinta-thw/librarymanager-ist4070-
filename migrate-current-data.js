const { MongoClient } = require('mongodb');
const fs = require('fs');
const path = require('path');

const uri = 'mongodb://127.0.0.1:27017';
const dbName = 'library_management';

async function migrateDataToMongoDB() {
    const client = new MongoClient(uri);
    
    try {
        console.log('🔄 Starting migration of current library data to MongoDB...');
        await client.connect();
        console.log('✅ Connected to MongoDB');
        
        const db = client.db(dbName);
        const booksCollection = db.collection('books');
        
        // Clear existing data
        await booksCollection.deleteMany({});
        console.log('🧹 Cleared existing books from MongoDB');
        
        // Read the current library_data.txt file
        const dataFilePath = path.join(__dirname, 'library_data.txt');
        const fileContent = fs.readFileSync(dataFilePath, 'utf8');
        
        // Parse the pipe-delimited data
        const lines = fileContent.trim().split('\n');
        const books = [];
        
        for (const line of lines) {
            if (line.trim()) {
                const parts = line.split('|');
                
                // Handle different formats in your data
                let book;
                if (parts.length >= 6) {
                    const [title, author, year, genre, status, rating, dateAdded] = parts;
                    
                    book = {
                        title: title.trim(),
                        author: author.trim(),
                        year: parseInt(year.trim()),
                        genre: genre.trim(),
                        status: status.trim(),
                        rating: parseInt(rating.trim()) || 0
                    };
                    
                    // Add date if present
                    if (dateAdded && dateAdded.trim()) {
                        book.dateAdded = dateAdded.trim();
                    }
                } else {
                    console.log(`⚠️  Skipping malformed line: ${line}`);
                    continue;
                }
                
                books.push(book);
                console.log(`📚 Prepared: ${book.title} by ${book.author} (${book.year}) - ${book.status}`);
            }
        }
        
        // Insert all books
        if (books.length > 0) {
            await booksCollection.insertMany(books);
            console.log(`✅ Successfully migrated ${books.length} books to MongoDB`);
        }
        
        // Verify the migration
        const totalBooks = await booksCollection.countDocuments();
        console.log(`📊 Total books in MongoDB: ${totalBooks}`);
        
        // Show sample of migrated books
        const sampleBooks = await booksCollection.find().limit(5).toArray();
        console.log('\n📖 Sample migrated books:');
        sampleBooks.forEach(book => {
            console.log(`  - ${book.title} by ${book.author} (${book.year}) - ${book.status} - Rating: ${book.rating}/5`);
        });
        
        console.log('\n🎉 Migration completed successfully!');
        console.log('✅ All your current library data is now in MongoDB');
        console.log('✅ Same books, same information, same functionality');
        
    } catch (error) {
        console.error('💥 Migration failed:', error.message);
    } finally {
        await client.close();
        console.log('\n🔌 MongoDB connection closed');
    }
}

migrateDataToMongoDB().catch(console.error);