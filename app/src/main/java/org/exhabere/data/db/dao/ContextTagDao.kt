package org.exhabere.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.model.TagCategory

@Dao
interface ContextTagDao {

    @Query("SELECT * FROM context_tags WHERE category = :category ORDER BY usage_count DESC, last_used_millis DESC")
    fun getTagsByCategoryFlow(category: TagCategory): Flow<List<ContextTagEntity>>

    @Query("SELECT * FROM context_tags ORDER BY category, usage_count DESC")
    fun getAllTagsFlow(): Flow<List<ContextTagEntity>>

    @Query("SELECT * FROM context_tags ORDER BY category, id")
    suspend fun getAllTags(): List<ContextTagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: ContextTagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<ContextTagEntity>): List<Long>

    @Query("SELECT * FROM context_tags WHERE category = :category AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findTagByName(category: TagCategory, name: String): ContextTagEntity?

    @Query("UPDATE context_tags SET usage_count = usage_count + 1, last_used_millis = :timestamp WHERE category = :category AND LOWER(name) = LOWER(:name)")
    suspend fun incrementUsage(category: TagCategory, name: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM context_tags WHERE id = :tagId AND is_custom = 1")
    suspend fun deleteCustomTag(tagId: Long): Int

    @Query("DELETE FROM context_tags WHERE is_custom = 1")
    suspend fun clearCustomTags(): Int

    @Query("UPDATE context_tags SET usage_count = 0, last_used_millis = 0 WHERE is_custom = 0")
    suspend fun resetDefaultTagUsage()

    @Query("DELETE FROM context_tags")
    suspend fun deleteAll()
}
