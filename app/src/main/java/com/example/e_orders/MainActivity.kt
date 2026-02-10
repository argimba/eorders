@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.e_orders

import android.os.Bundle
import android.annotation.SuppressLint
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

// ============================================================
// Data Models
// ============================================================

data class CustomizationOption(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val choices: List<String>
)

data class Product(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Double,
    val category: String
)

data class OrderItem(
    val product: Product,
    var quantity: Int = 1,
    val customizations: MutableMap<String, String> = mutableMapOf(),
    var notes: String = ""
) {
    fun getDisplayDetails(): String {
        val details = mutableListOf<String>()
        customizations.forEach { (key, value) -> if (value.isNotEmpty()) details.add("$key: $value") }
        if (notes.isNotEmpty()) details.add(notes)
        return if (details.isNotEmpty()) details.joinToString(", ") else ""
    }
}

enum class OrderStatus { DRAFT, SENT }

data class Table(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    var isActive: Boolean = true
)

data class TableOrder(
    val tableId: String,
    val tableName: String,
    val items: MutableList<OrderItem> = mutableListOf(),
    var status: OrderStatus = OrderStatus.DRAFT,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getTotal(): Double = items.sumOf { it.product.price * it.quantity }
    fun isEmpty(): Boolean = items.isEmpty()
}

data class CompletedOrder(
    val id: String = UUID.randomUUID().toString(),
    val tableName: String,
    val items: List<OrderItem>,
    val total: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val paymentMethod: String = "Μετρητά"
)

// ============================================================
// Main Activity
// ============================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dataManager = remember { DataManager(this) }
            var isDarkMode by remember { mutableStateOf(dataManager.loadDarkMode()) }

            // Load language
            val savedLang = remember { dataManager.loadLanguage() }
            var language by remember { mutableStateOf(if (savedLang == "EN") Language.EN else Language.EL) }
            Strings.current = language

            CafeOrderTheme(darkTheme = isDarkMode) {
                CafeOrderApp(
                    dataManager = dataManager,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = { isDarkMode = !isDarkMode; dataManager.saveDarkMode(isDarkMode) },
                    language = language,
                    onLanguageChange = { lang -> language = lang; Strings.current = lang; dataManager.saveLanguage(if (lang == Language.EN) "EN" else "EL") }
                )
            }
        }
    }
}

@Composable
fun CafeOrderTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFD4A574), secondary = Color(0xFF6B4423),
            background = Color(0xFF1A1A1A), surface = Color(0xFF2D2D2D),
            onBackground = Color.White, onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6B4423), secondary = Color(0xFFD4A574),
            background = Color(0xFFFFFBF5), surface = Color.White,
            onBackground = Color.Black, onSurface = Color.Black
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun CafeOrderApp(
    dataManager: DataManager, isDarkMode: Boolean, onToggleDarkMode: () -> Unit,
    language: Language, onLanguageChange: (Language) -> Unit
) {
    // #5 — User system
    var appUsers by remember { mutableStateOf(dataManager.loadUsers() ?: getInitialUsers()) }
    var loggedInUser by remember { mutableStateOf<AppUser?>(null) }

    // Ensure users are saved
    LaunchedEffect(appUsers) { dataManager.saveUsers(appUsers) }

    if (loggedInUser == null) {
        LoginScreen(
            users = appUsers,
            isDarkMode = isDarkMode,
            onToggleDarkMode = onToggleDarkMode,
            language = language,
            onLanguageChange = onLanguageChange,
            onLogin = { user -> loggedInUser = user }
        )
    } else {
        MainContent(
            dataManager = dataManager,
            isDarkMode = isDarkMode,
            onToggleDarkMode = onToggleDarkMode,
            language = language,
            onLanguageChange = onLanguageChange,
            loggedInUser = loggedInUser!!,
            appUsers = appUsers,
            onUsersChange = { appUsers = it; dataManager.saveUsers(it) },
            onLogout = { loggedInUser = null }
        )
    }
}

// ============================================================
// #5 — Login Screen
// ============================================================

