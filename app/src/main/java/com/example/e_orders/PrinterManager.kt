package com.example.e_orders

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

data class PrinterConfig(
    val type: String = "none",
    val bluetoothAddress: String = "",
    val bluetoothName: String = "",
    val wifiIp: String = "",
    val wifiPort: Int = 9100
)

object EscPos {
    val INIT = byteArrayOf(0x1B, 0x40)
    val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    val DOUBLE_HEIGHT_ON = byteArrayOf(0x1B, 0x21, 0x10)
    val DOUBLE_ON = byteArrayOf(0x1B, 0x21, 0x30)
    val NORMAL = byteArrayOf(0x1B, 0x21, 0x00)
    val PARTIAL_CUT = byteArrayOf(0x1D, 0x56, 0x01)
    val FEED_LINES = byteArrayOf(0x1B, 0x64, 0x04)
    val CODEPAGE_GREEK = byteArrayOf(0x1B, 0x74, 0x11) // ESC t 17 → Windows-1253 (Greek)
    const val LINE_WIDTH = 32

    fun line(char: Char = '-'): String = String(CharArray(LINE_WIDTH) { char })
    fun leftRight(left: String, right: String): String {
        val space = LINE_WIDTH - left.length - right.length
        return if (space > 0) left + " ".repeat(space) + right
        else (left.take(LINE_WIDTH - right.length - 1) + " " + right)
    }
}

class PrinterManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson()

    fun saveConfig(config: PrinterConfig) { prefs.edit().putString("config", gson.toJson(config)).apply() }
    fun loadConfig(): PrinterConfig {
        val json = prefs.getString("config", null) ?: return PrinterConfig()
        return try { gson.fromJson(json, PrinterConfig::class.java) } catch (e: Exception) { PrinterConfig() }
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermission()) return emptyList()
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bm?.adapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun printViaBluetooth(address: String, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val device = bm.adapter.getRemoteDevice(address)
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()
            val os: OutputStream = socket.outputStream
            os.write(data); os.flush(); Thread.sleep(500); os.close(); socket.close()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun printViaWifi(ip: String, port: Int, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val socket = Socket(ip, port); socket.soTimeout = 5000
            val os = socket.getOutputStream()
            os.write(data); os.flush(); Thread.sleep(500); os.close(); socket.close()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun print(data: ByteArray): Result<Unit> {
        val config = loadConfig()
        return when (config.type) {
            "bluetooth" -> printViaBluetooth(config.bluetoothAddress, data)
            "wifi" -> printViaWifi(config.wifiIp, config.wifiPort, data)
            else -> Result.failure(Exception("No printer configured"))
        }
    }

    // #4 — Added waiterName parameter
    fun buildOrderReceipt(tableName: String, items: List<OrderItem>, waiterName: String = ""): ByteArray {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val greek = charset("windows-1253")
        val buf = mutableListOf<Byte>()
        fun add(b: ByteArray) { buf.addAll(b.toList()) }
        fun text(s: String) { add(s.toByteArray(greek)) }
        fun nl() { text("\n") }

        add(EscPos.INIT); add(EscPos.CODEPAGE_GREEK); add(EscPos.ALIGN_CENTER); add(EscPos.DOUBLE_ON)
        text("ΠΑΡΑΓΓΕΛΙΑ"); nl(); add(EscPos.NORMAL); nl()
        add(EscPos.BOLD_ON); add(EscPos.DOUBLE_HEIGHT_ON); text(tableName); nl()
        add(EscPos.NORMAL); add(EscPos.BOLD_OFF)
        if (waiterName.isNotEmpty()) { text("Σερβ: $waiterName"); nl() }
        text(dateFormat.format(Date())); nl()
        text(EscPos.line('=')); nl()

        add(EscPos.ALIGN_LEFT)
        items.forEach { item ->
            add(EscPos.BOLD_ON); text("${item.quantity}x ${item.product.name}"); nl(); add(EscPos.BOLD_OFF)
            item.customizations.forEach { (k, v) -> if (v.isNotEmpty()) { text("  $k: $v"); nl() } }
            if (item.notes.isNotEmpty()) { text("  >> ${item.notes}"); nl() }
            text(EscPos.line('-')); nl()
        }
        add(EscPos.FEED_LINES); add(EscPos.PARTIAL_CUT)
        return buf.toByteArray()
    }

    fun buildNewItemsReceipt(tableName: String, newItems: List<OrderItem>, waiterName: String = ""): ByteArray {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val greek = charset("windows-1253")
        val buf = mutableListOf<Byte>()
        fun add(b: ByteArray) { buf.addAll(b.toList()) }
        fun text(s: String) { add(s.toByteArray(greek)) }
        fun nl() { text("\n") }

        add(EscPos.INIT); add(EscPos.CODEPAGE_GREEK); add(EscPos.ALIGN_CENTER); add(EscPos.DOUBLE_ON)
        text("** ΝΕΑ ΠΡΟΪΟΝΤΑ **"); nl(); add(EscPos.NORMAL); nl()
        add(EscPos.BOLD_ON); add(EscPos.DOUBLE_HEIGHT_ON); text(tableName); nl()
        add(EscPos.NORMAL); add(EscPos.BOLD_OFF)
        if (waiterName.isNotEmpty()) { text("Σερβ: $waiterName"); nl() }
        text(dateFormat.format(Date())); nl()
        text(EscPos.line('=')); nl()

        add(EscPos.ALIGN_LEFT)
        newItems.forEach { item ->
            add(EscPos.BOLD_ON); text("${item.quantity}x ${item.product.name}"); nl(); add(EscPos.BOLD_OFF)
            item.customizations.forEach { (k, v) -> if (v.isNotEmpty()) { text("  $k: $v"); nl() } }
            if (item.notes.isNotEmpty()) { text("  >> ${item.notes}"); nl() }
            text(EscPos.line('-')); nl()
        }
        add(EscPos.FEED_LINES); add(EscPos.PARTIAL_CUT)
        return buf.toByteArray()
    }

    fun buildShiftSummary(totalRevenue: Double, totalOrders: Int, averageOrder: Double, categoryStats: List<Pair<String, Double>>, productStats: List<Pair<String, Int>>): ByteArray {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val greek = charset("windows-1253")
        val buf = mutableListOf<Byte>()
        fun add(b: ByteArray) { buf.addAll(b.toList()) }
        fun text(s: String) { add(s.toByteArray(greek)) }
        fun nl() { text("\n") }

        add(EscPos.INIT); add(EscPos.CODEPAGE_GREEK); add(EscPos.ALIGN_CENTER); add(EscPos.DOUBLE_ON)
        text("ΚΛΕΙΣΙΜΟ ΒΑΡΔΙΑΣ"); nl(); add(EscPos.NORMAL)
        text(dateFormat.format(Date())); nl(); text(EscPos.line('=')); nl()

        add(EscPos.ALIGN_LEFT); add(EscPos.BOLD_ON); nl()
        text(EscPos.leftRight("ΣΥΝΟΛΟ ΕΣΟΔΩΝ:", "€${String.format("%.2f", totalRevenue)}")); nl()
        text(EscPos.leftRight("ΠΑΡΑΓΓΕΛΙΕΣ:", "$totalOrders")); nl()
        add(EscPos.BOLD_OFF)

        if (categoryStats.isNotEmpty()) {
            nl(); text(EscPos.line('-')); nl(); add(EscPos.BOLD_ON); text("ΑΝΑ ΚΑΤΗΓΟΡΙΑ:"); nl(); add(EscPos.BOLD_OFF)
            categoryStats.forEach { (c, r) -> text(EscPos.leftRight("  $c", "€${String.format("%.2f", r)}")); nl() }
        }
        if (productStats.isNotEmpty()) {
            nl(); text(EscPos.line('-')); nl(); add(EscPos.BOLD_ON); text("TOP ΠΡΟΪΟΝΤΑ:"); nl(); add(EscPos.BOLD_OFF)
            productStats.take(10).forEachIndexed { i, (n, c) -> text(EscPos.leftRight("  ${i+1}. $n", "${c}τεμ")); nl() }
        }
        nl(); text(EscPos.line('=')); nl(); add(EscPos.ALIGN_CENTER); text("e-Orders"); nl()
        add(EscPos.FEED_LINES); add(EscPos.PARTIAL_CUT)
        return buf.toByteArray()
    }
}
