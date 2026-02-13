@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.e_orders

import android.os.Bundle
import android.annotation.SuppressLint
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ============================================================
// Data Models
// ============================================================

data class CustomizationOption(val id: String = UUID.randomUUID().toString(), val name: String, val choices: List<String>)

data class Product(val id: String = UUID.randomUUID().toString(), val name: String, val price: Double, val category: String)

data class OrderItem(
    val product: Product,
    val quantity: Int = 1,
    val customizations: Map<String, String> = emptyMap(),
    val notes: String = ""
) {
    fun getDisplayDetails(): String {
        val details = mutableListOf<String>()
        customizations.forEach { (k, v) -> if (v.isNotEmpty()) details.add("$k: $v") }
        if (notes.isNotEmpty()) details.add(notes)
        return if (details.isNotEmpty()) details.joinToString(", ") else ""
    }
}

enum class OrderStatus { DRAFT, SENT }

data class Table(val id: String = UUID.randomUUID().toString(), val name: String, var isActive: Boolean = true)

data class TableOrder(
    val tableId: String, val tableName: String,
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
    val paymentMethod: String = "Μετρητά",
    val waiterName: String = ""
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
            CafeOrderTheme(darkTheme = isDarkMode) {
                CafeOrderApp(
                    dataManager = dataManager, isDarkMode = isDarkMode,
                    onToggleDarkMode = { isDarkMode = !isDarkMode; dataManager.saveDarkMode(isDarkMode) }
                )
            }
        }
    }
}

@Composable
fun CafeOrderTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val cs = if (darkTheme) darkColorScheme(primary = Color(0xFFD4A574), secondary = Color(0xFF6B4423), background = Color(0xFF1A1A1A), surface = Color(0xFF2D2D2D), onBackground = Color.White, onSurface = Color.White)
    else lightColorScheme(primary = Color(0xFF6B4423), secondary = Color(0xFFD4A574), background = Color(0xFFFFFBF5), surface = Color.White, onBackground = Color.Black, onSurface = Color.Black)
    MaterialTheme(colorScheme = cs, content = content)
}

@Composable
fun CafeOrderApp(dataManager: DataManager, isDarkMode: Boolean, onToggleDarkMode: () -> Unit) {
    var appUsers by remember { mutableStateOf(dataManager.loadUsers() ?: getInitialUsers()) }
    var loggedInUser by remember { mutableStateOf<AppUser?>(null) }
    LaunchedEffect(appUsers) { dataManager.saveUsers(appUsers) }

    if (loggedInUser == null) {
        LoginScreen(users = appUsers, isDarkMode = isDarkMode, onToggleDarkMode = onToggleDarkMode, onLogin = { loggedInUser = it })
    } else {
        MainContent(dataManager = dataManager, isDarkMode = isDarkMode, onToggleDarkMode = onToggleDarkMode,
            loggedInUser = loggedInUser!!, appUsers = appUsers, onUsersChange = { appUsers = it; dataManager.saveUsers(it) }, onLogout = { loggedInUser = null })
    }
}

// ============================================================
// Login Screen — #2 removed language toggle
// ============================================================

@Composable
fun LoginScreen(users: List<AppUser>, isDarkMode: Boolean, onToggleDarkMode: () -> Unit, onLogin: (AppUser) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("e-Orders") }, actions = {
            IconButton(onClick = onToggleDarkMode) { Text(if (isDarkMode) "☀️" else "🌙", fontSize = 20.sp) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White))
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Card(Modifier.fillMaxWidth().padding(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("☕", fontSize = 48.sp); Spacer(Modifier.height(8.dp))
                    Text("e-Orders", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(24.dp))
                    OutlinedTextField(username, { username = it; error = false }, label = { Text(Strings.username) }, singleLine = true, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp))
                    OutlinedTextField(password, { password = it; error = false }, label = { Text(Strings.password) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    if (error) { Spacer(Modifier.height(8.dp)); Text(Strings.wrongCredentials, color = Color.Red, fontSize = 14.sp) }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { val u = users.find { it.username == username && it.password == password }; if (u != null) onLogin(u) else error = true }, Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(Strings.login, fontSize = 18.sp) }
                }
            }
        }
    }
}

// ============================================================
// Main Content — #2 no language, #4 pass username
// ============================================================

@Composable
fun MainContent(dataManager: DataManager, isDarkMode: Boolean, onToggleDarkMode: () -> Unit,
    loggedInUser: AppUser, appUsers: List<AppUser>, onUsersChange: (List<AppUser>) -> Unit, onLogout: () -> Unit) {
    val isAdmin = loggedInUser.role == "admin"
    var isAdminMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val printerManager = remember { PrinterManager(context) }
    var products by remember { mutableStateOf(dataManager.loadProducts() ?: getInitialProducts()) }
    var tables by remember { mutableStateOf(dataManager.loadTables() ?: getInitialTables(17)) }
    var tableOrders by remember { mutableStateOf(dataManager.loadTableOrders() ?: emptyMap()) }
    var selectedTable by remember { mutableStateOf<Table?>(null) }
    var orderHistory by remember { mutableStateOf(dataManager.loadOrderHistory() ?: emptyList()) }
    var categoryCustomizations by remember { mutableStateOf(dataManager.loadCustomizations() ?: getInitialCustomizations()) }
    var categories by remember { mutableStateOf(dataManager.loadCategories() ?: listOf("Καφέδες", "Αναψυκτικά", "Μπίρες", "Cocktails", "Ποτά", "Snacks")) }

    LaunchedEffect(tableOrders) { dataManager.saveTableOrders(tableOrders) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(when { isAdminMode -> Strings.adminPanel; selectedTable != null -> selectedTable!!.name; else -> Strings.selectTable }) },
            navigationIcon = { if (selectedTable != null && !isAdminMode) IconButton(onClick = { selectedTable = null }) { Icon(Icons.Default.ArrowBack, Strings.back) } },
            actions = {
                IconButton(onClick = onToggleDarkMode) { Text(if (isDarkMode) "☀️" else "🌙", fontSize = 20.sp) }
                if (isAdmin) IconButton(onClick = { isAdminMode = !isAdminMode }) { Icon(if (isAdminMode) Icons.Default.ExitToApp else Icons.Default.Settings, contentDescription = null) }
                IconButton(onClick = onLogout) { Icon(Icons.Default.AccountCircle, Strings.logout) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, actionIconContentColor = Color.White, navigationIconContentColor = Color.White)
        )
    }) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                isAdminMode && isAdmin -> AdminPanel(products, categories, tables, orderHistory, categoryCustomizations, appUsers, printerManager, dataManager,
                    { products = it; dataManager.saveProducts(it) }, { categories = it; dataManager.saveCategories(it) },
                    { tables = it; dataManager.saveTables(it) }, { categoryCustomizations = it; dataManager.saveCustomizations(it) },
                    onUsersChange)
                selectedTable != null -> {
                    val table = selectedTable!!; val cto = tableOrders[table.id]
                    OrderScreen(products, categories, categoryCustomizations, table,
                        cto?.items?.toList() ?: emptyList(), cto?.status ?: OrderStatus.DRAFT,
                        tables.filter { it.isActive }, tableOrders, printerManager, loggedInUser.username,
                        onOrderUpdate = { items, status ->
                            if (items.isEmpty()) tableOrders = tableOrders - table.id
                            else tableOrders = tableOrders + (table.id to TableOrder(table.id, table.name, items.toMutableList(), status))
                        },
                        onTransferTable = { fromId, toTable -> val o = tableOrders[fromId]; if (o != null) { tableOrders = tableOrders - fromId + (toTable.id to o.copy(tableId = toTable.id, tableName = toTable.name)); selectedTable = toTable } },
                        onCloseOrder = { co -> orderHistory = orderHistory + co; dataManager.saveOrderHistory(orderHistory); tableOrders = tableOrders - table.id; selectedTable = null },
                        onBack = { selectedTable = null })
                }
                else -> TableSelectionScreen(tables.filter { it.isActive }, tableOrders) { selectedTable = it }
            }
        }
    }
}

