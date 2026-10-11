package com.example.pflanzenwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class PflanzenWidget : AppWidgetProvider() {

    // TRAGE HIER DIE ECHTE IP-ADRESSE DEINES ESP32 EIN:
    private val ESP32_URL = "http://192.168.178.99/data"

        override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            val intent = Intent(context, PflanzenWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            thread {
                try {
                    val url = URL(ESP32_URL)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000

                    if (conn.responseCode == 200) {
                        val stream = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(stream)

                        val moisture = json.optInt("moisture", -1)
                        val temp = json.optDouble("temp", 0.0)
                        val humidity = json.optInt("humidity", -1)

                        views.setTextViewText(R.id.tv_moisture, "Boden: $moisture %")
                        views.setTextViewText(R.id.tv_temp, String.format("Temp: %.1f °C", temp))
                        views.setTextViewText(R.id.tv_humidity, "Luft: $humidity %")
                        views.setTextViewText(R.id.tv_status, "Aktualisiert")
                    } else {
                        views.setTextViewText(R.id.tv_status, "HTTP Fehler ${conn.responseCode}")
                    }
                } catch (e: Exception) {
                    views.setTextViewText(R.id.tv_status, "WLAN/IP Fehler")
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
}