@Composable
fun LoginScreen(
    users: List<AppUser>, isDarkMode: Boolean, onToggleDarkMode: () -> Unit,
    language: Language, onLanguageChange: (Language) -> Unit, onLogin: (AppUser) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("e-Orders") },
                actions = {
                    // Language toggle
                    TextButton(onClick = { onLanguageChange(if (language == Language.EL) Language.EN else Language.EL) }) {
                        Text(if (language == Language.EL) "EN" else "EL", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    // Dark mode toggle
                    IconButton(onClick = onToggleDarkMode) {
                        Text(if (isDarkMode) "☀️" else "🌙", fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("☕", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("e-Orders", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = username, onValueChange = { username = it; error = false },
                        label = { Text(Strings.username) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it; error = false },
                        label = { Text(Strings.password) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (error) {
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.wrongCredentials, color = Color.Red, fontSize = 14.sp)
                    }

                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val user = users.find { it.username == username && it.password == password }
                            if (user != null) onLogin(user) else error = true
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(Strings.login, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

// ============================================================
// Main Content (after login)
// ============================================================

@Composable
fun MainContent(
    dataManager: DataManager, isDarkMode: Boolean, onToggleDarkMode: () -> Unit,
    language: Language, onLanguageChange: (Language) -> Unit,
    loggedInUser: AppUser, appUsers: List<AppUser>, onUsersChange: (List<AppUser>) -> Unit,
    onLogout: () -> Unit
) {
    val isAdmin = loggedInUser.role == "admin"
    var isAdminMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val printerManager = remember { PrinterManager(context) }
    var products by remember { mutableStateOf(dataManager.loadProducts() ?: getInitialProducts()) }
    var tables by remember { mutableStateOf(dataManager.loadTables() ?: getInitialTables(17)) }
    var tableOrders by remember { mutableStateOf<Map<String, TableOrder>>(emptyMap()) }
    var selectedTable by remember { mutableStateOf<Table?>(null) }
    var orderHistory by remember { mutableStateOf(dataManager.loadOrderHistory() ?: emptyList()) }
    var categoryCustomizations by remember { mutableStateOf(dataManager.loadCustomizations() ?: getInitialCustomizations()) }
    var categories by remember { mutableStateOf(dataManager.loadCategories() ?: listOf("Καφέδες", "Αναψυκτικά", "Μπίρες", "Cocktails", "Ποτά", "Snacks")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(when {
                        isAdminMode -> Strings.adminPanel
                        selectedTable != null -> selectedTable!!.name
                        else -> Strings.selectTable
                    })
                },
                navigationIcon = {
                    if (selectedTable != null && !isAdminMode) {
                        IconButton(onClick = { selectedTable = null }) { Icon(Icons.Default.ArrowBack, Strings.back) }
                    }
                },
                actions = {
                    // #8 — Language toggle
                    TextButton(onClick = { onLanguageChange(if (language == Language.EL) Language.EN else Language.EL) }) {
                        Text(if (language == Language.EL) "EN" else "EL", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    // #7 — Moon/Sun
                    IconButton(onClick = onToggleDarkMode) { Text(if (isDarkMode) "☀️" else "🌙", fontSize = 20.sp) }
                    // Admin / Logout
                    if (isAdmin) {
                        IconButton(onClick = { isAdminMode = !isAdminMode }) {
                            Icon(if (isAdminMode) Icons.Default.ExitToApp else Icons.Default.Settings, contentDescription = null)
                        }
                    }
                    // Logout
                    IconButton(onClick = onLogout) { Icon(Icons.Default.AccountCircle, Strings.logout) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                isAdminMode && isAdmin -> {
                    AdminPanel(
                        products = products, categories = categories, tables = tables,
                        orderHistory = orderHistory, categoryCustomizations = categoryCustomizations,
                        appUsers = appUsers, printerManager = printerManager,
                        onProductsChange = { products = it; dataManager.saveProducts(it) },
                        onCategoriesChange = { categories = it; dataManager.saveCategories(it) },
                        onTablesChange = { tables = it; dataManager.saveTables(it) },
                        onCustomizationsChange = { categoryCustomizations = it; dataManager.saveCustomizations(it) },
                        onClearHistory = { orderHistory = emptyList(); dataManager.saveOrderHistory(emptyList()) },
                        onUsersChange = onUsersChange
                    )
                }
                selectedTable != null -> {
                    val table = selectedTable!!
                    val currentTableOrder = tableOrders[table.id]
                    OrderScreen(
                        products = products, categories = categories,
                        categoryCustomizations = categoryCustomizations, table = table,
                        currentOrder = currentTableOrder?.items?.toMutableList() ?: mutableListOf(),
                        currentStatus = currentTableOrder?.status ?: OrderStatus.DRAFT,
                        allTables = tables.filter { it.isActive }, tableOrders = tableOrders,
                        printerManager = printerManager,
                        onOrderUpdate = { items, status ->
                            if (items.isEmpty()) tableOrders = tableOrders - table.id
                            else tableOrders = tableOrders + (table.id to TableOrder(tableId = table.id, tableName = table.name, items = items, status = status))
                        },
                        onTransferTable = { fromId, toTable ->
                            val order = tableOrders[fromId]
                            if (order != null) { tableOrders = tableOrders - fromId + (toTable.id to order.copy(tableId = toTable.id, tableName = toTable.name)); selectedTable = toTable }
                        },
                        onCloseOrder = { co -> orderHistory = orderHistory + co; dataManager.saveOrderHistory(orderHistory); tableOrders = tableOrders - table.id; selectedTable = null },
                        onBack = { selectedTable = null }
                    )
                }
                else -> {
                    TableSelectionScreen(tables = tables.filter { it.isActive }, tableOrders = tableOrders, onTableSelected = { selectedTable = it })
                }
            }
        }
    }
}

// ============================================================
// Table Selection Screen
// ============================================================

@Composable
fun TableSelectionScreen(tables: List<Table>, tableOrders: Map<String, TableOrder>, onTableSelected: (Table) -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Text(Strings.selectTable, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 16.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(tables) { table ->
                val to = tableOrders[table.id]; val hasOrder = to?.isEmpty() == false
                TableCard(table, hasOrder, to?.status, to?.getTotal() ?: 0.0) { onTableSelected(table) }
            }
        }
    }
}

@Composable
fun TableCard(table: Table, hasOrder: Boolean, orderStatus: OrderStatus?, total: Double, onClick: () -> Unit) {
    val cardColor = when { !hasOrder -> Color(0xFF4CAF50); orderStatus == OrderStatus.DRAFT -> Color(0xFFFF9800); orderStatus == OrderStatus.SENT -> Color(0xFFF44336); else -> Color(0xFF4CAF50) }
    val statusText = when { !hasOrder -> Strings.empty; orderStatus == OrderStatus.DRAFT -> Strings.draft; orderStatus == OrderStatus.SENT -> Strings.sent; else -> Strings.empty }
    Card(Modifier.aspectRatio(1f).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(if (hasOrder) Icons.Default.Face else Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(table.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
            if (hasOrder) { Text("€${String.format("%.2f", total)}", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium); Text(statusText, fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f)) }
            else Text(statusText, fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

// ============================================================
// Admin Panel — #5 added Users tab
// ============================================================

@Composable
fun AdminPanel(
    products: List<Product>, categories: List<String>, tables: List<Table>,
    orderHistory: List<CompletedOrder>, categoryCustomizations: Map<String, List<CustomizationOption>>,
    appUsers: List<AppUser>, printerManager: PrinterManager,
    onProductsChange: (List<Product>) -> Unit, onCategoriesChange: (List<String>) -> Unit,
    onTablesChange: (List<Table>) -> Unit, onCustomizationsChange: (Map<String, List<CustomizationOption>>) -> Unit,
    onClearHistory: () -> Unit, onUsersChange: (List<AppUser>) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(Strings.products, Strings.options, Strings.tables, Strings.users, Strings.printer, Strings.history, Strings.statistics)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEachIndexed { i, t -> Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) }) }
        }
        when (selectedTab) {
            0 -> ProductsAdminPanel(products, categories, onProductsChange)
            1 -> CustomizationAdminPanel(categories, categoryCustomizations, onCategoriesChange, onCustomizationsChange, products, onProductsChange)
            2 -> TablesAdminPanel(tables, onTablesChange)
            3 -> UsersAdminPanel(appUsers, onUsersChange)
            4 -> PrinterSettingsPanel(printerManager)
            5 -> OrderHistoryPanel(orderHistory)
            6 -> StatisticsPanel(orderHistory, onClearHistory, printerManager)
        }
    }
}

// ============================================================
// #5 — Users Admin Panel
// ============================================================

@Composable
fun UsersAdminPanel(users: List<AppUser>, onUsersChange: (List<AppUser>) -> Unit) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<AppUser?>(null) }
    var showError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            items(users) { user ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(user.username, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                if (user.role == "admin") Strings.admin else Strings.waiter,
                                fontSize = 13.sp,
                                color = if (user.role == "admin") Color(0xFFFF9800) else Color.Gray
                            )
                        }
                        Row {
                            IconButton(onClick = { editingUser = user }) { Icon(Icons.Default.Edit, Strings.edit, tint = Color.Gray) }
                            IconButton(onClick = {
                                if (user.role == "admin" && users.count { it.role == "admin" } <= 1) {
                                    showError = Strings.cantDeleteLastAdmin
                                } else {
                                    onUsersChange(users.filter { it.id != user.id })
                                }
                            }) { Icon(Icons.Default.Delete, Strings.delete, tint = Color.Red) }
                        }
                    }
                }
            }
        }
        Button(onClick = { showAddDialog = true }, Modifier.fillMaxWidth().padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(Strings.addUser)
        }
    }

    if (showAddDialog || editingUser != null) {
        UserDialog(
            user = editingUser,
            onDismiss = { showAddDialog = false; editingUser = null },
            onSave = { u ->
                if (editingUser != null) onUsersChange(users.map { if (it.id == u.id) u else it })
                else onUsersChange(users + u)
                showAddDialog = false; editingUser = null
            }
        )
    }

    if (showError != null) {
        AlertDialog(
            onDismissRequest = { showError = null },
            title = { Text("⚠️") },
            text = { Text(showError!!) },
            confirmButton = { Button(onClick = { showError = null }) { Text("OK") } }
        )
    }
}

@Composable
fun UserDialog(user: AppUser?, onDismiss: () -> Unit, onSave: (AppUser) -> Unit) {
    var username by remember { mutableStateOf(user?.username ?: "") }
    var password by remember { mutableStateOf(user?.password ?: "") }
    var role by remember { mutableStateOf(user?.role ?: "waiter") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (user == null) Strings.newUser else Strings.editUser) },
        text = {
            Column {
                OutlinedTextField(username, { username = it }, label = { Text(Strings.username) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(password, { password = it }, label = { Text(Strings.password) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("${Strings.role}:", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = role == "admin", onClick = { role = "admin" }, label = { Text(Strings.admin) }, modifier = Modifier.weight(1f))
                    FilterChip(selected = role == "waiter", onClick = { role = "waiter" }, label = { Text(Strings.waiter) }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = { Button(onClick = { if (username.isNotBlank() && password.isNotBlank()) onSave(AppUser(id = user?.id ?: UUID.randomUUID().toString(), username = username, password = password, role = role)) }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } }
    )
}

// ============================================================
// Customization Admin Panel
// ============================================================

@Composable
fun CustomizationAdminPanel(
    categories: List<String>, categoryCustomizations: Map<String, List<CustomizationOption>>,
    onCategoriesChange: (List<String>) -> Unit, onCustomizationsChange: (Map<String, List<CustomizationOption>>) -> Unit,
    products: List<Product>, onProductsChange: (List<Product>) -> Unit
) {
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategoryName by remember { mutableStateOf<String?>(null) }
    var showDeleteCategoryConfirm by remember { mutableStateOf<String?>(null) }
    var showAddOptionDialog by remember { mutableStateOf<String?>(null) }
    var editingOption by remember { mutableStateOf<Pair<String, CustomizationOption>?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            items(categories) { category ->
                val isExpanded = expandedCategory == category
                val options = categoryCustomizations[category] ?: emptyList()
                val pc = products.count { it.category == category }
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable { expandedCategory = if (isExpanded) null else category }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(category, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("${options.size} ${Strings.options.lowercase()} • $pc ${Strings.products.lowercase()}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Row {
                                IconButton(onClick = { editingCategoryName = category }) { Icon(Icons.Default.Edit, Strings.edit, tint = Color.Gray) }
                                IconButton(onClick = { showDeleteCategoryConfirm = category }) { Icon(Icons.Default.Delete, Strings.delete, tint = Color.Red) }
                                Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Expand", tint = Color.Gray)
                            }
                        }
                        if (isExpanded) {
                            Divider(color = Color.Gray.copy(alpha = 0.3f))
                            if (options.isEmpty()) Text(Strings.noOptions, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                            else options.forEach { option ->
                                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(option.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text(option.choices.joinToString("  •  "), fontSize = 12.sp, color = Color.Gray)
                                    }
                                    Row {
                                        IconButton(onClick = { editingOption = Pair(category, option) }, Modifier.size(36.dp)) { Icon(Icons.Default.Edit, Strings.edit, tint = Color.Gray, modifier = Modifier.size(18.dp)) }
                                        IconButton(onClick = { val u = categoryCustomizations.toMutableMap(); u[category] = (u[category] ?: emptyList()).filter { it.id != option.id }; onCustomizationsChange(u) }, Modifier.size(36.dp)) { Icon(Icons.Default.Delete, Strings.delete, tint = Color.Red, modifier = Modifier.size(18.dp)) }
                                    }
                                }
                            }
                            TextButton(onClick = { showAddOptionDialog = category }, Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Strings.addOption)
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = { showAddCategoryDialog = true }, Modifier.fillMaxWidth().padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(Strings.addCategory)
        }
    }

    if (showAddCategoryDialog) SimpleNameDialog(Strings.newCategory, "", { showAddCategoryDialog = false }) { name -> if (name.isNotBlank() && !categories.contains(name)) onCategoriesChange(categories + name); showAddCategoryDialog = false }
    if (editingCategoryName != null) SimpleNameDialog(Strings.renameCategory, editingCategoryName!!, { editingCategoryName = null }) { newName ->
        if (newName.isNotBlank()) { val old = editingCategoryName!!; onCategoriesChange(categories.map { if (it == old) newName else it }); onProductsChange(products.map { if (it.category == old) it.copy(category = newName) else it }); val u = categoryCustomizations.toMutableMap(); val o = u.remove(old); if (o != null) u[newName] = o; onCustomizationsChange(u) }; editingCategoryName = null
    }
    if (showDeleteCategoryConfirm != null) {
        val cat = showDeleteCategoryConfirm!!; val pc = products.count { it.category == cat }
        AlertDialog(onDismissRequest = { showDeleteCategoryConfirm = null }, title = { Text(Strings.deleteCategory) },
            text = { Column { Text("${Strings.deleteCategoryConfirm} \"$cat\";"); if (pc > 0) { Spacer(Modifier.height(8.dp)); Text("⚠️ $pc ${Strings.productsWillBeDeleted}", color = Color.Red, fontWeight = FontWeight.Bold) } } },
            confirmButton = { Button(onClick = { onProductsChange(products.filter { it.category != cat }); onCategoriesChange(categories.filter { it != cat }); val u = categoryCustomizations.toMutableMap(); u.remove(cat); onCustomizationsChange(u); showDeleteCategoryConfirm = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text(Strings.delete) } },
            dismissButton = { TextButton(onClick = { showDeleteCategoryConfirm = null }) { Text(Strings.cancel) } })
    }
    if (showAddOptionDialog != null || editingOption != null) {
        val catName = showAddOptionDialog ?: editingOption!!.first; val existing = editingOption?.second
        CustomizationOptionDialog(if (existing != null) Strings.editOption else "${Strings.newOption} \"$catName\"", existing?.name ?: "", existing?.choices ?: emptyList(),
            { showAddOptionDialog = null; editingOption = null },
            { name, choices -> val u = categoryCustomizations.toMutableMap(); val cur = (u[catName] ?: emptyList()).toMutableList(); if (existing != null) { val i = cur.indexOfFirst { it.id == existing.id }; if (i >= 0) cur[i] = existing.copy(name = name, choices = choices) } else cur.add(CustomizationOption(name = name, choices = choices)); u[catName] = cur; onCustomizationsChange(u); showAddOptionDialog = null; editingOption = null })
    }
}

@Composable
fun SimpleNameDialog(title: String, initialName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(Strings.name) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } })
}

@Composable
fun CustomizationOptionDialog(title: String, initialName: String, initialChoices: List<String>, onDismiss: () -> Unit, onSave: (String, List<String>) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var choices by remember { mutableStateOf(initialChoices.toMutableList()) }
    var newChoice by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text(Strings.optionName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp)); Text(Strings.choices, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                choices.forEachIndexed { i, c -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { Text(c, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)); IconButton(onClick = { choices = choices.toMutableList().also { it.removeAt(i) } }, Modifier.size(32.dp)) { Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(16.dp)) } } }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(newChoice, { newChoice = it }, label = { Text(Strings.newChoice) }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp)); IconButton(onClick = { if (newChoice.isNotBlank()) { choices = (choices + newChoice.trim()).toMutableList(); newChoice = "" } }) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) }
                }
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank() && choices.isNotEmpty()) onSave(name.trim(), choices.toList()) }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } })
}

