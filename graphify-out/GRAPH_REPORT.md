# Graph Report - Expense Tracker  (2026-09-26)

## Corpus Check
- 71 files · ~44,231 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 826 nodes · 1100 edges · 52 communities (37 shown, 15 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 93 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `e6a394f5`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]

## God Nodes (most connected - your core abstractions)
1. `IntentParserTest` - 38 edges
2. `TransactionRepository` - 29 edges
3. `ReceiptParserTest` - 26 edges
4. `AddTransactionViewModel` - 24 edges
5. `UpiSyncService` - 20 edges
6. `AppNavHost()` - 18 edges
7. `AddTransactionScreen()` - 16 edges
8. `ReceiptParser` - 16 edges
9. `UpiSyncScreen()` - 14 edges
10. `TransactionDao` - 14 edges

## Surprising Connections (you probably didn't know these)
- `AppNavHost()` --calls--> `DashboardScreen()`  [INFERRED]
  app/src/main/java/com/expensetracker/ui/navigation/Navigation.kt → app/src/main/java/com/expensetracker/ui/screens/dashboard/DashboardScreen.kt
- `AppNavHost()` --calls--> `TransactionsScreen()`  [INFERRED]
  app/src/main/java/com/expensetracker/ui/navigation/Navigation.kt → app/src/main/java/com/expensetracker/ui/screens/transaction/TransactionsScreen.kt
- `AppNavHost()` --calls--> `AnalyticsScreen()`  [INFERRED]
  app/src/main/java/com/expensetracker/ui/navigation/Navigation.kt → app/src/main/java/com/expensetracker/ui/screens/analytics/AnalyticsScreen.kt
- `AppNavHost()` --calls--> `BudgetsScreen()`  [INFERRED]
  app/src/main/java/com/expensetracker/ui/navigation/Navigation.kt → app/src/main/java/com/expensetracker/ui/screens/budgets/BudgetsScreen.kt
- `AppNavHost()` --calls--> `SettingsScreen()`  [INFERRED]
  app/src/main/java/com/expensetracker/ui/navigation/Navigation.kt → app/src/main/java/com/expensetracker/ui/screens/settings/SettingsScreen.kt

## Communities (52 total, 15 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.07
Nodes (48): AnalyticsInsightCard(), AnalyticsScreen(), AnimatedBarChart(), CategoryItem(), ChartPoint, DonutChartSection(), PeriodSelector(), QuickPill() (+40 more)

### Community 1 - "Community 1"
Cohesion: 0.05
Nodes (11): RecurringRule, ReceiptOcrService, Factory, RecurringUiState, RecurringViewModel, RecurringRuleRepository, AddTransactionUiState, AddTransactionViewModel (+3 more)

### Community 2 - "Community 2"
Cohesion: 0.05
Nodes (6): BudgetDao, CategoryDao, CategoryTotal, CurrencyRateDao, RecurringRuleDao, TransactionDao

### Community 4 - "Community 4"
Cohesion: 0.06
Nodes (35): ChatbotIntent, CompareCategories, CompareLast30Days, CompareLast7Days, CompareMonths, CompareSamePeriodLastMonth, CompareSamePeriodLastYear, CompareWeeks (+27 more)

### Community 5 - "Community 5"
Cohesion: 0.09
Nodes (29): ChatbotScreen(), MessageBubble(), FileSelectionView(), ImportPreview(), ImportScreen(), readTextLimited(), TransactionPreviewItem(), AddTransaction (+21 more)

### Community 6 - "Community 6"
Cohesion: 0.08
Nodes (14): BudgetEngine, BudgetStatus, Budget, BudgetPeriod, Category, CurrencyRate, RecurringFrequency, UserPreference (+6 more)

### Community 7 - "Community 7"
Cohesion: 0.1
Nodes (3): SyncedTransaction, UpiSyncService, UpiSyncWorker

### Community 9 - "Community 9"
Cohesion: 0.14
Nodes (9): AmountCandidate, DateExtraction, ReceiptParser, TotalLabel, Failure, NoText, ReceiptOcrResult, ReceiptScanResult (+1 more)

### Community 10 - "Community 10"
Cohesion: 0.16
Nodes (8): ChatbotUiState, ChatbotViewModel, ChatMessage, Factory, InsightSeverity, InsightType, SpendingInsight, SpendingInsightsService

### Community 11 - "Community 11"
Cohesion: 0.12
Nodes (9): Factory, ImportData, ImportUiState, ImportViewModel, Error, Loading, Result, Success (+1 more)

### Community 12 - "Community 12"
Cohesion: 0.18
Nodes (9): EmptySyncView(), ErrorView(), Factory, PermissionRequestView(), SyncTransactionList(), TransactionSyncItem(), UpiSyncScreen(), UpiSyncUiState (+1 more)

### Community 13 - "Community 13"
Cohesion: 0.14
Nodes (6): DailyTotal, RecurringEngine, ReportData, ReportEngine, ReportPeriod, Transaction

### Community 14 - "Community 14"
Cohesion: 0.2
Nodes (18): AddTransactionScreen(), AmountInput(), CategoryCard(), CategorySection(), createReceiptImageFile(), CurrencyPickerDialog(), DatePickerDialogContent(), ErrorMessage() (+10 more)