// ============================================================
// Table Selection
// ============================================================

@Composable
fun TableSelectionScreen(tables: List<Table>, tableOrders: Map<String, TableOrder>, onTableSelected: (Table) -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Text(Strings.selectTable, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 16.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(tables) { table -> val to = tableOrders[table.id]; val has = to?.isEmpty() == false; TableCard(table, has, to?.status, to?.getTotal() ?: 0.0) { onTableSelected(table) } }
        }
    }
}

@Composable
fun TableCard(table: Table, hasOrder: Boolean, orderStatus: OrderStatus?, total: Double, onClick: () -> Unit) {
    val cc = when { !hasOrder -> Color(0xFF4CAF50); orderStatus == OrderStatus.DRAFT -> Color(0xFFFF9800); orderStatus == OrderStatus.SENT -> Color(0xFFF44336); else -> Color(0xFF4CAF50) }
    val st = when { !hasOrder -> Strings.empty; orderStatus == OrderStatus.DRAFT -> Strings.draft; orderStatus == OrderStatus.SENT -> Strings.sent; else -> Strings.empty }
    Card(Modifier.aspectRatio(1f).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = cc), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(if (hasOrder) Icons.Default.Face else Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(28.dp)); Spacer(Modifier.height(4.dp))
            Text(table.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
            if (hasOrder) { Text("€${String.format("%.2f", total)}", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium) }
            Text(st, fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

// ============================================================
// Admin Panel
// ============================================================

@Composable
fun AdminPanel(products: List<Product>, categories: List<String>, tables: List<Table>, orderHistory: List<CompletedOrder>,
    categoryCustomizations: Map<String, List<CustomizationOption>>, appUsers: List<AppUser>, printerManager: PrinterManager, dataManager: DataManager,
    onProductsChange: (List<Product>) -> Unit, onCategoriesChange: (List<String>) -> Unit, onTablesChange: (List<Table>) -> Unit,
    onCustomizationsChange: (Map<String, List<CustomizationOption>>) -> Unit, onUsersChange: (List<AppUser>) -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(Strings.products, Strings.options, Strings.tables, Strings.users, Strings.printer, Strings.history, Strings.statistics)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) { tabs.forEachIndexed { i, t -> Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) }) } }
        when (selectedTab) {
            0 -> ProductsAdminPanel(products, categories, onProductsChange)
            1 -> CustomizationAdminPanel(categories, categoryCustomizations, onCategoriesChange, onCustomizationsChange, products, onProductsChange)
            2 -> TablesAdminPanel(tables, onTablesChange)
            3 -> UsersAdminPanel(appUsers, onUsersChange)
            4 -> PrinterSettingsPanel(printerManager)
            5 -> OrderHistoryPanel(orderHistory)
            6 -> StatisticsPanel(orderHistory, printerManager, dataManager)
        }
    }
}

// ============================================================
// Users Admin Panel
// ============================================================

@Composable
fun UsersAdminPanel(users: List<AppUser>, onUsersChange: (List<AppUser>) -> Unit) {
    var showAddDialog by remember { mutableStateOf(false) }; var editingUser by remember { mutableStateOf<AppUser?>(null) }; var showError by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            items(users) { user ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(user.username, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface); Text(if (user.role == "admin") Strings.admin else Strings.waiter, fontSize = 13.sp, color = if (user.role == "admin") Color(0xFFFF9800) else Color.Gray) }
                        Row {
                            IconButton(onClick = { editingUser = user }) { Icon(Icons.Default.Edit, Strings.edit, tint = Color.Gray) }
                            IconButton(onClick = { if (user.role == "admin" && users.count { it.role == "admin" } <= 1) showError = Strings.cantDeleteLastAdmin else onUsersChange(users.filter { it.id != user.id }) }) { Icon(Icons.Default.Delete, Strings.delete, tint = Color.Red) }
                        }
                    }
                }
            }
        }
        Button(onClick = { showAddDialog = true }, Modifier.fillMaxWidth().padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(Strings.addUser) }
    }
    if (showAddDialog || editingUser != null) UserDialog(editingUser, { showAddDialog = false; editingUser = null }) { u -> if (editingUser != null) onUsersChange(users.map { if (it.id == u.id) u else it }) else onUsersChange(users + u); showAddDialog = false; editingUser = null }
    if (showError != null) AlertDialog(onDismissRequest = { showError = null }, title = { Text("⚠️") }, text = { Text(showError!!) }, confirmButton = { Button(onClick = { showError = null }) { Text("OK") } })
}

