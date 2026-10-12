package com.example.scrollfit.platform.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.scrollfit.core.data.ChallengeMode
import com.example.scrollfit.core.data.ChallengeStatus
import com.example.scrollfit.core.exercise.Exercise

class EnumConverters {
    @TypeConverter fun exercise(value: String): Exercise = Exercise.valueOf(value)
    @TypeConverter fun exerciseName(value: Exercise): String = value.name
    @TypeConverter fun mode(value: String): ChallengeMode = ChallengeMode.valueOf(value)
    @TypeConverter fun modeName(value: ChallengeMode): String = value.name
    @TypeConverter fun status(value: String): ChallengeStatus = ChallengeStatus.valueOf(value)
    @TypeConverter fun statusName(value: ChallengeStatus): String = value.name
}

@Database(
    entities = [TargetRow::class, UsageRow::class, CombinedRow::class, ChallengeRow::class,
        AwardRow::class, ActivityRow::class, SessionRow::class, CursorRow::class],
    version = 2, exportSchema = true,
)
@TypeConverters(EnumConverters::class)
abstract class ExerciseGateDatabase : RoomDatabase() {
    abstract fun records(): ExerciseGateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS usage_cursors (timelineId TEXT NOT NULL, throughElapsedMs INTEGER NOT NULL, PRIMARY KEY(timelineId))")
            }
        }
        /** No destructive fallback: an unsupported upgrade fails rather than replenishing budgets. */
        fun open(context: Context, name: String = "exercise-gate.db"): ExerciseGateDatabase =
            Room.databaseBuilder(context.applicationContext, ExerciseGateDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2).build()
    }
}
