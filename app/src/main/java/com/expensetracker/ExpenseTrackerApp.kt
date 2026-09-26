package com.expensetracker

import android.app.Application
import android.util.Log
import com.expensetracker.data.local.ExpenseDatabase
import com.expensetracker.data.local.datastore.PreferencesManager
import com.expensetracker.data.repository.*
import com.expensetracker.domain.engine.BudgetEngine
import com.expensetracker.services.alerts.AlertService
import com.expensetracker.services.currency.CurrencyService
import com.expensetracker.services.export.ExportService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ExpenseTrackerApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var initializationJob: Job? = null

    val database: ExpenseDatabase by lazy {
        ExpenseDatabase.getDatabase(this)
    }

    val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(this)
    }

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(database.transactionDao())
    }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepository(database.categoryDao())
    }

    val budgetRepository: BudgetRepository by lazy {
        BudgetRepository(database.budgetDao())
    }

    val recurringRuleRepository: RecurringRuleRepository by lazy {
        RecurringRuleRepository(database.recurringRuleDao())
    }

    val currencyRepository: CurrencyRepository by lazy {
        CurrencyRepository(database.currencyRateDao())
    }

    val currencyService: CurrencyService by lazy {
        CurrencyService(currencyRepository)
    }

    val budgetEngine: BudgetEngine by lazy {
        BudgetEngine(budgetRepository, transactionRepository)
    }

    val alertService: AlertService by lazy {
        AlertService(this)
    }

    val exportService: ExportService by lazy {
        ExportService(this)
    }

    override fun onCreate() {
        super.onCreate()
        
        initializationJob = applicationScope.launch(Dispatchers.IO) {
            initializeDefaultData()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        initializationJob?.cancel()
        applicationScope.cancel()
    }

    private suspend fun initializeDefaultData() {
        try {
            val categoryDao = database.categoryDao()
            val categories = categoryDao.getAllCategories().first()
            if (categories.isEmpty()) {
                val defaultCategories = listOf(
                    com.expensetracker.data.model.Category(name = "Food & Dining", icon = "restaurant", keywords = "restaurant,food,meal,lunch,dinner,breakfast,cafe,coffee,pizza,burger", isDefault = true),
                    com.expensetracker.data.model.Category(name = "Transportation", icon = "directions_car", keywords = "uber,lyft,gas,fuel,parking,bus,train,metro,taxi", isDefault = true),
                    com.expensetracker.data.model.Category(name = "Shopping", icon = "shopping_bag", keywords = "amazon,ebay,walmart,target,shop,store,mall,retail", isDefault = true),
                    com.expensetracker.data.model.Category(name = "Entertainment", icon = "movie", keywords = "netflix,spotify,movie,game,concert,ticket,streaming", isDefault = true),
                    com.expensetracker.data.model.Category(name = "Bills & Utilities", icon = "receipt", keywords = "electric,water,internet,phone,bill,utility,rent,insurance", isDefault = true),
                    com.expensetracker.data.model.Category(name = "Healthcare", icon = "local_hospital", keywords = "doctor,pharmacy,medicine,hospital,clinic,health", isDefault = true),
                    com.expensetracker.data.model.Category(name = "Education", icon = "school", keywords = "book,course,school,university,tution,online learning", isDefault = true),
                    com.expensetracker.data.model.Category(name = "Personal Care", icon = "spa", keywords = "gym,beauty,spa,barber,haircut,cosmetic", isDefault = true),
                    com.expensetracker.data.model.Category(name = "Travel", icon = "flight", keywords = "hotel,flight,airbnb,vacation,trip,travel", isDefault = true),
                    com.expensetracker.data.model.Category(name = "Groceries", icon = "local_grocery_store", keywords = "grocery,supermarket,whole foods,trader joe", isDefault = true),
                    com.expensetracker.data.model.Category(name = "Income", icon = "payments", keywords = "salary,freelance,income,payment,earning", isDefault = true),
                    com.expensetracker.data.model.Category(name = "Other", icon = "more_horiz", keywords = "", isDefault = true)
                )
                categoryDao.insertCategories(defaultCategories)
                Log.i(TAG, "Default categories initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize default data", e)
        }
    }

    companion object {
        private const val TAG = "ExpenseTrackerApp"
    }
}