@Composable
fun UserDialog(user: AppUser?, onDismiss: () -> Unit, onSave: (AppUser) -> Unit) {
    var username by remember { mutableStateOf(user?.username ?: "") }; var password by remember { mutableStateOf(user?.password ?: "") }; var role by remember { mutableStateOf(user?.role ?: "waiter") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (user == null) Strings.newUser else Strings.editUser) },
        text = { Column {
            OutlinedTextField(username, { username = it }, label = { Text(Strings.username) }, singleLine = true, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
            OutlinedTextField(password, { password = it }, label = { Text(Strings.password) }, singleLine = true, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp))
            Text("${Strings.role}:", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = role == "admin", onClick = { role = "admin" }, label = { Text(Strings.admin) }, modifier = Modifier.weight(1f)); FilterChip(selected = role == "waiter", onClick = { role = "waiter" }, label = { Text(Strings.waiter) }, modifier = Modifier.weight(1f)) }
        } },
        confirmButton = { Button(onClick = { if (username.isNotBlank() && password.isNotBlank()) onSave(AppUser(id = user?.id ?: UUID.randomUUID().toString(), username = username, password = password, role = role)) }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } })
}

// ============================================================
// Customization Admin Panel
// ============================================================

@Composable
fun CustomizationAdminPanel(categories: List<String>, categoryCustomizations: Map<String, List<CustomizationOption>>,
    onCategoriesChange: (List<String>) -> Unit, onCustomizationsChange: (Map<String, List<CustomizationOption>>) -> Unit,
    products: List<Product>, onProductsChange: (List<Product>) -> Unit) {
    var expandedCategory by remember { mutableStateOf<String?>(null) }; var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategoryName by remember { mutableStateOf<String?>(null) }; var showDeleteCategoryConfirm by remember { mutableStateOf<String?>(null) }
    var showAddOptionDialog by remember { mutableStateOf<String?>(null) }; var editingOption by remember { mutableStateOf<Pair<String, CustomizationOption>?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            items(categories) { category ->
                val isExpanded = expandedCategory == category; val options = categoryCustomizations[category] ?: emptyList(); val pc = products.count { it.category == category }
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable { expandedCategory = if (isExpanded) null else category }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(category, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface); Text("${options.size} επιλογές • $pc προϊόντα", fontSize = 12.sp, color = Color.Gray) }
                            Row { IconButton(onClick = { editingCategoryName = category }) { Icon(Icons.Default.Edit, null, tint = Color.Gray) }; IconButton(onClick = { showDeleteCategoryConfirm = category }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }; Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color.Gray) }
                        }
                        if (isExpanded) {
                            Divider(color = Color.Gray.copy(alpha = 0.3f))
                            if (options.isEmpty()) Text(Strings.noOptions, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                            else options.forEach { option ->
                                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(option.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface); Text(option.choices.joinToString("  •  "), fontSize = 12.sp, color = Color.Gray) }
                                    Row { IconButton(onClick = { editingOption = Pair(category, option) }, Modifier.size(36.dp)) { Icon(Icons.Default.Edit, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) }; IconButton(onClick = { val u = categoryCustomizations.toMutableMap(); u[category] = (u[category] ?: emptyList()).filter { it.id != option.id }; onCustomizationsChange(u) }, Modifier.size(36.dp)) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(18.dp)) } }
                                }
                            }
                            TextButton(onClick = { showAddOptionDialog = category }, Modifier.padding(start = 16.dp, bottom = 8.dp)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Strings.addOption) }
                        }
                    }
                }
            }
        }
        Button(onClick = { showAddCategoryDialog = true }, Modifier.fillMaxWidth().padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(Strings.addCategory) }
    }
    if (showAddCategoryDialog) SimpleNameDialog(Strings.newCategory, "") { name -> if (name != null && name.isNotBlank() && !categories.contains(name)) onCategoriesChange(categories + name); showAddCategoryDialog = false }
    if (editingCategoryName != null) SimpleNameDialog(Strings.renameCategory, editingCategoryName!!) { newName -> if (newName != null && newName.isNotBlank()) { val old = editingCategoryName!!; onCategoriesChange(categories.map { if (it == old) newName else it }); onProductsChange(products.map { if (it.category == old) it.copy(category = newName) else it }); val u = categoryCustomizations.toMutableMap(); val o = u.remove(old); if (o != null) u[newName] = o; onCustomizationsChange(u) }; editingCategoryName = null }
    if (showDeleteCategoryConfirm != null) { val cat = showDeleteCategoryConfirm!!; val pc = products.count { it.category == cat }
        AlertDialog(onDismissRequest = { showDeleteCategoryConfirm = null }, title = { Text(Strings.deleteCategory) }, text = { Column { Text("${Strings.deleteCategoryConfirm} \"$cat\";"); if (pc > 0) { Spacer(Modifier.height(8.dp)); Text("⚠️ $pc ${Strings.productsWillBeDeleted}", color = Color.Red, fontWeight = FontWeight.Bold) } } },
            confirmButton = { Button(onClick = { onProductsChange(products.filter { it.category != cat }); onCategoriesChange(categories.filter { it != cat }); val u = categoryCustomizations.toMutableMap(); u.remove(cat); onCustomizationsChange(u); showDeleteCategoryConfirm = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text(Strings.delete) } }, dismissButton = { TextButton(onClick = { showDeleteCategoryConfirm = null }) { Text(Strings.cancel) } }) }
    if (showAddOptionDialog != null || editingOption != null) { val catName = showAddOptionDialog ?: editingOption!!.first; val existing = editingOption?.second
        CustomizationOptionDialog(if (existing != null) Strings.editOption else "${Strings.newOption} \"$catName\"", existing?.name ?: "", existing?.choices ?: emptyList()) { name, choices ->
            if (name != null && choices != null) { val u = categoryCustomizations.toMutableMap(); val cur = (u[catName] ?: emptyList()).toMutableList(); if (existing != null) { val i = cur.indexOfFirst { it.id == existing.id }; if (i >= 0) cur[i] = existing.copy(name = name, choices = choices) } else cur.add(CustomizationOption(name = name, choices = choices)); u[catName] = cur; onCustomizationsChange(u) }
            showAddOptionDialog = null; editingOption = null
        }
    }
}

@Composable
fun SimpleNameDialog(title: String, initialName: String, onResult: (String?) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(onDismissRequest = { onResult(null) }, title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(Strings.name) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onResult(name.trim()) }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text(Strings.cancel) } })
}

