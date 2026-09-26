# 🪙 Expense Tracker (v1.0.3)

A modern, minimalist, and intelligent expense-tracking application built for Android using Kotlin and Jetpack Compose. Designed with a focus on calm, scannable aesthetics and offline-first automation, the app streamlines expense management using smart features like SMS transaction syncing, budgets, recurring expenses, and analytics.

---

## 🚀 Core Features

### 1. 📬 Automated UPI SMS Syncing
Automatically syncs transactions by parsing incoming transactional SMS messages (specifically tailored for Indian banking UPI formats).
* **First-Launch Setup Prompt:** On first app launch, users are guided to the UPI Auto-Sync setup screen if SMS permission has not been granted yet.
* **Local Parsing:** The `UpiSmsParser` uses regex patterns and keyword matchers to extract transaction type (debit/credit), amount, merchant, UPI ID, reference number, and date.
* **Background Sync:** Utilizing Jetpack **WorkManager**, a background worker (`UpiSyncWorker`) runs periodic sync cycles (every 6 hours) to automatically check new SMS notifications and log transactions.
* **Auto-Categorization:** Uses keyword heuristics (e.g., `swiggy` $\rightarrow$ "Food & Dining", `uber` $\rightarrow$ "Transportation") to group expenses.

### 2. 📊 Rich Charts & Analytics
Understand your spending patterns through clean, scannable visualizations.
* **Hand-drawn Charts:** The bar chart and category donut are drawn directly on a Compose `Canvas` (`AnimatedBarChart`, `DonutChartSection` in `AnalyticsScreen`) rather than through a charting library. This keeps the visuals on the same palette as the rest of the app and avoids a charting dependency.
* **Calm Reveals:** The total, then the bars, then the donut animate in sequence off shared `MotionTokens` so the screen resolves top-to-bottom instead of everything moving at once.
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

### 6. 🧾 Receipt OCR Scanning
Scan physical receipts and bills using your camera to automatically extract transaction details.
* **ML Kit Text Recognition:** Uses Google ML Kit's on-device text recognition engine (`TextRecognition`) to extract raw text from receipt images — fully offline, no data leaves the device.
* **Smart Receipt Parsing:** The `ReceiptParser` analyses the extracted text to identify the total amount, merchant name, transaction date, and currency. It handles a wide range of receipt formats including Indian POS layouts, GST invoices, and restaurant bills.
* **Intelligent Total Detection:** Prioritises labelled totals in order (`Grand Total` → `Amount Due` → `Payable` → `Net Total` → `Total`) with exclusion rules to avoid picking up tax subtotals, item counts, or GST lines.
* **Merchant & Date Extraction:** Scores candidate merchant name lines based on position, capitalisation, and business suffix keywords. Detects dates in multiple formats (`DD/MM/YYYY`, `DD-MMM-YYYY`, ISO, etc.) with ambiguity flagging.
* **Multi-Currency Detection:** Automatically detects currency from symbols and keywords (`₹`, `Rs.`, `INR`, `$`, `€`, `£`) and falls back to the user's base currency.

### 7. 🤖 On-Device Finance Assistant
Ask plain-language questions about your finances without sending your data to a server.
* **Natural-Language Queries:** A local `IntentParser` recognizes questions about spending by period or category, individual transactions, income, balances, budgets, averages, and comparisons.
* **Actionable Answers:** The assistant can surface recent, highest, lowest, and threshold-based expenses; spending summaries; budget status; and month-, week-, year-, category-, or rolling-period comparisons.
* **Private by Design:** `ChatbotViewModel` answers queries directly from the locally stored Room data and the user's DataStore preferences. The chatbot uses no remote AI service.

---

## 🎨 Design Philosophy (`UI-SPEC`)

The user interface follows a specialized design contract outlined in [UI-SPEC.md](UI-SPEC.md):
* **Palette:** Calm, warm off-white surfaces (`#FAF8F2` background, `#FFFCF7` surface), deep green primary (`#0F3D34`), muted secondary blue (`#345E7D`), soft gold accent (`#DFAF3F`), and restricted status colors.
* **Shapes:** Clean, compact 8dp rounded corner cards for layouts, reserving circles strictly for icons and floating action buttons.
* **Elevation & Density:** Uses subtle borders and tonal surface overlays instead of heavy drop shadows. High visual density organizes financial data efficiently and cleanly.
* **Typography:** Bold fonts reserved for key monetary figures and screen headers; otherwise uses medium weights to maintain a calm hierarchy.
* **Motion:** Every duration, curve and reveal stagger comes from `MotionTokens` (`ui/theme/Motion.kt`) — calm easing that starts unhurried and settles without a hard stop, matched durations within a single gesture, tweens rather than springs on size changes, and full respect for the system "remove animations" setting.

