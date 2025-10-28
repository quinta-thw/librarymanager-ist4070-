const { MongoClient } = require('mongodb');

const uri = 'mongodb://127.0.0.1:27017';
const dbName = 'library_management';

async function fixUsersInMongoDB() {
    const client = new MongoClient(uri);
    
    try {
        console.log('🔄 Fixing user roles in MongoDB...');
        await client.connect();
        console.log('✅ Connected to MongoDB');
        
        const db = client.db(dbName);
        const usersCollection = db.collection('users');
        
        // Clear existing users
        await usersCollection.deleteMany({});
        console.log('🧹 Cleared existing users from MongoDB');
        
        // Create users with correct roles that match SimpleUser.Role enum (LIBRARIAN, USER)
        const correctUsers = [
            {
                username: 'librarian',
                password: 'admin123',  // Plain text - will be hashed on first login
                role: 'LIBRARIAN',     // Matches SimpleUser.Role.LIBRARIAN
                fullName: 'Head Librarian',
                salt: null,
                createdDate: new Date(),
                lastLogin: null,
                isActive: true
            },
            {
                username: 'admin',
                password: 'admin123',  // Plain text - will be hashed on first login
                role: 'LIBRARIAN',     // Matches SimpleUser.Role.LIBRARIAN
                fullName: 'Library Administrator',
                salt: null,
                createdDate: new Date(),
                lastLogin: null,
                isActive: true
            },
            {
                username: 'user1',
                password: 'user123',   // Plain text - will be hashed on first login
                role: 'USER',          // Matches SimpleUser.Role.USER
                fullName: 'John Reader',
                salt: null,
                createdDate: new Date(),
                lastLogin: null,
                isActive: true
            },
            {
                username: 'reader',
                password: 'read123',   // Plain text - will be hashed on first login
                role: 'USER',          // Matches SimpleUser.Role.USER
                fullName: 'Book Reader',
                salt: null,
                createdDate: new Date(),
                lastLogin: null,
                isActive: true
            }
        ];
        
        // Insert the correct users
        await usersCollection.insertMany(correctUsers);
        console.log('✅ Created users with correct roles:');
        console.log('   👤 librarian (LIBRARIAN role) - password: admin123');
        console.log('   👤 admin (LIBRARIAN role) - password: admin123');
        console.log('   👤 user1 (USER role) - password: user123');
        console.log('   👤 reader (USER role) - password: read123');
        
        // Verify users were created correctly
        const userCount = await usersCollection.countDocuments();
        console.log(`👥 Total users in database: ${userCount}`);
        
        console.log('\n✅ Users are now compatible with SimpleUser.Role enum!');
        console.log('🔐 All users have plain text passwords that will be hashed on first login.');
        
    } catch (error) {
        console.error('💥 Error fixing users:', error.message);
    } finally {
        await client.close();
        console.log('\n🔌 MongoDB connection closed');
    }
}

fixUsersInMongoDB().catch(console.error);