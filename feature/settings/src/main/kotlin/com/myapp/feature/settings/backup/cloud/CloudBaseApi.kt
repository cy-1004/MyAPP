package com.myapp.feature.settings.backup.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 腾讯云开发（CloudBase）接口的数据模型（PRD 3.13）。
 *
 * 接入说明与实测结论见 `docs/云数据备份.md`；账号凭证在不入库的
 * `docs/云数据备份.local.md`，代码里不写死任何密码。
 */
object CloudBaseApi {

    /** 环境 ID。整个环境专供本 App 备份使用，不与其它项目共用。 */
    const val ENV_ID = "myapp-d8gmzgql0a62c3cb8"

    const val BASE_URL = "https://$ENV_ID.api.tcloudbasegateway.com"

    const val SIGN_IN_PATH = "/auth/v1/signin"

    /** PostgREST 数据接口。只能增删改查，建表要去控制台执行 DDL。 */
    const val BACKUP_TABLE_PATH = "/v1/rdb/rest/app_backup"

    /** 列表只取元数据，**不带 payload**——payload 是整份备份密文，列表里拉它纯属浪费流量。 */
    const val METADATA_COLUMNS = "id,created_at,app_version,db_schema_version,size_bytes,checksum"
}

@Serializable
internal data class SignInRequest(
    val username: String,
    val password: String,
)

@Serializable
internal data class AuthToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String = "",
    /** 实测 86400 秒（24 小时）。每日备份任务每次都要重新登录。 */
    @SerialName("expires_in") val expiresIn: Long = 86_400,
)

/** 云端返回的错误体，用于把 `INVALID_USERNAME_OR_PASSWORD` 这类码转成可读文案。 */
@Serializable
internal data class CloudBaseError(
    val code: String = "",
    val message: String = "",
    @SerialName("error_description") val errorDescription: String = "",
)

/** `app_backup` 表的一行。列表查询时 [payload] 为 null（未 select 该列）。 */
@Serializable
data class BackupRecord(
    val id: Long = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("app_version") val appVersion: String = "",
    @SerialName("db_schema_version") val dbSchemaVersion: Int = 0,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    val checksum: String = "",
    val payload: String? = null,
)

/** 新增一行备份的请求体。不传 id / created_at，交给数据库默认值。 */
@Serializable
data class BackupInsert(
    @SerialName("app_version") val appVersion: String,
    @SerialName("db_schema_version") val dbSchemaVersion: Int,
    @SerialName("size_bytes") val sizeBytes: Long,
    val checksum: String,
    val payload: String,
)
