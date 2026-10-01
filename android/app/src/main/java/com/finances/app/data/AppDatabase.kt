package com.finances.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY type, name")
    suspend fun getAll(): List<Account>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: Account)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    /** Replaces the cache with the server's list, so accounts removed elsewhere don't linger. */
    @Transaction
    suspend fun replaceAll(accounts: List<Account>) {
        deleteAll()
        accounts.forEach { upsert(it) }
    }
}

// The database is only an offline cache of server data, so schema changes
// simply rebuild it.
@Database(entities = [UserProfile::class, Account::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "finances.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build().also { instance = it }
        }
    }
}
