@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.e_orders

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
        customizations.forEach { (key, value) ->
            if (value.isNotEmpty()) details.add("$key: $value")
        }
        if (notes.isNotEmpty()) details.add(notes)
        return if (details.isNotEmpty()) details.joinToString(", ") else ""
    }
}

enum class OrderStatus {
    DRAFT,
    SENT
}

data class TableOrder(
    val tableNumber: Int,
    val items: MutableList<OrderItem> = mutableListOf(),
    var status: OrderStatus = OrderStatus.DRAFT,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getTotal(): Double = items.sumOf { it.product.price * it.quantity }
    fun isEmpty(): Boolean = items.isEmpty()
}

data class Table(
    val number: Int,
    var isActive: Boolean = true
)

data class CompletedOrder(
    val id: String = UUID.randomUUID().toString(),
    val tableNumber: Int,
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
            var isDarkMode by remember { mutableStateOf(false) }

            CafeOrderTheme(darkTheme = isDarkMode) {
                CafeOrderApp(
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = { isDarkMode = !isDarkMode }
                )
            }
        }
    }
}

@Composable
fun CafeOrderTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFD4A574),
            secondary = Color(0xFF6B4423),
            background = Color(0xFF1A1A1A),
            surface = Color(0xFF2D2D2D),
            onBackground = Color.White,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6B4423),
            secondary = Color(0xFFD4A574),
            background = Color(0xFFFFFBF5),
            surface = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun CafeOrderApp(
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit
) {
    var isAdminMode by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var products by remember { mutableStateOf(getInitialProducts()) }
    var tables by remember { mutableStateOf(getInitialTables(17)) }
    var tableOrders by remember { mutableStateOf<Map<Int, TableOrder>>(emptyMap()) }
    var selectedTable by remember { mutableStateOf<Int?>(null) }
    var orderHistory by remember { mutableStateOf<List<CompletedOrder>>(emptyList()) }
    var categoryCustomizations by remember { mutableStateOf(getInitialCustomizations()) }
    var categories by remember { mutableStateOf(listOf("Καφέδες", "Αναψυκτικά", "Μπίρες", "Cocktails", "Ποτά", "Snacks")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isAdminMode -> "Admin Panel"
                            selectedTable != null -> "Τραπέζι $selectedTable"
                            else -> "Επιλογή Τραπεζιού"
                        }
                    )
                },
                navigationIcon = {
                    if (selectedTable != null && !isAdminMode) {
                        IconButton(onClick = { selectedTable = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Πίσω")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onToggleDarkMode) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.Star else Icons.Default.Star,
                            contentDescription = "Toggle Dark Mode",
                            tint = if (isDarkMode) Color.Yellow else Color.White
                        )
                    }

                    IconButton(onClick = {
                        if (isAdminMode) {
                            isAdminMode = false
                        } else {
                            showLoginDialog = true
                        }
                    }) {
                        Icon(
                            imageVector = if (isAdminMode) Icons.Default.ExitToApp else Icons.Default.Settings,
                            contentDescription = if (isAdminMode) "Exit Admin" else "Admin"
                        )
                    }
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
        Box(modifier = Modifier.padding(padding)) {
            when {
                isAdminMode -> {
                    AdminPanel(
                        products = products,
                        categories = categories,
                        tables = tables,
                        orderHistory = orderHistory,
                        categoryCustomizations = categoryCustomizations,
                        onProductsChange = { products = it },
                        onCategoriesChange = { categories = it },
                        onTablesChange = { tables = it },
                        onCustomizationsChange = { categoryCustomizations = it }
                    )
                }
                selectedTable != null -> {
                    val currentTableOrder = tableOrders[selectedTable!!]
                    OrderScreen(
                        products = products,
                        categories = categories,
                        categoryCustomizations = categoryCustomizations,
                        tableNumber = selectedTable!!,
                        currentOrder = currentTableOrder?.items?.toMutableList() ?: mutableListOf(),
                        currentStatus = currentTableOrder?.status ?: OrderStatus.DRAFT,
                        allTables = tables.filter { it.isActive },
                        tableOrders = tableOrders,
                        onOrderUpdate = { updatedItems, updatedStatus ->
                            if (updatedItems.isEmpty()) {
                                tableOrders = tableOrders - selectedTable!!
                            } else {
                                tableOrders = tableOrders + (selectedTable!! to TableOrder(
                                    tableNumber = selectedTable!!,
                                    items = updatedItems,
                                    status = updatedStatus
                                ))
                            }
                        },
                        onTransferTable = { fromTable, toTable ->
                            val order = tableOrders[fromTable]
                            if (order != null) {
                                tableOrders = tableOrders - fromTable + (toTable to order.copy(tableNumber = toTable))
                                selectedTable = toTable
                            }
                        },
                        onCloseOrder = { completedOrder ->
                            orderHistory = orderHistory + completedOrder
                            tableOrders = tableOrders - selectedTable!!
                            selectedTable = null
                        },
                        onBack = { selectedTable = null }
                    )
                }
                else -> {
                    TableSelectionScreen(
                        tables = tables.filter { it.isActive },
                        tableOrders = tableOrders,
                        onTableSelected = { selectedTable = it }
                    )
                }
            }
        }
    }

    if (showLoginDialog) {
        AdminLoginDialog(
            onDismiss = { showLoginDialog = false },
            onSuccess = {
                isAdminMode = true
                showLoginDialog = false
            }
        )
    }
}

