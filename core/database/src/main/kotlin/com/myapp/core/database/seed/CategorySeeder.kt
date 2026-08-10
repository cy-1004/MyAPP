package com.myapp.core.database.seed

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID
import org.json.JSONArray

/**
 * 内置记账分类种子（PRD 3.6.1）。
 *
 * **为什么需要种子**：分类存在 DB 表里不是硬编码枚举，用户可改可加。但开箱即用
 * 必须有 9 个默认分类 + 1 个保留项「未分类」。首次 DB 创建时从 assets/categories.json
 * 灌入，避免用户首次进记账页看到空分类选择器。
 *
 * **触发时机**：[DatabaseModule] 在 RoomDatabase.Callback.onOpen 里检查 category 表
 * 行数，为 0 时调用 [seedSync]。onOpen 同时覆盖两种场景：
 *   - 新用户首次安装：onCreate 建表后 onOpen 灌种子
 *   - 老用户从 v4 升级到 v5：MIGRATION_4_5 建 category 空表后 onOpen 灌种子
 * 幂等：已有数据时跳过，不会重复灌。
 *
 * **用 org.json 而非 kotlinx.serialization**：:core:database 不依赖 serialization
 * 库（仅 :feature:* 经约定插件有），为这一个 JSON 文件拉依赖不划算。org.json 是
 * Android SDK 自带的。
 */
object CategorySeeder {

    /**
     * 同步灌种子。在 DB 回调线程上调用，不能用 suspend DAO。
     * 直接 execSQL INSERT，列名与 [CategoryEntity] / schemas/5.json 逐字符一致。
     */
    fun seedSync(db: SupportSQLiteDatabase, context: Context) {
        val json = runCatching {
            context.assets.open("categories.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            db.execSQL(
                "INSERT INTO `category` (`uuid`, `name`, `icon`, `color`, " +
                    "`sort_order`, `is_active`, `is_protected`, `created_at`, `updated_at`) " +
                    "VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?)",
                arrayOf(
                    UUID.randomUUID().toString(),
                    item.getString("name"),
                    item.getString("icon"),
                    item.getString("color"),
                    item.getInt("sortOrder"),
                    if (item.getBoolean("isProtected")) 1 else 0,
                    now,
                    now,
                ),
            )
        }
    }
}
