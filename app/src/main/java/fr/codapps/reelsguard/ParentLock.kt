package fr.codapps.reelsguard

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Édition de l'application (fixée à la compilation). */
object Edition {
    val isFamily: Boolean get() = BuildConfig.FAMILLE
}

/**
 * Code parent de l'édition Famille.
 *
 *  - Le code (4 à 8 chiffres) et le code de secours ne sont jamais stockés en clair :
 *    seule une empreinte PBKDF2-SHA256 salée est conservée, sur l'appareil.
 *  - Après 5 essais ratés, la saisie est bloquée 1 min, puis 2, 4… jusqu'à 30 min.
 *  - Un code juste ouvre une « session parent » de 10 minutes pendant laquelle les
 *    réglages et les pages protégées sont accessibles.
 */
object ParentLock {

    private const val PREFS = "reelsguard_parent"
    private const val ITER = 120_000
    private const val SESSION_MS = 10 * 60_000L
    private const val FREE_TRIES = 5

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasPin(c: Context) = prefs(c).contains("pin_hash")
    fun consentGiven(c: Context) = prefs(c).getBoolean("consent", false)
    fun giveConsent(c: Context) = prefs(c).edit().putBoolean("consent", true).putLong("consent_at", System.currentTimeMillis()).apply()

    /** L'édition Famille est-elle prête (accord donné et code créé) ? */
    fun isSetUp(c: Context) = consentGiven(c) && hasPin(c)

    private fun hash(secret: String, salt: ByteArray): String {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITER, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    private fun newSalt() = ByteArray(16).also { SecureRandom().nextBytes(it) }
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    fun isValidPin(pin: String) = pin.length in 4..8 && pin.all { it.isDigit() }

    /**
     * Enregistre un nouveau code parent et retourne un nouveau code de secours
     * (à afficher une seule fois au parent).
     */
    fun setPin(c: Context, pin: String): String {
        require(isValidPin(pin))
        val salt = newSalt()
        val recovery = newRecoveryCode()
        val rsalt = newSalt()
        prefs(c).edit()
            .putString("pin_salt", b64(salt)).putString("pin_hash", hash(pin, salt))
            .putString("rec_salt", b64(rsalt)).putString("rec_hash", hash(normalizeRecovery(recovery), rsalt))
            .putInt("fails", 0).putLong("lock_until", 0L)
            .apply()
        openSession(c)
        return recovery
    }

    /** Code de secours lisible : 3 groupes de 4 caractères sans lettres ambiguës. */
    private fun newRecoveryCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val r = SecureRandom()
        return (0 until 3).joinToString("-") { (0 until 4).map { alphabet[r.nextInt(alphabet.length)] }.joinToString("") }
    }

    private fun normalizeRecovery(s: String) = s.uppercase().filter { it.isLetterOrDigit() }

    /** Millisecondes avant de pouvoir réessayer, ou 0. */
    fun lockedForMs(c: Context): Long = (prefs(c).getLong("lock_until", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    enum class Result { OK, WRONG, LOCKED }

    fun checkPin(c: Context, pin: String): Result {
        if (lockedForMs(c) > 0) return Result.LOCKED
        val p = prefs(c)
        val salt = p.getString("pin_salt", null)?.let(::unb64) ?: return Result.WRONG
        return if (hash(pin, salt) == p.getString("pin_hash", null)) {
            p.edit().putInt("fails", 0).apply()
            openSession(c)
            Result.OK
        } else {
            fail(c); Result.WRONG
        }
    }

    /** Vérifie le code de secours ; si juste, le parent peut choisir un nouveau code. */
    fun checkRecovery(c: Context, code: String): Result {
        if (lockedForMs(c) > 0) return Result.LOCKED
        val p = prefs(c)
        val salt = p.getString("rec_salt", null)?.let(::unb64) ?: return Result.WRONG
        return if (hash(normalizeRecovery(code), salt) == p.getString("rec_hash", null)) {
            p.edit().putInt("fails", 0).apply()
            openSession(c)
            Result.OK
        } else {
            fail(c); Result.WRONG
        }
    }

    private fun fail(c: Context) {
        val p = prefs(c)
        val fails = p.getInt("fails", 0) + 1
        val e = p.edit().putInt("fails", fails)
        if (fails >= FREE_TRIES) {
            val step = (fails - FREE_TRIES).coerceAtMost(5) // 1, 2, 4, 8, 16, 30 min
            val minutes = minOf(30, 1 shl step)
            e.putLong("lock_until", System.currentTimeMillis() + minutes * 60_000L)
        }
        e.apply()
        UsageStore(c).logEvent(UsageStore.EV_PIN_FAIL)
    }

    // ---------- session parent ----------

    fun openSession(c: Context) = prefs(c).edit().putLong("session_until", System.currentTimeMillis() + SESSION_MS).apply()
    fun closeSession(c: Context) = prefs(c).edit().putLong("session_until", 0L).apply()
    fun sessionActive(c: Context) = System.currentTimeMillis() < prefs(c).getLong("session_until", 0L)
    fun sessionUntil(c: Context) = prefs(c).getLong("session_until", 0L)
}