// ============================================================
// Table Selection Screen
// ============================================================

@Composable
fun TableSelectionScreen(
    tables: List<Table>,
    tableOrders: Map<Int, TableOrder>,
    onTableSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            "Επιλέξτε Τραπέζι",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tables.sortedBy { it.number }) { table ->
                val tableOrder = tableOrders[table.number]
                val hasOrder = tableOrder?.isEmpty() == false
                TableCard(
                    tableNumber = table.number,
                    hasOrder = hasOrder,
                    orderStatus = tableOrder?.status,
                    total = tableOrder?.getTotal() ?: 0.0,
                    onClick = { onTableSelected(table.number) }
                )
            }
        }
    }
}

@Composable
fun TableCard(
    tableNumber: Int,
    hasOrder: Boolean,
    orderStatus: OrderStatus?,
    total: Double,
    onClick: () -> Unit
) {
    val cardColor = when {
        !hasOrder -> Color(0xFF4CAF50)
        orderStatus == OrderStatus.DRAFT -> Color(0xFFFF9800)
        orderStatus == OrderStatus.SENT -> Color(0xFFF44336)
        else -> Color(0xFF4CAF50)
    }

    val statusText = when {
        !hasOrder -> "Κενό"
        orderStatus == OrderStatus.DRAFT -> "Draft"
        orderStatus == OrderStatus.SENT -> "Στάλθηκε"
        else -> "Κενό"
    }

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (hasOrder) Icons.Default.Face else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "$tableNumber", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (hasOrder) {
                Text(text = "€${String.format("%.2f", total)}", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text(text = statusText, fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f))
            } else {
                Text(text = statusText, fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

// ============================================================
// Admin Login
// ============================================================

@Composable
fun AdminLoginDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Admin Login") },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = false },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error,
                    singleLine = true
                )
                if (error) {
                    Text("Λάθος κωδικός", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Προεπιλεγμένος κωδικός: admin123", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(onClick = { if (password == "admin123") onSuccess() else error = true }) { Text("Είσοδος") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ακύρωση") }
        }
    )
}

// ============================================================
// Admin Panel
// ============================================================

@Composable
fun AdminPanel(
    products: List<Product>,
    categories: List<String>,
    tables: List<Table>,
    orderHistory: List<CompletedOrder>,
    categoryCustomizations: Map<String, List<CustomizationOption>>,
    onProductsChange: (List<Product>) -> Unit,
    onCategoriesChange: (List<String>) -> Unit,
    onTablesChange: (List<Table>) -> Unit,
    onCustomizationsChange: (Map<String, List<CustomizationOption>>) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Προϊόντα", "Επιλογές", "Τραπέζια", "Ιστορικό", "Στατιστικά")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }

        when (selectedTab) {
            0 -> ProductsAdminPanel(products, categories, onProductsChange)
            1 -> CustomizationAdminPanel(categories, categoryCustomizations, onCategoriesChange, onCustomizationsChange, products, onProductsChange)
            2 -> TablesAdminPanel(tables, onTablesChange)
            3 -> OrderHistoryPanel(orderHistory)
            4 -> StatisticsPanel(orderHistory)
        }
    }
}

// ============================================================
// Customization Admin Panel
// ============================================================

