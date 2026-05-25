package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AssistantRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // Firebase Helper References
    private val auth: FirebaseAuth?
        get() = try { FirebaseAuth.getInstance() } catch (e: Exception) { null }

    private val firestore: FirebaseFirestore?
        get() = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }

    // Auth State flow
    private val _isGuestMode = MutableStateFlow(true)
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

    private val _currentUserEmail = MutableStateFlow<String?>(null)
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    // Firestore Listener Registrations to properly clean up
    private val activeListeners = mutableListOf<ListenerRegistration>()

    init {
        // Observe auth state changes
        auth?.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                _isGuestMode.value = false
                _currentUserEmail.value = user.email
                _currentUserId.value = user.uid
                // Start sync listeners
                setupRealtimeSync(user.uid)
            } else {
                _isGuestMode.value = true
                _currentUserEmail.value = null
                _currentUserId.value = null
                stopRealtimeSync()
            }
        } ?: run {
            _isGuestMode.value = true
        }

        // Initialize default web links
        scope.launch {
            db.webLinkDao().getAllWebLinks().first().let { current ->
                if (current.isEmpty()) {
                    db.webLinkDao().insertWebLink(
                        WebLink("sys_dev_info", "Developer Info", "https://github.com/google", false)
                    )
                    db.webLinkDao().insertWebLink(
                        WebLink("sys_other_tools", "Other Helpful Tools", "https://ai.studio/build", false)
                    )
                }
            }
        }
    }

    // --- REALTIME SYNC ENGINE ---
    private fun setupRealtimeSync(uid: String) {
        stopRealtimeSync() // Clean existing
        val fs = firestore ?: return

        try {
            // Notes Listener
            val notesListener = fs.collection("users").document(uid).collection("notes")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.let { snap ->
                        scope.launch {
                            val notes = snap.documents.mapNotNull { it.toNote() }
                            // Sync with local: to avoid UI overwrite loops, replace Room cache
                            notes.forEach { db.noteDao().insertNote(it) }
                        }
                    }
                }
            activeListeners.add(notesListener)

            // Reminders Listener
            val remindersListener = fs.collection("users").document(uid).collection("reminders")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.let { snap ->
                        scope.launch {
                            val list = snap.documents.mapNotNull { it.toReminder() }
                            list.forEach { db.reminderDao().insertReminder(it) }
                        }
                    }
                }
            activeListeners.add(remindersListener)

            // Habits Listener
            val habitsListener = fs.collection("users").document(uid).collection("habits")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.let { snap ->
                        scope.launch {
                            val list = snap.documents.mapNotNull { it.toHabit() }
                            list.forEach { db.habitDao().insertHabit(it) }
                        }
                    }
                }
            activeListeners.add(habitsListener)

            // Accounts Listener
            val accountsListener = fs.collection("users").document(uid).collection("accounts")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.let { snap ->
                        scope.launch {
                            val list = snap.documents.mapNotNull { it.toAccount() }
                            list.forEach { db.accountDao().insertAccount(it) }
                        }
                    }
                }
            activeListeners.add(accountsListener)

            // Transactions Listener
            val txListener = fs.collection("users").document(uid).collection("financeTransactions")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.let { snap ->
                        scope.launch {
                            val list = snap.documents.mapNotNull { it.toFinanceTransaction() }
                            list.forEach { db.financeTransactionDao().insertTransaction(it) }
                        }
                    }
                }
            activeListeners.add(txListener)

            // NetWorth Items Listener
            val netWorthListener = fs.collection("users").document(uid).collection("netWorthItems")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.let { snap ->
                        scope.launch {
                            val list = snap.documents.mapNotNull { it.toNetWorthItem() }
                            list.forEach { db.netWorthItemDao().insertNetWorthItem(it) }
                        }
                    }
                }
            activeListeners.add(netWorthListener)

            // Advanced Ledgers Listener
            val ledgersListener = fs.collection("users").document(uid).collection("advanceLedgers")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.let { snap ->
                        scope.launch {
                            val list = snap.documents.mapNotNull { it.toAdvancedLedger() }
                            list.forEach { db.advancedLedgerDao().insertLedger(it) }
                        }
                    }
                }
            activeListeners.add(ledgersListener)

            // Ledger Movements Listener
            val movementsListener = fs.collection("users").document(uid).collection("ledgerMovements")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.let { snap ->
                        scope.launch {
                            val list = snap.documents.mapNotNull { it.toLedgerMovement() }
                            list.forEach { db.ledgerMovementDao().insertMovement(it) }
                        }
                    }
                }
            activeListeners.add(movementsListener)

            // WebLinks Listener
            val linksListener = fs.collection("users").document(uid).collection("webLinks")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.let { snap ->
                        scope.launch {
                            val list = snap.documents.mapNotNull { it.toWebLink() }
                            list.forEach { db.webLinkDao().insertWebLink(it) }
                        }
                    }
                }
            activeListeners.add(linksListener)

        } catch (e: Exception) {
            Log.e("RepositorySync", "Failed setting up Firestore listeners: ${e.message}")
        }
    }

    private fun stopRealtimeSync() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }

    // --- CONVERSION METHODS ---
    private fun DocumentSnapshot.toNote() = getString("title")?.let {
        Note(
            id = id,
            title = it,
            content = getString("content") ?: "",
            category = getString("category") ?: "General",
            date = getString("date") ?: "",
            isPinned = getBoolean("isPinned") ?: false,
            timestamp = getLong("timestamp") ?: 0L,
            userId = getString("userId") ?: ""
        )
    }

    private fun DocumentSnapshot.toReminder() = getString("title")?.let {
        Reminder(
            id = id,
            title = it,
            date = getString("date") ?: "",
            time = getString("time") ?: "",
            repeatType = getString("repeatType") ?: "NONE",
            isDone = getBoolean("isDone") ?: false,
            timestamp = getLong("timestamp") ?: 0L,
            userId = getString("userId") ?: ""
        )
    }

    private fun DocumentSnapshot.toHabit() = getString("name")?.let {
        Habit(
            id = id,
            name = it,
            history = getString("history") ?: "",
            streak = getLong("streak")?.toInt() ?: 0,
            completionRate = getDouble("completionRate")?.toFloat() ?: 0f,
            timestamp = getLong("timestamp") ?: 0L,
            userId = getString("userId") ?: ""
        )
    }

    private fun DocumentSnapshot.toAccount() = getString("name")?.let {
        Account(
            id = id,
            name = it,
            type = getString("type") ?: "BANK",
            balance = getDouble("balance") ?: 0.0,
            timestamp = getLong("timestamp") ?: 0L,
            userId = getString("userId") ?: ""
        )
    }

    private fun DocumentSnapshot.toFinanceTransaction() = getString("type")?.let {
        FinanceTransaction(
            id = id,
            type = it,
            category = getString("category") ?: "Other",
            amount = getDouble("amount") ?: 0.0,
            date = getString("date") ?: "",
            note = getString("note") ?: "",
            accountId = getString("accountId") ?: "",
            timestamp = getLong("timestamp") ?: 0L,
            userId = getString("userId") ?: ""
        )
    }

    private fun DocumentSnapshot.toNetWorthItem() = getString("name")?.let {
        NetWorthItem(
            id = id,
            name = it,
            type = getString("type") ?: "ASSET",
            amount = getDouble("amount") ?: 0.0,
            confidence = getString("confidence") ?: "YES",
            timestamp = getLong("timestamp") ?: 0L,
            userId = getString("userId") ?: ""
        )
    }

    private fun DocumentSnapshot.toAdvancedLedger() = getString("contactName")?.let {
        AdvancedLedger(
            id = id,
            contactName = it,
            projectName = getString("projectName") ?: "",
            linkedAccountId = getString("linkedAccountId") ?: "",
            advanceReceived = getDouble("advanceReceived") ?: 0.0,
            costAmount = getDouble("costAmount") ?: 0.0,
            workCompletedAmount = getDouble("workCompletedAmount") ?: 0.0,
            refundAmount = getDouble("refundAmount") ?: 0.0,
            payableBalance = getDouble("payableBalance") ?: 0.0,
            receivableBalance = getDouble("receivableBalance") ?: 0.0,
            status = getString("status") ?: "ACTIVE",
            confidence = getString("confidence") ?: "YES",
            timestamp = getLong("timestamp") ?: 0L,
            userId = getString("userId") ?: ""
        )
    }

    private fun DocumentSnapshot.toLedgerMovement() = getString("ledgerId")?.let {
        LedgerMovement(
            id = id,
            ledgerId = it,
            type = getString("type") ?: "ADVANCE_REC",
            amount = getDouble("amount") ?: 0.0,
            note = getString("note") ?: "",
            timestamp = getLong("timestamp") ?: 0L,
            userId = getString("userId") ?: ""
        )
    }

    private fun DocumentSnapshot.toWebLink() = getString("title")?.let {
        WebLink(
            id = id,
            title = it,
            url = getString("url") ?: "",
            isCustom = getBoolean("isCustom") ?: true,
            timestamp = getLong("timestamp") ?: 0L
        )
    }

    // --- DATABASE API ACCESSORS (FLOWS) ---
    val allNotes: Flow<List<Note>> = db.noteDao().getAllNotes()
    val allReminders: Flow<List<Reminder>> = db.reminderDao().getAllReminders()
    val allHabits: Flow<List<Habit>> = db.habitDao().getAllHabits()
    val allAccounts: Flow<List<Account>> = db.accountDao().getAllAccounts()
    val allTransactions: Flow<List<FinanceTransaction>> = db.financeTransactionDao().getAllTransactions()
    val allNetWorthItems: Flow<List<NetWorthItem>> = db.netWorthItemDao().getAllNetWorthItems()
    val allLedgers: Flow<List<AdvancedLedger>> = db.advancedLedgerDao().getAllLedgers()
    val allWebLinks: Flow<List<WebLink>> = db.webLinkDao().getAllWebLinks()

    fun getMovementsForLedger(ledgerId: String): Flow<List<LedgerMovement>> =
        db.ledgerMovementDao().getMovementsForLedger(ledgerId)

    // --- MUTATION ACTIONS (SAVES LOCALLY + ATOMIC FIRESTORE TRANSACTIONS) ---

    private fun getUserId(): String = currentUserId.value ?: "guest"

    private suspend fun saveItem(
        localSave: suspend () -> Unit,
        remotePath: String,
        itemId: String,
        remoteMap: Map<String, Any>
    ) {
        // Always save locally first so the offline cache works immediately
        localSave()

        // If authenticated, sync with Firestore securely
        val uid = currentUserId.value
        val fs = firestore
        if (uid != null && fs != null) {
            try {
                fs.collection("users").document(uid)
                    .collection(remotePath).document(itemId)
                    .set(remoteMap).await()
            } catch (e: Exception) {
                Log.e("RepositorySave", "Failed remote sync: ${e.message}")
            }
        }
    }

    private suspend fun deleteRemoteItem(remotePath: String, itemId: String) {
        val uid = currentUserId.value
        val fs = firestore
        if (uid != null && fs != null) {
            try {
                fs.collection("users").document(uid)
                    .collection(remotePath).document(itemId)
                    .delete().await()
            } catch (e: Exception) {
                Log.e("RepositoryDelete", "Failed remote delete: ${e.message}")
            }
        }
    }

    // NOTES
    suspend fun saveNote(note: Note) {
        val finalNote = note.copy(userId = getUserId())
        val map = mapOf(
            "title" to finalNote.title,
            "content" to finalNote.content,
            "category" to finalNote.category,
            "date" to finalNote.date,
            "isPinned" to finalNote.isPinned,
            "timestamp" to finalNote.timestamp,
            "userId" to finalNote.userId
        )
        saveItem({ db.noteDao().insertNote(finalNote) }, "notes", finalNote.id, map)
        FirebaseServices.trackEvent(context, "note_created", mapOf("noteId" to finalNote.id, "category" to finalNote.category))
    }

    suspend fun deleteNote(id: String) {
        db.noteDao().deleteNoteById(id)
        deleteRemoteItem("notes", id)
    }

    // REMINDERS
    suspend fun saveReminder(reminder: Reminder) {
        val finalReminder = reminder.copy(userId = getUserId())
        val map = mapOf(
            "title" to finalReminder.title,
            "date" to finalReminder.date,
            "time" to finalReminder.time,
            "repeatType" to finalReminder.repeatType,
            "isDone" to finalReminder.isDone,
            "timestamp" to finalReminder.timestamp,
            "userId" to finalReminder.userId
        )
        saveItem({ db.reminderDao().insertReminder(finalReminder) }, "reminders", finalReminder.id, map)
        FirebaseServices.trackEvent(context, "reminder_created", mapOf("reminderId" to finalReminder.id))
    }

    suspend fun deleteReminder(id: String) {
        db.reminderDao().deleteReminderById(id)
        deleteRemoteItem("reminders", id)
    }

    // HABITS
    suspend fun saveHabit(habit: Habit) {
        val finalHabit = habit.copy(userId = getUserId())
        val map = mapOf(
            "name" to finalHabit.name,
            "history" to finalHabit.history,
            "streak" to finalHabit.streak,
            "completionRate" to finalHabit.completionRate,
            "timestamp" to finalHabit.timestamp,
            "userId" to finalHabit.userId
        )
        saveItem({ db.habitDao().insertHabit(finalHabit) }, "habits", finalHabit.id, map)
        FirebaseServices.trackEvent(context, "habit_completed", mapOf("habitId" to finalHabit.id, "streak" to finalHabit.streak))
    }

    suspend fun deleteHabit(id: String) {
        db.habitDao().deleteHabitById(id)
        deleteRemoteItem("habits", id)
    }

    // WEBLINKS
    suspend fun saveWebLink(link: WebLink) {
        val map = mapOf(
            "title" to link.title,
            "url" to link.url,
            "isCustom" to link.isCustom,
            "timestamp" to link.timestamp
        )
        saveItem({ db.webLinkDao().insertWebLink(link) }, "webLinks", link.id, map)
    }

    suspend fun deleteWebLink(id: String) {
        db.webLinkDao().deleteWebLinkById(id)
        deleteRemoteItem("webLinks", id)
    }

    // NETWORTH ITEMS
    suspend fun saveNetWorthItem(item: NetWorthItem) {
        val finalItem = item.copy(userId = getUserId())
        val map = mapOf(
            "name" to finalItem.name,
            "type" to finalItem.type,
            "amount" to finalItem.amount,
            "confidence" to finalItem.confidence,
            "timestamp" to finalItem.timestamp,
            "userId" to finalItem.userId
        )
        saveItem({ db.netWorthItemDao().insertNetWorthItem(finalItem) }, "netWorthItems", finalItem.id, map)
    }

    suspend fun deleteNetWorthItem(id: String) {
        db.netWorthItemDao().deleteNetWorthItemById(id)
        deleteRemoteItem("netWorthItems", id)
    }

    // --- FINANCE MUTATION ACTIONS (WITH ATOMIC BALANCING & REVERSAL LAWS) ---

    // Standard Multi-Bank Accounts operations
    suspend fun saveAccount(account: Account) {
        val finalAcc = account.copy(userId = getUserId())
        val map = mapOf(
            "name" to finalAcc.name,
            "type" to finalAcc.type,
            "balance" to finalAcc.balance,
            "timestamp" to finalAcc.timestamp,
            "userId" to finalAcc.userId
        )
        saveItem({ db.accountDao().insertAccount(finalAcc) }, "accounts", finalAcc.id, map)
    }

    suspend fun deleteAccountCompletely(id: String) {
        db.accountDao().deleteAccountById(id)
        deleteRemoteItem("accounts", id)
    }

    // Reversal entry pattern is strictly required: Instead of hard deletion of finance
    // transactions, we insert a REVERSAL entry to negate the transaction!
    suspend fun registerFinanceTransaction(tx: FinanceTransaction) {
        val currentUid = getUserId()
        val finalTx = tx.copy(userId = currentUid)

        // Perform atomic update on account balance locally
        val account = db.accountDao().getAccountById(tx.accountId)
        if (account != null) {
            val balanceDiff = when (tx.type) {
                "INCOME" -> tx.amount
                "EXPENSE" -> -tx.amount
                "REVERSAL" -> -tx.amount // Negates previous income, or flips previous expense if added as reversal
                else -> 0.0
            }
            val updatedAccount = account.copy(balance = account.balance + balanceDiff)
            db.accountDao().insertAccount(updatedAccount)

            // Sync account balance updates to Firestore
            saveAccount(updatedAccount)
        }

        // Save transaction
        val txMap = mapOf(
            "type" to finalTx.type,
            "category" to finalTx.category,
            "amount" to finalTx.amount,
            "date" to finalTx.date,
            "note" to finalTx.note,
            "accountId" to finalTx.accountId,
            "timestamp" to finalTx.timestamp,
            "userId" to finalTx.userId
        )
        saveItem({ db.financeTransactionDao().insertTransaction(finalTx) }, "financeTransactions", finalTx.id, txMap)
        if (finalTx.type == "EXPENSE") {
            FirebaseServices.trackEvent(context, "expense_added", mapOf("amount" to finalTx.amount, "category" to finalTx.category))
        }
    }

    // Reverse Transaction
    suspend fun reverseTransaction(originalTx: FinanceTransaction, note: String) {
        val reversalId = "rev_" + originalTx.id + "_" + UUID.randomUUID().toString().take(4)
        val reversalType = "REVERSAL"
        // If reversing INCOME, account balance decreases. So reversal amount is treated as subtraction.
        // If reversing EXPENSE, account balance increases. So we register a reversal with negated effect.
        // To be safe, let's create a perfect balancing transaction:
        val reversalTx = FinanceTransaction(
            id = reversalId,
            type = reversalType,
            category = "Reversal: ${originalTx.category}",
            amount = if (originalTx.type == "INCOME") originalTx.amount else -originalTx.amount,
            date = originalTx.date,
            note = "${note} (Reversed Transaction ID: ${originalTx.id})",
            accountId = originalTx.accountId,
            timestamp = System.currentTimeMillis(),
            userId = getUserId()
        )
        registerFinanceTransaction(reversalTx)
    }


    // --- ADVANCED LEDGER & LAWS ENGINE ---

    suspend fun createLedger(contactName: String, projectName: String, linkedAccountId: String, confidence: String): AdvancedLedger {
        val ledgerId = UUID.randomUUID().toString()
        val ledger = AdvancedLedger(
            id = ledgerId,
            contactName = contactName,
            projectName = projectName,
            linkedAccountId = linkedAccountId,
            advanceReceived = 0.0,
            costAmount = 0.0,
            workCompletedAmount = 0.0,
            refundAmount = 0.0,
            payableBalance = 0.0,
            receivableBalance = 0.0,
            status = "ACTIVE",
            confidence = confidence,
            timestamp = System.currentTimeMillis(),
            userId = getUserId()
        )
        saveLedgerWithMovement(ledger, "INITIAL", 0.0, "Project initial ledger created.")
        return ledger
    }

    // Atomic Helper
    private suspend fun saveLedgerValues(ledger: AdvancedLedger) {
        val finalLedger = ledger.copy(userId = getUserId())
        val map = mapOf(
            "contactName" to finalLedger.contactName,
            "projectName" to finalLedger.projectName,
            "linkedAccountId" to finalLedger.linkedAccountId,
            "advanceReceived" to finalLedger.advanceReceived,
            "costAmount" to finalLedger.costAmount,
            "workCompletedAmount" to finalLedger.workCompletedAmount,
            "refundAmount" to finalLedger.refundAmount,
            "payableBalance" to finalLedger.payableBalance,
            "receivableBalance" to finalLedger.receivableBalance,
            "status" to finalLedger.status,
            "confidence" to finalLedger.confidence,
            "timestamp" to finalLedger.timestamp,
            "userId" to finalLedger.userId
        )
        saveItem({ db.advancedLedgerDao().insertLedger(finalLedger) }, "advanceLedgers", finalLedger.id, map)
        if (finalLedger.payableBalance > 0.0) {
            FirebaseServices.trackEvent(context, "payable_added", mapOf("payableBalance" to finalLedger.payableBalance))
        }
    }

    private suspend fun saveLedgerWithMovement(
        ledger: AdvancedLedger,
        movementType: String,
        amount: Double,
        note: String
    ) {
        // Save the ledger
        saveLedgerValues(ledger)

        // Generate movement record
        val mId = UUID.randomUUID().toString()
        val movement = LedgerMovement(
            id = mId,
            ledgerId = ledger.id,
            type = movementType,
            amount = amount,
            note = note,
            timestamp = System.currentTimeMillis(),
            userId = getUserId()
        )

        // Save movement
        val map = mapOf(
            "ledgerId" to movement.ledgerId,
            "type" to movement.type,
            "amount" to movement.amount,
            "note" to movement.note,
            "timestamp" to movement.timestamp,
            "userId" to movement.userId
        )
        saveItem({ db.ledgerMovementDao().insertMovement(movement) }, "ledgerMovements", movement.id, map)
    }

    /**
     * Rules implementation:
     * - "Advance received increases cash/bank but also creates payable liability. Advance received is not profit!"
     * - "Do not double count advance as income."
     * - "Cost/work/settlement reduces payable"
     * - "If payable becomes negative, it becomes receivable"
     */
    suspend fun addAdvanceReceived(ledgerId: String, amount: Double, note: String) {
        if (amount <= 0.0) return
        val ledger = db.advancedLedgerDao().getLedgerById(ledgerId) ?: return

        // 1. Advance received increases bank/cash account
        val account = db.accountDao().getAccountById(ledger.linkedAccountId)
        if (account != null) {
            val updatedAccount = account.copy(balance = account.balance + amount)
            saveAccount(updatedAccount)

            // Register special transaction to reflect financial input without double-counting as a core profit category (we category it as "Advance Liability")
            val transId = "adv_" + ledgerId + "_" + UUID.randomUUID().toString().take(4)
            val tx = FinanceTransaction(
                id = transId,
                type = "INCOME",
                category = "Advance Liability",
                amount = amount,
                date = getCurrentFormattedDate(),
                note = "Advance received on ledger for ${ledger.contactName}: ${note}",
                accountId = ledger.linkedAccountId,
                timestamp = System.currentTimeMillis(),
                userId = getUserId()
            )
            // Save transaction locally/remotely (this increments account but the Category "Advance Liability" will be filtered out/shown separately from real operational Income profit!)
            db.financeTransactionDao().insertTransaction(tx)
            val txMap = mapOf(
                "type" to tx.type,
                "category" to tx.category,
                "amount" to tx.amount,
                "date" to tx.date,
                "note" to tx.note,
                "accountId" to tx.accountId,
                "timestamp" to tx.timestamp,
                "userId" to tx.userId
            )
            saveItem({ db.financeTransactionDao().insertTransaction(tx) }, "financeTransactions", tx.id, txMap)
        }

        // 2. Recalculate balances: advance received increases payable balance (liability)
        val newAdvanceTotal = ledger.advanceReceived + amount
        val initialPayable = ledger.payableBalance + amount
        val finalPayable: Double
        val finalReceivable: Double

        if (initialPayable < 0.0) {
            finalReceivable = -initialPayable
            finalPayable = 0.0
        } else {
            finalPayable = initialPayable
            finalReceivable = 0.0
        }

        val updatedLedger = ledger.copy(
            advanceReceived = newAdvanceTotal,
            payableBalance = finalPayable,
            receivableBalance = finalReceivable,
            status = if (finalPayable == 0.0 && finalReceivable == 0.0) "SETTLED" else "ACTIVE"
        )

        saveLedgerWithMovement(updatedLedger, "ADVANCE_REC", amount, "Advance liability received: $note")
    }

    suspend fun addCostAmount(ledgerId: String, amount: Double, note: String) {
        if (amount <= 0.0) return
        val ledger = db.advancedLedgerDao().getLedgerById(ledgerId) ?: return

        // Cost or work added reduces our payable liability
        val newCostTotal = ledger.costAmount + amount
        val initialPayable = ledger.payableBalance - amount
        val finalPayable: Double
        val finalReceivable: Double

        if (initialPayable < 0.0) {
            finalReceivable = -initialPayable
            finalPayable = 0.0
        } else {
            finalPayable = initialPayable
            finalReceivable = 0.0
        }

        val updatedLedger = ledger.copy(
            costAmount = newCostTotal,
            payableBalance = finalPayable,
            receivableBalance = finalReceivable,
            status = if (finalPayable == 0.0 && finalReceivable == 0.0) "SETTLED" else "ACTIVE"
        )

        saveLedgerWithMovement(updatedLedger, "COST_ADDED", amount, "Cost/Work settlement cost added: $note")
    }

    suspend fun addWorkCompleted(ledgerId: String, amount: Double, note: String) {
        if (amount <= 0.0) return
        val ledger = db.advancedLedgerDao().getLedgerById(ledgerId) ?: return

        val newWorkTotal = ledger.workCompletedAmount + amount
        val updatedLedger = ledger.copy(
            workCompletedAmount = newWorkTotal
        )

        saveLedgerWithMovement(updatedLedger, "WORK_COMPLETED", amount, "Work completion verification: $note")
    }

    suspend fun addRefundAmount(ledgerId: String, amount: Double, note: String) {
        if (amount <= 0.0) return
        val ledger = db.advancedLedgerDao().getLedgerById(ledgerId) ?: return

        // Refund happens:
        // 1. Decrements bank/cash account
        val account = db.accountDao().getAccountById(ledger.linkedAccountId)
        if (account != null) {
            val updatedAccount = account.copy(balance = account.balance - amount)
            saveAccount(updatedAccount)

            // Register expense transaction to reflect actual payout
            val transId = "pay_" + ledgerId + "_" + UUID.randomUUID().toString().take(4)
            val tx = FinanceTransaction(
                id = transId,
                type = "EXPENSE",
                category = "Refund Release",
                amount = amount,
                date = getCurrentFormattedDate(),
                note = "Refund paid on ledger ${ledger.contactName}: ${note}",
                accountId = ledger.linkedAccountId,
                timestamp = System.currentTimeMillis(),
                userId = getUserId()
            )
            val txMap = mapOf(
                "type" to tx.type,
                "category" to tx.category,
                "amount" to tx.amount,
                "date" to tx.date,
                "note" to tx.note,
                "accountId" to tx.accountId,
                "timestamp" to tx.timestamp,
                "userId" to tx.userId
            )
            saveItem({ db.financeTransactionDao().insertTransaction(tx) }, "financeTransactions", tx.id, txMap)
        }

        // 2. Refund reduces bank cash and reduces payable liability balance
        val newRefundTotal = ledger.refundAmount + amount
        val initialPayable = ledger.payableBalance - amount
        val finalPayable: Double
        val finalReceivable: Double

        if (initialPayable < 0.0) {
            finalReceivable = -initialPayable
            finalPayable = 0.0
        } else {
            finalPayable = initialPayable
            finalReceivable = 0.0
        }

        val updatedLedger = ledger.copy(
            refundAmount = newRefundTotal,
            payableBalance = finalPayable,
            receivableBalance = finalReceivable,
            status = if (finalPayable == 0.0 && finalReceivable == 0.0) "SETTLED" else "ACTIVE"
        )

        saveLedgerWithMovement(updatedLedger, "REFUND", amount, "Liability settlement refund: $note")
    }

    suspend fun settleLedger(ledgerId: String, note: String) {
        val ledger = db.advancedLedgerDao().getLedgerById(ledgerId) ?: return
        val updatedLedger = ledger.copy(
            payableBalance = 0.0,
            receivableBalance = 0.0,
            status = "SETTLED"
        )
        saveLedgerWithMovement(updatedLedger, "SETTLEMENT", 0.0, "Manual ledger reconciliation settlement: $note")
    }

    suspend fun deleteLedger(id: String) {
        db.advancedLedgerDao().deleteLedgerById(id)
        db.ledgerMovementDao().deleteMovementsForLedger(id)
        deleteRemoteItem("advanceLedgers", id)
    }

    // --- GUEST CONVERT / DATA MIGRATION ENGINE ---

    suspend fun migrateGuestDataToUser(targetUid: String) {
        try {
            val fs = firestore ?: return

            // 1. NOTES
            val notes = db.noteDao().getAllNotes().first()
            notes.forEach { note ->
                val updatedNote = note.copy(userId = targetUid)
                db.noteDao().insertNote(updatedNote)
                fs.collection("users").document(targetUid).collection("notes")
                    .document(updatedNote.id).set(updatedNote).await()
            }

            // 2. REMINDERS
            val reminders = db.reminderDao().getAllReminders().first()
            reminders.forEach { reminder ->
                val updated = reminder.copy(userId = targetUid)
                db.reminderDao().insertReminder(updated)
                fs.collection("users").document(targetUid).collection("reminders")
                    .document(updated.id).set(updated).await()
            }

            // 3. HABITS
            val habits = db.habitDao().getAllHabits().first()
            habits.forEach { habit ->
                val updated = habit.copy(userId = targetUid)
                db.habitDao().insertHabit(updated)
                fs.collection("users").document(targetUid).collection("habits")
                    .document(updated.id).set(updated).await()
            }

            // 4. ACCOUNTS
            val accounts = db.accountDao().getAllAccounts().first()
            accounts.forEach { acc ->
                val updated = acc.copy(userId = targetUid)
                db.accountDao().insertAccount(updated)
                fs.collection("users").document(targetUid).collection("accounts")
                    .document(updated.id).set(updated).await()
            }

            // 5. TRANSACTIONS
            val txs = db.financeTransactionDao().getAllTransactions().first()
            txs.forEach { tx ->
                val updated = tx.copy(userId = targetUid)
                db.financeTransactionDao().insertTransaction(updated)
                fs.collection("users").document(targetUid).collection("financeTransactions")
                    .document(updated.id).set(updated).await()
            }

            // 6. NETWORTH ELEMENTS
            val nwItems = db.netWorthItemDao().getAllNetWorthItems().first()
            nwItems.forEach { item ->
                val updated = item.copy(userId = targetUid)
                db.netWorthItemDao().insertNetWorthItem(updated)
                fs.collection("users").document(targetUid).collection("netWorthItems")
                    .document(updated.id).set(updated).await()
            }

            // 7. LEDGERS
            val ledgers = db.advancedLedgerDao().getAllLedgers().first()
            ledgers.forEach { ledger ->
                val updated = ledger.copy(userId = targetUid)
                db.advancedLedgerDao().insertLedger(updated)
                fs.collection("users").document(targetUid).collection("advanceLedgers")
                    .document(updated.id).set(updated).await()
            }

            // 8. MOVEMENTS
            val movements = db.ledgerMovementDao().getAllMovements().first()
            movements.forEach { m ->
                val updated = m.copy(userId = targetUid)
                db.ledgerMovementDao().insertMovement(updated)
                fs.collection("users").document(targetUid).collection("ledgerMovements")
                    .document(m.id).set(updated).await()
            }

            // Clear database guest items
            Log.d("MigrationSync", "Guest data successfully synchronized and migrated to user $targetUid")
        } catch (e: Exception) {
            Log.e("MigrationSync", "Failed migrating guest data: ${e.message}")
        }
    }

    private fun getCurrentFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }
}
