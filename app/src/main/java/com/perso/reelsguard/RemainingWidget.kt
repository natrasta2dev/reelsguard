package com.perso.reelsguard

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Widget d'écran d'accueil : temps restant aujourd'hui. */
class RemainingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context)

    companion object {
        private var lastShown: String? = null

        /** Met à jour tous les widgets posés (appelé par le service chaque minute). */
        fun update(ctx: Context, force: Boolean = true) {
            val (big, caption) = summary(ctx)
            val key = "$big|$caption"
            if (!force && key == lastShown) return
            lastShown = key

            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, RemainingWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(ctx.packageName, R.layout.widget_remaining).apply {
                setTextViewText(R.id.widget_big, big)
                setTextViewText(R.id.widget_caption, caption)
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                )
            }
            manager.updateAppWidget(ids, views)
        }

        /** Gros chiffre + légende, partagés avec l'écran principal. */
        fun summary(ctx: Context): Pair<String, String> {
            val store = UsageStore(ctx)
            val schedule = Schedule(ctx)
            val left = store.remainingMs
            return when {
                !schedule.isActiveToday() -> "∞" to "pas de limite"
                left <= 0 -> "0" to "à demain"
                !schedule.isInWindow() -> "${left / 60_000}" to "hors plage"
                else -> "${left / 60_000}" to "min restantes"
            }
        }
    }
}