@Composable
fun CustomizationAdminPanel(
    categories: List<String>,
    categoryCustomizations: Map<String, List<CustomizationOption>>,
    onCategoriesChange: (List<String>) -> Unit,
    onCustomizationsChange: (Map<String, List<CustomizationOption>>) -> Unit,
    products: List<Product>,
    onProductsChange: (List<Product>) -> Unit
) {
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategoryName by remember { mutableStateOf<String?>(null) }
    var showDeleteCategoryConfirm by remember { mutableStateOf<String?>(null) }
    var showAddOptionDialog by remember { mutableStateOf<String?>(null) }
    var editingOption by remember { mutableStateOf<Pair<String, CustomizationOption>?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            items(categories) { category ->
                val isExpanded = expandedCategory == category
                val options = categoryCustomizations[category] ?: emptyList()
                val productCount = products.count { it.category == category }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
                        // Category Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedCategory = if (isExpanded) null else category }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(category, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "${options.size} επιλογές • $productCount προϊόντα",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            Row {
                                IconButton(onClick = { editingCategoryName = category }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Μετονομασία", tint = Color.Gray)
                                }
                                IconButton(onClick = { showDeleteCategoryConfirm = category }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Διαγραφή", tint = Color.Red)
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand",
                                    tint = Color.Gray
                                )
                            }
                        }

                        // Customization Options (expanded)
                        if (isExpanded) {
                            Divider(color = Color.Gray.copy(alpha = 0.3f))

                            if (options.isEmpty()) {
                                Text(
                                    "Δεν υπάρχουν επιλογές — τα προϊόντα αυτής της κατηγορίας θα εμφανίζουν μόνο ποσότητα & σχόλια",
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            } else {
                                options.forEach { option ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(option.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text(option.choices.joinToString("  •  "), fontSize = 12.sp, color = Color.Gray)
                                        }
                                        Row {
                                            IconButton(onClick = { editingOption = Pair(category, option) }, modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Default.Edit, contentDescription = "Επεξεργασία", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    val updated = categoryCustomizations.toMutableMap()
                                                    updated[category] = (updated[category] ?: emptyList()).filter { it.id != option.id }
                                                    onCustomizationsChange(updated)
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Διαγραφή", tint = Color.Red, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            TextButton(
                                onClick = { showAddOptionDialog = category },
                                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Προσθήκη Επιλογής")
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = { showAddCategoryDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Προσθήκη Κατηγορίας")
        }
    }

    // Add Category Dialog
    if (showAddCategoryDialog) {
        SimpleNameDialog(
            title = "Νέα Κατηγορία",
            initialName = "",
            onDismiss = { showAddCategoryDialog = false },
            onSave = { name ->
                if (name.isNotBlank() && !categories.contains(name)) {
                    onCategoriesChange(categories + name)
                }
                showAddCategoryDialog = false
            }
        )
    }

    // Edit Category Name
    if (editingCategoryName != null) {
        SimpleNameDialog(
            title = "Μετονομασία Κατηγορίας",
            initialName = editingCategoryName!!,
            onDismiss = { editingCategoryName = null },
            onSave = { newName ->
                if (newName.isNotBlank()) {
                    val oldName = editingCategoryName!!
                    onCategoriesChange(categories.map { if (it == oldName) newName else it })
                    onProductsChange(products.map { if (it.category == oldName) it.copy(category = newName) else it })
                    val updated = categoryCustomizations.toMutableMap()
                    val opts = updated.remove(oldName)
                    if (opts != null) updated[newName] = opts
                    onCustomizationsChange(updated)
                }
                editingCategoryName = null
            }
        )
    }

    // Delete Category
    if (showDeleteCategoryConfirm != null) {
        val cat = showDeleteCategoryConfirm!!
        val productCount = products.count { it.category == cat }

        AlertDialog(
            onDismissRequest = { showDeleteCategoryConfirm = null },
            title = { Text("Διαγραφή Κατηγορίας") },
            text = {
                Column {
                    Text("Θέλετε να διαγράψετε την κατηγορία \"$cat\";")
                    if (productCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("⚠️ $productCount προϊόντα θα διαγραφούν!", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onProductsChange(products.filter { it.category != cat })
                        onCategoriesChange(categories.filter { it != cat })
                        val updated = categoryCustomizations.toMutableMap()
                        updated.remove(cat)
                        onCustomizationsChange(updated)
                        showDeleteCategoryConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Διαγραφή") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCategoryConfirm = null }) { Text("Ακύρωση") }
            }
        )
    }

    // Add/Edit Customization Option
    if (showAddOptionDialog != null || editingOption != null) {
        val categoryName = showAddOptionDialog ?: editingOption!!.first
        val existingOption = editingOption?.second

        CustomizationOptionDialog(
            title = if (existingOption != null) "Επεξεργασία Επιλογής" else "Νέα Επιλογή στο \"$categoryName\"",
            initialName = existingOption?.name ?: "",
            initialChoices = existingOption?.choices ?: emptyList(),
            onDismiss = {
                showAddOptionDialog = null
                editingOption = null
            },
            onSave = { name, choices ->
                val updated = categoryCustomizations.toMutableMap()
                val currentOptions = (updated[categoryName] ?: emptyList()).toMutableList()

                if (existingOption != null) {
                    val index = currentOptions.indexOfFirst { it.id == existingOption.id }
                    if (index >= 0) {
                        currentOptions[index] = existingOption.copy(name = name, choices = choices)
                    }
                } else {
                    currentOptions.add(CustomizationOption(name = name, choices = choices))
                }

                updated[categoryName] = currentOptions
                onCustomizationsChange(updated)
                showAddOptionDialog = null
                editingOption = null
            }
        )
    }
}

@Composable
fun SimpleNameDialog(title: String, initialName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Όνομα") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) { Text("Αποθήκευση") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ακύρωση") }
        }
    )
}

@Composable
fun CustomizationOptionDialog(
    title: String,
    initialName: String,
    initialChoices: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var choices by remember { mutableStateOf(initialChoices.toMutableList()) }
    var newChoice by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Όνομα (π.χ. Ζάχαρη)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Επιλογές:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                choices.forEachIndexed { index, choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(choice, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { choices = choices.toMutableList().also { it.removeAt(index) } },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Αφαίρεση", tint = Color.Red, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newChoice,
                        onValueChange = { newChoice = it },
                        label = { Text("Νέα επιλογή") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (newChoice.isNotBlank()) {
                            choices = (choices + newChoice.trim()).toMutableList()
                            newChoice = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Προσθήκη", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && choices.isNotEmpty()) onSave(name.trim(), choices.toList())
            }) { Text("Αποθήκευση") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ακύρωση") }
        }
    )
}

// ============================================================
// Products Admin Panel
// ============================================================

@Composable
fun ProductsAdminPanel(products: List<Product>, categories: List<String>, onProductsChange: (List<Product>) -> Unit) {
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (categories.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = maxOf(0, categories.indexOf(selectedCategory)),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                categories.forEach { category ->
                    Tab(selected = selectedCategory == category, onClick = { selectedCategory = category }, text = { Text(category) })
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            val categoryProducts = products.filter { it.category == selectedCategory }
            items(categoryProducts) { product ->
                ProductCard(product = product, onEdit = { editingProduct = product }, onDelete = { onProductsChange(products.filter { it.id != product.id }) })
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Προσθήκη Προϊόντος")
        }
    }

    if (showAddDialog || editingProduct != null) {
        ProductDialog(
            product = editingProduct,
            category = selectedCategory,
            categories = categories,
            onDismiss = { showAddDialog = false; editingProduct = null },
            onSave = { newProduct ->
                if (editingProduct != null) onProductsChange(products.map { if (it.id == newProduct.id) newProduct else it })
                else onProductsChange(products + newProduct)
                showAddDialog = false; editingProduct = null
            }
        )
    }
}

@Composable
fun ProductCard(product: Product, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "€${String.format("%.2f", product.price)}", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            }
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red) }
            }
        }
    }
}

