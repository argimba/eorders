package com.example.e_orders

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DataManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("cafe_order_data", Context.MODE_PRIVATE)
    private val gson = Gson()

    // ============================================================
    // Categories
    // ============================================================

    fun saveCategories(categories: List<String>) {
        prefs.edit().putString("categories", gson.toJson(categories)).apply()
    }

    fun loadCategories(): List<String>? {
        val json = prefs.getString("categories", null) ?: return null
        return gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
    }

    // ============================================================
    // Products
    // ============================================================

    fun saveProducts(products: List<Product>) {
        prefs.edit().putString("products", gson.toJson(products)).apply()
    }

    fun loadProducts(): List<Product>? {
        val json = prefs.getString("products", null) ?: return null
        return gson.fromJson(json, object : TypeToken<List<Product>>() {}.type)
    }

    // ============================================================
    // Customization Options
    // ============================================================

    fun saveCustomizations(customizations: Map<String, List<CustomizationOption>>) {
        prefs.edit().putString("customizations", gson.toJson(customizations)).apply()
    }

    fun loadCustomizations(): Map<String, List<CustomizationOption>>? {
        val json = prefs.getString("customizations", null) ?: return null
        return gson.fromJson(json, object : TypeToken<Map<String, List<CustomizationOption>>>() {}.type)
    }

    // ============================================================
    // Tables
    // ============================================================

    fun saveTables(tables: List<Table>) {
        prefs.edit().putString("tables", gson.toJson(tables)).apply()
    }

    fun loadTables(): List<Table>? {
        val json = prefs.getString("tables", null) ?: return null
        return gson.fromJson(json, object : TypeToken<List<Table>>() {}.type)
    }

    // ============================================================
    // Order History
    // ============================================================

    fun saveOrderHistory(history: List<CompletedOrder>) {
        prefs.edit().putString("order_history", gson.toJson(history)).apply()
    }

    fun loadOrderHistory(): List<CompletedOrder>? {
        val json = prefs.getString("order_history", null) ?: return null
        return gson.fromJson(json, object : TypeToken<List<CompletedOrder>>() {}.type)
    }

    // ============================================================
    // Dark Mode
    // ============================================================

    fun saveDarkMode(isDark: Boolean) {
        prefs.edit().putBoolean("dark_mode", isDark).apply()
    }

    fun loadDarkMode(): Boolean {
        return prefs.getBoolean("dark_mode", false)
    }

    // ============================================================
    // Admin Password
    // ============================================================

    fun saveAdminPassword(password: String) {
        prefs.edit().putString("admin_password", password).apply()
    }

    fun loadAdminPassword(): String {
        return prefs.getString("admin_password", "admin123") ?: "admin123"
    }

    // ============================================================
    // Clear all data (reset)
    // ============================================================

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}