@Composable
fun CustomizationOptionDialog(title: String, initialName: String, initialChoices: List<String>, onResult: (String?, List<String>?) -> Unit) {
    var name by remember { mutableStateOf(initialName) }; var choices by remember { mutableStateOf(initialChoices.toMutableList()) }; var newChoice by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { onResult(null, null) }, title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(name, { name = it }, label = { Text(Strings.optionName) }, singleLine = true, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(16.dp)); Text(Strings.choices, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
            choices.forEachIndexed { i, c -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { Text(c, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)); IconButton(onClick = { choices = choices.toMutableList().also { it.removeAt(i) } }, Modifier.size(32.dp)) { Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(16.dp)) } } }
            Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { OutlinedTextField(newChoice, { newChoice = it }, label = { Text(Strings.newChoice) }, singleLine = true, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); IconButton(onClick = { if (newChoice.isNotBlank()) { choices = (choices + newChoice.trim()).toMutableList(); newChoice = "" } }) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) } }
        } },
        confirmButton = { Button(onClick = { if (name.isNotBlank() && choices.isNotEmpty()) onResult(name.trim(), choices.toList()) }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = { onResult(null, null) }) { Text(Strings.cancel) } })
}

// ============================================================
// Products Admin Panel
// ============================================================

@Composable
fun ProductsAdminPanel(products: List<Product>, categories: List<String>, onProductsChange: (List<Product>) -> Unit) {
    var selCat by remember { mutableStateOf(categories.firstOrNull() ?: "") }; var showAddDialog by remember { mutableStateOf(false) }; var editingProduct by remember { mutableStateOf<Product?>(null) }
    Column(Modifier.fillMaxSize()) {
        if (categories.isNotEmpty()) ScrollableTabRow(selectedTabIndex = maxOf(0, categories.indexOf(selCat)), containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary) { categories.forEach { c -> Tab(selected = selCat == c, onClick = { selCat = c }, text = { Text(c) }) } }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) { items(products.filter { it.category == selCat }) { p -> ProductCard(p, { editingProduct = p }, { onProductsChange(products.filter { it.id != p.id }) }); Spacer(Modifier.height(8.dp)) } }
        Button(onClick = { showAddDialog = true }, Modifier.fillMaxWidth().padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(Strings.addProduct) }
    }
    if (showAddDialog || editingProduct != null) ProductDialog(editingProduct, selCat, categories, { showAddDialog = false; editingProduct = null }) { p -> if (editingProduct != null) onProductsChange(products.map { if (it.id == p.id) p else it }) else onProductsChange(products + p); showAddDialog = false; editingProduct = null }
}

@Composable
fun ProductCard(product: Product, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface); Text("€${String.format("%.2f", product.price)}", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp) }
            Row { IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = Color.Gray) }; IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Red) } }
        }
    }
}

