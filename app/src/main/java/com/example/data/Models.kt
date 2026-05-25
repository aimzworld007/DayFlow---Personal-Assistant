package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val category: String,
    val date: String, // YYYY-MM-DD
    val isPinned: Boolean,
    val timestamp: Long,
    val userId: String = ""
)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey val id: String,
    val title: String,
    val date: String, // YYYY-MM-DD
    val time: String, // HH:MM
    val repeatType: String, // NONE, DAILY, WEEKLY, MONTHLY
    val isDone: Boolean,
    val timestamp: Long,
    val userId: String = ""
)

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey val id: String,
    val name: String,
    val history: String, // Comma-separated dates where it was checked-in (e.g. "2026-05-24,2026-05-25")
    val streak: Int,
    val completionRate: Float,
    val timestamp: Long,
    val userId: String = ""
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // BANK, CASH
    val balance: Double,
    val timestamp: Long,
    val userId: String = ""
)

@Entity(tableName = "finance_transactions")
data class FinanceTransaction(
    @PrimaryKey val id: String,
    val type: String, // INCOME, EXPENSE, REVERSAL
    val category: String,
    val amount: Double,
    val date: String, // YYYY-MM-DD
    val note: String,
    val accountId: String,
    val timestamp: Long,
    val userId: String = ""
)

@Entity(tableName = "net_worth_items")
data class NetWorthItem(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // ASSET, LIABILITY
    val amount: Double,
    val confidence: String, // YES, MAYBE, NO
    val timestamp: Long,
    val userId: String = ""
)

@Entity(tableName = "advanced_ledgers")
data class AdvancedLedger(
    @PrimaryKey val id: String,
    val contactName: String,
    val projectName: String,
    val linkedAccountId: String,
    val advanceReceived: Double,
    val costAmount: Double,
    val workCompletedAmount: Double,
    val refundAmount: Double,
    val payableBalance: Double,
    val receivableBalance: Double,
    val status: String, // ACTIVE, PARTIAL, SETTLED
    val confidence: String, // YES, MAYBE, NO
    val timestamp: Long,
    val userId: String = ""
)

@Entity(tableName = "ledger_movements")
data class LedgerMovement(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val type: String, // ADVANCE_REC, COST_ADDED, WORK_COMPLETED, REFUND, SETTLEMENT, ADJUSTMENT
    val amount: Double,
    val note: String,
    val timestamp: Long,
    val userId: String = ""
)

@Entity(tableName = "web_links")
data class WebLink(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val isCustom: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
