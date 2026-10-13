package com.example.pflanzenwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class PflanzenWidget : AppWidgetProvider() {

    // TRAGE HIER DIE ECHTE IP-ADRESSE DEINES ESP32 EIN:
    private val ESP32_URL = "http://192.168.178.99/data"

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        thread {
            try {
                for (appWidgetId in appWidgetIds) updateWidget(context, appWidgetManager, appWidgetId)
            } finally {
                pending.finish()
            }
        }
    }

    private fun clickIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, PflanzenWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        return PendingIntent.getBroadcast(
            context, appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openDashboardIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ESP32_URL.removeSuffix("/data")))
        return PendingIntent.getActivity(context, appWidgetId + 1000, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        // tap anywhere -> open the web dashboard; tap the time/status (top right) -> refresh
        views.setOnClickPendingIntent(R.id.widget_root, openDashboardIntent(context, appWidgetId))
        views.setOnClickPendingIntent(R.id.tv_status, clickIntent(context, appWidgetId))

        try {
            val conn = URL(ESP32_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000

            if (conn.responseCode != 200) {
                showError(appWidgetManager, appWidgetId, views, "HTTP Fehler ${conn.responseCode}")
                return
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            bind(views, json)
            views.setTextViewText(
                R.id.tv_status,
                "● " + SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date())
            )
            views.setTextColor(R.id.tv_status, Color.parseColor("#7A8F7A"))
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            showError(appWidgetManager, appWidgetId, views, "WLAN/IP Fehler")
        }
    }

    // keep the last values on screen, only flag the problem
    private fun showError(mgr: AppWidgetManager, id: Int, views: RemoteViews, msg: String) {
        val v = RemoteViews(views.`package`, R.layout.widget_layout)
        v.setTextViewText(R.id.tv_status, "⚠ $msg")
        v.setTextColor(R.id.tv_status, Color.parseColor("#FF5252"))
        mgr.partiallyUpdateAppWidget(id, v)
    }

    private fun num(json: JSONObject, key: String): Double? =
        if (json.has(key) && !json.isNull(key)) json.optDouble(key).takeUnless { it.isNaN() } else null

    private fun bind(views: RemoteViews, json: JSONObject) {
        // --- soil moisture (hero) ---
        val moisture = json.optInt("moisture", -1)
        val threshold = json.optInt("threshold", 40)
        val (color, bar, state) = when {
            moisture < 0 -> Triple("#9E9E9E", R.id.bar_green, "💧 Kein Messwert")
            moisture < threshold -> Triple("#FF5252", R.id.bar_red, "🥵 Durstig – gießen!")
            moisture < threshold + 15 -> Triple("#FFAB00", R.id.bar_orange, "⏳ Bald gießen")
            else -> Triple("#76FF03", R.id.bar_green, "😎 Gut versorgt")
        }
        views.setTextViewText(R.id.tv_moisture, if (moisture < 0) "-- %" else "$moisture %")
        views.setTextColor(R.id.tv_moisture, Color.parseColor(color))
        views.setTextViewText(R.id.tv_soil_state, state)
        for (b in intArrayOf(R.id.bar_green, R.id.bar_orange, R.id.bar_red)) {
            views.setViewVisibility(b, if (b == bar) View.VISIBLE else View.GONE)
        }
        views.setProgressBar(bar, 100, moisture.coerceIn(0, 100), false)

        // --- temp / humidity / vpd ---
        val temp = num(json, "temp")
        val hum = num(json, "humidity")
        val vpd = num(json, "vpd")
        views.setTextViewText(R.id.tv_temp, temp?.let { String.format(Locale.GERMANY, "%.1f °C", it) } ?: "-- °C")
        views.setTextViewText(R.id.tv_humidity, hum?.let { String.format(Locale.GERMANY, "%.0f %%", it) } ?: "-- %")
        views.setTextViewText(R.id.tv_vpd, vpd?.let { String.format(Locale.GERMANY, "%.2f kPa", it) } ?: "-- kPa")
        views.setTextViewText(
            R.id.tv_vpd_stage,
            json.optString("vpdStage", "").replace("vegetative", "veg.").replace("flowering", "flower")
        )

        // --- 24 h soil range ---
        val mm = json.optJSONObject("minmax")
        val lo = mm?.let { num(it, "soilMin") }
        val hi = mm?.let { num(it, "soilMax") }
        fun range(lo: String, hi: String, fmt: String): String {
            val a = mm?.let { num(it, lo) }
            val b = mm?.let { num(it, hi) }
            return if (a == null || b == null) "" else
                String.format(Locale.GERMANY, fmt, a) + "–" + String.format(Locale.GERMANY, fmt, b)
        }
        views.setTextViewText(R.id.tv_temp_mm, range("tempMin", "tempMax", "%.0f°"))
        views.setTextViewText(R.id.tv_hum_mm, range("humMin", "humMax", "%.0f%%"))
        val soilRange = if (lo != null && hi != null) String.format(Locale.GERMANY, " · %.0f–%.0f%%", lo, hi) else ""

        // --- last watered (device clock) ---
        val watered = json.optLong("lastWatered", 0)
        val now = json.optLong("now", 0)
        views.setTextViewText(R.id.tv_watered, "🚿 " + timeAgo(watered, now) + soilRange)
    }

    private fun timeAgo(t: Long, now: Long): String {
        if (t <= 0 || now <= 0) return "noch nie"
        val s = now - t
        return when {
            s < 60 -> "gerade eben"
            s < 3600 -> "vor ${s / 60} min"
            s < 86400 -> "vor ${s / 3600} h"
            else -> "vor ${s / 86400} d ${s % 86400 / 3600} h"
        }
    }
}
