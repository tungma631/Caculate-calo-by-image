package vn.calolens.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException

class ApiClient {
    fun post(path: String, body: JSONObject): JSONObject {
        check(BuildConfig.APP_TOKEN.isNotBlank()) { "Chưa cấu hình kết nối backend. Xem README của dự án." }
        val connection = URL(BuildConfig.API_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 105000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.APP_TOKEN}")
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            val result = try { JSONObject(raw) } catch (_: Exception) {
                throw IllegalStateException("Backend trả dữ liệu không hợp lệ (HTTP $code).")
            }
            if (code !in 200..299) throw IllegalStateException(result.optString("error", "Yêu cầu thất bại (HTTP $code)."))
            return result
        } catch (_: SocketTimeoutException) {
            throw IllegalStateException("Kết nối quá thời gian chờ. Kiểm tra backend và thử lại.")
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Không kết nối được backend. Kiểm tra địa chỉ máy chủ và kết nối mạng.")
        } finally { connection.disconnect() }
    }
}