@Composable
fun ProductDialog(product: Product?, category: String, categories: List<String>, onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var name by remember { mutableStateOf(product?.name ?: "") }; var price by remember { mutableStateOf(product?.price?.toString() ?: "") }; var selCat by remember { mutableStateOf(product?.category ?: category) }; var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (product == null) Strings.newProduct else Strings.edit) },
        text = { Column {
            OutlinedTextField(name, { name = it }, label = { Text(Strings.name) }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
            OutlinedTextField(price, { price = it }, label = { Text("${Strings.price} (€)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded, { expanded = it }) { OutlinedTextField(selCat, {}, readOnly = true, label = { Text(Strings.category) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor()); ExposedDropdownMenu(expanded, { expanded = false }) { categories.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { selCat = c; expanded = false }) } } }
        } },
        confirmButton = { Button(onClick = { val pv = price.toDoubleOrNull(); if (name.isNotBlank() && pv != null && pv > 0) onSave(Product(id = product?.id ?: UUID.randomUUID().toString(), name = name, price = pv, category = selCat)) }) { Text(Strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } })
}

// ============================================================
// Tables Admin Panel
// ============================================================

@Composable
fun TablesAdminPanel(tables: List<Table>, onTablesChange: (List<Table>) -> Unit) {
    var showAddDialog by remember { mutableStateOf(false) }; var editingTable by remember { mutableStateOf<Table?>(null) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            items(tables) { table ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = if (table.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(table.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Row { IconButton(onClick = { editingTable = table }) { Icon(Icons.Default.Edit, null, tint = Color.Gray) }; Switch(checked = table.isActive, onCheckedChange = { onTablesChange(tables.map { if (it.id == table.id) it.copy(isActive = !it.isActive) else it }) }); IconButton(onClick = { onTablesChange(tables.filter { it.id != table.id }) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) } }
                    }
                }
            }
        }
        Button(onClick = { showAddDialog = true }, Modifier.fillMaxWidth().padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(Strings.addTable) }
    }
    if (showAddDialog) SimpleNameDialog(Strings.newTable, "") { n -> if (n != null) onTablesChange(tables + Table(name = n)); showAddDialog = false }
    if (editingTable != null) SimpleNameDialog(Strings.renameTable, editingTable!!.name) { n -> if (n != null) onTablesChange(tables.map { if (it.id == editingTable!!.id) it.copy(name = n) else it }); editingTable = null }
}

// ============================================================
// Order Screen — #4 waiterName, #5 fix +/-
// ============================================================

@Composable
fun OrderScreen(products: List<Product>, categories: List<String>, categoryCustomizations: Map<String, List<CustomizationOption>>,
    table: Table, currentOrder: List<OrderItem>, currentStatus: OrderStatus,
    allTables: List<Table>, tableOrders: Map<String, TableOrder>, printerManager: PrinterManager, waiterName: String,
    onOrderUpdate: (List<OrderItem>, OrderStatus) -> Unit, onTransferTable: (String, Table) -> Unit,
    onCloseOrder: (CompletedOrder) -> Unit, onBack: () -> Unit) {
    var selCat by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    var orderItems by remember(table.id) { mutableStateOf(currentOrder.toList()) }
    var orderStatus by remember(table.id) { mutableStateOf(currentStatus) }
    var showOrderSummary by remember { mutableStateOf(false) }; var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var showTransferDialog by remember { mutableStateOf(false) }; var showSplitBillDialog by remember { mutableStateOf(false) }
    var sentItemsCount by remember(table.id) { mutableStateOf(if (currentStatus == OrderStatus.SENT) currentOrder.size else 0) }
    var paidItemIndices by remember(table.id) { mutableStateOf<Set<Int>>(emptySet()) }
    val hasNewItems = orderItems.size > sentItemsCount
    val scope = rememberCoroutineScope()
    fun saveOrder() { onOrderUpdate(orderItems, orderStatus) }
    fun sendAndPrint() {
        val isNewItems = orderStatus == OrderStatus.SENT && hasNewItems
        val itemsToPrint = if (isNewItems) orderItems.drop(sentItemsCount) else orderItems
        orderStatus = OrderStatus.SENT; sentItemsCount = orderItems.size; saveOrder()
        scope.launch {
            try {
                val receipt = if (isNewItems) printerManager.buildNewItemsReceipt(table.name, itemsToPrint, waiterName)
                else printerManager.buildOrderReceipt(table.name, itemsToPrint, waiterName)
                printerManager.print(receipt)
            } catch (_: Exception) {}
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (categories.isNotEmpty()) ScrollableTabRow(selectedTabIndex = maxOf(0, categories.indexOf(selCat)), containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary) { categories.forEach { c -> Tab(selected = selCat == c, onClick = { selCat = c }, text = { Text(c) }) } }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) { items(products.filter { it.category == selCat }) { p -> ProductOrderCard(p) { selectedProduct = p }; Spacer(Modifier.height(8.dp)) } }
        if (orderItems.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = when (orderStatus) { OrderStatus.DRAFT -> Color(0xFFFF9800); OrderStatus.SENT -> Color(0xFFF44336) }), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("${orderItems.sumOf { it.quantity }} ${Strings.products}", color = Color.White, fontSize = 14.sp); Text("€${String.format("%.2f", orderItems.sumOf { it.product.price * it.quantity })}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(if (orderStatus == OrderStatus.DRAFT) Strings.draft else Strings.sent, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { showTransferDialog = true }, Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))) { Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White) }
                            if (orderStatus == OrderStatus.SENT) IconButton(onClick = { showSplitBillDialog = true }, Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))) { Icon(Icons.Default.Person, null, tint = Color.White) }
                            Button(onClick = { showOrderSummary = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = if (orderStatus == OrderStatus.DRAFT) Color(0xFFFF9800) else Color(0xFFF44336))) { Text(Strings.view) }
                        }
                    }
                    if (orderStatus == OrderStatus.DRAFT || hasNewItems) { Spacer(Modifier.height(8.dp)); Button(onClick = { sendAndPrint() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFFF9800))) { Icon(Icons.Default.Send, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(if (hasNewItems && orderStatus == OrderStatus.SENT) Strings.sendNew else Strings.sendToBar) } }
                }
            }
        }
    }

    if (selectedProduct != null) ProductCustomizationDialog(selectedProduct!!, categoryCustomizations[selectedProduct!!.category] ?: emptyList(), { selectedProduct = null }) { item -> orderItems = orderItems + item; saveOrder(); selectedProduct = null }

    // #5 — Fixed OrderSummaryDialog with proper +/- handling
    if (showOrderSummary) OrderSummaryDialog(table.name, orderItems, orderStatus, hasNewItems, waiterName,
        onDismiss = { showOrderSummary = false },
        onPrint = { sendAndPrint(); showOrderSummary = false },
        onClose = { m -> onCloseOrder(CompletedOrder(tableName = table.name, items = orderItems, total = orderItems.sumOf { it.product.price * it.quantity }, paymentMethod = m, waiterName = waiterName)); showOrderSummary = false },
        onUpdateOrder = { updated -> orderItems = updated; saveOrder() })

    if (showTransferDialog) TransferTableDialog(table, allTables.filter { it.id != table.id && tableOrders[it.id]?.isEmpty() != false }, { showTransferDialog = false }) { t -> onTransferTable(table.id, t); showTransferDialog = false }

    // #6 — Split bill with paid tracking
    if (showSplitBillDialog) SplitBillDialog(orderItems, orderItems.sumOf { it.product.price * it.quantity }, paidItemIndices,
        onDismiss = { showSplitBillDialog = false },
        onMarkPaid = { newPaid -> paidItemIndices = paidItemIndices + newPaid },
        onCloseFullyPaid = { onCloseOrder(CompletedOrder(tableName = table.name, items = orderItems, total = orderItems.sumOf { it.product.price * it.quantity }, paymentMethod = "Διαχωρισμός", waiterName = waiterName)); showSplitBillDialog = false })
}

@Composable
fun ProductCustomizationDialog(product: Product, customizationOptions: List<CustomizationOption>, onDismiss: () -> Unit, onAddToOrder: (OrderItem) -> Unit) {
    var quantity by remember { mutableStateOf(1) }; var notes by remember { mutableStateOf("") }; var selections by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(product.name) },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(Strings.quantity, fontWeight = FontWeight.Bold); Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { if (quantity > 1) quantity-- }) { Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold) }; Text("$quantity", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp)); IconButton(onClick = { quantity++ }) { Icon(Icons.Default.Add, "Plus") } } }
            if (customizationOptions.isNotEmpty()) { Divider(Modifier.padding(vertical = 8.dp)); customizationOptions.forEach { opt -> Text("${opt.name}:", fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { opt.choices.forEach { ch -> FilterChip(selected = selections[opt.name] == ch, onClick = { selections = selections.toMutableMap().apply { this[opt.name] = ch } }, label = { Text(ch, fontSize = 11.sp) }, modifier = Modifier.weight(1f)) } }; Spacer(Modifier.height(4.dp)) } }
            Divider(Modifier.padding(vertical = 8.dp)); Text(Strings.notes, fontWeight = FontWeight.Bold); OutlinedTextField(notes, { notes = it }, placeholder = { Text(Strings.notesPlaceholder) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), minLines = 2, maxLines = 4)
            Spacer(Modifier.height(8.dp)); Text("${Strings.total} €${String.format("%.2f", product.price * quantity)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        } },
        confirmButton = { Button(onClick = { onAddToOrder(OrderItem(product = product, quantity = quantity, customizations = selections.toMap(), notes = notes)) }) { Text(Strings.add) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } })
}

