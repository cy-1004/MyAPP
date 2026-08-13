package com.myapp.feature.settings.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 少量敏感字符串的本地安全存储：云账号 token、备份密码。
 *
 * 项目里没有 androidx.security:security-crypto（且它已进入维护状态），
 * 这里直接用 Android Keystore：密钥由系统持有、可硬件背书，App 拿不到密钥material，
 * 只能请求它加解密。落盘的是密文，即使有人把 App 私有目录整个拷走也读不出内容。
 *
 * **为什么备份密码要存在本机**：每日一次的备份是无人值守的后台任务，
 * 不可能每天弹窗让用户输密码。密码存在 Keystore 保护下，
 * 而云端只有密文——「云账号泄露 ≠ 数据泄露」这一层保证仍然成立。
 *
 * 密钥丢失场景（恢复出厂、部分机型的锁屏凭据变更会使 Keystore 密钥失效）：
 * 解密会抛异常，这里统一按「没存过」处理并清掉脏数据，UI 会退回未登录态要求重新输入。
 */
@Singleton
class SecretStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    operator fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val ivSize = blob[0].toInt()
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    secretKey(),
                    GCMParameterSpec(TAG_BITS, blob, 1, ivSize),
                )
            }
            String(cipher.doFinal(blob, 1 + ivSize, blob.size - 1 - ivSize), Charsets.UTF_8)
        } catch (_: Exception) {
            // Keystore 密钥失效或数据损坏——清掉，让上层当作未设置
            prefs.edit().remove(key).apply()
            null
        }
    }

    operator fun set(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val blob = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + ciphertext
        prefs.edit().putString(key, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "backup_secrets"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "myapp_backup_secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128

        /** 云账号用户名（昵称或邮箱；手机号登录不被支持）。 */
        const val KEY_USERNAME = "cloud_username"

        /** 云账号密码。token 只有 24 小时有效期，无人值守的每日任务需要它自动重登。 */
        const val KEY_PASSWORD = "cloud_password"

        /** 备份密码：派生 AES 密钥用，云端永远拿不到。 */
        const val KEY_PASSPHRASE = "backup_passphrase"
    }
}
