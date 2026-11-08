package com.balancecalories.data.db

import androidx.room.TypeConverter
import com.balancecalories.core.energy.ActivityLevel
import com.balancecalories.core.energy.Sex

/**
 * Enum storage follows the SQL model in docs/03-architecture.md §3: `sex` is `male|female` and
 * `activity_level` is the lowercase enum name. Storing the wire form rather than the ordinal means a
 * future enum reorder cannot silently rewrite a profile's meaning.
 */
class Converters {

    @TypeConverter
    fun sexToString(value: Sex): String = when (value) {
        Sex.FEMALE -> "female"
        Sex.MALE -> "male"
    }

    @TypeConverter
    fun stringToSex(value: String): Sex = when (value) {
        "female" -> Sex.FEMALE
        "male" -> Sex.MALE
        else -> error("Unknown sex value in database: $value")
    }

    @TypeConverter
    fun activityLevelToString(value: ActivityLevel): String = value.name.lowercase()

    @TypeConverter
    fun stringToActivityLevel(value: String): ActivityLevel =
        ActivityLevel.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: error("Unknown activity level in database: $value")
}
