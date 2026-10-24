package org.exhabere.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.exhabere.data.db.model.TagCategory

/**
 * Entity storing pre-seeded and dynamically created context tags (Emotions & Locations).
 * Custom tags created via "Other" are persisted here and cached in quick-select chips.
 */
@Entity(
    tableName = "context_tags",
    indices = [
        Index(value = ["category", "name"], unique = true)
    ]
)
data class ContextTagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "category")
    val category: TagCategory,

    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean = false,

    @ColumnInfo(name = "usage_count")
    val usageCount: Int = 0,

    @ColumnInfo(name = "last_used_millis")
    val lastUsedMillis: Long = System.currentTimeMillis()
)