@Composable
fun ProductDialog(product: Product?, category: String, categories: List<String>, onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var price by remember { mutableStateOf(product?.price?.toString() ?: "") }
    var selectedCategory by remember { mutableStateOf(product?.category ?: category) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Νέο Προϊόν" else "Επεξεργασία") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Όνομα") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Τιμή (€)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(value = selectedCategory, onValueChange = {}, readOnly = true, label = { Text("Κατηγορία") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat) }, onClick = { selectedCategory = cat; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val priceValue = price.toDoubleOrNull()
                if (name.isNotBlank() && priceValue != null && priceValue > 0) {
                    onSave(Product(id = product?.id ?: UUID.randomUUID().toString(), name = name, price = priceValue, category = selectedCategory))
                }
            }) { Text("Αποθήκευση") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}

// ============================================================
// Order Screen
// ============================================================

@Composable
fun OrderScreen(
    products: List<Product>,
    categories: List<String>,
    categoryCustomizations: Map<String, List<CustomizationOption>>,
    tableNumber: Int,
    currentOrder: MutableList<OrderItem>,
    currentStatus: OrderStatus,
    allTables: List<Table>,
    tableOrders: Map<Int, TableOrder>,
    onOrderUpdate: (MutableList<OrderItem>, OrderStatus) -> Unit,
    onTransferTable: (Int, Int) -> Unit,
    onCloseOrder: (CompletedOrder) -> Unit,
    onBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    var orderItems by remember(tableNumber) { mutableStateOf(currentOrder.toMutableList()) }
    var orderStatus by remember(tableNumber) { mutableStateOf(currentStatus) }
    var showOrderSummary by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showSplitBillDialog by remember { mutableStateOf(false) }
    var sentItemsCount by remember(tableNumber) { mutableStateOf(if (currentStatus == OrderStatus.SENT) currentOrder.size else 0) }
    val hasNewItems = orderItems.size > sentItemsCount

    fun saveOrder() { onOrderUpdate(orderItems.toMutableList(), orderStatus) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (categories.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = maxOf(0, categories.indexOf(selectedCategory)),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                categories.forEach { category ->
                    Tab(selected = selectedCategory == category, onClick = { selectedCategory = category }, text = { Text(category) })
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            val categoryProducts = products.filter { it.category == selectedCategory }
            items(categoryProducts) { product ->
                ProductOrderCard(product = product, onAddToOrder = { selectedProduct = product })
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (orderItems.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = when (orderStatus) { OrderStatus.DRAFT -> Color(0xFFFF9800); OrderStatus.SENT -> Color(0xFFF44336) }),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("${orderItems.sumOf { it.quantity }} Προϊόντα", color = Color.White, fontSize = 14.sp)
                            Text("€${String.format("%.2f", orderItems.sumOf { it.product.price * it.quantity })}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(if (orderStatus == OrderStatus.DRAFT) "Draft" else "Στάλθηκε", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { showTransferDialog = true }, modifier = Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))) {
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Μεταφορά", tint = Color.White)
                            }
                            if (orderStatus == OrderStatus.SENT) {
                                IconButton(onClick = { showSplitBillDialog = true }, modifier = Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))) {
                                    Icon(Icons.Default.Person, contentDescription = "Split Bill", tint = Color.White)
                                }
                            }
                            Button(onClick = { showOrderSummary = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = if (orderStatus == OrderStatus.DRAFT) Color(0xFFFF9800) else Color(0xFFF44336))) {
                                Text("Προβολή")
                            }
                        }
                    }
                    if (orderStatus == OrderStatus.DRAFT || hasNewItems) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { orderStatus = OrderStatus.SENT; sentItemsCount = orderItems.size; saveOrder() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFFF9800))
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (hasNewItems && orderStatus == OrderStatus.SENT) "Αποστολή Νέων" else "Αποστολή στο Bar")
                        }
                    }
                }
            }
        }
    }

    if (selectedProduct != null) {
        val options = categoryCustomizations[selectedProduct!!.category] ?: emptyList()
        ProductCustomizationDialog(
            product = selectedProduct!!,
            customizationOptions = options,
            onDismiss = { selectedProduct = null },
            onAddToOrder = { orderItem -> orderItems = (orderItems + orderItem).toMutableList(); saveOrder(); selectedProduct = null }
        )
    }

    if (showOrderSummary) {
        OrderSummaryDialog(
            tableNumber = tableNumber, orderItems = orderItems, orderStatus = orderStatus, hasNewItems = hasNewItems,
            onDismiss = { showOrderSummary = false },
            onPrint = { orderStatus = OrderStatus.SENT; sentItemsCount = orderItems.size; saveOrder(); showOrderSummary = false },
            onClose = { paymentMethod ->
                onCloseOrder(CompletedOrder(tableNumber = tableNumber, items = orderItems.toList(), total = orderItems.sumOf { it.product.price * it.quantity }, paymentMethod = paymentMethod))
                showOrderSummary = false
            },
            onUpdateOrder = { updated -> orderItems = updated.toMutableList(); saveOrder() }
        )
    }

    if (showTransferDialog) {
        TransferTableDialog(
            currentTable = tableNumber,
            availableTables = allTables.filter { it.number != tableNumber && tableOrders[it.number]?.isEmpty() != false },
            onDismiss = { showTransferDialog = false },
            onTransfer = { newTable -> onTransferTable(tableNumber, newTable); showTransferDialog = false }
        )
    }

    if (showSplitBillDialog) {
        SplitBillDialog(orderItems = orderItems, total = orderItems.sumOf { it.product.price * it.quantity }, onDismiss = { showSplitBillDialog = false })
    }
}