// ============================================================
// Products Admin Panel
// ============================================================

@Composable
fun ProductsAdminPanel(products: List<Product>, categories: List<String>, onProductsChange: (List<Product>) -> Unit) {
    var selCat by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    Column(Modifier.fillMaxSize()) {
        if (categories.isNotEmpty()) ScrollableTabRow(selectedTabIndex = maxOf(0, categories.indexOf(selCat)), containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary) { categories.forEach { c -> Tab(selected = selCat == c, onClick = { selCat = c }, text = { Text(c) }) } }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) { items(products.filter { it.category == selCat }) { p -> ProductCard(p, { editingProduct = p }, { onProductsChange(products.filter { it.id != p.id }) }); Spacer(Modifier.height(8.dp)) } }
        Button(onClick = { showAddDialog = true }, Modifier.fillMaxWidth().padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(Strings.addProduct) }
    }
    if (showAddDialog || editingProduct != null) ProductDialog(editingProduct, selCat, categories, { showAddDialog = false; editingProduct = null }) { p -> if (editingProduct != null) onProductsChange(products.map { if (it.id == p.id) p else it }) else onProductsChange(products + p); showAddDialog = false; editingProduct = null }
}

@Composable
fun ProductCard(product: Product, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface); Text("€${String.format("%.2f", product.price)}", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp) }
            Row { IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, Strings.edit, tint = Color.Gray) }; IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, Strings.delete, tint = Color.Red) } }
        }
    }
}

