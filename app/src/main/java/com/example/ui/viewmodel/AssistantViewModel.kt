package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AssistantRepository(application, db)

    // Auth state flows
    val isGuestMode: StateFlow<Boolean> = repository.isGuestMode
    val currentUserEmail: StateFlow<String?> = repository.currentUserEmail
    val currentUserId: StateFlow<String?> = repository.currentUserId

    // Theme and Currency states for global accessibility
    private val _themeSetting = MutableStateFlow("SYSTEM")
    val themeSetting: StateFlow<String> = _themeSetting.asStateFlow()

    private val _currencySetting = MutableStateFlow("$")
    val currencySetting: StateFlow<String> = _currencySetting.asStateFlow()

    private val _profileName = MutableStateFlow("Assistant User")
    val profileName: StateFlow<String> = _profileName.asStateFlow()

    fun updateThemeSetting(theme: String) {
        _themeSetting.value = theme
    }

    fun updateCurrencySetting(currency: String) {
        _currencySetting.value = currency
    }

    fun updateProfileName(name: String) {
        _profileName.value = name
    }

    // Dialog & navigation state
    private val _showMigrationPrompt = MutableStateFlow(false)
    val showMigrationPrompt: StateFlow<Boolean> = _showMigrationPrompt.asStateFlow()

    // Observable Raw Data lists
    val notes: StateFlow<List<Note>> = repository.allNotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reminders: StateFlow<List<Reminder>> = repository.allReminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val habits: StateFlow<List<Habit>> = repository.allHabits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val accounts: StateFlow<List<Account>> = repository.allAccounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions: StateFlow<List<FinanceTransaction>> = repository.allTransactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val netWorthItems: StateFlow<List<NetWorthItem>> = repository.allNetWorthItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val ledgers: StateFlow<List<AdvancedLedger>> = repository.allLedgers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val webLinks: StateFlow<List<WebLink>> = repository.allWebLinks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cache of active ledger movements
    private val _activeLedgerMovements = MutableStateFlow<List<LedgerMovement>>(emptyList())
    val activeLedgerMovements: StateFlow<List<LedgerMovement>> = _activeLedgerMovements.asStateFlow()

    // --- RECTIVE DASHBOARD CALCULATIONS ENGINE ---

    data class DashboardStats(
        val totalBankCash: Double = 0.0,
        val monthlyIncome: Double = 0.0,
        val monthlyExpense: Double = 0.0,
        val confirmedPayables: Double = 0.0,
        val confirmedReceivables: Double = 0.0,
        val netWorth: Double = 0.0,
        val maybeExpectedAmount: Double = 0.0,
        val habitCompletionPercentage: Int = 0,
        val todayNotesCount: Int = 0,
        val pendingRemindersCount: Int = 0
    )

    val dashboardStats: StateFlow<DashboardStats> = combine(
        accounts,
        transactions,
        netWorthItems,
        ledgers,
        habits,
        notes,
        reminders
    ) { flowsArray ->
        val accList = flowsArray[0] as List<Account>
        val txList = flowsArray[1] as List<FinanceTransaction>
        val nwList = flowsArray[2] as List<NetWorthItem>
        val ldList = flowsArray[3] as List<AdvancedLedger>
        val habList = flowsArray[4] as List<Habit>
        val noteList = flowsArray[5] as List<Note>
        val remList = flowsArray[6] as List<Reminder>
        
        val todayStr = getCurrentFormattedDate()
        val currentMonthStr = getCurrentYearMonth() // "2026-05"

        // 1. Bank/Cash Balance
        val totalBank = accList.sumOf { it.balance }

        // 2. Active monthly operations (excluding special ledger double-count parameters)
        val validIncome = txList
            .filter { it.type == "INCOME" && it.date.startsWith(currentMonthStr) && it.category != "Advance Liability" }
            .sumOf { it.amount }

        val validExpense = txList
            .filter { it.type == "EXPENSE" && it.date.startsWith(currentMonthStr) && it.category != "Refund Release" }
            .sumOf { it.amount }

        // 3. Confirmed entries (YES status)
        val confirmedPayableLedgers = ldList.filter { it.confidence == "YES" }.sumOf { it.payableBalance }
        val confirmedPayableNWItems = nwList.filter { it.type == "LIABILITY" && it.confidence == "YES" }.sumOf { it.amount }
        val confirmedPayables = confirmedPayableLedgers + confirmedPayableNWItems

        val confirmedReceivableLedgers = ldList.filter { it.confidence == "YES" }.sumOf { it.receivableBalance }
        val confirmedReceivableNWItems = nwList.filter { it.type == "ASSET" && it.confidence == "YES" }.sumOf { it.amount }
        val confirmedReceivables = confirmedReceivableLedgers + confirmedReceivableNWItems

        // 4. Net worth calculation
        // Net Worth = Total Bank/Cash Balance + Confirmed Receivables + Confirmed Assets (ConfirmedReceivableNWItems covers it) - Confirmed Payables
        val netWorth = totalBank + confirmedReceivables - confirmedPayables

        // 5. Expected Amounts (MAYBE status)
        val expectedReceivableLedgers = ldList.filter { it.confidence == "MAYBE" }.sumOf { it.receivableBalance }
        val expectedReceivableNWItems = nwList.filter { it.type == "ASSET" && it.confidence == "MAYBE" }.sumOf { it.amount }
        val maybeExpectedAssets = expectedReceivableLedgers + expectedReceivableNWItems

        val expectedPayableLedgers = ldList.filter { it.confidence == "MAYBE" }.sumOf { it.payableBalance }
        val expectedPayableNWItems = nwList.filter { it.type == "LIABILITY" && it.confidence == "MAYBE" }.sumOf { it.amount }
        val maybeExpectedLiabilities = expectedPayableNWItems + expectedPayableLedgers

        val maybeExpectedNet = maybeExpectedAssets - maybeExpectedLiabilities

        // 6. Habit Completion Stats
        val habitPercent = if (habList.isEmpty()) 0 else {
            val totalScore = habList.sumOf { it.completionRate.toInt() }
            totalScore / habList.size
        }

        // 7. Today Notes
        val todayNotes = noteList.filter { it.date == todayStr }.size

        // 8. Pending Reminders
        val pendingReminders = remList.filter { !it.isDone }.size

        DashboardStats(
            totalBankCash = totalBank,
            monthlyIncome = validIncome,
            monthlyExpense = validExpense,
            confirmedPayables = confirmedPayables,
            confirmedReceivables = confirmedReceivables,
            netWorth = netWorth,
            maybeExpectedAmount = maybeExpectedNet,
            habitCompletionPercentage = habitPercent,
            todayNotesCount = todayNotes,
            pendingRemindersCount = pendingReminders
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    // --- NOTES CONTROLS ---

    fun addNote(title: String, content: String, category: String, date: String, isPinned: Boolean) {
        viewModelScope.launch {
            val note = Note(
                id = UUID.randomUUID().toString(),
                title = title,
                content = content,
                category = if (category.isBlank()) "General" else category,
                date = if (date.isBlank()) getCurrentFormattedDate() else date,
                isPinned = isPinned,
                timestamp = System.currentTimeMillis()
            )
            repository.saveNote(note)
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.saveNote(note.copy(timestamp = System.currentTimeMillis()))
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    // --- REMINDERS CONTROLS ---

    fun addReminder(title: String, date: String, time: String, repeatType: String) {
        viewModelScope.launch {
            val rem = Reminder(
                id = UUID.randomUUID().toString(),
                title = title,
                date = date,
                time = time,
                repeatType = repeatType,
                isDone = false,
                timestamp = System.currentTimeMillis()
            )
            repository.saveReminder(rem)
        }
    }

    fun toggleReminderStatus(reminder: Reminder) {
        viewModelScope.launch {
            repository.saveReminder(reminder.copy(isDone = !reminder.isDone))
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            repository.deleteReminder(id)
        }
    }

    // --- HABITS LIFE ---

    fun addHabit(name: String) {
        viewModelScope.launch {
            val hab = Habit(
                id = UUID.randomUUID().toString(),
                name = name,
                history = "",
                streak = 0,
                completionRate = 0f,
                timestamp = System.currentTimeMillis()
            )
            repository.saveHabit(hab)
        }
    }

    fun checkInHabit(habit: Habit, dateStr: String = getCurrentFormattedDate()) {
        viewModelScope.launch {
            val dateList = habit.history.split(",").filter { it.isNotBlank() }.toMutableList()
            if (dateList.contains(dateStr)) {
                // If already completed on this date, check-out/remove it
                dateList.remove(dateStr)
            } else {
                dateList.add(dateStr)
            }

            // Calculate streaks and rate based on last 7 days checkins
            val calendar = Calendar.getInstance()
            var daysCompletedInLast7 = 0
            for (i in 0..6) {
                val checkDate = formatDate(calendar.time)
                if (dateList.contains(checkDate)) {
                    daysCompletedInLast7++
                }
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }
            val completionRate = (daysCompletedInLast7.toFloat() / 7f) * 100f

            // Clean streak count
            var currentStreak = 0
            val todayCal = Calendar.getInstance()
            while (true) {
                val dStr = formatDate(todayCal.time)
                if (dateList.contains(dStr)) {
                    currentStreak++
                    todayCal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    // Check if yesterday was completed to preserve streak or break
                    if (currentStreak == 0) {
                        todayCal.add(Calendar.DAY_OF_YEAR, -1)
                        val yesterdayStr = formatDate(todayCal.time)
                        if (dateList.contains(yesterdayStr)) {
                            currentStreak++
                            todayCal.add(Calendar.DAY_OF_YEAR, -1)
                            continue
                        }
                    }
                    break
                }
            }

            val updatedHistory = dateList.joinToString(",")
            repository.saveHabit(habit.copy(
                history = updatedHistory,
                streak = currentStreak,
                completionRate = completionRate
            ))
        }
    }

    fun deleteHabit(id: String) {
        viewModelScope.launch {
            repository.deleteHabit(id)
        }
    }

    // --- MULTI ACCOUNT SYSTEM ---

    fun addAccount(name: String, type: String, balance: Double) {
        viewModelScope.launch {
            val acc = Account(
                id = UUID.randomUUID().toString(),
                name = name,
                type = type,
                balance = balance,
                timestamp = System.currentTimeMillis()
            )
            repository.saveAccount(acc)
        }
    }

    fun deleteAccount(id: String) {
        viewModelScope.launch {
            repository.deleteAccountCompletely(id)
        }
    }

    // --- GENERAL TRANSACTIONS SYSTEM ---

    fun addTransaction(type: String, category: String, amount: Double, date: String, note: String, accountId: String) {
        viewModelScope.launch {
            val tx = FinanceTransaction(
                id = UUID.randomUUID().toString(),
                type = type,
                category = category,
                amount = amount,
                date = if (date.isBlank()) getCurrentFormattedDate() else date,
                note = note,
                accountId = accountId,
                timestamp = System.currentTimeMillis()
            )
            repository.registerFinanceTransaction(tx)
        }
    }

    fun triggerTransactionReversal(tx: FinanceTransaction, note: String) {
        viewModelScope.launch {
            repository.reverseTransaction(tx, note)
        }
    }

    // --- MANUAL NETWORTH CALCULATOR ---

    fun addNetWorthItem(name: String, type: String, amount: Double, confidence: String) {
        viewModelScope.launch {
            val item = NetWorthItem(
                id = UUID.randomUUID().toString(),
                name = name,
                type = type,
                amount = amount,
                confidence = confidence,
                timestamp = System.currentTimeMillis()
            )
            repository.saveNetWorthItem(item)
        }
    }

    fun deleteNetWorthItem(id: String) {
        viewModelScope.launch {
            repository.deleteNetWorthItem(id)
        }
    }

    // --- ADVANCED BOOKKEEPING LEDGER DEALS ---

    fun addLedger(contactName: String, projectName: String, accountId: String, confidence: String) {
        viewModelScope.launch {
            repository.createLedger(contactName, projectName, accountId, confidence)
        }
    }

    fun loadLedgerMovements(ledgerId: String) {
        viewModelScope.launch {
            repository.getMovementsForLedger(ledgerId).collect {
                _activeLedgerMovements.value = it
            }
        }
    }

    fun addLedgerAdvance(ledgerId: String, amount: Double, note: String) {
        viewModelScope.launch {
            repository.addAdvanceReceived(ledgerId, amount, note)
        }
    }

    fun addLedgerCost(ledgerId: String, amount: Double, note: String) {
        viewModelScope.launch {
            repository.addCostAmount(ledgerId, amount, note)
        }
    }

    fun addLedgerWork(ledgerId: String, amount: Double, note: String) {
        viewModelScope.launch {
            repository.addWorkCompleted(ledgerId, amount, note)
        }
    }

    fun addLedgerRefund(ledgerId: String, amount: Double, note: String) {
        viewModelScope.launch {
            repository.addRefundAmount(ledgerId, amount, note)
        }
    }

    fun settleLedgerDirectly(ledgerId: String, note: String) {
        viewModelScope.launch {
            repository.settleLedger(ledgerId, note)
        }
    }

    fun deleteLedgerCompletely(id: String) {
        viewModelScope.launch {
            repository.deleteLedger(id)
        }
    }

    // --- WEB LINKS INTRANET ---

    fun addCustomWebLink(title: String, url: String) {
        viewModelScope.launch {
            val link = WebLink(
                id = UUID.randomUUID().toString(),
                title = title,
                url = url,
                isCustom = true
            )
            repository.saveWebLink(link)
        }
    }

    fun deleteWebLink(id: String) {
        viewModelScope.launch {
            repository.deleteWebLink(id)
        }
    }


    // --- SECURITY RULES & FIREBASE AUTH COMMANDS ---

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            // User just logged in! If there's local guest data left in database, flag prompt
            viewModelScope.launch {
                val hasGuestNotes = db.noteDao().getAllNotes().first().any { it.userId == "guest" || it.userId == "" }
                val hasGuestReminders = db.reminderDao().getAllReminders().first().any { it.userId == "guest" || it.userId == "" }
                val hasGuestHabits = db.habitDao().getAllHabits().first().any { it.userId == "guest" || it.userId == "" }
                val hasGuestAccounts = db.accountDao().getAllAccounts().first().any { it.userId == "guest" || it.userId == "" }
                val hasGuestTransactions = db.financeTransactionDao().getAllTransactions().first().any { it.userId == "guest" || it.userId == "" }
                val hasGuestNetWorth = db.netWorthItemDao().getAllNetWorthItems().first().any { it.userId == "guest" || it.userId == "" }
                val hasGuestLedgers = db.advancedLedgerDao().getAllLedgers().first().any { it.userId == "guest" || it.userId == "" }

                val hasGuestData = hasGuestNotes || hasGuestReminders || hasGuestHabits || 
                        hasGuestAccounts || hasGuestTransactions || hasGuestNetWorth || hasGuestLedgers

                if (hasGuestData) {
                    _showMigrationPrompt.value = true
                }
            }
        }
    }

    init {
        try {
            FirebaseAuth.getInstance().addAuthStateListener(authListener)
        } catch (e: Exception) {
            // Null firebase sandbox fallback
        }
    }

    fun triggerMigrationExplicitly() {
        val uid = currentUserId.value ?: return
        viewModelScope.launch {
            repository.migrateGuestDataToUser(uid)
            _showMigrationPrompt.value = false
        }
    }

    fun dismissMigrationPrompt() {
        _showMigrationPrompt.value = false
    }

    fun logout() {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            // Fallback signout simulation
        }
    }

    // Helper Date utilities
    private fun getCurrentFormattedDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    private fun getCurrentYearMonth(): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        return sdf.format(Date())
    }

    private fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(date)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            FirebaseAuth.getInstance().removeAuthStateListener(authListener)
        } catch (e: Exception) {}
    }
}
