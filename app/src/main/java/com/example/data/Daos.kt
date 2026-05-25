package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, timestamp DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY date ASC, time ASC")
    fun getAllReminders(): Flow<List<Reminder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: String)

    @Query("DELETE FROM reminders")
    suspend fun deleteAllReminders()
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY timestamp DESC")
    fun getAllHabits(): Flow<List<Habit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: Habit)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabitById(id: String)

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()
}

@Dao
interface FinanceTransactionDao {
    @Query("SELECT * FROM finance_transactions ORDER BY date DESC, timestamp DESC")
    fun getAllTransactions(): Flow<List<FinanceTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: FinanceTransaction)

    @Query("DELETE FROM finance_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Query("DELETE FROM finance_transactions")
    suspend fun deleteAllTransactions()
}

@Dao
interface NetWorthItemDao {
    @Query("SELECT * FROM net_worth_items ORDER BY timestamp DESC")
    fun getAllNetWorthItems(): Flow<List<NetWorthItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetWorthItem(item: NetWorthItem)

    @Query("DELETE FROM net_worth_items WHERE id = :id")
    suspend fun deleteNetWorthItemById(id: String)

    @Query("DELETE FROM net_worth_items")
    suspend fun deleteAllNetWorthItems()
}

@Dao
interface AdvancedLedgerDao {
    @Query("SELECT * FROM advanced_ledgers ORDER BY timestamp DESC")
    fun getAllLedgers(): Flow<List<AdvancedLedger>>

    @Query("SELECT * FROM advanced_ledgers WHERE id = :id LIMIT 1")
    suspend fun getLedgerById(id: String): AdvancedLedger?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedger(ledger: AdvancedLedger)

    @Query("DELETE FROM advanced_ledgers WHERE id = :id")
    suspend fun deleteLedgerById(id: String)

    @Query("DELETE FROM advanced_ledgers")
    suspend fun deleteAllLedgers()
}

@Dao
interface LedgerMovementDao {
    @Query("SELECT * FROM ledger_movements WHERE ledgerId = :ledgerId ORDER BY timestamp DESC")
    fun getMovementsForLedger(ledgerId: String): Flow<List<LedgerMovement>>

    @Query("SELECT * FROM ledger_movements ORDER BY timestamp DESC")
    fun getAllMovements(): Flow<List<LedgerMovement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: LedgerMovement)

    @Query("DELETE FROM ledger_movements WHERE ledgerId = :ledgerId")
    suspend fun deleteMovementsForLedger(ledgerId: String)

    @Query("DELETE FROM ledger_movements")
    suspend fun deleteAllMovements()
}

@Dao
interface WebLinkDao {
    @Query("SELECT * FROM web_links ORDER BY timestamp DESC")
    fun getAllWebLinks(): Flow<List<WebLink>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWebLink(link: WebLink)

    @Query("DELETE FROM web_links WHERE id = :id")
    suspend fun deleteWebLinkById(id: String)

    @Query("DELETE FROM web_links")
    suspend fun deleteAllWebLinks()
}
