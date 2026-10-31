package app.leftover.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.leftover.domain.ME
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.domain.Money
import app.leftover.ui.activity.AccountsScreen
import app.leftover.ui.activity.ActivityScreen
import app.leftover.ui.activity.AddAccountScreen
import app.leftover.data.db.AccountType
import app.leftover.ui.activity.CardScreen
import app.leftover.ui.activity.CashScreen
import app.leftover.ui.activity.TransactionDetailScreen
import app.leftover.ui.add.QuickAddScreen
import app.leftover.ui.add.VoiceScreen
import app.leftover.ui.home.AffordScreen
import app.leftover.ui.home.HomeScreen
import app.leftover.ui.home.LeftBreakdownScreen
import app.leftover.ui.insights.InsightsScreen
import app.leftover.ui.insights.RecapScreen
import app.leftover.ui.insights.ReportsScreen
import app.leftover.ui.insights.AskScreen
import app.leftover.ui.launch.LaunchScreen
import app.leftover.ui.onboarding.BalanceScreen
import app.leftover.ui.onboarding.FirstExpenseScreen
import app.leftover.ui.onboarding.OnboardingDraft
import app.leftover.ui.onboarding.RhythmScreen
import app.leftover.ui.onboarding.SetupDoneScreen
import app.leftover.ui.plan.AddBillScreen
import app.leftover.ui.plan.AddEmiScreen
import app.leftover.ui.plan.CheckInScreen
import app.leftover.ui.plan.TagDetailScreen
import app.leftover.ui.settings.RemindersScreen
import app.leftover.ui.plan.TagsScreen
import app.leftover.ui.plan.EmiDetailScreen
import app.leftover.ui.plan.AddGoalScreen
import app.leftover.ui.plan.BillsScreen
import app.leftover.ui.plan.BudgetsScreen
import app.leftover.ui.plan.CategoriesScreen
import app.leftover.ui.plan.EditLimitsScreen
import app.leftover.ui.plan.GoalDetailScreen
import app.leftover.ui.plan.GoalsScreen
import app.leftover.ui.plan.WishlistScreen
import app.leftover.ui.settings.AboutScreen
import app.leftover.ui.settings.AppearanceScreen
import app.leftover.ui.settings.DeleteDataScreen
import app.leftover.ui.settings.MoneyRhythmScreen
import app.leftover.ui.settings.PrivacyScreen
import app.leftover.ui.settings.SettingsActions
import app.leftover.ui.settings.SettingsScreen
import app.leftover.ui.split.AddPersonScreen
import app.leftover.ui.backup.BackupScreen
import app.leftover.ui.backup.ImportScreen
import app.leftover.ui.backup.PassphraseDialog
import app.leftover.ui.sms.MandatesScreen
import app.leftover.ui.sms.ReviewScreen
import app.leftover.ui.sms.RulesScreen
import app.leftover.ui.sms.SmsScreen
import app.leftover.ui.split.AddGroupScreen
import app.leftover.ui.split.GroupScreen
import app.leftover.ui.split.PersonScreen
import app.leftover.ui.split.SettleScreen
import app.leftover.ui.split.SplitBillScreen
import app.leftover.ui.split.SplitScreen
import app.leftover.ui.theme.Leftover