### Community 15 - "Community 15"
Cohesion: 0.16
Nodes (8): Intent, Interaction Contract, Scope, UI-SPEC, Visual Contract, ExportService, ReportsScreen(), SummaryRow()

### Community 17 - "Community 17"
Cohesion: 0.11
Nodes (18): 1. 📬 Automated UPI SMS Syncing, 2. 📊 Rich Charts & Analytics, 3. 🎯 Budgets & Threshold Alerts, 4. 🔄 Subscriptions & Recurring Outflows, 5. 💱 Multi-Currency Support, 6. 🧾 Receipt OCR Scanning, 📂 Architecture, Build & Run (+10 more)

### Community 19 - "Community 19"
Cohesion: 0.19
Nodes (3): UpiSmsParser, UpiSmsResult, UpiTransactionType

### Community 20 - "Community 20"
Cohesion: 0.19
Nodes (6): Error, ExportResult, Factory, ReportsUiState, ReportsViewModel, Success

### Community 21 - "Community 21"
Cohesion: 0.24
Nodes (4): CsvParser, ImportResult, ParsedRow, TransactionType

### Community 22 - "Community 22"
Cohesion: 0.28
Nodes (3): DatasetRecord, FieldFailure, ReceiptDatasetEvaluationTest

### Community 25 - "Community 25"
Cohesion: 0.15
Nodes (12): Assumptions, Constraints, Core Features, Expense Tracker Android App PRD, In Scope, MVP Scope, Out of Scope, Problem Statement (+4 more)

### Community 26 - "Community 26"
Cohesion: 0.17
Nodes (5): AddTransactionUseCase, DeleteTransactionUseCase, GetMonthlyTransactionsUseCase, GetTransactionsUseCase, UpdateTransactionUseCase

### Community 27 - "Community 27"
Cohesion: 0.17
Nodes (11): Delivery Milestones, Implementation Plan, Milestone 1, Milestone 2, Milestone 3, Milestone 4, Phase 1: Foundation, Phase 2: Core Experience (+3 more)

### Community 28 - "Community 28"
Cohesion: 0.27
Nodes (5): AnalyticsUiState, AnalyticsViewModel, DailySpending, Factory, SpendTrend

### Community 29 - "Community 29"
Cohesion: 0.18
Nodes (10): Add Expense, App Flow, Export Data, Key User Journeys, Manage Budget, Navigation Model, Primary Flow, Review Spending (+2 more)

### Community 30 - "Community 30"
Cohesion: 0.27
Nodes (3): BudgetsUiState, BudgetsViewModel, Factory

### Community 32 - "Community 32"
Cohesion: 0.22
Nodes (8): Backend Structure, code:text (app/), Core Modules, Data Entities, Data Flow, Future Extension, Service Responsibilities, Suggested Package Layout

### Community 33 - "Community 33"
Cohesion: 0.22
Nodes (8): Architecture, Background Processing, Business Logic, Data Layer, Frontend, Recommended Libraries, Tech Stack, Why This Stack

### Community 35 - "Community 35"
Cohesion: 0.32
Nodes (3): Factory, TransactionListUiState, TransactionsViewModel

### Community 36 - "Community 36"
Cohesion: 0.32
Nodes (4): CurrencyInfo, CurrencyService, formatAmount(), getSymbol()

### Community 37 - "Community 37"
Cohesion: 0.25
Nodes (7): Anti-Patterns to Avoid, Component Guidance, Design Principles, Frontend Guidelines, Interaction Rules, UX Priorities, Visual Style

### Community 38 - "Community 38"
Cohesion: 0.33
Nodes (3): Factory, SettingsUiState, SettingsViewModel

### Community 39 - "Community 39"
Cohesion: 0.48
Nodes (5): CameraPreviewWithAnalysis(), MLKitImageAnalyzer, OcrScanScreen(), PermissionDeniedPermanentUI(), PermissionNotGrantedUI()

### Community 43 - "Community 43"
Cohesion: 0.47
Nodes (3): DashboardUiState, DashboardViewModel, Factory

## Knowledge Gaps
- **129 isolated node(s):** `Dashboard`, `Transactions`, `Analytics`, `Budgets`, `Settings` (+124 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **15 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TransactionRepository` connect `Community 16` to `Community 1`, `Community 35`, `Community 7`, `Community 10`, `Community 43`, `Community 12`, `Community 11`, `Community 20`, `Community 23`, `Community 28`, `Community 30`?**
  _High betweenness centrality (0.123) - this node is a cross-community bridge._
- **Why does `AppNavHost()` connect `Community 5` to `Community 0`, `Community 12`, `Community 14`, `Community 15`?**
  _High betweenness centrality (0.094) - this node is a cross-community bridge._
- **Why does `UpiSyncService` connect `Community 7` to `Community 12`?**
  _High betweenness centrality (0.064) - this node is a cross-community bridge._
- **Are the 10 inferred relationships involving `TransactionRepository` (e.g. with `.create()` and `.create()`) actually correct?**
  _`TransactionRepository` has 10 INFERRED edges - model-reasoned connections that need verification._
- **Are the 2 inferred relationships involving `UpiSyncService` (e.g. with `UpiSyncScreen()` and `.doWork()`) actually correct?**
  _`UpiSyncService` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Dashboard`, `Transactions`, `Analytics` to the rest of the system?**
  _129 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.07 - nodes in this community are weakly interconnected._