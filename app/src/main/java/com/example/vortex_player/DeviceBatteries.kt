package com.example.vortex_player

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings

/** Um aparelho no widget de baterias. [level] vai de 0 a 100, ou null se o aparelho não informa. */
data class DeviceBattery(
    val id: String,
    val name: String,
    val kind: Kind,
    val level: Int?,
    val charging: Boolean,
) {
    enum class Kind { Phone, Headphones, Watch, Other }
}

/**
 * Lê a bateria do próprio celular e dos aparelhos Bluetooth conectados (fones, relógios).
 *
 * O Android não tem uma forma pública de ler a bateria de um aparelho Bluetooth; os métodos
 * `isConnected` e `getBatteryLevel` existem, mas escondidos, então são chamados por reflexão.
 * Quando não funcionam, o aparelho aparece sem nível (só o anel vazio com o ícone).
 */
object DeviceBatteries {

    /** Avisa quando muda algo: bateria do celular, aparelho conectado/desconectado, bateria do fone. */
    val updates = IntentFilter().apply {
        addAction(Intent.ACTION_BATTERY_CHANGED)
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        addAction(ACTION_BATTERY_LEVEL_CHANGED)
    }

    /** Níveis recebidos pelo aviso do sistema, para quando a leitura direta não funciona. */
    private val reported = mutableMapOf<String, Int>()

    fun hasBluetoothPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Guarda o nível que veio num aviso do sistema (o [Intent] recebido pelo app). */
    fun onBroadcast(intent: Intent) {
        if (intent.action != ACTION_BATTERY_LEVEL_CHANGED) return
        val device = deviceFrom(intent) ?: return
        val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
        if (level in 0..100) reported[device.address] = level
    }

    fun read(context: Context): List<DeviceBattery> = listOf(phone(context)) + bluetooth(context)

    private fun phone(context: Context): DeviceBattery {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val state = status?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = state == BatteryManager.BATTERY_STATUS_CHARGING ||
            (state == BatteryManager.BATTERY_STATUS_FULL && (status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0)
        return DeviceBattery(
            id = "phone",
            // Nome dado ao celular nas configurações (ex.: "Galaxy A23"), não o código do modelo
            name = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                ?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Celular",
            kind = DeviceBattery.Kind.Phone,
            level = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = charging,
        )
    }

    @SuppressLint("MissingPermission") // verificado em hasBluetoothPermission
    private fun bluetooth(context: Context): List<DeviceBattery> {
        if (!hasBluetoothPermission(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        val bonded = try {
            if (!adapter.isEnabled) return emptyList()
            adapter.bondedDevices.orEmpty()
        } catch (_: SecurityException) {
            return emptyList()
        }
        return bonded
            .filter { hidden<Boolean>(it, "isConnected") == true }
            .map { device ->
                val name = try { device.alias ?: device.name } catch (_: SecurityException) { null } ?: "Bluetooth"
                val level = hidden<Int>(device, "getBatteryLevel")?.takeIf { it in 0..100 }
                    ?: reported[device.address]
                DeviceBattery(
                    id = device.address,
                    name = name,
                    kind = kindOf(device, name),
                    level = level,
                    charging = false,
                )
            }
            .sortedBy { it.kind.ordinal }
    }

    @SuppressLint("MissingPermission")
    private fun kindOf(device: BluetoothDevice, name: String): DeviceBattery.Kind {
        val lower = name.lowercase()
        if ("watch" in lower || "relógio" in lower) return DeviceBattery.Kind.Watch
        if ("buds" in lower || "pods" in lower || "fone" in lower || "headphone" in lower) return DeviceBattery.Kind.Headphones
        val cls = try { device.bluetoothClass } catch (_: SecurityException) { null } ?: return DeviceBattery.Kind.Other
        return when (cls.majorDeviceClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> DeviceBattery.Kind.Headphones
            BluetoothClass.Device.Major.WEARABLE -> DeviceBattery.Kind.Watch
            else -> DeviceBattery.Kind.Other
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> hidden(device: BluetoothDevice, method: String): T? = try {
        BluetoothDevice::class.java.getMethod(method).invoke(device) as? T
    } catch (_: Exception) {
        null
    }

    private fun deviceFrom(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    // Constantes escondidas do Android (mesmo valor em todas as versões)
    private const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
}
