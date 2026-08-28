package com.hangfolyam.app

import android.content.Context
import androidx.room.*

@Entity(tableName = "favorite_songs")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val source: String
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_songs")
    suspend fun getAllFavorites(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(song: FavoriteEntity)

    @Query("DELETE FROM favorite_songs WHERE id = :songId")
    suspend fun deleteFavorite(songId: String)
}

@Database(entities = [FavoriteEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hangfolyam_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