@Composable
fun ProductDialog(product: Product?, category: String, categories: List<String>, onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var price by remember { mutableStateOf(product?.price?.toString() ?: "") }
    var selCat by remember { mutableStateOf(product?.category ?: category) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (product == null) Strings.newProduct else Strings.edit) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(Strings.name) }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                OutlinedTextField(price, { price = it }, label = { Text("${Strings.price} (€)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded, { expanded = it }) {
                    OutlinedTextField(selCat, {}, readOnly = true, label = { Text(Strings.category) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded, { expanded = false }) { categories.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { selCat = c; expanded = false }) } }
                }
            }
        },
        confirmButton = { Button(onClick = { val pv = price.toDoubleOrNull(); if (name.isNotBlank() && pv != null && pv > 0) onSave(Product(id = product?.id ?: UUID.randomUUID().toString(), name = name, price = pv, category = selCat)) }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } })
}

// ============================================================
// Tables Admin Panel
// ============================================================

@Composable
fun TablesAdminPanel(tables: List<Table>, onTablesChange: (List<Table>) -> Unit) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTable by remember { mutableStateOf<Table?>(null) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            items(tables) { table ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = if (table.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(table.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Row {
                            IconButton(onClick = { editingTable = table }) { Icon(Icons.Default.Edit, Strings.edit, tint = Color.Gray) }
                            Switch(checked = table.isActive, onCheckedChange = { onTablesChange(tables.map { if (it.id == table.id) it.copy(isActive = !it.isActive) else it }) })
                            IconButton(onClick = { onTablesChange(tables.filter { it.id != table.id }) }) { Icon(Icons.Default.Delete, Strings.delete, tint = Color.Red) }
                        }
                    }
                }
            }
        }
        Button(onClick = { showAddDialog = true }, Modifier.fillMaxWidth().padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(Strings.addTable) }
    }
    if (showAddDialog) SimpleNameDialog(Strings.newTable, "", { showAddDialog = false }) { n -> onTablesChange(tables + Table(name = n)); showAddDialog = false }
    if (editingTable != null) SimpleNameDialog(Strings.renameTable, editingTable!!.name, { editingTable = null }) { n -> onTablesChange(tables.map { if (it.id == editingTable!!.id) it.copy(name = n) else it }); editingTable = null }
}