@Composable
fun ProductOrderCard(product: Product, onAddToOrder: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onAddToOrder() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
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
        text = { Column {
            Text("${Strings.transferFrom} ${currentTable.name} ${Strings.to}", modifier = Modifier.padding(bottom = 16.dp))
            if (availableTables.isEmpty()) Text(Strings.noAvailableTables, color = Color.Gray)
            else LazyColumn(Modifier.heightIn(max = 300.dp)) { items(availableTables) { t -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onTransfer(t) }, colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50))) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(t.name, color = Color.White, fontWeight = FontWeight.Bold); Icon(Icons.Default.ArrowForward, null, tint = Color.White) } } } }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } })
}

// ============================================================
// #6 — Split Bill with paid tracking
// ============================================================

@Composable
fun SplitBillDialog(orderItems: List<OrderItem>, total: Double, paidIndices: Set<Int>,
    onDismiss: () -> Unit, onMarkPaid: (Set<Int>) -> Unit, onCloseFullyPaid: () -> Unit) {
    var numberOfPeople by remember { mutableStateOf(2) }; var splitType by remember { mutableStateOf("equal") }
    var selectedItems by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val paidTotal = paidIndices.sumOf { idx -> if (idx < orderItems.size) orderItems[idx].product.price * orderItems[idx].quantity else 0.0 }
    val remainingTotal = total - paidTotal
    val allPaid = paidIndices.size == orderItems.size

    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.splitBill) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("${Strings.total} €${String.format("%.2f", total)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            if (paidTotal > 0) { Text("${Strings.paid} €${String.format("%.2f", paidTotal)}", fontSize = 14.sp, color = Color(0xFF4CAF50)); Text("${Strings.remaining} €${String.format("%.2f", remainingTotal)}", fontSize = 14.sp, color = Color.Gray) }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = splitType == "equal", onClick = { splitType = "equal" }, label = { Text(Strings.equalParts) }, modifier = Modifier.weight(1f))
                FilterChip(selected = splitType == "custom", onClick = { splitType = "custom"; selectedItems = emptySet() }, label = { Text(Strings.perProduct) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            if (splitType == "equal") {
                Text(Strings.numberOfPeople, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) { IconButton(onClick = { if (numberOfPeople > 2) numberOfPeople-- }) { Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold) }; Text("$numberOfPeople", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp)); IconButton(onClick = { if (numberOfPeople < 10) numberOfPeople++ }) { Icon(Icons.Default.Add, "Plus") } }
                Divider(Modifier.padding(vertical = 8.dp)); Text(Strings.eachPersonPays, fontSize = 14.sp, color = Color.Gray)
                Text("€${String.format("%.2f", remainingTotal / numberOfPeople)}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            } else {
                if (allPaid) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50))) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✓ ${Strings.allPaid}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                } else {
                    Text(Strings.selectProducts, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                }
                orderItems.forEachIndexed { i, item ->
                    val isPaid = paidIndices.contains(i)
                    val isSel = selectedItems.contains(i)
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !isPaid) { if (!isPaid) selectedItems = if (isSel) selectedItems - i else selectedItems + i },
                        colors = CardDefaults.cardColors(containerColor = when { isPaid -> Color(0xFF4CAF50).copy(alpha = 0.15f); isSel -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f); else -> MaterialTheme.colorScheme.surface }),
                        border = when { isPaid -> androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4CAF50)); isSel -> androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary); else -> null }) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                if (isPaid) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                                else Checkbox(checked = isSel, onCheckedChange = { selectedItems = if (isSel) selectedItems - i else selectedItems + i })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(item.product.name, fontWeight = FontWeight.Medium, textDecoration = if (isPaid) TextDecoration.LineThrough else TextDecoration.None, color = if (isPaid) Color.Gray else MaterialTheme.colorScheme.onSurface)
                                    Text("${item.quantity}x €${String.format("%.2f", item.product.price)}", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("€${String.format("%.2f", item.product.price * item.quantity)}", fontWeight = FontWeight.Bold, color = if (isPaid) Color.Gray else MaterialTheme.colorScheme.primary)
                                if (isPaid) Text(Strings.markAsPaid, fontSize = 11.sp, color = Color(0xFF4CAF50))
                            }
                        }
                    }
                }

                if (selectedItems.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    val selTotal = selectedItems.sumOf { idx -> orderItems[idx].product.price * orderItems[idx].quantity }
                    Text("${Strings.selected} €${String.format("%.2f", selTotal)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onMarkPaid(selectedItems); selectedItems = emptySet() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                        Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text(Strings.markAsPaid)
                    }
                }
            }
        } },
        confirmButton = {
            if (allPaid && splitType == "custom") {
                Button(onClick = onCloseFullyPaid, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text(Strings.closeOrder) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.close) } })
}

// ============================================================
// #5 — Fixed Order Summary Dialog with proper +/- and − icon
// ============================================================

@Composable
fun OrderSummaryDialog(tableName: String, orderItems: List<OrderItem>, orderStatus: OrderStatus, hasNewItems: Boolean, waiterName: String,
    onDismiss: () -> Unit, onPrint: () -> Unit, onClose: (String) -> Unit, onUpdateOrder: (List<OrderItem>) -> Unit) {
    var items by remember { mutableStateOf(orderItems.toList()) }
    var showPaymentDialog by remember { mutableStateOf(false) }

    // #5 — Proper immutable update functions
    fun updateItemQuantity(index: Int, newQty: Int) {
        if (newQty < 1) return
        items = items.toMutableList().also { list -> val old = list[index]; list[index] = OrderItem(product = old.product, quantity = newQty, customizations = old.customizations, notes = old.notes) }
        onUpdateOrder(items)
    }
    fun removeItem(index: Int) { items = items.toMutableList().also { it.removeAt(index) }; onUpdateOrder(items) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("$tableName - ${Strings.order}") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            // #4 — Show waiter name
            if (waiterName.isNotEmpty()) { Text("${Strings.waiterLabel} $waiterName", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp)) }

            items.forEachIndexed { index, item ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                val d = item.getDisplayDetails(); if (d.isNotEmpty()) Text(d, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                                Text("€${String.format("%.2f", item.product.price)} x ${item.quantity}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // #5 — Proper minus button with − symbol
                                IconButton(onClick = { updateItemQuantity(index, item.quantity - 1) }) { Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Gray) }
                                Text("${item.quantity}", fontWeight = FontWeight.Bold)
                                IconButton(onClick = { updateItemQuantity(index, item.quantity + 1) }) { Icon(Icons.Default.Add, "Add", tint = Color.Gray) }
                                IconButton(onClick = { removeItem(index) }) { Icon(Icons.Default.Delete, Strings.delete, tint = Color.Red) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp)); Divider()
            Text("${Strings.total} €${String.format("%.2f", items.sumOf { it.product.price * it.quantity })}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.End).padding(top = 8.dp))
        } },
        confirmButton = { Column(horizontalAlignment = Alignment.End) {
            if (orderStatus == OrderStatus.DRAFT || hasNewItems) { Button(onClick = onPrint, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text(if (hasNewItems && orderStatus == OrderStatus.SENT) Strings.sendNew else Strings.send) }; Spacer(Modifier.height(8.dp)) }
            if (orderStatus == OrderStatus.SENT && !hasNewItems) { Button(onClick = { showPaymentDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text(Strings.close) } }
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.back) } })
    if (showPaymentDialog) PaymentMethodDialog({ showPaymentDialog = false }) { m -> showPaymentDialog = false; onClose(m) }
}

