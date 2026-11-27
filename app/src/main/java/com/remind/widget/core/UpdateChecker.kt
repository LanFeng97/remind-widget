package com.remind.widget.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.remind.widget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新检查：直接读 GitHub Releases 的最新一条。
 *
 * 约定（发布脚本 push-release.ps1 与这里必须一致）：
 *   · tag 形如 `v1.1.0`
 *   · 该 release 附带一个 .apk 资源
 * 不依赖任何第三方库：JSON 用 org.json，网络用 HttpURLConnection。
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val API = "https://api.github.com/repos/${BuildConfig.REPO_OWNER}/${BuildConfig.REPO_NAME}/releases/latest"

    /** 国内直连 github 有时很慢，给一个够用的超时 */
    private const val TIMEOUT_MS = 15_000

    data class ReleaseInfo(
        val versionName: String,
        val notes: String,
        val apkUrl: String,
        val htmlUrl: String,
        val sizeBytes: Long
    )

    sealed interface CheckResult {
        data class Available(val info: ReleaseInfo) : CheckResult
        data object UpToDate : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    // ---------------- 版本比较 ----------------

    /** 把 "v1.2.3" / "1.2.3" 解析成 [1,2,3] */
    private fun parseVersion(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .mapNotNull { it.toIntOrNull() }

    /** a > b 返回 1，相等 0，小于 -1 */
    fun compareVersions(a: String, b: String): Int {
        val x = parseVersion(a)
        val y = parseVersion(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val xi = x.getOrElse(i) { 0 }
            val yi = y.getOrElse(i) { 0 }
            if (xi != yi) return if (xi > yi) 1 else -1
        }
        return 0
    }

    // ---------------- 检查 ----------------

    suspend fun checkLatest(): CheckResult = withContext(Dispatchers.IO) {
        try {
            val body = httpGet(API)
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { return@withContext CheckResult.Failed("返回里没有版本号") }
            val notes = json.optString("body").ifBlank { "（该版本没有写更新说明）" }
            val htmlUrl = json.optString("html_url")

            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            var size = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url")
                        size = a.optLong("size")
                        break
                    }
                }
            }
            if (apkUrl.isBlank()) {
                return@withContext CheckResult.Failed("最新 Release 里没有 APK 附件")
            }

            val current = BuildConfig.VERSION_NAME
            if (compareVersions(tag, current) <= 0) {
                CheckResult.UpToDate
            } else {
                CheckResult.Available(
                    ReleaseInfo(
                        versionName = tag.removePrefix("v"),
                        notes = notes,
                        apkUrl = apkUrl,
                        htmlUrl = htmlUrl,
                        sizeBytes = size
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "检查更新失败", e)
            CheckResult.Failed(humanize(e))
        }
    }

    private fun humanize(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "连不上 GitHub，检查网络或稍后再试"
        is java.net.SocketTimeoutException -> "连接 GitHub 超时，稍后再试"
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun httpGet(url: String): String {
        var current = URL(url)
        var redirects = 0
        while (true) {
            val conn = current.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "RemindWidget/${BuildConfig.VERSION_NAME}")

            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) {
                val loc = conn.getHeaderField("Location") ?: break
                current = URL(current, loc)
                conn.disconnect()
                redirects++
                continue
            }
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()
                throw IllegalStateException("HTTP $code ${err.take(120)}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
        }
        throw IllegalStateException("重定向次数过多")
    }

    // ---------------- 下载 ----------------

    /**
     * 下载 APK 到 files/update/，返回本地文件。
     * GitHub 的 release 资源会 302 到 objects.githubusercontent.com，这里手动跟。
     */
    suspend fun download(
        context: Context,
        info: ReleaseInfo,
        onProgress: (percent: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "update").apply { mkdirs() }
        val out = File(dir, "RemindWidget-${info.versionName}.apk")
        if (out.exists()) out.delete()

        var current = URL(info.apkUrl)
        var redirects = 0
        var conn: HttpURLConnection? = null
        while (true) {
            conn = current.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "RemindWidget/${BuildConfig.VERSION_NAME}")
            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) {
                val loc = conn.getHeaderField("Location") ?: break
                current = URL(current, loc)
                conn.disconnect()
                redirects++
                continue
            }
            if (code != 200) {
                conn.disconnect()
                throw IllegalStateException("下载失败 HTTP $code")
            }
            break
        }
        val c = conn ?: throw IllegalStateException("下载失败")

        val total = c.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
        var read = 0L
        var lastPct = -1

        c.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    read += n
                    if (total > 0) {
                        val pct = ((read * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                }
            }
        }
        c.disconnect()

        // 粗校验：APK 是 zip，头四字节应为 PK..
        val magic = ByteArray(2)
        out.inputStream().use { it.read(magic) }
        if (magic[0] != 'P'.code.toByte() || magic[1] != 'K'.code.toByte()) {
            out.delete()
            throw IllegalStateException("下载到的不是有效 APK（可能被网络劫持或代理插入）")
        }
        out
    }

    // ---------------- 安装 ----------------

    fun hasInstallPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    fun openInstallPermissionSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
