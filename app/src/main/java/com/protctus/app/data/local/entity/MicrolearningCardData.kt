package com.protctus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "microlearning_cards")
@TypeConverters(StringListConverter::class)
data class MicrolearningCardData(
    @PrimaryKey val id: String,
    val type: String, // "INFO", "QUIZ_BOOL", "QUIZ_MCQ"
    val category: String, // e.g. "Phishing", "Malware", "Network Security"
    val title: String,
    val description: String,
    val visualUrl: String, // Can be an image URL or a Lottie JSON URL
    val options: List<String>, // Empty if "INFO"
    val correctAnswer: String,
    val isCompleted: Boolean = false,
    val priority: Int = 0,
    val orderIndex: Int = 0,
    val isUnlocked: Boolean = false
)

class StringListConverter {
    @TypeConverter
    fun fromString(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return Gson().toJson(list)
    }
}
