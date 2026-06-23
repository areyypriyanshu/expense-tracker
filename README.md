# 🪙 Expense Tracker

A modern, minimalist, and intelligent expense-tracking application built for Android using Kotlin and Jetpack Compose. Designed with a focus on calm, scannable aesthetics and offline-first automation, the app streamlines expense management using smart features like SMS transaction syncing, budgets, recurring expenses, and analytics.

---

## 🚀 Core Features

### 1. 📬 Automated UPI SMS Syncing
Automatically syncs transactions by parsing incoming transactional SMS messages (specifically tailored for Indian banking UPI formats).
* **Local Parsing:** The `UpiSmsParser` uses regex patterns and keyword matchers to extract transaction type (debit/credit), amount, merchant, UPI ID, reference number, and date.
* **Background Sync:** Utilizing Jetpack **WorkManager**, a background worker (`UpiSyncWorker`) runs periodic sync cycles (every 6 hours) to automatically check new SMS notifications and log transactions.
* **Auto-Categorization:** Uses keyword heuristics (e.g., `swiggy` $\rightarrow$ "Food & Dining", `uber` $\rightarrow$ "Transportation") to group expenses.

### 2. 📊 Rich Charts & Analytics
Understand your spending patterns through clean, interactive visualizations.
* **Vico Charting:** Integrates the Vico Compose charting library for seamless Material 3-styled bar graphs and line charts showing income vs. expense.
* **Category Breakdown:** Aggregated spending charts that show where money goes over specified date ranges.

### 3. 🎯 Budgets & Threshold Alerts
Maintain budget control with non-intrusive notifications.
* **Flexible Budgets:** Set spending limits for specific categories or overall monthly budgets.
* **Smart Alerting:** Monitors spending and triggers alerts via `AlertService` when spending exceeds a custom threshold (default: 80%).

### 4. 🔄 Subscriptions & Recurring Outflows
Keep track of periodic bills, utilities, and memberships.
* **Recurring Rules:** Define transactions that repeat daily, weekly, monthly, or yearly.
* **Automated Processing:** Automatically inserts pending recurring expenses when they become due.

### 5. 💱 Multi-Currency Support
Logs international expenses with double-entry caching.
* **Dynamic Conversions:** Records both the original currency and a converted amount in the user's base currency.
* **Local Cache:** Stores conversion rates (`CurrencyRate`) locally to ensure conversion works fully offline.

> Receipt scanning and voice input are paused for now. Their UI entry points, Android permissions, and OCR/camera/audio dependencies have been removed from the current build.

---

## 🎨 Design Philosophy (`UI-SPEC`)

The user interface follows a specialized design contract outlined in [UI-SPEC.md](file:///Users/mandal/Documents/Expense%20Tracker%20%28AG%29/Expense%20Tracker/UI-SPEC.md):
* **Palette:** Calm, warm off-white surfaces (`#FAFAF9`), deep green primary tones (`#1B4332`), muted secondary blue (`#2D6A4F`), soft gold accents, and restricted status colors.
* **Shapes:** Clean, compact 8dp rounded corner cards for layouts, reserving circles strictly for icons and floating action buttons.
* **Elevation & Density:** Uses subtle borders and tonal surface overlays instead of heavy drop shadows. High visual density organizes financial data efficiently and cleanly.
* **Typography:** Bold fonts reserved for key monetary figures and screen headers; otherwise uses medium weights to maintain a calm hierarchy.

---

## 🛠️ Technology Stack

* **Language:** Kotlin (2.0.0)
* **UI Framework:** Jetpack Compose (Compose Compiler 2.0.0) with Material Design 3
* **Database:** Room (2.7.0) with Coroutines Flow for reactive updates
* **Local Settings:** Jetpack DataStore Preferences (1.0.0)
* **Background Processing:** Jetpack WorkManager (2.9.0)
* **Charting:** Vico Compose (1.14.0)

---

## 📂 Architecture

The project is structured following clean coding guidelines and MVVM (Model-View-ViewModel):

```
app/src/main/java/com/expensetracker/
├── data/
│   ├── local/          # Room DB, Dao declarations, PreferencesManager (DataStore)
│   ├── model/          # Room Entity definitions (Transaction, Budget, Category, etc.)
│   └── repository/     # Repositories facilitating data layer abstraction
├── domain/
│   ├── engine/         # Heuristic Engines (CategoryEngine, BudgetEngine, ReportEngine)
│   ├── model/          # View State definitions and utilities
│   └── usecase/        # Domain business logic wrappers (Transaction & Budget Use Cases)
├── services/
│   ├── alerts/         # Budget threshold warnings and notification services
│   ├── currency/       # Offline exchange rate caching and conversions
│   ├── export/         # CSV and file export services
│   ├── import/         # CSV parsing utilities
│   ├── insights/       # Automated spending insights generators
│   └── upisync/        # WorkManager background workers and SMS parsers
└── ui/
    ├── components/     # Reusable layout UI components
    ├── navigation/     # Jetpack Compose navigation configuration
    ├── screens/        # Feature screens (Dashboard, Transactions, Analytics, UpiSync, etc.)
    └── theme/          # Material 3 typography, shapes, and color configurations
```

---

## 🔒 Permissions Used

To support its offline-automation capabilities, the app requests the following Android runtime permissions:
* `android.permission.READ_SMS`: Required to parse UPI transactional SMS.
* `android.permission.POST_NOTIFICATIONS`: Required for budget warnings and sync alerts on Android 13+ (API 33).

---

## 🚀 Getting Started

### Prerequisites
* **Android Studio:** Jellyfish | 2024.1.1 or newer.
* **JDK:** Version 17.
* **SDK:** Target SDK 34, Minimum SDK 26 (Android 8.0).

### Build & Run
1. Clone this repository:
   ```bash
   git clone https://github.com/your-username/expense-tracker.git
   ```
2. Open the project in Android Studio.
3. Allow Gradle to sync and download all dependencies.
4. Connect an Android device (or launch an emulator).
5. Build and run the `:app` configuration.

---

## 📄 License

This project is licensed under the Apache License 2.0. See the `LICENSE` file for details.
# expense-tracker
# expense-tracker
