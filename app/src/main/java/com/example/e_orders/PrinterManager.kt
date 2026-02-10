package com.example.e_orders

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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

// ============================================================
// Printer Configuration
// ============================================================

data class PrinterConfig(
    val type: String = "none", // "bluetooth", "wifi", "none"
    val bluetoothAddress: String = "",
    val bluetoothName: String = "",
    val wifiIp: String = "",
    val wifiPort: Int = 9100
)

// ============================================================
// ESC/POS Commands for 58mm (32 chars per line)
// ============================================================

object EscPos {
    val INIT = byteArrayOf(0x1B, 0x40) // Initialize printer
    val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    val DOUBLE_HEIGHT_ON = byteArrayOf(0x1B, 0x21, 0x10)
    val DOUBLE_WIDTH_ON = byteArrayOf(0x1B, 0x21, 0x20)
    val DOUBLE_ON = byteArrayOf(0x1B, 0x21, 0x30) // Double width + height
    val NORMAL = byteArrayOf(0x1B, 0x21, 0x00)
    val CUT = byteArrayOf(0x1D, 0x56, 0x00) // Full cut
    val PARTIAL_CUT = byteArrayOf(0x1D, 0x56, 0x01)
    val FEED_LINES = byteArrayOf(0x1B, 0x64, 0x04) // Feed 4 lines

    const val LINE_WIDTH = 32 // 58mm = 32 chars

    fun line(char: Char = '-'): String = String(CharArray(LINE_WIDTH) { char })

    fun padRight(text: String, width: Int): String {
        return if (text.length >= width) text.take(width)
        else text + " ".repeat(width - text.length)
    }

    fun leftRight(left: String, right: String): String {
        val space = LINE_WIDTH - left.length - right.length
        return if (space > 0) left + " ".repeat(space) + right
        else (left.take(LINE_WIDTH - right.length - 1) + " " + right)
    }
}

// ============================================================
// Printer Manager
// ============================================================

class PrinterManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson()

    fun saveConfig(config: PrinterConfig) {
        prefs.edit().putString("config", gson.toJson(config)).apply()
    }

    fun loadConfig(): PrinterConfig {
        val json = prefs.getString("config", null) ?: return PrinterConfig()
        return try {
            gson.fromJson(json, PrinterConfig::class.java)
        } catch (e: Exception) {
            PrinterConfig()
        }
    }

    // ============================================================
    // Bluetooth
    // ============================================================

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
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return emptyList()
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun printViaBluetooth(address: String, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter ?: return@withContext Result.failure(Exception("Bluetooth not available"))
            val device = adapter.getRemoteDevice(address)
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP UUID
            val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()
            val outputStream: OutputStream = socket.outputStream
            outputStream.write(data)
            outputStream.flush()
            Thread.sleep(500) // Wait for print to complete
            outputStream.close()
            socket.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    // WiFi
    // ============================================================

    suspend fun printViaWifi(ip: String, port: Int, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val socket = Socket(ip, port)
            socket.soTimeout = 5000
            val outputStream = socket.getOutputStream()
            outputStream.write(data)
            outputStream.flush()
            Thread.sleep(500)
            outputStream.close()
            socket.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    // Print dispatcher
    // ============================================================

    suspend fun print(data: ByteArray): Result<Unit> {
        val config = loadConfig()
        return when (config.type) {
            "bluetooth" -> printViaBluetooth(config.bluetoothAddress, data)
            "wifi" -> printViaWifi(config.wifiIp, config.wifiPort, data)
            else -> Result.failure(Exception("No printer configured"))
        }
    }

    // ============================================================
    // Build Order Receipt (sent to bar)
    // ============================================================

    fun buildOrderReceipt(tableName: String, items: List<OrderItem>): ByteArray {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val buffer = mutableListOf<Byte>()

        fun add(bytes: ByteArray) { buffer.addAll(bytes.toList()) }
        fun addText(text: String) { add(text.toByteArray(Charsets.UTF_8)) }
        fun newLine() { addText("\n") }

        add(EscPos.INIT)

        // Header
        add(EscPos.ALIGN_CENTER)
        add(EscPos.DOUBLE_ON)
        addText("ΠΑΡΑΓΓΕΛΙΑ")
        newLine()
        add(EscPos.NORMAL)
        newLine()

        // Table name & date
        add(EscPos.BOLD_ON)
        add(EscPos.DOUBLE_HEIGHT_ON)
        addText(tableName)
        newLine()
        add(EscPos.NORMAL)
        add(EscPos.BOLD_OFF)
        addText(dateFormat.format(Date()))
        newLine()
        addText(EscPos.line('='))
        newLine()

        // Items
        add(EscPos.ALIGN_LEFT)
        items.forEach { item ->
            add(EscPos.BOLD_ON)
            addText("${item.quantity}x ${item.product.name}")
            newLine()
            add(EscPos.BOLD_OFF)

            // Customizations
            item.customizations.forEach { (key, value) ->
                if (value.isNotEmpty()) {
                    addText("  $key: $value")
                    newLine()
                }
            }
            // Notes
            if (item.notes.isNotEmpty()) {
                addText("  >> ${item.notes}")
                newLine()
            }
            addText(EscPos.line('-'))
            newLine()
        }

        // Feed & cut
        add(EscPos.FEED_LINES)
        add(EscPos.PARTIAL_CUT)

        return buffer.toByteArray()
    }

    // ============================================================
    // Build Shift Summary Receipt
    // ============================================================

    fun buildShiftSummary(
        totalRevenue: Double,
        totalOrders: Int,
        averageOrder: Double,
        categoryStats: List<Pair<String, Double>>,
        productStats: List<Pair<String, Int>>
    ): ByteArray {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val buffer = mutableListOf<Byte>()

        fun add(bytes: ByteArray) { buffer.addAll(bytes.toList()) }
        fun addText(text: String) { add(text.toByteArray(Charsets.UTF_8)) }
        fun newLine() { addText("\n") }

        add(EscPos.INIT)

        // Header
        add(EscPos.ALIGN_CENTER)
        add(EscPos.DOUBLE_ON)
        addText("ΚΛΕΙΣΙΜΟ ΒΑΡΔΙΑΣ")
        newLine()
        add(EscPos.NORMAL)
        addText(dateFormat.format(Date()))
        newLine()
        addText(EscPos.line('='))
        newLine()

        // Totals
        add(EscPos.ALIGN_LEFT)
        add(EscPos.BOLD_ON)
        newLine()
        addText(EscPos.leftRight("ΣΥΝΟΛΟ ΕΣΟΔΩΝ:", "€${String.format("%.2f", totalRevenue)}"))
        newLine()
        addText(EscPos.leftRight("ΠΑΡΑΓΓΕΛΙΕΣ:", "$totalOrders"))
        newLine()
        addText(EscPos.leftRight("ΜΕΣΟΣ ΟΡΟΣ:", "€${String.format("%.2f", averageOrder)}"))
        newLine()
        add(EscPos.BOLD_OFF)

        // Category breakdown
        if (categoryStats.isNotEmpty()) {
            newLine()
            addText(EscPos.line('-'))
            newLine()
            add(EscPos.BOLD_ON)
            addText("ΑΝΑ ΚΑΤΗΓΟΡΙΑ:")
            newLine()
            add(EscPos.BOLD_OFF)
            categoryStats.forEach { (cat, rev) ->
                addText(EscPos.leftRight("  $cat", "€${String.format("%.2f", rev)}"))
                newLine()
            }
        }

        // Top products
        if (productStats.isNotEmpty()) {
            newLine()
            addText(EscPos.line('-'))
            newLine()
            add(EscPos.BOLD_ON)
            addText("TOP ΠΡΟΪΟΝΤΑ:")
            newLine()
            add(EscPos.BOLD_OFF)
            productStats.take(10).forEachIndexed { i, (name, count) ->
                addText(EscPos.leftRight("  ${i + 1}. $name", "${count}τεμ"))
                newLine()
            }
        }

        // Footer
        newLine()
        addText(EscPos.line('='))
        newLine()
        add(EscPos.ALIGN_CENTER)
        addText("e-Orders")
        newLine()

        // Feed & cut
        add(EscPos.FEED_LINES)
        add(EscPos.PARTIAL_CUT)

        return buffer.toByteArray()
    }
}
