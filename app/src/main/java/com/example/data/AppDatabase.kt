package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Note::class,
        Reminder::class,
        Habit::class,
        Account::class,
        FinanceTransaction::class,
        NetWorthItem::class,
        AdvancedLedger::class,
        LedgerMovement::class,
        WebLink::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun habitDao(): HabitDao
    abstract fun accountDao(): AccountDao
    abstract fun financeTransactionDao(): FinanceTransactionDao
    abstract fun netWorthItemDao(): NetWorthItemDao
    abstract fun advancedLedgerDao(): AdvancedLedgerDao
    abstract fun ledgerMovementDao(): LedgerMovementDao
    abstract fun webLinkDao(): WebLinkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "personal_assistant_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