// ============================================================
// Order Screen
// ============================================================

@Composable
fun OrderScreen(
    products: List<Product>, categories: List<String>, categoryCustomizations: Map<String, List<CustomizationOption>>,
    table: Table, currentOrder: MutableList<OrderItem>, currentStatus: OrderStatus,
    allTables: List<Table>, tableOrders: Map<String, TableOrder>,
    printerManager: PrinterManager,
    onOrderUpdate: (MutableList<OrderItem>, OrderStatus) -> Unit, onTransferTable: (String, Table) -> Unit,
    onCloseOrder: (CompletedOrder) -> Unit, onBack: () -> Unit
) {
    var selCat by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    var orderItems by remember(table.id) { mutableStateOf(currentOrder.toMutableList()) }
    var orderStatus by remember(table.id) { mutableStateOf(currentStatus) }
    var showOrderSummary by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showSplitBillDialog by remember { mutableStateOf(false) }
    var sentItemsCount by remember(table.id) { mutableStateOf(if (currentStatus == OrderStatus.SENT) currentOrder.size else 0) }
    val hasNewItems = orderItems.size > sentItemsCount
    val scope = rememberCoroutineScope()
    var printMessage by remember { mutableStateOf<String?>(null) }
    fun saveOrder() { onOrderUpdate(orderItems.toMutableList(), orderStatus) }
    fun sendAndPrint() {
        orderStatus = OrderStatus.SENT; sentItemsCount = orderItems.size; saveOrder()
        // Print to thermal printer
        scope.launch {
            try {
                val receipt = printerManager.buildOrderReceipt(table.name, orderItems)
                printerManager.print(receipt)
            } catch (_: Exception) { /* Ignore print errors silently */ }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (categories.isNotEmpty()) ScrollableTabRow(selectedTabIndex = maxOf(0, categories.indexOf(selCat)), containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary) { categories.forEach { c -> Tab(selected = selCat == c, onClick = { selCat = c }, text = { Text(c) }) } }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) { items(products.filter { it.category == selCat }) { p -> ProductOrderCard(p) { selectedProduct = p }; Spacer(Modifier.height(8.dp)) } }
        if (orderItems.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = when (orderStatus) { OrderStatus.DRAFT -> Color(0xFFFF9800); OrderStatus.SENT -> Color(0xFFF44336) }), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("${orderItems.sumOf { it.quantity }} ${Strings.products}", color = Color.White, fontSize = 14.sp)
                            Text("€${String.format("%.2f", orderItems.sumOf { it.product.price * it.quantity })}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(if (orderStatus == OrderStatus.DRAFT) Strings.draft else Strings.sent, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { showTransferDialog = true }, Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))) { Icon(Icons.Default.KeyboardArrowRight, Strings.transferTable, tint = Color.White) }
                            if (orderStatus == OrderStatus.SENT) IconButton(onClick = { showSplitBillDialog = true }, Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))) { Icon(Icons.Default.Person, Strings.splitBill, tint = Color.White) }
                            Button(onClick = { showOrderSummary = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = if (orderStatus == OrderStatus.DRAFT) Color(0xFFFF9800) else Color(0xFFF44336))) { Text(Strings.view) }
                        }
                    }
                    if (orderStatus == OrderStatus.DRAFT || hasNewItems) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { sendAndPrint() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFFF9800))) {
                            Icon(Icons.Default.Send, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(if (hasNewItems && orderStatus == OrderStatus.SENT) Strings.sendNew else Strings.sendToBar)
                        }
                    }
                }
            }
        }
    }

    if (selectedProduct != null) ProductCustomizationDialog(selectedProduct!!, categoryCustomizations[selectedProduct!!.category] ?: emptyList(), { selectedProduct = null }) { item -> orderItems = (orderItems + item).toMutableList(); saveOrder(); selectedProduct = null }
    if (showOrderSummary) OrderSummaryDialog(table.name, orderItems, orderStatus, hasNewItems, { showOrderSummary = false }, { sendAndPrint(); showOrderSummary = false }, { m -> onCloseOrder(CompletedOrder(tableName = table.name, items = orderItems.toList(), total = orderItems.sumOf { it.product.price * it.quantity }, paymentMethod = m)); showOrderSummary = false }, { u -> orderItems = u.toMutableList(); saveOrder() })
    if (showTransferDialog) TransferTableDialog(table, allTables.filter { it.id != table.id && tableOrders[it.id]?.isEmpty() != false }, { showTransferDialog = false }) { t -> onTransferTable(table.id, t); showTransferDialog = false }
    if (showSplitBillDialog) SplitBillDialog(orderItems, orderItems.sumOf { it.product.price * it.quantity }) { showSplitBillDialog = false }
}

