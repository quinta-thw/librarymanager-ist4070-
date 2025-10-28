const { MongoClient } = require('mongodb');

const uri = 'mongodb://127.0.0.1:27017';
const dbName = 'library_management';

async function showDatabase() {
    const client = new MongoClient(uri);
    
    try {
        console.log('🔍 Connecting to your MongoDB database...');
        await client.connect();
        console.log('✅ Connected to MongoDB');
        
        const db = client.db(dbName);
        
        // Show database info
        console.log('\n📊 DATABASE: library_management');
        console.log('=====================================');
        
        // Show collections
        const collections = await db.listCollections().toArray();
        console.log('\n📂 COLLECTIONS:');
        collections.forEach(col => {
            console.log(`   - ${col.name}`);
        });
        
        // Show books collection
        console.log('\n📚 BOOKS COLLECTION:');
        const booksCollection = db.collection('books');
        const bookCount = await booksCollection.countDocuments();
        console.log(`   Total books: ${bookCount}`);
        
        const sampleBooks = await booksCollection.find().limit(5).toArray();
        console.log('\n   Sample books:');
        sampleBooks.forEach((book, index) => {
            console.log(`   ${index + 1}. "${book.title}" by ${book.author} (${book.year})`);
            console.log(`      Genre: ${book.genre} | Status: ${book.status} | Rating: ${book.rating}/5`);
        });
        
        // Show users collection
        console.log('\n👥 USERS COLLECTION:');
        const usersCollection = db.collection('users');
        const userCount = await usersCollection.countDocuments();
        console.log(`   Total users: ${userCount}`);
        
        const users = await usersCollection.find().toArray();
        console.log('\n   User accounts:');
        users.forEach((user, index) => {
            console.log(`   ${index + 1}. Username: ${user.username}`);
            console.log(`      Role: ${user.role} | Full Name: ${user.fullName}`);
            console.log(`      Active: ${user.isActive} | Last Login: ${user.lastLogin || 'Never'}`);
        });
        
        // Show sessions if any
        const sessionsCollection = db.collection('sessions');
        const sessionCount = await sessionsCollection.countDocuments();
        console.log(`\n🔐 SESSIONS COLLECTION:`);
        console.log(`   Active sessions: ${sessionCount}`);
        
        console.log('\n🎯 TO VIEW IN MONGODB COMPASS:');
        console.log('   1. Open MongoDB Compass');
        console.log('   2. Connect to: mongodb://localhost:27017');
        console.log('   3. Navigate to: library_management database');
        console.log('   4. Explore collections: books, users, sessions');
        
    } catch (error) {
        console.error('💥 Error:', error.message);
    } finally {
        await client.close();
        console.log('\n🔌 Connection closed');
    }
}

showDatabase().catch(console.error);