package com.example.keetronics

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.*
import android.widget.ImageButton

class MainActivity : AppCompatActivity() {

    private lateinit var connectButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var messageTextView: TextView
    private lateinit var voltageTextView: TextView
    private lateinit var currentTextView: TextView
    private lateinit var impedanceTextView: TextView
    private lateinit var leds: List<ImageView>
    private lateinit var buttons: List<ImageButton>

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())

    private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private val ESP32_MAC_ADDRESS = "08:B6:1F:28:B4:6E"

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.values.all { it }) {
                connectToMacAddress(ESP32_MAC_ADDRESS)
            } else {
                statusTextView.text = "Permissions denied. Cannot connect."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        connectButton = findViewById(R.id.connectButton)
        statusTextView = findViewById(R.id.statusTextView)
        messageTextView = findViewById(R.id.messageTextView)
        voltageTextView = findViewById(R.id.voltageTextView)
        currentTextView = findViewById(R.id.currentTextView)
        impedanceTextView = findViewById(R.id.impedanceTextView)

        leds = listOf(
            findViewById(R.id.led1), findViewById(R.id.led2), findViewById(R.id.led3),
            findViewById(R.id.led4), findViewById(R.id.led5), findViewById(R.id.led6),
            findViewById(R.id.led7), findViewById(R.id.led8), findViewById(R.id.led9),
            findViewById(R.id.led10), findViewById(R.id.led11), findViewById(R.id.led12),
            findViewById(R.id.led13), findViewById(R.id.led14)
        )

        buttons = listOf(
            findViewById(R.id.KEY1), findViewById(R.id.KEY2),
            findViewById(R.id.KEY3), findViewById(R.id.KEY4)
        )

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        connectButton.setOnClickListener {
            checkPermissionsAndConnect()
            connectButton.setBackgroundColor(resources.getColor(android.R.color.holo_green_light))
        }
    }

    private fun checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            connectToMacAddress(ESP32_MAC_ADDRESS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToMacAddress(macAddress: String) {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            statusTextView.text = "Bluetooth is OFF. Enable Bluetooth and try again."
            return
        }

        val device = bluetoothAdapter?.getRemoteDevice(macAddress)
        if (device != null) {
            statusTextView.text = "Connecting to ESP32..."
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
        } else {
            statusTextView.text = "Device not found."
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotification(gatt: BluetoothGatt) {
        val service = gatt.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)

        if (characteristic != null) {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor =
                characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(it)
                statusTextView.text = "CONNECTED"
            } ?: run {
                statusTextView.text = "Descriptor not found!"
            }
        } else {
            statusTextView.text = "Failed to find BLE characteristic"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            runOnUiThread {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        statusTextView.text = "Connected to ESP32"
                        gatt.discoverServices()
                        startLedAnimation()  // Start LED animation on connection
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        statusTextView.text = "Disconnected. Retrying..."
                        reconnect(gatt)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                enableNotification(gatt)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.getStringValue(0) ?: return
            runOnUiThread {
                processReceivedData(data)
            }
        }
    }

    private fun reconnect(gatt: BluetoothGatt) {
        handler.postDelayed({
            gatt.close()
            connectToMacAddress(ESP32_MAC_ADDRESS)
        }, 3000)
    }

    private fun processReceivedData(data: String) {
        when {
            data.startsWith("Key ") -> updateLedsAndMessage(data)
            data.startsWith("V:") -> extractValues(data)
        }
    }

    private fun extractValues(data: String) {
        val regex = """V:\s*([\d.]+),\s*I:\s*([\d.]+),\s*R:\s*([\d.]+)""".toRegex()
        val match = regex.find(data)

        match?.let {
            val (voltage, current, impedance) = it.destructured
            voltageTextView.text = "Voltage: $voltage V"
            currentTextView.text = "Current: $current mA"
            impedanceTextView.text = "Impedance: $impedance Ω"
        }
    }

    private fun updateLedsAndMessage(data: String) {
        messageTextView.text = data
        leds.forEach { it.setImageResource(R.drawable.default_led) } // Reset all LEDs

        val redLed = R.drawable.red_led
        val greenLed = R.drawable.green_led
        val blueLed = R.drawable.darkgreen_led  // For KEYxB indicators
        val defaultKeyLed = R.drawable.key_led  // Reset indicator LED

        // Reset all KEYxB indicators
        findViewById<ImageView>(R.id.key_1B).setImageResource(defaultKeyLed)
        findViewById<ImageView>(R.id.key_2B).setImageResource(defaultKeyLed)
        findViewById<ImageView>(R.id.key_3B).setImageResource(defaultKeyLed)
        findViewById<ImageView>(R.id.key_4B).setImageResource(defaultKeyLed)

        // Extract values from the data string
        val impedance = extractValue(data, "Imp")
        val voltage = extractValue(data, "V")
        val current = extractValue(data, "I")

        // Display extracted values in TextViews
        findViewById<TextView>(R.id.impedanceTextView)?.text = "Impedance: $impedance Ω"
        findViewById<TextView>(R.id.voltageTextView)?.text = "Voltage: $voltage V"
        findViewById<TextView>(R.id.currentTextView)?.text = "Current: $current A"

        when {
            data.startsWith("Key 1") -> {
                animateLeds(redLed)  // LEDs light up red one by one
                findViewById<ImageView>(R.id.key_1B).setImageResource(blueLed) // KEY1B Glows Blue
            }

            data.startsWith("Key 2") -> {
                animateLeds(greenLed)  // LEDs light up green one by one
                findViewById<ImageView>(R.id.key_2B).setImageResource(blueLed) // KEY2B Glows Blue
                findViewById<ImageView>(R.id.key_1B).setImageResource(defaultKeyLed) // Reset KEY1B
            }

            data.startsWith("Key 3") -> {
                animateLedsAlternate(redLed, greenLed)  // Top 8 Red, Bottom 6 Green one by one
                findViewById<ImageView>(R.id.key_3B).setImageResource(blueLed) // KEY3B Glows Blue
            }

            data.startsWith("Key 4") -> {
                animateLedsAlternate(greenLed, redLed)  // Top 8 Green, Bottom 6 Red one by one
                findViewById<ImageView>(R.id.key_4B).setImageResource(blueLed) // KEY4B Glows Blue
            }
        }
    }

    // Function to extract numerical values from the received BLE data string
    private fun extractValue(data: String, prefix: String): String {
        val regex = Regex("$prefix:\\s*(-?\\d*\\.?\\d+)") // Match "Imp: 10.5", "V: 3.3", "I: 0.02"
        return regex.find(data)?.groupValues?.get(1) ?: "N/A"
    }

    // Function to animate all LEDs one by one with the same color
    private fun animateLeds(color: Int) {
        leds.forEachIndexed { index, led ->
            handler.postDelayed({
                led.setImageResource(color)
            }, index * 200L)  // 200ms delay per LED
        }
    }

    // Function to animate LEDs one by one with different colors for top 8 and bottom 6
    private fun animateLedsAlternate(colorTop: Int, colorBottom: Int) {
        leds.forEachIndexed { index, led ->
            handler.postDelayed({
                led.setImageResource(if (index < 8) colorTop else colorBottom)
            }, index * 200L)  // 200ms delay per LED
        }
    }

    // New method to start LED animation
    private fun startLedAnimation() {
        val ledColor = R.drawable.green_led // Choose the color for the LEDs
        animateLeds(ledColor) // Call the existing animateLeds method
    }
}