@Composable
fun AppNav(viewModel: AppViewModel) {
    val snapshot by viewModel.snapshot.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val nav = rememberNavController()
    val draft = remember { OnboardingDraft() }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var welcomeEncrypted by remember { mutableStateOf<String?>(null) }
    var passphraseError by remember { mutableStateOf<String?>(null) }
    var passphraseBusy by remember { mutableStateOf(false) }
    val c = Leftover.colors

    val data = snapshot
    if (data == null) {
        Box(Modifier.fillMaxSize().background(c.ground))
        return
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val pending by viewModel.pendingRoute.collectAsState()

    // Open a screen requested from a widget as soon as the opening animation and onboarding are out of the way.
    LaunchedEffect(pending, route) {
        val target = pending
        if (target != null && route != null && route != Routes.LAUNCH && !route.startsWith("onboarding") && data.settings.onboarded) {
            viewModel.requestRoute(null)
            nav.navigate(target) { launchSingleTop = true }
        }
    }
    val showBar = route in Routes.withBottomBar

    Box(Modifier.fillMaxSize().background(c.ground)) {
        Column(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = Routes.LAUNCH,
                modifier = Modifier.weight(1f),
                enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 14 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(240)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 14 } },
            ) {
                composable(Routes.LAUNCH) {
                    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) {
                            restoreError = null
                            viewModel.readAndRestore(uri) { outcome ->
                                when (outcome) {
                                    RestoreOutcome.Done -> nav.navigate(Routes.HOME) { popUpTo(Routes.LAUNCH) { inclusive = true } }
                                    is RestoreOutcome.NeedsPassphrase -> { welcomeEncrypted = outcome.text; passphraseError = null }
                                    is RestoreOutcome.Failed -> restoreError = outcome.message
                                }
                            }
                        }
                    }
                    LaunchScreen(
                        showWelcome = !data.settings.onboarded,
                        restoreError = restoreError,
                        onGetStarted = { nav.navigate(Routes.ONB_FIRST) },
                        onRestore = { pickBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        onFinished = { nav.navigate(Routes.HOME) { popUpTo(Routes.LAUNCH) { inclusive = true } } },
                    )
                }
                composable(Routes.ONB_FIRST) { FirstExpenseScreen(draft, onBack = { nav.popBackStack() }, onNext = { nav.navigate(Routes.ONB_RHYTHM) }) }
                composable(Routes.ONB_RHYTHM) { RhythmScreen(draft, onBack = { nav.popBackStack() }, onNext = { nav.navigate(Routes.ONB_BALANCE) }) }
                composable(Routes.ONB_BALANCE) { BalanceScreen(draft, onBack = { nav.popBackStack() }, onNext = { nav.navigate(Routes.ONB_DONE) }) }
                composable(Routes.ONB_DONE) {
                    SetupDoneScreen(draft, onFinish = { next ->
                        viewModel.completeOnboarding(draft.toAnswers()) {
                            nav.navigate(Routes.HOME) { popUpTo(Routes.LAUNCH) { inclusive = true } }
                            if (next != null) nav.navigate(next)
                        }
                    })
                }

                composable(Routes.HOME) {
                    HomeScreen(
                        snapshot = data,
                        onToggleHide = { viewModel.setHideBalances(!data.settings.hideBalances) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onOpenBills = { nav.switchTab(Routes.BILLS) },
                        onOpenBreakdown = { nav.navigate(Routes.LEFT) },
                        onOpenAfford = { nav.navigate(Routes.AFFORD) },
                        onOpenInsights = { nav.navigate(Routes.INSIGHTS) },
                        onOpenVoice = { nav.navigate(Routes.VOICE) },
                        onOpenGoals = { nav.switchTab(Routes.GOALS) },
                        onOpenActivity = { nav.switchTab(Routes.ACTIVITY) },
                        onOpenTransaction = { nav.navigate(Routes.tx(it)) },
                        onOpenReview = { nav.navigate(Routes.REVIEW) },
                        onOpenBackup = { nav.navigate(Routes.BACKUP) },
                        onAddBalance = { nav.navigate(Routes.addAccount()) },
                    )
                }
                composable(Routes.LEFT) {
                    LeftBreakdownScreen(data, onBack = { nav.popBackStack() }, onSetIncluded = { account, included -> viewModel.updateAccount(account.copy(includeInLeft = included)) })
                }
                composable(
                    Routes.ADD_PATTERN,
                    arguments = listOf(
                        navArgument("kind") { type = NavType.StringType; defaultValue = "Expense" },
                        navArgument("draft") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) { backStack ->
                    val draft = remember(backStack) { if (backStack.arguments?.getBoolean("draft") == true) viewModel.takeDraft() else null }
                    QuickAddScreen(
                        initialKind = backStack.arguments?.getString("kind") ?: "Expense",
                        draft = draft,
                        snapshot = data,
                        onClose = { nav.popBackStack() },
                        onSave = { tx, message, tags ->
                            viewModel.saveWithToast(tx, message.title, message.subtitle, tags)
                            nav.popBackStack()
                        },
                        onAddTag = { viewModel.addTag(it, "lavender", null) },
                        onAddCategory = { name, kind -> viewModel.addCategory(CategoryEntity(name = name, icon = "other", color = if (kind == CategoryKind.INCOME) "lime" else "grey", kind = kind)) },
                        onSaveTemplate = viewModel::addTemplate,
                        onDeleteTemplate = viewModel::deleteTemplate,
                    )
                }
                composable(Routes.VOICE) {
                    VoiceScreen(
                        snapshot = data,
                        onClose = { nav.popBackStack() },
                        onSave = { tx, message ->
                            viewModel.saveWithToast(tx, message.title, message.subtitle)
                            nav.popBackStack()
                        },
                    )
                }
                composable(Routes.AFFORD) {
                    AffordScreen(
                        snapshot = data,
                        onBack = { nav.popBackStack() },
                        onAddToWishlist = { wish -> viewModel.addWish(wish); nav.navigate(Routes.WISHLIST) { popUpTo(Routes.HOME) } },
                        onBought = { nav.navigate(Routes.ADD) { popUpTo(Routes.HOME) } },
                    )
                }

                composable(Routes.ACTIVITY) {
                    ActivityScreen(data, onOpenTransaction = { nav.navigate(Routes.tx(it)) }, onOpenAccounts = { nav.navigate(Routes.ACCOUNTS) }, onOpenInsights = { nav.navigate(Routes.INSIGHTS) }, onOpenImport = { nav.navigate(Routes.IMPORT_CSV) }, onOpenReview = { nav.navigate(Routes.REVIEW) },
                        onDelete = { tx -> viewModel.deleteWithUndo(tx, app.leftover.domain.TagTotals.tagsOf(tx.id, data.txTags)) })
                }
                composable(Routes.TX, arguments = listOf(navArgument("id") { type = NavType.LongType })) { backStack ->
                    TransactionDetailScreen(data, backStack.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() }, onUpdate = viewModel::updateTransaction, onDelete = viewModel::deleteTransaction, onSetTags = viewModel::setTransactionTags, onAttachReceipt = viewModel::attachReceipt)
                }
                composable(Routes.ACCOUNTS) { AccountsScreen(data, onBack = { nav.popBackStack() }, onAdd = { nav.navigate(Routes.addAccount()) }, onAddCash = { nav.navigate(Routes.addAccount("CASH")) }, onOpenCard = { nav.navigate(Routes.card(it)) }, onOpenCash = { nav.navigate(Routes.cash(it)) }, onOpenGoals = { nav.switchTab(Routes.GOALS) }) }
                composable(Routes.CASH, arguments = listOf(navArgument("id") { type = NavType.LongType })) { backStack ->
                    CashScreen(data, backStack.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() }, onAddTransaction = viewModel::addTransaction, onUpdateAccount = viewModel::updateAccount)
                }
                composable(Routes.CARD, arguments = listOf(navArgument("id") { type = NavType.LongType })) { backStack ->
                    CardScreen(data, backStack.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() }, onSave = viewModel::updateAccount, onAddTransaction = viewModel::addTransaction, onSavePerk = viewModel::saveCardPerk)
                }
                composable(Routes.ADD_ACCOUNT, arguments = listOf(navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null })) { backStack ->
                    val initial = backStack.arguments?.getString("type")?.let { runCatching { AccountType.valueOf(it) }.getOrNull() } ?: AccountType.BANK
                    AddAccountScreen(onClose = { nav.popBackStack() }, onSave = viewModel::addAccount, initialType = initial)
                }

                composable(Routes.PLAN) {
                    BudgetsScreen(
                        snapshot = data, onSelectTab = { nav.switchTab(it) }, onEditLimits = { nav.navigate(Routes.EDIT_LIMITS) }, onCategories = { nav.navigate(Routes.CATEGORIES) }, onOpenTags = { nav.navigate(Routes.TAGS) }, onOpenCheckIn = { nav.navigate(Routes.CHECKIN) },
                        onOpenBreakdown = { nav.navigate(Routes.LEFT) }, onToggleRollover = { viewModel.updateCategory(it.copy(rollover = !it.rollover)) },
                    )
                }
                composable(Routes.EDIT_LIMITS) { EditLimitsScreen(data, onClose = { nav.popBackStack() }, onSave = viewModel::updateCategories) }
                composable(Routes.CATEGORIES) { CategoriesScreen(data, onBack = { nav.popBackStack() }, onUpdate = viewModel::updateCategory, onAdd = viewModel::addCategory, onMerge = viewModel::mergeCategory) }
                composable(Routes.GOALS) { GoalsScreen(data, onSelectTab = { nav.switchTab(it) }, onOpenGoal = { nav.navigate(Routes.goal(it)) }, onAddGoal = { nav.navigate(Routes.ADD_GOAL) }) }
                composable(Routes.GOAL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { backStack ->
                    GoalDetailScreen(data, backStack.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() }, onUpdate = viewModel::updateGoal, onMoveMoney = viewModel::moveGoalMoney)
                }
                composable(Routes.ADD_GOAL) { AddGoalScreen(data, onClose = { nav.popBackStack() }, onSave = viewModel::addGoal) }
                composable(Routes.BILLS) {
                    BillsScreen(
                        data, onSelectTab = { nav.switchTab(it) }, onAddBill = { nav.navigate(Routes.ADD_BILL) },
                        onMarkPaid = { bill, due ->
                            val from = data.accounts.firstOrNull { it.id == bill.accountId } ?: data.accounts.firstOrNull()
                            if (from != null) viewModel.markBillPaid(bill, due, from.id)
                        },
                        onOpenEmi = { nav.navigate(Routes.emi(it)) }, onAddEmi = { nav.navigate(Routes.ADD_EMI) },
                        onMarkEmiPaid = { emi, due -> viewModel.markEmiPaid(emi, due, data.accounts.firstOrNull { it.type == app.leftover.data.db.AccountType.BANK }?.id) },
                        onKeepSubscription = viewModel::keepSubscription,
                    )
                }
                composable(Routes.CHECKIN) { CheckInScreen(data, onBack = { nav.popBackStack() }, onAddChallenge = viewModel::addChallenge, onDeleteChallenge = viewModel::deleteChallenge) }
                composable(Routes.REMINDERS) {
                    RemindersScreen(data, onBack = { nav.popBackStack() }, onBills = viewModel::setBillReminders, onPayday = viewModel::setPaydayReminder, onBudgets = viewModel::setBudgetAlerts, onWeekly = viewModel::setWeeklyCheckIn, onDaily = viewModel::setDailyLog, onBackup = viewModel::setBackupReminder)
                }
                composable(Routes.TAGS) { TagsScreen(data, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.tag(it)) }, onAdd = viewModel::addTag) }
                composable(Routes.TAG, arguments = listOf(navArgument("id") { type = NavType.LongType })) { backStack ->
                    TagDetailScreen(data, backStack.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() }, onUpdate = viewModel::updateTag, onDelete = viewModel::deleteTag, onOpenTransaction = { nav.navigate(Routes.tx(it)) })
                }
                composable(Routes.ADD_EMI) { AddEmiScreen(data, onClose = { nav.popBackStack() }, onSave = viewModel::addEmi) }
                composable(Routes.EMI, arguments = listOf(navArgument("id") { type = NavType.LongType })) { backStack ->
                    EmiDetailScreen(
                        data, backStack.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() },
                        onMarkPaid = { emi, due -> viewModel.markEmiPaid(emi, due, data.accounts.firstOrNull { it.type == app.leftover.data.db.AccountType.BANK }?.id) },
                        onDelete = viewModel::deleteEmi,
                    )
                }
                composable(Routes.ADD_BILL) { AddBillScreen(data, onClose = { nav.popBackStack() }, onSave = viewModel::saveBill) }
                composable(Routes.WISHLIST) {
                    WishlistScreen(
                        data, onSelectTab = { nav.switchTab(it) }, onAdd = viewModel::addWish, onResolve = viewModel::resolveWish,
                        onPostpone = viewModel::postponeWish, onBuy = { nav.navigate(Routes.ADD) }, onRestore = viewModel::restoreWish,
                    )
                }

                composable(Routes.SPLIT) {
                    SplitScreen(
                        data,
                        onSplitBill = { nav.navigate(Routes.splitBill()) },
                        onOpenPerson = { nav.navigate(Routes.person(it)) },
                        onOpenGroup = { nav.navigate(Routes.group(it)) },
                        onAddPerson = { nav.navigate(Routes.ADD_PERSON) },
                        onAddGroup = { nav.navigate(Routes.ADD_GROUP) },
                        onOpenInsights = { nav.navigate(Routes.INSIGHTS) },
                        onStopRepeat = viewModel::stopRecurringSplit,
                    )
                }
                composable(
                    Routes.SPLIT_BILL,
                    arguments = listOf(
                        navArgument("person") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("group") { type = NavType.LongType; defaultValue = -1L },
                    ),
                ) { backStack ->
                    SplitBillScreen(
                        snapshot = data,
                        initialPersonId = backStack.arguments?.getLong("person")?.takeIf { it > 0 },
                        initialGroupId = backStack.arguments?.getLong("group")?.takeIf { it > 0 },
                        onClose = { nav.popBackStack() },
                        onAddPerson = { nav.navigate(Routes.ADD_PERSON) },
                        onSave = { expense, repeat ->
                            val mine = expense.shares[ME] ?: 0L
                            viewModel.addSharedExpense(expense, Toast("Split saved", "${expense.title} · your share ${Money.display(mine, data.settings.hideBalances)}" + if (repeat) " · repeats monthly" else ""), repeat)
                        },
                    )
                }
                composable(Routes.PERSON, arguments = listOf(navArgument("id") { type = NavType.LongType })) { backStack ->
                    val id = backStack.arguments?.getLong("id") ?: 0L
                    PersonScreen(
                        snapshot = data, personId = id, onBack = { nav.popBackStack() },
                        onSettle = { nav.navigate(Routes.settle(id)) },
                        onSplitBill = { nav.navigate(Routes.splitBill(personId = id)) },
                        onUpdatePerson = viewModel::updatePerson,
                        onDeleteExpense = viewModel::deleteSharedExpense,
                        onDeleteSettlement = viewModel::deleteSettlement,
                    )
                }
                composable(
                    Routes.SETTLE,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("group") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("amount") { type = NavType.LongType; defaultValue = -1L },
                    ),
                ) { backStack ->
                    val id = backStack.arguments?.getLong("id") ?: 0L
                    val group = backStack.arguments?.getLong("group")?.takeIf { it > 0 }
                    SettleScreen(
                        snapshot = data, personId = id, suggested = backStack.arguments?.getLong("amount")?.takeIf { it > 0 },
                        onClose = { nav.popBackStack() }, onUpdatePerson = viewModel::updatePerson,
                        onRecord = { amount, direction, method, accountId -> viewModel.addSettlement(id, amount, direction, method, accountId, group) },
                    )
                }
                composable(Routes.ADD_GROUP) {
                    AddGroupScreen(data, onClose = { nav.popBackStack() }, onAddPerson = { nav.navigate(Routes.ADD_PERSON) }, onSave = viewModel::addGroup)
                }
                composable(Routes.GROUP, arguments = listOf(navArgument("id") { type = NavType.LongType })) { backStack ->
                    val id = backStack.arguments?.getLong("id") ?: 0L
                    GroupScreen(
                        snapshot = data, groupId = id, onBack = { nav.popBackStack() },
                        onSplitBill = { nav.navigate(Routes.splitBill(groupId = id)) },
                        onSettle = { person, amount -> nav.navigate(Routes.settle(person, groupId = id, amount = amount)) },
                        onDeleteExpense = viewModel::deleteSharedExpense,
                        onUpdateGroup = { g -> viewModel.updateGroup(g, data.shared.groupMembers(id).map { it.id }.toSet()) },
                    )
                }
                composable(Routes.ADD_PERSON) { AddPersonScreen(onClose = { nav.popBackStack() }, onSave = viewModel::addPerson) }

                composable(Routes.INSIGHTS) { InsightsScreen(data, onBack = { nav.popBackStack() }, onOpenRecap = { nav.navigate(Routes.RECAP) }, onOpenReports = { nav.navigate(Routes.REPORTS) }, onOpenAsk = { nav.navigate(Routes.ASK) }) }
                composable(Routes.REPORTS) { ReportsScreen(data, onBack = { nav.popBackStack() }) }
                composable(Routes.ASK) { AskScreen(data, onBack = { nav.popBackStack() }, onOpenTransaction = { nav.navigate(Routes.tx(it)) }) }
                composable(Routes.RECAP) { RecapScreen(data, onClose = { nav.popBackStack() }) }
                composable(Routes.SMS) {
                    SmsScreen(
                        snapshot = data, onBack = { nav.popBackStack() }, onSetTracking = viewModel::setSmsTracking, onSetTrusted = viewModel::setSenderTrusted,
                        onScan = viewModel::scanInbox, onOpenReview = { nav.navigate(Routes.REVIEW) }, onOpenRules = { nav.navigate(Routes.RULES) },
                        onOpenMandates = { nav.navigate(Routes.MANDATES) }, onSetIgnored = viewModel::setSenderIgnored, onAcceptLinks = viewModel::setAcceptLinks,
                        onSetAutoSave = viewModel::setSmsAutoSave, onSetNotify = viewModel::setSmsNotify, onSetNotifications = viewModel::setCaptureNotifications, onSetNotificationApp = viewModel::setNotificationApp,
                        onDeleteData = viewModel::deleteSmsData,
                    )
                }
                composable(Routes.BACKUP) {
                    BackupScreen(
                        snapshot = data, onBack = { nav.popBackStack() },
                        onWriteBackup = viewModel::writeBackup, onReadRestore = viewModel::readAndRestore, onRestoreEncrypted = viewModel::restoreEncrypted,
                        onSetPassphrase = viewModel::setBackupPassphrase, onClearPassphrase = viewModel::clearBackupPassphrase, onAutoBackup = viewModel::setAutoBackup,
                        onRestored = { nav.navigate(Routes.HOME) { popUpTo(0) } },
                        onOpenImport = { nav.navigate(Routes.IMPORT_CSV) },
                    )
                }
                composable(Routes.IMPORT_CSV) { ImportScreen(data, onBack = { nav.popBackStack() }, onImport = viewModel::importTransactions) }
                composable(Routes.RULES) { RulesScreen(data, onBack = { nav.popBackStack() }, onAdd = viewModel::addRule, onDelete = viewModel::deleteRule) }
                composable(Routes.MANDATES) {
                    MandatesScreen(data, onBack = { nav.popBackStack() }, onTrack = viewModel::trackMandate, onStatus = viewModel::setMandateStatus)
                }
                composable(Routes.REVIEW) {
                    ReviewScreen(data, onBack = { nav.popBackStack() }, onSave = viewModel::approvePending, onSkip = viewModel::dismissPending, onAcceptAll = viewModel::acceptSuggested)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        data,
                        SettingsActions(
                            onBack = { nav.popBackStack() }, onSetName = viewModel::setName, onSetHide = viewModel::setHideBalances,
                            onSetAppLock = viewModel::setAppLock, onSetHideInSwitcher = viewModel::setHideInSwitcher,
                            onOpenAccounts = { nav.navigate(Routes.ACCOUNTS) }, onOpenCategories = { nav.navigate(Routes.CATEGORIES) }, onOpenTags = { nav.navigate(Routes.TAGS) },
                            onOpenRhythm = { nav.navigate(Routes.RHYTHM) }, onOpenBills = { nav.navigate(Routes.BILLS) }, onOpenSms = { nav.navigate(Routes.SMS) },
                            onOpenReminders = { nav.navigate(Routes.REMINDERS) }, onOpenBackup = { nav.navigate(Routes.BACKUP) }, onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                            onOpenAppearance = { nav.navigate(Routes.APPEARANCE) }, onOpenAbout = { nav.navigate(Routes.ABOUT) }, onOpenDelete = { nav.navigate(Routes.DELETE) },
                            onSetPin = { pin, done -> viewModel.setPin(pin, done) }, onClearPin = viewModel::clearPin, onCheckPin = { pin, done -> viewModel.tryPin(pin, done) },
                        ),
                    )
                }
                composable(Routes.RHYTHM) { MoneyRhythmScreen(data, onBack = { nav.popBackStack() }, onSetPayday = viewModel::setPaydayDay, onSetIncome = viewModel::setIncome, onSetIrregular = viewModel::setIrregularIncome) }
                composable(Routes.APPEARANCE) { AppearanceScreen(data, onBack = { nav.popBackStack() }, onSetTheme = viewModel::setThemeMode, onSetVoiceLanguage = viewModel::setVoiceLanguage, onSetPureBlack = viewModel::setPureBlack) }
                composable(Routes.PRIVACY) { PrivacyScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.DELETE) {
                    DeleteDataScreen(data, onBack = { nav.popBackStack() }, onBackup = { nav.navigate(Routes.BACKUP) }, onDelete = { viewModel.deleteEverything { nav.navigate(Routes.LAUNCH) { popUpTo(0) } } })
                }
            }
        }

        AnimatedVisibility(
            visible = showBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            BottomBar(route, onTab = { nav.switchTab(it) }, onAdd = { nav.navigate(Routes.ADD) })
        }
        welcomeEncrypted?.let { text ->
            PassphraseDialog(
                title = "This backup is protected", message = "Enter the passphrase you chose when you made it.", confirmLabel = "Restore",
                creating = false, error = passphraseError, busy = passphraseBusy,
                onSubmit = { pass ->
                    passphraseBusy = true
                    viewModel.restoreEncrypted(text, pass) { outcome ->
                        passphraseBusy = false
                        when (outcome) {
                            RestoreOutcome.Done -> { welcomeEncrypted = null; nav.navigate(Routes.HOME) { popUpTo(Routes.LAUNCH) { inclusive = true } } }
                            is RestoreOutcome.Failed -> passphraseError = outcome.message
                            is RestoreOutcome.NeedsPassphrase -> Unit
                        }
                    }
                },
                onCancel = { welcomeEncrypted = null },
            )
        }
        ToastHost(
            toast = toast,
            onUndo = viewModel::undoToast,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (showBar) 96.dp + 24.dp else 24.dp),
        )
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