@Composable
fun PaymentMethodDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(Strings.paymentMethod) },
        text = { Column {
            listOf(Strings.cash to "Μετρητά", Strings.card to "Κάρτα").forEach { (label, value) ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect(value) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp); Icon(if (value == "Μετρητά") Icons.Default.ShoppingCart else Icons.Default.Favorite, null, tint = Color.White) }
                }
            }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } })
}

// ============================================================
// #3 — Order History (persistent, never cleared)
// ============================================================

@Composable
fun OrderHistoryPanel(orderHistory: List<CompletedOrder>) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    var showToday by remember { mutableStateOf(false) }
    val todayStart = remember { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis }
    val filtered = if (showToday) orderHistory.filter { it.timestamp >= todayStart } else orderHistory

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (orderHistory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.DateRange, null, Modifier.size(64.dp), tint = Color.Gray); Spacer(Modifier.height(16.dp)); Text(Strings.noHistory, color = Color.Gray, fontSize = 18.sp) } }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${Strings.orderHistory} (${filtered.size})", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !showToday, onClick = { showToday = false }, label = { Text(Strings.allHistory) })
                    FilterChip(selected = showToday, onClick = { showToday = true }, label = { Text(Strings.todayOnly) })
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn {
                items(filtered.sortedByDescending { it.timestamp }) { order ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(order.tableName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface); Text("€${String.format("%.2f", order.total)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(dateFormat.format(Date(order.timestamp)), fontSize = 12.sp, color = Color.Gray)
                                if (order.waiterName.isNotEmpty()) Text("${Strings.waiterLabel} ${order.waiterName}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Spacer(Modifier.height(8.dp))
                            order.items.forEach { item -> val d = item.getDisplayDetails(); Text("${item.quantity}x ${item.product.name}" + if (d.isNotEmpty()) " ($d)" else "", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
                            Divider(Modifier.padding(vertical = 8.dp)); Text("${Strings.payment} ${order.paymentMethod}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// #1 removed average, #3 shift tracking (history never cleared)
// ============================================================

@Composable
fun StatisticsPanel(orderHistory: List<CompletedOrder>, printerManager: PrinterManager, dataManager: DataManager) {
    var shiftStartTime by remember { mutableStateOf(dataManager.loadShiftStartTime()) }
    // Filter orders for current shift only
    val shiftOrders = orderHistory.filter { it.timestamp > shiftStartTime }
    val totalRevenue = shiftOrders.sumOf { it.total }; val totalOrders = shiftOrders.size
    val productStats = shiftOrders.flatMap { it.items }.groupBy { it.product.name }.mapValues { (_, items) -> items.sumOf { it.quantity } }.toList().sortedByDescending { it.second }.take(10)
    val categoryStats = shiftOrders.flatMap { it.items }.groupBy { it.product.category }.mapValues { (_, items) -> items.sumOf { it.product.price * it.quantity } }.toList().sortedByDescending { it.second }
    var showCloseShiftConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("${Strings.salesStatistics} (${Strings.currentShift})", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 16.dp))

        // #1 — Removed average, only revenue + orders
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Strings.totalRevenue, "€${String.format("%.2f", totalRevenue)}", Color(0xFF4CAF50), Modifier.weight(1f))
            StatCard(Strings.orders, "$totalOrders", Color(0xFF2196F3), Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = { showCloseShiftConfirm = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))) { Icon(Icons.Default.ExitToApp, null); Spacer(Modifier.width(8.dp)); Text(Strings.closeShift, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp))

        if (productStats.isNotEmpty()) { Text(Strings.topProducts, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground); Spacer(Modifier.height(8.dp)); productStats.forEachIndexed { i, (n, c) -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Row(verticalAlignment = Alignment.CenterVertically) { Text("${i+1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(30.dp)); Text(n, color = MaterialTheme.colorScheme.onSurface) }; Text("$c ${Strings.pcs}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } } } }
        Spacer(Modifier.height(24.dp))
        if (categoryStats.isNotEmpty()) { Text(Strings.salesByCategory, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground); Spacer(Modifier.height(8.dp)); categoryStats.forEach { (cat, rev) -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(cat, color = MaterialTheme.colorScheme.onSurface); Text("€${String.format("%.2f", rev)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } } } }
        if (shiftOrders.isEmpty()) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(Strings.noDataYet, textAlign = TextAlign.Center, color = Color.Gray) }
    }

    if (showCloseShiftConfirm) {
        AlertDialog(onDismissRequest = { showCloseShiftConfirm = false }, title = { Text(Strings.closeShift) },
            text = { Column {
                Text(Strings.shiftSummary, fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(Modifier.height(12.dp))
                Text("${Strings.totalRevenue}: €${String.format("%.2f", totalRevenue)}", fontSize = 16.sp)
                Text("${Strings.orders}: $totalOrders", fontSize = 16.sp)
                if (categoryStats.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text(Strings.perCategory, fontWeight = FontWeight.Bold); categoryStats.forEach { (c, r) -> Text("  $c: €${String.format("%.2f", r)}", fontSize = 14.sp) } }
                Spacer(Modifier.height(16.dp)); Text("⚠️ ${Strings.historyWillReset}", color = Color.Red, fontWeight = FontWeight.Bold)
            } },
            confirmButton = { Button(onClick = {
                // Print shift summary, then reset shift timer (history stays!)
                scope.launch { try { printerManager.print(printerManager.buildShiftSummary(totalRevenue, totalOrders, if (totalOrders > 0) totalRevenue / totalOrders else 0.0, categoryStats, productStats)) } catch (_: Exception) {} }
                val now = System.currentTimeMillis(); shiftStartTime = now; dataManager.saveShiftStartTime(now); showCloseShiftConfirm = false
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
    var config by remember { mutableStateOf(printerManager.loadConfig()) }; var wifiIp by remember { mutableStateOf(config.wifiIp) }; var wifiPort by remember { mutableStateOf(config.wifiPort.toString()) }
    var isPrinting by remember { mutableStateOf(false) }; var printResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(printerManager.hasBluetoothPermission()) }
    var pairedDevices by remember { mutableStateOf(if (hasPermission) printerManager.getPairedDevices() else emptyList()) }
    val btPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        hasPermission = results.values.all { it }
        if (hasPermission) pairedDevices = printerManager.getPairedDevices()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(Strings.printerSettings, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground); Spacer(Modifier.height(16.dp))
        // Status card
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (config.type != "none") Color(0xFF4CAF50) else MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text(if (config.type != "none") Strings.printerConfigured else Strings.printerNotConfigured, fontWeight = FontWeight.Bold, color = if (config.type != "none") Color.White else MaterialTheme.colorScheme.onSurface)
                    if (config.type == "bluetooth") Text("Bluetooth: ${config.bluetoothName}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                    if (config.type == "wifi") Text("WiFi: ${config.wifiIp}:${config.wifiPort}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                }; if (config.type != "none") Icon(Icons.Default.CheckCircle, null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(24.dp)); Text("${Strings.connectionType}:", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = config.type == "none", onClick = { config = PrinterConfig(type = "none"); printerManager.saveConfig(config) }, label = { Text(Strings.noPrinter) }, modifier = Modifier.weight(1f))
            FilterChip(selected = config.type == "bluetooth", onClick = { config = config.copy(type = "bluetooth"); printerManager.saveConfig(config) }, label = { Text(Strings.bluetooth) }, modifier = Modifier.weight(1f))
            FilterChip(selected = config.type == "wifi", onClick = { config = config.copy(type = "wifi"); printerManager.saveConfig(config) }, label = { Text(Strings.wifi) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        if (config.type == "bluetooth") {
            LaunchedEffect(Unit) { if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) btPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)) }
            Text(Strings.pairedDevices, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.height(8.dp))
            if (!hasPermission) Card(Modifier.fillMaxWidth().clickable { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) btPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)) }, colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800))) { Text(Strings.bluetoothPermissionNeeded + " — Πατήστε για άδεια", color = Color.White, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
            else if (pairedDevices.isEmpty()) Text(Strings.noPairedDevices, color = Color.Gray, fontSize = 14.sp)
            else pairedDevices.forEach { device -> val dn = try { device.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }; val da = device.address; val isSel = config.bluetoothAddress == da
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { config = config.copy(bluetoothAddress = da, bluetoothName = dn); printerManager.saveConfig(config) },
                    colors = CardDefaults.cardColors(containerColor = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface),
                    border = if (isSel) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text(dn, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text(da, fontSize = 12.sp, color = Color.Gray) }; if (isSel) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
        if (config.type == "wifi") {
            OutlinedTextField(wifiIp, { wifiIp = it }, label = { Text(Strings.ipAddress) }, placeholder = { Text("192.168.1.100") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); Spacer(Modifier.height(8.dp))
            OutlinedTextField(wifiPort, { wifiPort = it }, label = { Text(Strings.port) }, placeholder = { Text("9100") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); Spacer(Modifier.height(12.dp))
            Button(onClick = { val p = wifiPort.toIntOrNull() ?: 9100; config = config.copy(wifiIp = wifiIp, wifiPort = p); printerManager.saveConfig(config) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(Strings.save) }
        }
        Spacer(Modifier.height(24.dp))
        if (config.type != "none") {
            Button(onClick = { isPrinting = true; printResult = null; scope.launch { val r = printerManager.print(buildTestReceipt()); isPrinting = false; printResult = if (r.isSuccess) Strings.printSuccess else "${Strings.printError}: ${r.exceptionOrNull()?.message ?: "Unknown"}" } },
                Modifier.fillMaxWidth(), enabled = !isPrinting && (config.type == "wifi" || config.bluetoothAddress.isNotEmpty()), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))) {
                if (isPrinting) { CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(Strings.printing) }
                else { Icon(Icons.Default.Build, null); Spacer(Modifier.width(8.dp)); Text(Strings.testPrint) }
            }
            if (printResult != null) { Spacer(Modifier.height(12.dp)); Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (printResult == Strings.printSuccess) Color(0xFF4CAF50) else Color(0xFFF44336))) { Text(printResult!!, color = Color.White, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) } }
        }
    }
}

fun buildTestReceipt(): ByteArray {
    val buf = mutableListOf<Byte>(); fun add(b: ByteArray) { buf.addAll(b.toList()) }; fun text(s: String) { add(s.toByteArray(Charsets.UTF_8)) }; fun nl() { text("\n") }
    add(EscPos.INIT); add(EscPos.ALIGN_CENTER); add(EscPos.DOUBLE_ON); text("e-Orders"); nl(); add(EscPos.NORMAL); nl(); text("Test Print OK!"); nl(); text(EscPos.line('-')); nl()
    text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())); nl(); add(EscPos.FEED_LINES); add(EscPos.PARTIAL_CUT); return buf.toByteArray()
}

// ============================================================
// Initial Data
// ============================================================

fun getInitialUsers(): List<AppUser> = listOf(AppUser(username = "admin", password = "admin123", role = "admin"))

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
    Product(name = "Chips", price = 2.00, category = "Snacks"), Product(name = "Nuts", price = 3.00, category = "Snacks"), Product(name = "Popcorn", price = 2.50, category = "Snacks"))

fun getInitialCustomizations(): Map<String, List<CustomizationOption>> = mapOf(
    "Καφέδες" to listOf(CustomizationOption(name = "Ζάχαρη", choices = listOf("Σκέτο", "Μέτριο", "Γλυκό")), CustomizationOption(name = "Γάλα", choices = listOf("Όχι", "Κανονικό", "Χωρίς λακτόζη", "Φυτικό"))))

fun getInitialTables(count: Int): List<Table> = (1..count).map { Table(name = "Τραπέζι $it") }