// ============================================================
// Product Customization Dialog (DYNAMIC)
// ============================================================

@Composable
fun ProductCustomizationDialog(
    product: Product,
    customizationOptions: List<CustomizationOption>,
    onDismiss: () -> Unit,
    onAddToOrder: (OrderItem) -> Unit
) {
    var quantity by remember { mutableStateOf(1) }
    var notes by remember { mutableStateOf("") }
    var selections by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                // Quantity
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Ποσότητα:", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (quantity > 1) quantity-- }) { Icon(Icons.Default.Clear, contentDescription = "Minus") }
                        Text("$quantity", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                        IconButton(onClick = { quantity++ }) { Icon(Icons.Default.Add, contentDescription = "Plus") }
                    }
                }

                // Dynamic Customizations
                if (customizationOptions.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    customizationOptions.forEach { option ->
                        Text("${option.name}:", fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            option.choices.forEach { choice ->
                                FilterChip(
                                    selected = selections[option.name] == choice,
                                    onClick = { selections = selections.toMutableMap().apply { this[option.name] = choice } },
                                    label = { Text(choice, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Notes
                Text("Σχόλια:", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("π.χ. χωρίς αφρόγαλα, με πάγο...") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Σύνολο: €${String.format("%.2f", product.price * quantity)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            Button(onClick = {
                onAddToOrder(OrderItem(product = product, quantity = quantity, customizations = selections.toMutableMap(), notes = notes))
            }) { Text("Προσθήκη") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}

@Composable
fun ProductOrderCard(product: Product, onAddToOrder: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onAddToOrder() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "€${String.format("%.2f", product.price)}", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
    }
}

// ============================================================
// Order Summary, Transfer, Split, Payment Dialogs
// ============================================================

@Composable
fun TransferTableDialog(currentTable: Int, availableTables: List<Table>, onDismiss: () -> Unit, onTransfer: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Μεταφορά Τραπεζιού") },
        text = {
            Column {
                Text("Μεταφορά από Τραπέζι $currentTable σε:", modifier = Modifier.padding(bottom = 16.dp))
                if (availableTables.isEmpty()) {
                    Text("Δεν υπάρχουν διαθέσιμα τραπέζια", color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(availableTables.sortedBy { it.number }) { table ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onTransfer(table.number) },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Τραπέζι ${table.number}", color = Color.White, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}

@Composable
fun SplitBillDialog(orderItems: List<OrderItem>, total: Double, onDismiss: () -> Unit) {
    var numberOfPeople by remember { mutableStateOf(2) }
    var splitType by remember { mutableStateOf("equal") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Διαχωρισμός Λογαριασμού") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Σύνολο: €${String.format("%.2f", total)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = splitType == "equal", onClick = { splitType = "equal" }, label = { Text("Ίσα μέρη") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = splitType == "custom", onClick = { splitType = "custom" }, label = { Text("Ανά προϊόν") }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (splitType == "equal") {
                    Text("Αριθμός ατόμων:", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        IconButton(onClick = { if (numberOfPeople > 2) numberOfPeople-- }) { Icon(Icons.Default.Clear, contentDescription = "Minus") }
                        Text("$numberOfPeople", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                        IconButton(onClick = { if (numberOfPeople < 10) numberOfPeople++ }) { Icon(Icons.Default.Add, contentDescription = "Plus") }
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Κάθε άτομο πληρώνει:", fontSize = 14.sp, color = Color.Gray)
                    Text("€${String.format("%.2f", total / numberOfPeople)}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Επιλέξτε προϊόντα για κάθε άτομο:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    orderItems.forEach { item ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(item.product.name, fontWeight = FontWeight.Medium)
                                    Text("${item.quantity}x €${String.format("%.2f", item.product.price)}", fontSize = 12.sp, color = Color.Gray)
                                }
                                Text("€${String.format("%.2f", item.product.price * item.quantity)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Κλείσιμο") } }
    )
}

@Composable
fun OrderSummaryDialog(
    tableNumber: Int, orderItems: List<OrderItem>, orderStatus: OrderStatus, hasNewItems: Boolean,
    onDismiss: () -> Unit, onPrint: () -> Unit, onClose: (String) -> Unit, onUpdateOrder: (List<OrderItem>) -> Unit
) {
    var items by remember { mutableStateOf(orderItems.toList()) }
    var showPaymentDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Τραπέζι $tableNumber - Παραγγελία") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    val details = item.getDisplayDetails()
                                    if (details.isNotEmpty()) {
                                        Text(details, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                                    }
                                    Text("€${String.format("%.2f", item.product.price)} x ${item.quantity}", fontSize = 12.sp, color = Color.Gray)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { if (item.quantity > 1) { item.quantity--; items = items.toList(); onUpdateOrder(items) } }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Remove", tint = Color.Gray)
                                    }
                                    Text("${item.quantity}", fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { item.quantity++; items = items.toList(); onUpdateOrder(items) }) {
                                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Gray)
                                    }
                                    IconButton(onClick = { items = items.filter { it != item }; onUpdateOrder(items) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Text(
                    "Σύνολο: €${String.format("%.2f", items.sumOf { it.product.price * it.quantity })}",
                    fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (orderStatus == OrderStatus.DRAFT || hasNewItems) {
                    Button(onClick = onPrint, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) {
                        Icon(Icons.Default.Send, contentDescription = null); Spacer(modifier = Modifier.width(8.dp))
                        Text(if (hasNewItems && orderStatus == OrderStatus.SENT) "Αποστολή Νέων" else "Αποστολή")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (orderStatus == OrderStatus.SENT && !hasNewItems) {
                    Button(onClick = { showPaymentDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Κλείσιμο")
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Πίσω") } }
    )

    if (showPaymentDialog) {
        PaymentMethodDialog(onDismiss = { showPaymentDialog = false }, onSelect = { method -> showPaymentDialog = false; onClose(method) })
    }
}

@Composable
fun PaymentMethodDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Τρόπος Πληρωμής") },
        text = {
            Column {
                listOf("Μετρητά", "Κάρτα", "POS").forEach { method ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect(method) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(method, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Icon(when (method) { "Μετρητά" -> Icons.Default.ShoppingCart; "Κάρτα" -> Icons.Default.Favorite; else -> Icons.Default.Phone }, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}

// ============================================================
// Order History & Statistics
// ============================================================

@Composable
fun OrderHistoryPanel(orderHistory: List<CompletedOrder>) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (orderHistory.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Δεν υπάρχει ιστορικό", color = Color.Gray, fontSize = 18.sp)
                }
            }
        } else {
            Text("Ιστορικό Παραγγελιών (${orderHistory.size})", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 16.dp))
            LazyColumn {
                items(orderHistory.sortedByDescending { it.timestamp }) { order ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Τραπέζι ${order.tableNumber}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("€${String.format("%.2f", order.total)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(dateFormat.format(Date(order.timestamp)), fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            order.items.forEach { item ->
                                val details = item.getDisplayDetails()
                                Text("${item.quantity}x ${item.product.name}" + if (details.isNotEmpty()) " ($details)" else "", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Πληρωμή: ${order.paymentMethod}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatisticsPanel(orderHistory: List<CompletedOrder>) {
    val totalRevenue = orderHistory.sumOf { it.total }
    val totalOrders = orderHistory.size
    val averageOrder = if (totalOrders > 0) totalRevenue / totalOrders else 0.0
    val productStats = orderHistory.flatMap { it.items }.groupBy { it.product.name }.mapValues { (_, items) -> items.sumOf { it.quantity } }.toList().sortedByDescending { it.second }.take(10)
    val categoryStats = orderHistory.flatMap { it.items }.groupBy { it.product.category }.mapValues { (_, items) -> items.sumOf { it.product.price * it.quantity } }.toList().sortedByDescending { it.second }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Στατιστικά Πωλήσεων", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(title = "Συνολικά Έσοδα", value = "€${String.format("%.2f", totalRevenue)}", color = Color(0xFF4CAF50), modifier = Modifier.weight(1f))
            StatCard(title = "Παραγγελίες", value = "$totalOrders", color = Color(0xFF2196F3), modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        StatCard(title = "Μέσος Όρος", value = "€${String.format("%.2f", averageOrder)}", color = Color(0xFFFF9800), modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))
        if (productStats.isNotEmpty()) {
            Text("🏆 Top 10 Προϊόντα", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(8.dp))
            productStats.forEachIndexed { index, (name, count) ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(30.dp))
                            Text(name, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text("$count τεμ.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (categoryStats.isNotEmpty()) {
            Text("📊 Πωλήσεις ανά Κατηγορία", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(8.dp))
            categoryStats.forEach { (category, revenue) ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(category, color = MaterialTheme.colorScheme.onSurface)
                        Text("€${String.format("%.2f", revenue)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        if (orderHistory.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Δεν υπάρχουν δεδομένα ακόμα.\nΚλείστε παραγγελίες για να δείτε στατιστικά.", textAlign = TextAlign.Center, color = Color.Gray)
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
            Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TablesAdminPanel(tables: List<Table>, onTablesChange: (List<Table>) -> Unit) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            items(tables.sortedBy { it.number }) { table ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = if (table.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Τραπέζι ${table.number}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Row {
                            Switch(checked = table.isActive, onCheckedChange = { onTablesChange(tables.map { if (it.number == table.number) it.copy(isActive = !it.isActive) else it }) })
                            IconButton(onClick = { onTablesChange(tables.filter { it.number != table.number }) }) { Icon(Icons.Default.Delete, contentDescription = "Διαγραφή", tint = Color.Red) }
                        }
                    }
                }
            }
        }
        Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth().padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Icon(Icons.Default.Add, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Προσθήκη Τραπεζιού")
        }
    }

    if (showAddDialog) {
        var newTableNumber by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Νέο Τραπέζι") },
            text = { OutlinedTextField(value = newTableNumber, onValueChange = { newTableNumber = it }, label = { Text("Αριθμός Τραπεζιού") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = {
                Button(onClick = {
                    val number = newTableNumber.toIntOrNull()
                    if (number != null && tables.none { it.number == number }) { onTablesChange(tables + Table(number = number)); showAddDialog = false }
                }) { Text("Προσθήκη") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Ακύρωση") } }
        )
    }
}

// ============================================================
// Initial Data
// ============================================================

fun getInitialProducts(): List<Product> {
    return listOf(
        Product(name = "Espresso", price = 2.50, category = "Καφέδες"),
        Product(name = "Cappuccino", price = 3.50, category = "Καφέδες"),
        Product(name = "Freddo Espresso", price = 3.00, category = "Καφέδες"),
        Product(name = "Freddo Cappuccino", price = 3.50, category = "Καφέδες"),
        Product(name = "Latte", price = 3.80, category = "Καφέδες"),
        Product(name = "Frappé", price = 3.50, category = "Καφέδες"),
        Product(name = "Coca Cola", price = 2.50, category = "Αναψυκτικά"),
        Product(name = "Sprite", price = 2.50, category = "Αναψυκτικά"),
        Product(name = "Fanta", price = 2.50, category = "Αναψυκτικά"),
        Product(name = "Χυμός Πορτοκάλι", price = 3.00, category = "Αναψυκτικά"),
        Product(name = "Mythos", price = 4.00, category = "Μπίρες"),
        Product(name = "Heineken", price = 4.50, category = "Μπίρες"),
        Product(name = "Fix", price = 4.00, category = "Μπίρες"),
        Product(name = "Mojito", price = 8.00, category = "Cocktails"),
        Product(name = "Margarita", price = 8.50, category = "Cocktails"),
        Product(name = "Pina Colada", price = 8.50, category = "Cocktails"),
        Product(name = "Cosmopolitan", price = 8.00, category = "Cocktails"),
        Product(name = "Vodka", price = 6.00, category = "Ποτά"),
        Product(name = "Whiskey", price = 7.00, category = "Ποτά"),
        Product(name = "Gin", price = 6.50, category = "Ποτά"),
        Product(name = "Rum", price = 6.00, category = "Ποτά"),
        Product(name = "Chips", price = 2.00, category = "Snacks"),
        Product(name = "Nuts", price = 3.00, category = "Snacks"),
        Product(name = "Popcorn", price = 2.50, category = "Snacks")
    )
}

fun getInitialCustomizations(): Map<String, List<CustomizationOption>> {
    return mapOf(
        "Καφέδες" to listOf(
            CustomizationOption(name = "Ζάχαρη", choices = listOf("Σκέτο", "Μέτριο", "Γλυκό")),
            CustomizationOption(name = "Γάλα", choices = listOf("Όχι", "Κανονικό", "Χωρίς λακτόζη", "Φυτικό"))
        )
    )
}

fun getInitialTables(count: Int): List<Table> {
    return (1..count).map { Table(number = it) }
}
