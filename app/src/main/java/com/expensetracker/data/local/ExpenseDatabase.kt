package com.expensetracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.expensetracker.data.local.dao.*
import com.expensetracker.data.model.*
import java.time.LocalDateTime

@Database(
    entities = [Transaction::class, Category::class, Budget::class, RecurringRule::class, CurrencyRate::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun currencyRateDao(): CurrencyRateDao

    companion object {
        @Volatile
        private var INSTANCE: ExpenseDatabase? = null

        private val MIGRATION_1_TO_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Version 2 - no schema changes
            }
        }

        private val MIGRATION_2_TO_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN isIncome INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): ExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    "expense_database"
                )
                    .addMigrations(MIGRATION_1_TO_2, MIGRATION_2_TO_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        if (value == null) return null
        return try {
            LocalDateTime.parse(value)
        } catch (e: Exception) {
            android.util.Log.w("Converters", "Corrupt date value in DB: ${value.take(30)}")
            LocalDateTime.MIN
        }
    }

    @TypeConverter
    fun fromBudgetPeriod(value: BudgetPeriod): String = value.name

    @TypeConverter
    fun toBudgetPeriod(value: String): BudgetPeriod {
        return try { 
            BudgetPeriod.valueOf(value) 
        } catch (e: Exception) { 
            BudgetPeriod.MONTHLY 
        }
    }

    @TypeConverter
    fun fromRecurringFrequency(value: RecurringFrequency): String = value.name

    @TypeConverter
    fun toRecurringFrequency(value: String): RecurringFrequency {
        return try { 
            RecurringFrequency.valueOf(value) 
        } catch (e: Exception) { 
            RecurringFrequency.MONTHLY 
        }
    }
}
