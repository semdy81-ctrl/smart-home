package ru.smarthome.home

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

class LanDevice(val id: String, val host: String, val port: Int)

// eWeLink LAN mode: управление реле Sonoff по локальной сети, без облака.
// Поиск — mDNS-сервис «_ewelink._tcp», команды — HTTP POST на http://IP:порт/zeroconf/...,
// тело шифруется ключом устройства (devicekey из облачного списка, хранится как «ewl_key_<id>»).
// Найденные адреса сохраняются как «ewl_ip_<id>» = «IP:порт».
object EwelinkLan {

    fun saved(id: String): LanDevice? {
        val s = Store.get("ewl_ip_" + id)
        val i = s.lastIndexOf(':')
        if (i <= 0) return null
        val port = s.substring(i + 1).toIntOrNull() ?: return null
        return LanDevice(id, s.substring(0, i), port)
    }

    // результат приходит в главном потоке
    fun discover(ctx: Context, timeoutMs: Long, onDone: (Map<String, LanDevice>) -> Unit) {
        val app = ctx.applicationContext
        val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("ewelink-lan")
        lock.setReferenceCounted(false)
        try {
            lock.acquire()
        } catch (e: Exception) {
        }
        val found = ConcurrentHashMap<String, LanDevice>()
        val queue = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        var finished = false
        lateinit var listener: NsdManager.DiscoveryListener

        fun finish() {
            synchronized(queue) {
                if (finished) return
                finished = true
            }
            try {
                nsd.stopServiceDiscovery(listener)
            } catch (e: Exception) {
            }
            try {
                if (lock.isHeld) lock.release()
            } catch (e: Exception) {
            }
            for ((id, d) in found) Store.put("ewl_ip_" + id, d.host + ":" + d.port)
            val res = HashMap(found)
            Net.ui { onDone(res) }
        }

        // старые Android разбирают только один сервис за раз — идём по очереди
        fun resolveNext() {
            val next = synchronized(queue) {
                if (finished || resolving || queue.isEmpty()) null else {
                    resolving = true
                    queue.poll()
                }
            } ?: return
            try {
                nsd.resolveService(next, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                        synchronized(queue) { resolving = false }
                        resolveNext()
                    }

                    override fun onServiceResolved(si: NsdServiceInfo) {
                        var id = ""
                        try {
                            id = si.attributes["id"]?.toString(Charsets.UTF_8) ?: ""
                        } catch (e: Exception) {
                        }
                        if (id.isEmpty()) id = si.serviceName.removePrefix("eWeLink_").substringBefore(" ")
                        val host = si.host?.hostAddress
                        if (host != null && id.isNotEmpty()) found[id] = LanDevice(id, host, si.port)
                        synchronized(queue) { resolving = false }
                        resolveNext()
                    }
                })
            } catch (e: Exception) {
                synchronized(queue) { resolving = false }
            }
        }

        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(si: NsdServiceInfo) {
                synchronized(queue) { queue.add(si) }
                resolveNext()
            }
            override fun onServiceLost(si: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                finish()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        try {
            nsd.discoverServices("_ewelink._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            finish()
            return
        }
        Thread {
            try {
                Thread.sleep(timeoutMs)
            } catch (e: Exception) {
            }
            finish()
        }.start()
    }

    // включить/выключить реле по локальной сети; вызывать из фонового потока.
    // outlet — номер канала для многоканальных устройств, null — для одноканальных (BASICR2)
    fun setSwitch(id: String, on: Boolean, outlet: Int? = null) {
        val dev = saved(id) ?: throw Exception("Адрес устройства в локальной сети неизвестен — выполните поиск")
        val key = Store.get("ewl_key_" + id)
        if (key.isEmpty()) throw Exception("Нет ключа устройства — загрузите устройства eWeLink")
        val state = if (on) "on" else "off"
        val data = if (outlet == null) {
            JSONObject().put("switch", state)
        } else {
            JSONObject().put("switches", JSONArray().put(JSONObject().put("switch", state).put("outlet", outlet)))
        }
        val path = if (outlet == null) "/zeroconf/switch" else "/zeroconf/switches"
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val k = MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), IvParameterSpec(iv))
        val enc = Base64.encodeToString(cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        val body = JSONObject()
            .put("sequence", System.currentTimeMillis().toString())
            .put("deviceid", id)
            .put("selfApikey", "123")
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("encrypt", true)
            .put("data", enc)
            .toString()
        val c = URL("http://" + dev.host + ":" + dev.port + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 3000
            c.readTimeout = 3000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json;charset=UTF-8")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val s = if (c.responseCode >= 400) c.errorStream else c.inputStream
            val text = s?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val err = try {
                JSONObject(text).optInt("error", -1)
            } catch (e: Exception) {
                -1
            }
            if (err != 0) throw Exception("Реле ответило ошибкой: " + text.take(120))
        } finally {
            c.disconnect()
        }
    }
}
