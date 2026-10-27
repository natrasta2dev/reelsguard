package fr.codapps.reelsguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Notification discrète et silencieuse avec le temps restant, pendant le visionnage. */
class Notifier(private val ctx: Context) {

    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private var lastText: String? = null

    init {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Temps restant", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun show(text: String) {
        if (text == lastText) return
        lastText = text
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_fg)
            .setContentTitle(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(ID, n) } // ignoré si l'autorisation de notifier est refusée
    }

    fun cancel() {
        if (lastText == null) return
        lastText = null
        nm.cancel(ID)
    }

    companion object {
        private const val CHANNEL = "remaining"
        private const val ID = 1
    }
}
