package ru.smarthome.home

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

object Net {
    private val handler = Handler(Looper.getMainLooper())

    fun ui(r: () -> Unit) {
        handler.post { r() }
    }

    fun bg(r: () -> Unit) {
        Thread { r() }.start()
    }

    fun call(method: String, url: String, headers: Map<String, String>, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10000
            c.readTimeout = 15000
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body.isNotEmpty()) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val s = if (code >= 400) c.errorStream else c.inputStream
            if (s == null) return ""
            return s.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

object Store {
    private var sp: SharedPreferences? = null

    fun init(c: Context) {
        sp = c.getSharedPreferences("smart", Context.MODE_PRIVATE)
    }

    fun get(k: String): String {
        val p = sp ?: return ""
        return p.getString(k, "") ?: ""
    }

    fun put(k: String, v: String) {
        val p = sp ?: return
        p.edit().putString(k, v).apply()
    }
}

class Dev(val id: String, val name: String, val online: Boolean, val codes: List<String>) {
    companion object {
        fun toJson(l: List<Dev>): String {
            val a = JSONArray()
            for (d in l) {
                val o = JSONObject()
                o.put("id", d.id)
                o.put("name", d.name)
                o.put("online", d.online)
                o.put("codes", JSONArray(d.codes))
                a.put(o)
            }
            return a.toString()
        }

        fun fromJson(s: String): List<Dev> {
            val out = ArrayList<Dev>()
            if (s.isEmpty()) return out
            try {
                val a = JSONArray(s)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val c = o.getJSONArray("codes")
                    val cl = ArrayList<String>()
                    for (k in 0 until c.length()) cl.add(c.getString(k))
                    out.add(Dev(o.getString("id"), o.getString("name"), o.optBoolean("online"), cl))
                }
            } catch (e: Exception) {
            }
            return out
        }
    }
}

class Tuya(private val base: String, private val clientId: String, private val secret: String, private val uid: String) {
    private var token = ""
    private var expires = 0L

    private fun hex(b: ByteArray): String {
        val sb = StringBuilder()
        for (x in b) sb.append(Integer.toHexString((x.toInt() and 0xff) or 0x100).substring(1))
        return sb.toString()
    }

    private fun sha256(s: String): String =
        hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)))

    private fun hmac(s: String): String {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return hex(m.doFinal(s.toByteArray(Charsets.UTF_8))).uppercase()
    }

    private fun req(method: String, path: String, body: String, withToken: Boolean): JSONObject {
        val t = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val stringToSign = method + "\n" + sha256(body) + "\n\n" + path
        val str = clientId + (if (withToken) token else "") + t + nonce + stringToSign
        val h = HashMap<String, String>()
        h["client_id"] = clientId
        h["sign"] = hmac(str)
        h["t"] = t
        h["sign_method"] = "HMAC-SHA256"
        h["nonce"] = nonce
        if (withToken) h["access_token"] = token
        if (body.isNotEmpty()) h["Content-Type"] = "application/json"
        val text = Net.call(method, base + path, h, body)
        try {
            return JSONObject(text)
        } catch (e: Exception) {
            throw Exception("Неожиданный ответ сервера: " + text.take(200))
        }
    }

    private fun ensureToken() {
        if (token.isNotEmpty() && System.currentTimeMillis() < expires) return
        val j = req("GET", "/v1.0/token?grant_type=1", "", false)
        if (!j.optBoolean("success")) {
            throw Exception("Токен: " + j.optString("msg") + " (" + j.optString("code") + ")")
        }
        val r = j.getJSONObject("result")
        token = r.getString("access_token")
        expires = System.currentTimeMillis() + (r.optLong("expire_time", 7200L) - 120L) * 1000L
    }

    private fun api(method: String, path: String, body: String): JSONObject {
        ensureToken()
        var j = req(method, path, body, true)
        if (!j.optBoolean("success") && j.optString("code") == "1010") {
            token = ""
            ensureToken()
            j = req(method, path, body, true)
        }
        if (!j.optBoolean("success")) {
            throw Exception(j.optString("msg") + " (" + j.optString("code") + ")")
        }
        return j
    }

    fun devices(): List<Dev> {
        val j = api("GET", "/v1.0/users/" + uid + "/devices", "")
        val arr = j.optJSONArray("result") ?: return ArrayList()
        val out = ArrayList<Dev>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val codes = ArrayList<String>()
            val st = o.optJSONArray("status")
            if (st != null) {
                for (k in 0 until st.length()) {
                    val x = st.getJSONObject(k)
                    val c = x.optString("code")
                    if (c.startsWith("switch") && x.opt("value") is Boolean) codes.add(c)
                }
            }
            out.add(Dev(o.optString("id"), o.optString("name").replace("|", "/"), o.optBoolean("online"), codes))
        }
        return out
    }

    fun statusBools(id: String): Map<String, Boolean> {
        val j = api("GET", "/v1.0/devices/" + id + "/status", "")
        val arr = j.optJSONArray("result") ?: return HashMap()
        val m = HashMap<String, Boolean>()
        for (i in 0 until arr.length()) {
            val x = arr.getJSONObject(i)
            val v = x.opt("value")
            if (v is Boolean) m[x.optString("code")] = v
        }
        return m
    }

    fun setBool(id: String, code: String, v: Boolean) {
        val body = "{\"commands\":[{\"code\":\"" + code + "\",\"value\":" + v.toString() + "}]}"
        api("POST", "/v1.0/devices/" + id + "/commands", body)
    }
}

object Drivers {
    private var tuya: Tuya? = null

    fun reset() {
        tuya = null
    }

    fun tuya(): Tuya {
        val cur = tuya
        if (cur != null) return cur
        val ep = Store.get("tuya_ep").ifEmpty { "https://openapi.tuyaeu.com" }
        val id = Store.get("tuya_id")
        val sec = Store.get("tuya_secret")
        val uid = Store.get("tuya_uid")
        if (id.isEmpty() || sec.isEmpty() || uid.isEmpty()) {
            throw Exception("Заполните Access ID, Access Secret и UID")
        }
        val t = Tuya(ep, id, sec, uid)
        tuya = t
        return t
    }

    // привязка хранится как «тип|id устройства|код|название»
    fun set(b: String, on: Boolean): String? {
        try {
            val p = b.split("|")
            if (p[0] == "tuya") tuya().setBool(p[1], p[2], on) else throw Exception("Неизвестный тип привязки")
            return null
        } catch (e: Exception) {
            return e.message ?: e.toString()
        }
    }

    fun states(binds: Map<String, String>): Map<String, Boolean> {
        val out = HashMap<String, Boolean>()
        val cache = HashMap<String, Map<String, Boolean>>()
        for ((slot, b) in binds) {
            try {
                val p = b.split("|")
                if (p[0] == "tuya") {
                    var m = cache[p[1]]
                    if (m == null) {
                        m = tuya().statusBools(p[1])
                        cache[p[1]] = m
                    }
                    val v = m[p[2]]
                    if (v != null) out[slot] = v
                }
            } catch (e: Exception) {
            }
        }
        return out
    }
}
