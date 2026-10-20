package com.example.vortex_player

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.Display
import java.util.Locale
import kotlin.math.roundToInt

/** Ficha do celular para a tela do modo desenvolvedor: o que dá para ler ao vivo + a ficha técnica. */
object DeviceSpecs {

    data class Spec(val label: String, val value: String)

    private val PtBr = Locale.forLanguageTag("pt-BR")

    /** Lido do próprio aparelho agora (muda com o uso). */
    fun live(context: Context): List<Spec> {
        val specs = mutableListOf<Spec>()

        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (battery != null) {
            val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
            if (level >= 0 && scale > 0) {
                specs += Spec("Bateria", "${level * 100 / scale}%" + if (charging) " · carregando" else "")
            }
            val temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            if (temp != Int.MIN_VALUE) specs += Spec("Temperatura da bateria", String.format(PtBr, "%.1f °C", temp / 10f))
            healthLabel(battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1))?.let { specs += Spec("Saúde da bateria", it) }
        }

        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
        if (memory.totalMem > 0) {
            specs += Spec("RAM em uso", "${gb(memory.totalMem - memory.availMem)} de ${gb(memory.totalMem)}")
        }

        val stat = StatFs(Environment.getDataDirectory().path)
        specs += Spec("Armazenamento usado", "${gb(stat.totalBytes - stat.availableBytes)} de ${gb(stat.totalBytes)}")

        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.let { display ->
            val mode = display.mode
            specs += Spec("Tela agora", "${mode.physicalWidth} × ${mode.physicalHeight} · ${mode.refreshRate.roundToInt()} Hz")
        }

        specs += Spec("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        specs += Spec("Atualização de segurança", Build.VERSION.SECURITY_PATCH)
        specs += Spec("Modelo", Build.MODEL)
        return specs
    }

    /** Ficha técnica fixa (o aparelho não informa isso sobre si mesmo). Só para o Galaxy A23 4G. */
    fun sheet(): List<Spec>? {
        if (!Build.MODEL.startsWith("SM-A235")) return null
        return listOf(
            Spec("Processador", "Snapdragon 680 · 8 núcleos, 6 nm"),
            Spec("Tela", "6,6\" PLS LCD · 1080 × 2408 · 90 Hz"),
            Spec("Câmera traseira", "50 MP (OIS) + 5 MP ultra-wide + 2 MP macro + 2 MP profundidade"),
            Spec("Câmera frontal", "8 MP"),
            Spec("Bateria", "5000 mAh · carregamento de 25 W"),
            Spec("Lançamento", "Março de 2022"),
        )
    }

    /** AirPods ao vivo: o que o Bluetooth informa sobre eles agora. */
    fun airPodsLive(context: Context, id: String): List<Spec> {
        val device = DeviceBatteries.read(context).firstOrNull { it.id == id }
            ?: return listOf(Spec("Conexão", "Desconectado"))
        return listOf(
            Spec("Bateria", device.level?.let { "$it%" } ?: "Não informada"),
            Spec("Conexão", "Conectado por Bluetooth"),
            Spec("Nome no Bluetooth", device.name),
        )
    }

    /** Ficha técnica fixa dos AirPods Pro (2ª geração). */
    fun airPodsSheet(): List<Spec> = listOf(
        Spec("Chip", "Apple H2"),
        Spec("Som", "Cancelamento ativo de ruído · modo Ambiente · áudio espacial"),
        Spec("Bateria", "Até 6 h nos fones · até 30 h com o estojo"),
        Spec("Resistência", "IP54 (poeira, suor e água)"),
        Spec("Lançamento", "Setembro de 2022"),
    )

    private fun healthLabel(health: Int): String? = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Boa"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Superaquecida"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Gasta"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Tensão alta"
        BatteryManager.BATTERY_HEALTH_COLD -> "Fria"
        else -> null
    }

    private fun gb(bytes: Long): String =
        String.format(PtBr, "%.1f GB", bytes / 1_000_000_000.0)
}
