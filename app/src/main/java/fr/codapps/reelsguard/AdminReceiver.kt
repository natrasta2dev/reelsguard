package fr.codapps.reelsguard

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Édition Famille : administrateur de l'appareil, sans aucune politique.
 * Son seul rôle : Android impose de le désactiver avant de pouvoir désinstaller l'application,
 * et la page de désactivation est protégée par le code parent.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Désactiver cette protection permet de désinstaller ReelsGuard Famille. Le code parent est nécessaire."

    companion object {
        fun component(c: Context) = ComponentName(c, AdminReceiver::class.java)
        fun isActive(c: Context) =
            c.getSystemService(android.app.admin.DevicePolicyManager::class.java).isAdminActive(component(c))
    }
}
