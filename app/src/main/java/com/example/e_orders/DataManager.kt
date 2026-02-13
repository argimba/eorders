package com.example.e_orders

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class AppUser(
    val id: String = java.util.UUID.randomUUID().toString(),
    val username: String,
    val password: String,
    val role: String = "waiter"
)

class DataManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("cafe_order_data", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveCategories(categories: List<String>) { prefs.edit().putString("categories", gson.toJson(categories)).apply() }
    fun loadCategories(): List<String>? { val j = prefs.getString("categories", null) ?: return null; return gson.fromJson(j, object : TypeToken<List<String>>() {}.type) }

    fun saveProducts(products: List<Product>) { prefs.edit().putString("products", gson.toJson(products)).apply() }
    fun loadProducts(): List<Product>? { val j = prefs.getString("products", null) ?: return null; return gson.fromJson(j, object : TypeToken<List<Product>>() {}.type) }

    fun saveCustomizations(customizations: Map<String, List<CustomizationOption>>) { prefs.edit().putString("customizations", gson.toJson(customizations)).apply() }
    fun loadCustomizations(): Map<String, List<CustomizationOption>>? { val j = prefs.getString("customizations", null) ?: return null; return gson.fromJson(j, object : TypeToken<Map<String, List<CustomizationOption>>>() {}.type) }

    fun saveTables(tables: List<Table>) { prefs.edit().putString("tables", gson.toJson(tables)).apply() }
    fun loadTables(): List<Table>? { val j = prefs.getString("tables", null) ?: return null; return gson.fromJson(j, object : TypeToken<List<Table>>() {}.type) }

    fun saveTableOrders(orders: Map<String, TableOrder>) { prefs.edit().putString("table_orders", gson.toJson(orders)).apply() }
    fun loadTableOrders(): Map<String, TableOrder>? { val j = prefs.getString("table_orders", null) ?: return null; return gson.fromJson(j, object : TypeToken<Map<String, TableOrder>>() {}.type) }

    fun saveOrderHistory(history: List<CompletedOrder>) { prefs.edit().putString("order_history", gson.toJson(history)).apply() }
    fun loadOrderHistory(): List<CompletedOrder>? { val j = prefs.getString("order_history", null) ?: return null; return gson.fromJson(j, object : TypeToken<List<CompletedOrder>>() {}.type) }

    fun saveDarkMode(isDark: Boolean) { prefs.edit().putBoolean("dark_mode", isDark).apply() }
    fun loadDarkMode(): Boolean { return prefs.getBoolean("dark_mode", false) }

    fun saveUsers(users: List<AppUser>) { prefs.edit().putString("users", gson.toJson(users)).apply() }
    fun loadUsers(): List<AppUser>? { val j = prefs.getString("users", null) ?: return null; return gson.fromJson(j, object : TypeToken<List<AppUser>>() {}.type) }

    // Shift tracking — stores the timestamp of the last shift close
    fun saveShiftStartTime(time: Long) { prefs.edit().putLong("shift_start_time", time).apply() }
    fun loadShiftStartTime(): Long { return prefs.getLong("shift_start_time", 0L) }

    fun clearAll() { prefs.edit().clear().apply() }
}