@Composable
fun ProductCustomizationDialog(product: Product, customizationOptions: List<CustomizationOption>, onDismiss: () -> Unit, onAddToOrder: (OrderItem) -> Unit) {
    var quantity by remember { mutableStateOf(1) }; var notes by remember { mutableStateOf("") }; var selections by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(product.name) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(Strings.quantity, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { if (quantity > 1) quantity-- }) { Icon(Icons.Default.Clear, "Minus") }; Text("$quantity", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp)); IconButton(onClick = { quantity++ }) { Icon(Icons.Default.Add, "Plus") } }
                }
                if (customizationOptions.isNotEmpty()) {
                    Divider(Modifier.padding(vertical = 8.dp))
                    customizationOptions.forEach { opt ->
                        Text("${opt.name}:", fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { opt.choices.forEach { ch -> FilterChip(selected = selections[opt.name] == ch, onClick = { selections = selections.toMutableMap().apply { this[opt.name] = ch } }, label = { Text(ch, fontSize = 11.sp) }, modifier = Modifier.weight(1f)) } }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                Divider(Modifier.padding(vertical = 8.dp)); Text(Strings.notes, fontWeight = FontWeight.Bold)
                OutlinedTextField(notes, { notes = it }, placeholder = { Text(Strings.notesPlaceholder) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), minLines = 2, maxLines = 4)
                Spacer(Modifier.height(8.dp)); Text("${Strings.total} €${String.format("%.2f", product.price * quantity)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { Button(onClick = { onAddToOrder(OrderItem(product = product, quantity = quantity, customizations = selections.toMutableMap(), notes = notes)) }) { Text(Strings.add) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } })
}

@Composable
fun ProductOrderCard(product: Product, onAddToOrder: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onAddToOrder() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface); Text("€${String.format("%.2f", product.price)}", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            Icon(Icons.Default.Add, Strings.add, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
    }
}

// ============================================================
// Dialogs
// ============================================================

@Composable
fun TransferTableDialog(currentTable: Table, availableTables: List<Table>, onDismiss: () -> Unit, onTransfer: (Table) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.transferTable) },
        text = {
            Column {
                Text("${Strings.transferFrom} ${currentTable.name} ${Strings.to}", modifier = Modifier.padding(bottom = 16.dp))
                if (availableTables.isEmpty()) Text(Strings.noAvailableTables, color = Color.Gray)
                else LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(availableTables) { t -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onTransfer(t) }, colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50))) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(t.name, color = Color.White, fontWeight = FontWeight.Bold); Icon(Icons.Default.ArrowForward, null, tint = Color.White) } } }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } })
}

@Composable
fun SplitBillDialog(orderItems: List<OrderItem>, total: Double, onDismiss: () -> Unit) {
    var numberOfPeople by remember { mutableStateOf(2) }; var splitType by remember { mutableStateOf("equal") }; var selectedItems by remember { mutableStateOf<Set<Int>>(emptySet()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.splitBill) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${Strings.total} €${String.format("%.2f", total)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = splitType == "equal", onClick = { splitType = "equal" }, label = { Text(Strings.equalParts) }, modifier = Modifier.weight(1f))
                    FilterChip(selected = splitType == "custom", onClick = { splitType = "custom"; selectedItems = emptySet() }, label = { Text(Strings.perProduct) }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                if (splitType == "equal") {
                    Text(Strings.numberOfPeople, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) { IconButton(onClick = { if (numberOfPeople > 2) numberOfPeople-- }) { Icon(Icons.Default.Clear, "Minus") }; Text("$numberOfPeople", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp)); IconButton(onClick = { if (numberOfPeople < 10) numberOfPeople++ }) { Icon(Icons.Default.Add, "Plus") } }
                    Divider(Modifier.padding(vertical = 8.dp)); Text(Strings.eachPersonPays, fontSize = 14.sp, color = Color.Gray)
                    Text("€${String.format("%.2f", total / numberOfPeople)}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(Strings.selectProducts, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                    orderItems.forEachIndexed { i, item ->
                        val isSel = selectedItems.contains(i)
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedItems = if (isSel) selectedItems - i else selectedItems + i },
                            colors = CardDefaults.cardColors(containerColor = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface),
                            border = if (isSel) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Checkbox(checked = isSel, onCheckedChange = { selectedItems = if (isSel) selectedItems - i else selectedItems + i })
                                    Spacer(Modifier.width(8.dp)); Column { Text(item.product.name, fontWeight = FontWeight.Medium); Text("${item.quantity}x €${String.format("%.2f", item.product.price)}", fontSize = 12.sp, color = Color.Gray) }
                                }
                                Text("€${String.format("%.2f", item.product.price * item.quantity)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (selectedItems.isNotEmpty()) { Divider(Modifier.padding(vertical = 8.dp)); val st = selectedItems.sumOf { idx -> orderItems[idx].product.price * orderItems[idx].quantity }; Text("${Strings.selected} €${String.format("%.2f", st)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary); Text("${Strings.remaining} €${String.format("%.2f", total - st)}", fontSize = 14.sp, color = Color.Gray) }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.close) } })
}

@Composable
fun OrderSummaryDialog(tableName: String, orderItems: List<OrderItem>, orderStatus: OrderStatus, hasNewItems: Boolean, onDismiss: () -> Unit, onPrint: () -> Unit, onClose: (String) -> Unit, onUpdateOrder: (List<OrderItem>) -> Unit) {
    var items by remember { mutableStateOf(orderItems.toList()) }; var showPaymentDialog by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("$tableName - ${Strings.order}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { item ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp); val d = item.getDisplayDetails(); if (d.isNotEmpty()) Text(d, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp)); Text("€${String.format("%.2f", item.product.price)} x ${item.quantity}", fontSize = 12.sp, color = Color.Gray) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { if (item.quantity > 1) { item.quantity--; items = items.toList(); onUpdateOrder(items) } }) { Icon(Icons.Default.Clear, "Remove", tint = Color.Gray) }
                                    Text("${item.quantity}", fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { item.quantity++; items = items.toList(); onUpdateOrder(items) }) { Icon(Icons.Default.Add, "Add", tint = Color.Gray) }
                                    IconButton(onClick = { items = items.filter { it != item }; onUpdateOrder(items) }) { Icon(Icons.Default.Delete, Strings.delete, tint = Color.Red) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp)); Divider()
                Text("${Strings.total} €${String.format("%.2f", items.sumOf { it.product.price * it.quantity })}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.End).padding(top = 8.dp))
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (orderStatus == OrderStatus.DRAFT || hasNewItems) { Button(onClick = onPrint, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text(if (hasNewItems && orderStatus == OrderStatus.SENT) Strings.sendNew else Strings.send) }; Spacer(Modifier.height(8.dp)) }
                if (orderStatus == OrderStatus.SENT && !hasNewItems) { Button(onClick = { showPaymentDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text(Strings.close) } }
            }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.back) } })
    if (showPaymentDialog) PaymentMethodDialog({ showPaymentDialog = false }) { m -> showPaymentDialog = false; onClose(m) }
}

@Composable
fun PaymentMethodDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.paymentMethod) },
        text = {
            Column {
                listOf(Strings.cash to "Μετρητά", Strings.card to "Κάρτα").forEach { (label, value) ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect(value) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Icon(if (value == "Μετρητά") Icons.Default.ShoppingCart else Icons.Default.Favorite, null, tint = Color.White)
                        }
                    }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } })
}

// ============================================================
// Order History & Statistics
// ============================================================