---

## 🛠️ Technology Stack

* **Language:** Kotlin (2.0.21)
* **UI Framework:** Jetpack Compose with the Kotlin Compose compiler plugin (2.0.21) and Material Design 3
* **Database:** Room (2.7.0) with Coroutines Flow for reactive updates
* **Local Settings:** Jetpack DataStore Preferences (1.0.0)
* **Background Processing:** Jetpack WorkManager (2.9.0)
* **Charting:** None — charts are drawn directly on a Compose `Canvas`. *(The `com.patrykandpatrick.vico` dependency is still declared in `app/build.gradle.kts` but is no longer referenced by any source file; it can be dropped.)*

---

## 📂 Architecture

The project is structured following clean coding guidelines and MVVM (Model-View-ViewModel):

```
app/src/main/java/com/expensetracker/
├── data/
│   ├── local/          # Room database and PreferencesManager (DataStore)
│   │   ├── dao/        # Room DAO declarations
│   │   └── datastore/  # DataStore-backed settings storage
│   ├── model/          # Room Entity definitions (Transaction, Budget, Category, etc.)
│   └── repository/     # Repositories facilitating data layer abstraction
├── domain/
│   ├── chatbot/        # Local natural-language intent parsing for the Finance Assistant
│   ├── engine/         # Heuristic engines (CategoryEngine, BudgetEngine, ReportEngine)
│   ├── model/          # Shared domain UI-state models and monetary helpers
│   └── util/           # Shared domain helpers (date handling)
├── services/
│   ├── alerts/         # Budget threshold warnings and notification services
│   ├── currency/       # Offline exchange rate caching and conversions
│   ├── export/         # CSV and file export services
│   ├── import/         # CSV parsing utilities
│   ├── insights/       # Automated spending insights generators
│   ├── receipt/        # On-device OCR and receipt-detail parsing
│   └── upisync/        # WorkManager background workers and SMS parsers
└── ui/
    ├── components/     # Reusable layout UI components and the scroll-aware blur scrim
    ├── navigation/     # Jetpack Compose navigation configuration
    ├── screens/        # Feature screens (Dashboard, Transactions, Analytics, Budgets,
    │                   #   Recurring, Import, Reports, Assistant, UPI Sync, Settings)
    └── theme/          # Material 3 color, typography, and the shared MotionTokens system
```

The Compose UI follows a unidirectional MVVM flow: screens observe `StateFlow` from feature ViewModels, ViewModels coordinate repositories, domain engines, and services, and repositories persist data through Room and DataStore. The Finance Assistant is a feature of this same flow: `ChatbotScreen` → `ChatbotViewModel` → local `IntentParser`, repositories, and `BudgetEngine`.

---

## 🔒 Permissions Used

To support its offline-automation capabilities, the app requests the following Android runtime permissions:
* `android.permission.READ_SMS`: Required to parse UPI transactional SMS.
* `android.permission.POST_NOTIFICATIONS`: Required for budget warnings and sync alerts on Android 13+ (API 33).
* `android.permission.CAMERA`: Required to scan receipts.

On first launch, the app shows a one-time prompt that sends users to **UPI Auto-Sync** to grant SMS access. Users can skip it and return later from Settings.

---

## 🚀 Getting Started

### Prerequisites
* **Android Studio:** Ladybug | 2024.2.1 or newer. (The project uses Android Gradle Plugin 8.7.3, which Jellyfish / 2024.1.1 cannot build.)
* **JDK:** Version 17.
* **SDK:** Compile SDK 35, target SDK 34, minimum SDK 26 (Android 8.0).
* **Gradle:** 9.2.1, supplied by the checked-in wrapper — no local Gradle install needed.

### Build & Run
1. Clone this repository:
   ```bash
   git clone https://github.com/areyypriyanshu/expense-tracker.git
   ```
2. Open the project in Android Studio.
3. Allow Gradle to sync and download all dependencies.
4. Connect an Android device (or launch an emulator).
5. Build and run the `:app` configuration.

From the project root, the same checks can be run from a terminal:

```bash
./gradlew assembleDebug
./gradlew test
```

> **Known failures:** `./gradlew test` currently reports 2 failing tests out of 63 — `ReceiptParserTest.doesNotAssociateUnrelatedTaxOrItemLinesWithMultilineTotal` and `ReceiptDatasetEvaluationTest.evaluateDataset`. Both are pre-existing receipt-parsing issues and are unrelated to the UI.

---

## 📄 License

This repository does not currently include a license file.
