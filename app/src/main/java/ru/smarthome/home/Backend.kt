package ru.smarthome.home

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
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

class Dev(val id: String, val name: String, val online: Boolean, val codes: List<String>, val info: String = "") {
    companion object {
        fun toJson(l: List<Dev>): String {
            val a = JSONArray()
            for (d in l) {
                val o = JSONObject()
                o.put("id", d.id)
                o.put("name", d.name)
                o.put("online", d.online)
                o.put("codes", JSONArray(d.codes))
                o.put("info", d.info)
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
                    out.add(Dev(o.getString("id"), o.getString("name"), o.optBoolean("online"), cl, o.optString("info")))
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

    // управляемые коды устройства: логические и режимы охраны (arm / disarmed)
    private fun functionCodes(id: String): List<String> {
        val out = ArrayList<String>()
        try {
            val j = api("GET", "/v1.0/devices/" + id + "/functions", "")
            val r = j.optJSONObject("result") ?: return out
            val fs = r.optJSONArray("functions") ?: return out
            for (i in 0 until fs.length()) {
                val f = fs.getJSONObject(i)
                val code = f.optString("code")
                val type = f.optString("type")
                if (type.equals("Boolean", true)) {
                    out.add(code)
                } else if (type.equals("Enum", true)) {
                    var range: JSONArray? = null
                    try {
                        range = JSONObject(f.optString("values")).optJSONArray("range")
                    } catch (e: Exception) {
                    }
                    if (range != null) {
                        val list = ArrayList<String>()
                        for (k in 0 until range.length()) list.add(range.optString(k))
                        if (list.contains("arm") && list.contains("disarmed")) out.add(code + "#arm#disarmed")
                    }
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    // свойства по модели данных устройства (для устройств с нестандартными кодами, например счётчика)
    private fun propertyCodes(id: String): List<String> {
        val out = ArrayList<String>()
        try {
            val j = api("GET", "/v2.0/cloud/thing/" + id + "/shadow/properties", "")
            val r = j.optJSONObject("result") ?: return out
            val ps = r.optJSONArray("properties") ?: return out
            for (i in 0 until ps.length()) {
                val x = ps.getJSONObject(i)
                if (x.opt("value") is Boolean) out.add(x.optString("code") + "@p")
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun propsAll(id: String): Map<String, Any> {
        val j = api("GET", "/v2.0/cloud/thing/" + id + "/shadow/properties", "")
        val m = HashMap<String, Any>()
        val r = j.optJSONObject("result") ?: return m
        val ps = r.optJSONArray("properties") ?: return m
        for (i in 0 until ps.length()) {
            val x = ps.getJSONObject(i)
            val v = x.opt("value")
            if (v != null) m[x.optString("code")] = v
        }
        return m
    }

    fun setProp(id: String, code: String, v: Boolean) {
        val inner = "{\"" + code + "\":" + v.toString() + "}"
        val body = JSONObject().put("properties", inner).toString()
        api("POST", "/v2.0/cloud/thing/" + id + "/shadow/properties/issue", body)
    }

    private fun describe(st: JSONArray?): String {
        if (st == null || st.length() == 0) return "нет данных о состоянии"
        val sb = StringBuilder("состояние: ")
        val n = minOf(st.length(), 12)
        for (k in 0 until n) {
            val x = st.getJSONObject(k)
            sb.append(x.optString("code")).append("=").append(x.opt("value").toString())
            if (k < n - 1) sb.append(", ")
        }
        return sb.toString()
    }

    fun devices(): List<Dev> {
        val j = api("GET", "/v1.0/users/" + uid + "/devices", "")
        val arr = j.optJSONArray("result") ?: return ArrayList()
        val out = ArrayList<Dev>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            val st = o.optJSONArray("status")
            val statusCodes = ArrayList<String>()
            if (st != null) {
                for (k in 0 until st.length()) {
                    val x = st.getJSONObject(k)
                    val c = x.optString("code")
                    if (c.startsWith("switch") && x.opt("value") is Boolean) statusCodes.add(c)
                }
            }
            val fc = functionCodes(id)
            var all: List<String> = if (fc.isNotEmpty()) fc else statusCodes
            var info = ""
            if (all.isEmpty()) {
                val pc = propertyCodes(id)
                if (pc.isNotEmpty()) all = pc else info = describe(st)
            }
            out.add(Dev(id, o.optString("name").replace("|", "/"), o.optBoolean("online"), all, info))
        }
        return out
    }

    fun statusAll(id: String): Map<String, Any> {
        val j = api("GET", "/v1.0/devices/" + id + "/status", "")
        val arr = j.optJSONArray("result") ?: return HashMap()
        val m = HashMap<String, Any>()
        for (i in 0 until arr.length()) {
            val x = arr.getJSONObject(i)
            val v = x.opt("value")
            if (v != null) m[x.optString("code")] = v
        }
        return m
    }

    fun spec(id: String): JSONObject =
        api("GET", "/v1.0/devices/" + id + "/specifications", "").optJSONObject("result") ?: JSONObject()

    fun describeDevice(id: String): String {
        val sb = StringBuilder("Команды:\n")
        val fs = spec(id).optJSONArray("functions") ?: JSONArray()
        for (i in 0 until fs.length()) {
            val f = fs.getJSONObject(i)
            sb.append("  ").append(f.optString("code")).append(" (").append(f.optString("type")).append(") ")
                .append(f.optString("values")).append("\n")
        }
        sb.append("Состояние:\n")
        for ((k, v) in statusAll(id)) sb.append("  ").append(k).append(" = ").append(v.toString()).append("\n")
        return sb.toString()
    }

    fun setRaw(id: String, code: String, rawJson: String) {
        val body = "{\"commands\":[{\"code\":\"" + code + "\",\"value\":" + rawJson + "}]}"
        api("POST", "/v1.0/devices/" + id + "/commands", body)
    }

    fun setBool(id: String, code: String, v: Boolean) {
        setRaw(id, code, v.toString())
    }
}

object Drivers {
    private var tuya: Tuya? = null

    private var zont: Zont? = null
    private var ewl: Ewelink? = null
    private val thermos = HashMap<String, Thermo>()

    fun reset() {
        tuya = null
        zont = null
        ewl = null
        thermos.clear()
    }

    fun thermo(id: String): Thermo {
        val c = thermos[id]
        if (c != null) return c
        val t = Thermo.from(tuya().spec(id))
        thermos[id] = t
        return t
    }

    fun ewl(): Ewelink {
        val cur = ewl
        if (cur != null) return cur
        val id = Store.get("ewl_appid")
        val sec = Store.get("ewl_secret")
        if (id.isEmpty() || sec.isEmpty()) throw Exception("Заполните APPID и APP SECRET eWeLink")
        val e = Ewelink(id, sec)
        ewl = e
        return e
    }

    fun zont(): Zont {
        val cur = zont
        if (cur != null) return cur
        val login = Store.get("zont_login")
        val pass = Store.get("zont_pass")
        if (login.isEmpty() || pass.isEmpty()) throw Exception("Заполните логин и пароль ZONT")
        val z = Zont(login, pass, Store.get("zont_mail").ifEmpty { login })
        zont = z
        return z
    }

    // выбранный контроллер ZONT; если сохранён неверный номер (раньше мог сохраниться 0) — берём первый
    fun zontInfo(): ZontInfo? {
        val arr = zont().devices()
        if (arr.length() == 0) return null
        val want = Store.get("zont_dev")
        var first: ZontInfo? = null
        for (i in 0 until arr.length()) {
            val z = ZontInfo.from(arr.getJSONObject(i))
            if (first == null) first = z
            if (z.id.toString() == want) return z
        }
        if (first != null) Store.put("zont_dev", first.id.toString())
        return first
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

    // привязка: «tuya|id|код|название» (стандартные команды), «tuyap|id|код|название» (свойства модели данных)
    // и для режимов охраны ещё «|значение вкл|значение выкл»
    fun set(b: String, on: Boolean): String? {
        try {
            val p = b.split("|")
            if (p[0] == "tuyap") {
                tuya().setProp(p[1], p[2], on)
            } else if (p[0] == "tuya") {
                if (p.size >= 6) {
                    tuya().setRaw(p[1], p[2], "\"" + (if (on) p[4] else p[5]) + "\"")
                } else {
                    tuya().setBool(p[1], p[2], on)
                }
            } else if (p[0] == "ewl") {
                ewlSet(p[1], on)
            } else if (p[0] == "ewlp") {
                ewlSet(p[1], true)
            } else {
                throw Exception("Неизвестный тип привязки")
            }
            return null
        } catch (e: Exception) {
            return e.message ?: e.toString()
        }
    }

    // облако eWeLink недоступно (например, через Wi-Fi дачи) — не ждём его повторно 5 минут
    @Volatile private var ewlCloudDownUntil = 0L

    private fun ewlCloudOk(): Boolean = System.currentTimeMillis() >= ewlCloudDownUntil

    private fun ewlCloudFailed() {
        ewlCloudDownUntil = System.currentTimeMillis() + 5 * 60 * 1000L
    }

    // eWeLink: сначала локальная сеть (LAN mode), при ошибке — облако
    private fun ewlSet(id: String, on: Boolean) {
        var lanErr: String? = null
        if (EwelinkLan.saved(id) != null) {
            try {
                EwelinkLan.setSwitch(id, on)
                return
            } catch (e: Exception) {
                lanErr = e.message ?: e.toString()
                EwelinkLan.refresh()
            }
        }
        if (!ewlCloudOk()) {
            throw Exception(if (lanErr != null) "локально: " + lanErr + "; облако eWeLink недоступно"
                else "реле не найдено в локальной сети, облако eWeLink недоступно")
        }
        try {
            ewl().setSwitch(id, on)
        } catch (e: Exception) {
            if (e is java.io.IOException) ewlCloudFailed()
            val ce = e.message ?: e.toString()
            throw Exception(if (lanErr != null) "локально: " + lanErr + "; облако: " + ce else ce)
        }
    }

    fun states(binds: Map<String, String>): Map<String, Boolean> {
        val out = HashMap<String, Boolean>()
        val cache = HashMap<String, Map<String, Any>>()
        for ((slot, b) in binds) {
            try {
                val p = b.split("|")
                if (p[0] != "tuya" && p[0] != "tuyap") continue
                val key = p[0] + ":" + p[1]
                var m = cache[key]
                if (m == null) {
                    m = if (p[0] == "tuyap") tuya().propsAll(p[1]) else tuya().statusAll(p[1])
                    cache[key] = m
                }
                val v = m[p[2]]
                if (v is Boolean) {
                    out[slot] = v
                } else if (v is String && p.size >= 6) {
                    out[slot] = (v == p[4])
                }
            } catch (e: Exception) {
            }
        }
        val ewlIds = ArrayList<String>()
        for ((_, b) in binds) {
            val p = b.split("|")
            if (p[0] == "ewl") ewlIds.add(p[1])
        }
        if (ewlIds.isNotEmpty() && ewlCloudOk()) {
            try {
                val m = ewl().states(ewlIds)
                for ((slot, b) in binds) {
                    val p = b.split("|")
                    if (p[0] == "ewl") {
                        val v = m[p[1]]
                        if (v != null) out[slot] = v
                    }
                }
            } catch (e: Exception) {
                if (e is java.io.IOException) ewlCloudFailed()
            }
        }
        return out
    }
}

fun fmt1(v: Double): String = String.format(Locale("ru"), "%.1f", v)

class ZontError(m: String) : Exception(m)

// ZONT: официальный API zont-online.ru (вход по логину и паролю, заголовок X-ZONT-Client)
class Zont(private val login: String, private val password: String, private val client: String) {
    private val bases = listOf("https://zont-online.ru/api/", "https://my.zont.online/api/")
    private var base = ""

    private fun post(method: String, body: JSONObject): JSONObject {
        val auth = "Basic " + Base64.encodeToString((login + ":" + password).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val h = HashMap<String, String>()
        h["Authorization"] = auth
        h["X-ZONT-Client"] = client
        h["Content-Type"] = "application/json"
        var last: Exception? = null
        val order = if (base.isNotEmpty()) listOf(base) else bases
        for (b in order) {
            try {
                val text = Net.call("POST", b + method, h, body.toString())
                val j = JSONObject(text)
                base = b
                if ((j.has("ok") && !j.optBoolean("ok")) || (!j.has("ok") && j.has("error") && !j.isNull("error"))) {
                    val ui = j.optString("error_ui", "")
                    throw ZontError(if (ui.isNotEmpty()) ui else j.optString("error", "ошибка"))
                }
                return j
            } catch (e: ZontError) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw Exception("Нет связи с сервером ZONT: " + (last?.message ?: ""))
    }

    fun devices(): JSONArray {
        val j = post("devices", JSONObject().put("load_io", true))
        return j.optJSONArray("devices") ?: JSONArray()
    }

    fun update(deviceId: Long, params: JSONObject) {
        params.put("device_id", deviceId)
        post("update_device", params)
    }

    // уставка отопления — способом, который подходит этому прибору
    fun setTarget(info: ZontInfo, value: Int) {
        when (info.kind) {
            "z3k" -> post("send_z3k_command", JSONObject()
                .put("device_id", info.id)
                .put("object_id", info.circuit)
                .put("command_name", "TargetTemperature")
                .put("command_args", JSONObject().put("value", value))
                .put("is_guaranteed", true))
            "temp" -> update(info.id, JSONObject().put("thermostat_temp", value))
            else -> update(info.id, JSONObject().put("thermostat_mode_temps", JSONObject().put("comfort", value)))
        }
    }
}

// Описание контроллера ZONT. Уставка бывает в одном из трёх мест (зависит от модели):
//  "z3k"     — новые приборы (Connect+, H-2000+, H-1V…): контур из z3k_config.heating_circuits,
//              уставка в io["z3k-state"][id контура].target_temp, задаётся командой send_z3k_command;
//  "temp"    — поле thermostat_temp (старые H-1), задаётся update_device;
//  "comfort" — thermostat_mode_temps.comfort, задаётся update_device.
class ZontInfo(val id: Long, val name: String, val mode: String, val target: Double, val current: Double?,
               val boilerOn: Boolean, val fail: Boolean, val keys: String, val kind: String,
               val circuit: Long, val circuitName: String, val circuits: String) {
    companion object {
        private fun num(o: JSONObject?, k: String): Double =
            if (o == null || !o.has(k) || o.isNull(k)) Double.NaN else o.optDouble(k, Double.NaN)

        fun from(d: JSONObject): ZontInfo {
            val id = if (d.has("id") && !d.isNull("id")) d.optLong("id") else d.optLong("device_id")
            val io = d.optJSONObject("io")
            var kind = ""
            var target = Double.NaN
            var circuit = 0L
            var cname = ""
            var cur: Double? = null
            val cl = StringBuilder()
            val ks = StringBuilder()

            val hc = d.optJSONObject("z3k_config")?.optJSONArray("heating_circuits")
            if (hc != null && hc.length() > 0) {
                // контур: выбранный ранее, иначе с «отоп»/«дом» в названии, иначе первый
                val want = Store.get("zont_circuit")
                var c: JSONObject? = null
                for (i in 0 until hc.length()) {
                    val o = hc.optJSONObject(i) ?: continue
                    cl.append(o.optLong("id")).append(" «").append(o.optString("name")).append("»  ")
                    if (o.optLong("id").toString() == want) c = o
                }
                if (c == null) {
                    for (i in 0 until hc.length()) {
                        val o = hc.optJSONObject(i) ?: continue
                        val n = o.optString("name").lowercase()
                        if (n.contains("отоп") || n.contains("дом")) { c = o; break }
                    }
                }
                if (c == null) c = hc.optJSONObject(0)
                if (c != null) {
                    kind = "z3k"
                    circuit = c.optLong("id")
                    cname = c.optString("name")
                    val zs = io?.optJSONObject("z3k-state")?.optJSONObject(circuit.toString())
                    target = num(zs, "target_temp")
                    if (target.isNaN()) target = num(zs, "setpoint_temp")
                    for (k in listOf("curr_temp", "actual_temp", "temperature")) {
                        val v = num(zs, k)
                        if (!v.isNaN()) { cur = v; break }
                    }
                    if (zs != null) {
                        val it = zs.keys()
                        while (it.hasNext()) ks.append(it.next()).append(" ")
                    } else ks.append("нет io.z3k-state для контура")
                }
            }
            if (kind.isEmpty()) {
                val temps = d.optJSONObject("thermostat_mode_temps")
                val c = num(temps, "comfort")
                if (!c.isNaN()) {
                    kind = "comfort"
                    target = c
                } else if (d.has("thermostat_temp")) {
                    kind = "temp"
                    target = num(d, "thermostat_temp")
                }
            }

            // текущая температура: термометр управления (как раньше), если у контура её нет
            if (cur == null) {
                val th = d.optJSONArray("thermometers")
                if (th != null) {
                    for (i in 0 until th.length()) {
                        val t = th.getJSONObject(i)
                        if (!t.optBoolean("is_assigned_to_slot", true)) continue
                        if (t.isNull("last_value")) continue
                        val v = t.optDouble("last_value", Double.NaN)
                        if (v.isNaN()) continue
                        val control = t.optString("function") == "control"
                        if (cur == null || control) cur = v
                        if (control) break
                    }
                }
            }
            val st = io?.optJSONObject("last-boiler-state")
            var mode = if (d.isNull("thermostat_mode")) "" else d.optString("thermostat_mode", "")
            if (mode.isEmpty() && st != null && !st.isNull("thermostat_mode")) mode = st.optString("thermostat_mode", "")
            val on = st != null && st.optInt("boiler_work_time", 0) > 0
            val fail = st != null && st.optBoolean("fail", false)
            if (kind.isEmpty()) {
                // для диагностики: поля устройства, похожие на температуру и z3k
                val it = d.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    if (k.contains("temp") || k.contains("z3k") || k.contains("thermostat")) ks.append(k).append(" ")
                }
            }
            return ZontInfo(id, d.optString("name"), mode, target, cur, on, fail, ks.toString().trim(),
                kind, circuit, cname, cl.toString().trim())
        }
    }

    fun describe(): String {
        val sb = StringBuilder(name + " (ID " + id + ")")
        if (mode.isNotEmpty()) sb.append(": режим ").append(mode) else sb.append(":")
        if (!target.isNaN()) sb.append(", уставка ").append(fmt1(target)).append(" °C")
        val c = current
        if (c != null) sb.append(", сейчас ").append(fmt1(c)).append(" °C")
        sb.append(if (boilerOn) ", котёл работает" else ", котёл не работает")
        if (fail) sb.append(", АВАРИЯ котла")
        when (kind) {
            "z3k" -> {
                sb.append("\nКонтур: ").append(circuit).append(" «").append(circuitName).append("»")
                if (circuits.isNotEmpty()) sb.append("\nВсе контуры: ").append(circuits)
                if (target.isNaN()) sb.append("\nУставка контура не найдена, поля: ").append(keys)
            }
            "temp" -> sb.append("\nУставка: поле thermostat_temp")
            "comfort" -> sb.append("\nУставка: thermostat_mode_temps.comfort")
            else -> sb.append("\nУставка не найдена, поля: ").append(keys)
        }
        return sb.toString()
    }
}

const val EWL_REDIRECT = "https://127.0.0.1/smarthome"

// eWeLink: вход OAuth2.0 (APPID и APP SECRET из dev.ewelink.cc), облачное API v2
class Ewelink(private val appId: String, private val appSecret: String) {
    private fun hmacB64(msg: String): String {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(appSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.encodeToString(m.doFinal(msg.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun nonce(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val r = java.util.Random()
        val sb = StringBuilder()
        for (i in 0 until 8) sb.append(chars[r.nextInt(chars.length)])
        return sb.toString()
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    fun authUrl(state: String): String {
        val seq = System.currentTimeMillis().toString()
        return "https://c2ccdn.coolkit.cc/oauth/index.html?clientId=" + enc(appId) +
            "&seq=" + seq + "&authorization=" + enc(hmacB64(appId + "_" + seq)) +
            "&redirectUrl=" + enc(EWL_REDIRECT) + "&grantType=authorization_code" +
            "&state=" + enc(state) + "&nonce=" + nonce()
    }

    private fun base(): String {
        val r = Store.get("ewl_region").ifEmpty { "eu" }
        return if (r == "cn") "https://cn-apia.coolkit.cn" else "https://" + r + "-apia.coolkit.cc"
    }

    private fun parse(text: String): JSONObject {
        try {
            return JSONObject(text)
        } catch (e: Exception) {
            throw Exception("Неожиданный ответ eWeLink: " + text.take(200))
        }
    }

    fun exchange(code: String, region: String) {
        Store.put("ewl_region", region)
        val body = JSONObject().put("code", code).put("redirectUrl", EWL_REDIRECT)
            .put("grantType", "authorization_code").toString()
        val h = HashMap<String, String>()
        h["X-CK-Appid"] = appId
        h["X-CK-Nonce"] = nonce()
        h["Authorization"] = "Sign " + hmacB64(body)
        h["Content-Type"] = "application/json"
        val j = parse(Net.call("POST", base() + "/v2/user/oauth/token", h, body))
        if (j.optInt("error", -1) != 0) throw Exception("Вход: " + j.optString("msg") + " (" + j.optInt("error") + ")")
        val d = j.getJSONObject("data")
        Store.put("ewl_at", d.optString("accessToken"))
        Store.put("ewl_rt", d.optString("refreshToken"))
    }

    private fun headers(): HashMap<String, String> {
        val h = HashMap<String, String>()
        h["Authorization"] = "Bearer " + Store.get("ewl_at")
        h["X-CK-Appid"] = appId
        h["X-CK-Nonce"] = nonce()
        h["Content-Type"] = "application/json"
        return h
    }

    private fun refreshToken() {
        val rt = Store.get("ewl_rt")
        if (rt.isEmpty()) throw Exception("Войдите в eWeLink на экране «Подключения»")
        val body = JSONObject().put("rt", rt).toString()
        val j = parse(Net.call("POST", base() + "/v2/user/refresh", headers(), body))
        if (j.optInt("error", -1) != 0) throw Exception("Сессия eWeLink истекла, войдите заново (" + j.optString("msg") + ")")
        val d = j.getJSONObject("data")
        Store.put("ewl_at", d.optString("at", d.optString("accessToken")))
        Store.put("ewl_rt", d.optString("rt", d.optString("refreshToken")))
    }

    private fun api(method: String, path: String, body: String): JSONObject {
        if (Store.get("ewl_at").isEmpty()) throw Exception("Войдите в eWeLink на экране «Подключения»")
        var j = parse(Net.call(method, base() + path, headers(), body))
        val err = j.optInt("error", -1)
        if (err == 401 || err == 402) {
            refreshToken()
            j = parse(Net.call(method, base() + path, headers(), body))
        }
        if (j.optInt("error", -1) != 0) throw Exception("eWeLink: " + j.optString("msg") + " (" + j.optInt("error") + ")")
        return j
    }

    // список устройств; ключи устройств (devicekey) сохраняются для будущего локального режима
    fun devices(): List<Dev> {
        val j = api("GET", "/v2/device/thing?num=0", "")
        val arr = j.optJSONObject("data")?.optJSONArray("thingList") ?: JSONArray()
        val out = ArrayList<Dev>()
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            val type = t.optInt("itemType")
            if (type != 1 && type != 2) continue
            val d = t.optJSONObject("itemData") ?: continue
            val id = d.optString("deviceid")
            val key = d.optString("devicekey")
            if (key.isNotEmpty()) Store.put("ewl_key_" + id, key)
            var model = d.optString("productModel")
            if (model.isEmpty()) model = d.optJSONObject("extra")?.optString("model") ?: ""
            out.add(Dev(id, d.optString("name").replace("|", "/"), d.optBoolean("online"), listOf("switch"), model))
        }
        return out
    }

    fun states(ids: List<String>): Map<String, Boolean> {
        val list = JSONArray()
        for (id in ids) list.put(JSONObject().put("itemType", 1).put("id", id))
        val j = api("POST", "/v2/device/thing", JSONObject().put("thingList", list).toString())
        val arr = j.optJSONObject("data")?.optJSONArray("thingList") ?: JSONArray()
        val m = HashMap<String, Boolean>()
        for (i in 0 until arr.length()) {
            val d = arr.getJSONObject(i).optJSONObject("itemData") ?: continue
            val p = d.optJSONObject("params") ?: continue
            if (p.has("switch")) m[d.optString("deviceid")] = p.optString("switch") == "on"
        }
        return m
    }

    fun setSwitch(id: String, on: Boolean) {
        val body = JSONObject().put("type", 1).put("id", id)
            .put("params", JSONObject().put("switch", if (on) "on" else "off")).toString()
        api("POST", "/v2/device/thing/status", body)
    }
}

// терморегулятор Tuya: коды уставки и текущей температуры определяются по спецификации устройства
class Thermo(val setCode: String, val setScale: Int, val setMin: Double, val setMax: Double, val setStep: Double,
             val curCode: String, val curScale: Int, val modeCode: String) {
    fun real(raw: Any?, scale: Int): Double? {
        val n = (raw as? Number)?.toDouble() ?: return null
        return n / Math.pow(10.0, scale.toDouble())
    }

    companion object {
        private fun vals(o: JSONObject): JSONObject {
            return try {
                JSONObject(o.optString("values"))
            } catch (e: Exception) {
                JSONObject()
            }
        }

        fun setScore(c: String): Int {
            if (c == "temp_set") return 3
            if (c.contains("temp_set") || c.contains("set_temp")) return 2
            if (c.contains("temp") && c.contains("set") && !c.contains("upper") && !c.contains("lower")) return 1
            return 0
        }

        fun curScore(c: String): Int {
            if (c == "temp_current") return 3
            if (c.contains("temp_current") || c.contains("current_temp")) return 2
            if (c.contains("temp") && c.contains("current")) return 1
            return 0
        }

        fun from(spec: JSONObject): Thermo {
            var setO: JSONObject? = null
            var setS = 0
            var modeCode = ""
            val fs = spec.optJSONArray("functions") ?: JSONArray()
            for (i in 0 until fs.length()) {
                val f = fs.getJSONObject(i)
                val c = f.optString("code").lowercase()
                val t = f.optString("type")
                if (t.equals("Integer", true)) {
                    val sc = setScore(c)
                    if (sc > setS) {
                        setS = sc
                        setO = f
                    }
                }
                if (t.equals("Enum", true) && c == "mode") modeCode = f.optString("code")
            }
            var curO: JSONObject? = null
            var curS = 0
            val st = spec.optJSONArray("status") ?: JSONArray()
            for (i in 0 until st.length()) {
                val f = st.getJSONObject(i)
                val c = f.optString("code").lowercase()
                if (f.optString("type").equals("Integer", true)) {
                    val sc = curScore(c)
                    if (sc > curS) {
                        curS = sc
                        curO = f
                    }
                }
            }
            val so = setO
            val co = curO
            val sv = if (so != null) vals(so) else JSONObject()
            val cv = if (co != null) vals(co) else JSONObject()
            val ss = sv.optInt("scale", 0)
            val p = Math.pow(10.0, ss.toDouble())
            return Thermo(
                so?.optString("code") ?: "", ss,
                sv.optDouble("min", 5.0 * p) / p, sv.optDouble("max", 35.0 * p) / p,
                maxOf(sv.optDouble("step", 1.0), 1.0) / p,
                co?.optString("code") ?: "", cv.optInt("scale", 0), modeCode
            )
        }
    }
}