@Composable
fun OrderHistoryPanel(orderHistory: List<CompletedOrder>) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (orderHistory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.DateRange, null, Modifier.size(64.dp), tint = Color.Gray); Spacer(Modifier.height(16.dp)); Text(Strings.noHistory, color = Color.Gray, fontSize = 18.sp) } }
        } else {
            Text("${Strings.orderHistory} (${orderHistory.size})", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 16.dp))
            LazyColumn {
                items(orderHistory.sortedByDescending { it.timestamp }) { order ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(order.tableName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface); Text("€${String.format("%.2f", order.total)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                            Text(dateFormat.format(Date(order.timestamp)), fontSize = 12.sp, color = Color.Gray); Spacer(Modifier.height(8.dp))
                            order.items.forEach { item -> val d = item.getDisplayDetails(); Text("${item.quantity}x ${item.product.name}" + if (d.isNotEmpty()) " ($d)" else "", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
                            Divider(Modifier.padding(vertical = 8.dp)); Text("${Strings.payment} ${order.paymentMethod}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatisticsPanel(orderHistory: List<CompletedOrder>, onClearHistory: () -> Unit, printerManager: PrinterManager) {
    val totalRevenue = orderHistory.sumOf { it.total }; val totalOrders = orderHistory.size; val averageOrder = if (totalOrders > 0) totalRevenue / totalOrders else 0.0
    val productStats = orderHistory.flatMap { it.items }.groupBy { it.product.name }.mapValues { (_, items) -> items.sumOf { it.quantity } }.toList().sortedByDescending { it.second }.take(10)
    val categoryStats = orderHistory.flatMap { it.items }.groupBy { it.product.category }.mapValues { (_, items) -> items.sumOf { it.product.price * it.quantity } }.toList().sortedByDescending { it.second }
    var showCloseShiftConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(Strings.salesStatistics, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { StatCard(Strings.totalRevenue, "€${String.format("%.2f", totalRevenue)}", Color(0xFF4CAF50), Modifier.weight(1f)); StatCard(Strings.orders, "$totalOrders", Color(0xFF2196F3), Modifier.weight(1f)) }
        Spacer(Modifier.height(12.dp)); StatCard(Strings.average, "€${String.format("%.2f", averageOrder)}", Color(0xFFFF9800), Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Button(onClick = { showCloseShiftConfirm = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))) { Icon(Icons.Default.ExitToApp, null); Spacer(Modifier.width(8.dp)); Text(Strings.closeShift, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp))
        if (productStats.isNotEmpty()) { Text(Strings.topProducts, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground); Spacer(Modifier.height(8.dp)); productStats.forEachIndexed { i, (n, c) -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Row(verticalAlignment = Alignment.CenterVertically) { Text("${i + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(30.dp)); Text(n, color = MaterialTheme.colorScheme.onSurface) }; Text("$c ${Strings.pcs}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } } } }
        Spacer(Modifier.height(24.dp))
        if (categoryStats.isNotEmpty()) { Text(Strings.salesByCategory, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground); Spacer(Modifier.height(8.dp)); categoryStats.forEach { (cat, rev) -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(cat, color = MaterialTheme.colorScheme.onSurface); Text("€${String.format("%.2f", rev)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } } } }
        if (orderHistory.isEmpty()) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(Strings.noDataYet, textAlign = TextAlign.Center, color = Color.Gray) }
    }

    if (showCloseShiftConfirm) {
        AlertDialog(onDismissRequest = { showCloseShiftConfirm = false }, title = { Text(Strings.closeShift) },
            text = {
                Column {
                    Text(Strings.shiftSummary, fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(Modifier.height(12.dp))
                    Text("${Strings.totalRevenue}: €${String.format("%.2f", totalRevenue)}", fontSize = 16.sp)
                    Text("${Strings.orders}: $totalOrders", fontSize = 16.sp)
                    Text("${Strings.average}: €${String.format("%.2f", averageOrder)}", fontSize = 16.sp)
                    if (categoryStats.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text(Strings.perCategory, fontWeight = FontWeight.Bold); categoryStats.forEach { (c, r) -> Text("  $c: €${String.format("%.2f", r)}", fontSize = 14.sp) } }
                    Spacer(Modifier.height(16.dp)); Text("⚠️ ${Strings.historyWillReset}", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = { Button(onClick = {
                // Print shift summary
                scope.launch {
                    try {
                        val receipt = printerManager.buildShiftSummary(totalRevenue, totalOrders, averageOrder, categoryStats, productStats)
                        printerManager.print(receipt)
                    } catch (_: Exception) { /* Ignore print errors */ }
                }
                onClearHistory(); showCloseShiftConfirm = false
            }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text(Strings.closeShift) } },
            dismissButton = { TextButton(onClick = { showCloseShiftConfirm = false }) { Text(Strings.cancel) } })
    }
}

@Composable
fun StatCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp); Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
    }
}

// ============================================================
// Printer Settings Panel
// ============================================================

@SuppressLint("MissingPermission")
@Composable
fun PrinterSettingsPanel(printerManager: PrinterManager) {
    var config by remember { mutableStateOf(printerManager.loadConfig()) }
    var wifiIp by remember { mutableStateOf(config.wifiIp) }
    var wifiPort by remember { mutableStateOf(config.wifiPort.toString()) }
    var isPrinting by remember { mutableStateOf(false) }
    var printResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pairedDevices = remember { printerManager.getPairedDevices() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text(Strings.printerSettings, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))

        // Current status
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = if (config.type != "none") Color(0xFF4CAF50) else MaterialTheme.colorScheme.surface
        )) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        if (config.type != "none") Strings.printerConfigured else Strings.printerNotConfigured,
                        fontWeight = FontWeight.Bold,
                        color = if (config.type != "none") Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    if (config.type == "bluetooth") Text("Bluetooth: ${config.bluetoothName}", fontSize = 13.sp, color = if (config.type != "none") Color.White.copy(alpha = 0.9f) else Color.Gray)
                    if (config.type == "wifi") Text("WiFi: ${config.wifiIp}:${config.wifiPort}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                }
                if (config.type != "none") Icon(Icons.Default.CheckCircle, null, tint = Color.White)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Connection type selector
        Text("${Strings.connectionType}:", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = config.type == "none",
                onClick = { config = PrinterConfig(type = "none"); printerManager.saveConfig(config) },
                label = { Text(Strings.noPrinter) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = config.type == "bluetooth",
                onClick = { config = config.copy(type = "bluetooth"); printerManager.saveConfig(config) },
                label = { Text(Strings.bluetooth) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = config.type == "wifi",
                onClick = { config = config.copy(type = "wifi"); printerManager.saveConfig(config) },
                label = { Text(Strings.wifi) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))

        // Bluetooth settings
        if (config.type == "bluetooth") {
            Text(Strings.pairedDevices, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))

            if (!printerManager.hasBluetoothPermission()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800))) {
                    Text(Strings.bluetoothPermissionNeeded, color = Color.White, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                }
            } else if (pairedDevices.isEmpty()) {
                Text(Strings.noPairedDevices, color = Color.Gray, fontSize = 14.sp)
            } else {
                pairedDevices.forEach { device ->
                    val deviceName = try { device.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }
                    val deviceAddress = device.address
                    val isSelected = config.bluetoothAddress == deviceAddress

                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            config = config.copy(bluetoothAddress = deviceAddress, bluetoothName = deviceName)
                            printerManager.saveConfig(config)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(deviceName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(deviceAddress, fontSize = 12.sp, color = Color.Gray)
                            }
                            if (isSelected) Icon(Icons.Default.CheckCircle, Strings.connected, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // WiFi settings
        if (config.type == "wifi") {
            Text("${Strings.ipAddress}:", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = wifiIp, onValueChange = { wifiIp = it },
                label = { Text(Strings.ipAddress) },
                placeholder = { Text("192.168.1.100") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = wifiPort, onValueChange = { wifiPort = it },
                label = { Text(Strings.port) },
                placeholder = { Text("9100") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val p = wifiPort.toIntOrNull() ?: 9100
                    config = config.copy(wifiIp = wifiIp, wifiPort = p)
                    printerManager.saveConfig(config)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text(Strings.save) }
        }

        Spacer(Modifier.height(24.dp))

        // Test print button
        if (config.type != "none") {
            Button(
                onClick = {
                    isPrinting = true; printResult = null
                    scope.launch {
                        val testData = buildTestReceipt()
                        val result = printerManager.print(testData)
                        isPrinting = false
                        printResult = if (result.isSuccess) Strings.printSuccess
                        else "${Strings.printError}: ${result.exceptionOrNull()?.message ?: "Unknown"}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPrinting && (config.type == "wifi" || config.bluetoothAddress.isNotEmpty()),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                if (isPrinting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text(Strings.printing)
                } else {
                    Icon(Icons.Default.Build, null); Spacer(Modifier.width(8.dp)); Text(Strings.testPrint)
                }
            }

            if (printResult != null) {
                Spacer(Modifier.height(12.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (printResult == Strings.printSuccess) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                ) {
                    Text(printResult!!, color = Color.White, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun buildTestReceipt(): ByteArray {
    val buffer = mutableListOf<Byte>()
    fun add(bytes: ByteArray) { buffer.addAll(bytes.toList()) }
    fun addText(text: String) { add(text.toByteArray(Charsets.UTF_8)) }
    fun newLine() { addText("\n") }

    add(EscPos.INIT)
    add(EscPos.ALIGN_CENTER)
    add(EscPos.DOUBLE_ON)
    addText("e-Orders")
    newLine()
    add(EscPos.NORMAL)
    newLine()
    addText("Test Print OK!")
    newLine()
    addText(EscPos.line('-'))
    newLine()
    addText(java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))
    newLine()
    add(EscPos.FEED_LINES)
    add(EscPos.PARTIAL_CUT)
    return buffer.toByteArray()
}

// ============================================================
// Initial Data
// ============================================================

fun getInitialUsers(): List<AppUser> = listOf(
    AppUser(username = "admin", password = "admin123", role = "admin")
)

fun getInitialProducts(): List<Product> = listOf(
    Product(name = "Espresso", price = 2.50, category = "Καφέδες"), Product(name = "Cappuccino", price = 3.50, category = "Καφέδες"),
    Product(name = "Freddo Espresso", price = 3.00, category = "Καφέδες"), Product(name = "Freddo Cappuccino", price = 3.50, category = "Καφέδες"),
    Product(name = "Latte", price = 3.80, category = "Καφέδες"), Product(name = "Frappé", price = 3.50, category = "Καφέδες"),
    Product(name = "Coca Cola", price = 2.50, category = "Αναψυκτικά"), Product(name = "Sprite", price = 2.50, category = "Αναψυκτικά"),
    Product(name = "Fanta", price = 2.50, category = "Αναψυκτικά"), Product(name = "Χυμός Πορτοκάλι", price = 3.00, category = "Αναψυκτικά"),
    Product(name = "Mythos", price = 4.00, category = "Μπίρες"), Product(name = "Heineken", price = 4.50, category = "Μπίρες"), Product(name = "Fix", price = 4.00, category = "Μπίρες"),
    Product(name = "Mojito", price = 8.00, category = "Cocktails"), Product(name = "Margarita", price = 8.50, category = "Cocktails"),
    Product(name = "Pina Colada", price = 8.50, category = "Cocktails"), Product(name = "Cosmopolitan", price = 8.00, category = "Cocktails"),
    Product(name = "Vodka", price = 6.00, category = "Ποτά"), Product(name = "Whiskey", price = 7.00, category = "Ποτά"),
    Product(name = "Gin", price = 6.50, category = "Ποτά"), Product(name = "Rum", price = 6.00, category = "Ποτά"),
    Product(name = "Chips", price = 2.00, category = "Snacks"), Product(name = "Nuts", price = 3.00, category = "Snacks"), Product(name = "Popcorn", price = 2.50, category = "Snacks")
)

fun getInitialCustomizations(): Map<String, List<CustomizationOption>> = mapOf(
    "Καφέδες" to listOf(CustomizationOption(name = "Ζάχαρη", choices = listOf("Σκέτο", "Μέτριο", "Γλυκό")), CustomizationOption(name = "Γάλα", choices = listOf("Όχι", "Κανονικό", "Χωρίς λακτόζη", "Φυτικό")))
)

fun getInitialTables(count: Int): List<Table> = (1..count).map { Table(name = "Τραπέζι $it") }
