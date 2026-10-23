package ru.smarthome.home

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import org.json.JSONObject

private val BG = 0xFF12171D.toInt()
private val PANEL = 0xFF1E252E.toInt()
private val LINE = 0xFF2E3947.toInt()
private val TEXT = 0xFFE8EDF2.toInt()
private val MUTED = 0xFF9AA7B5.toInt()
private val SOFT = 0xFFB8C4D0.toInt()
private val FAINT = 0xFF8896A5.toInt()
private val OFFI = 0xFF5B6775.toInt()
private val AMBER = 0xFFF0B35A.toInt()
private val AMBER_BG = 0xFF2B2619.toInt()
private val AMBER_CHIP = 0xFF3A3220.toInt()
private val TEAL = 0xFF5CC4D6.toInt()
private val TEAL_BG = 0xFF15292E.toInt()
private val CHIP = 0xFF28323D.toInt()
private val PHOTO = 0xFF1A2129.toInt()
private val PILL = 0xD912171D.toInt()
private val SCENE = 0xFF0E1319.toInt()
private val BRK_BG = 0xFF232B35.toInt()

// версия кода экрана: должна совпадать с versionName в app/build.gradle
private const val CODE_VER = "0.26"

private val NAMES = arrayOf("Дом", "Мастерская", "Хозблок", "Костровая", "Двор")
private val TEMPS = arrayOf("21,4 °C", "14,0 °C", "", "", "")
private val NAV_ICONS = intArrayOf(R.drawable.ic_house, R.drawable.ic_shop, R.drawable.ic_cube, R.drawable.ic_flame, R.drawable.ic_tree)

object St {
    var selected = 0
    val light = booleanArrayOf(false, false, false, false, false)
    var setpoint = 22
    var gate = false
    var alarmHouse = false
    var alarmShop = false
    var breaker = true
    var floorHeat = false
    var garland = false
    var spotBalcony = false
    var spotGarden = false
    var pump = false
    var houseHeat = true
    var houseSet = 22
    var houseTemp = "21,4"
    var houseTempV = 21.4
    var shopTemp = "14,0"
    var shopTempV = 14.0
    var shopSet = 5.5
    var shopMode = ""
    var shopRelay: Boolean? = null
    var shopLight = false
    var shopHeat = false
    var facade = false
    var awning = false
    var yPark = false
    var yPaths = false
    var yFlood = false
}

private class Sty(val fill: Int, val stroke: Int, val sw: Int, val fg: Int)

private fun sty(on: Boolean, accent: Int): Sty {
    if (!on) return Sty(PANEL, LINE, 1, MUTED)
    return if (accent == AMBER) Sty(AMBER_BG, AMBER, 2, AMBER) else Sty(TEAL_BG, TEAL, 2, TEAL)
}

private class Slot(val id: String, val label: String, val get: () -> Boolean, val set: (Boolean) -> Unit)

private val SLOTS = listOf(
    Slot("gate", "Ворота", { St.gate }, { St.gate = it }),
    Slot("bonfire", "Костровая, свет", { St.light[3] }, { St.light[3] = it }),
    Slot("shed", "Хозблок, свет", { St.light[2] }, { St.light[2] = it }),
    Slot("alarm_house", "Охрана дома", { St.alarmHouse }, { St.alarmHouse = it }),
    Slot("alarm_shop", "Охрана мастерской", { St.alarmShop }, { St.alarmShop = it }),
    Slot("breaker", "Дом: вводной автомат", { St.breaker }, { St.breaker = it }),
    Slot("floor", "Дом: тёплый пол", { St.floorHeat }, { St.floorHeat = it }),
    Slot("garland", "Дом: гирлянда", { St.garland }, { St.garland = it }),
    Slot("spot_balcony", "Дом: прожектор балкон", { St.spotBalcony }, { St.spotBalcony = it }),
    Slot("spot_garden", "Дом: прожектор огород", { St.spotGarden }, { St.spotGarden = it }),
    Slot("pump", "Дом: насос скважины", { St.pump }, { St.pump = it }),
    Slot("shop_light", "Мастерская: свет", { St.shopLight }, { St.shopLight = it }),
    Slot("shop_heat", "Мастерская: отопление", { St.shopHeat }, { St.shopHeat = it }),
    Slot("facade", "Мастерская: фасад", { St.facade }, { St.facade = it }),
    Slot("awning", "Мастерская: навес", { St.awning }, { St.awning = it }),
    Slot("park", "Двор: парковка", { St.yPark }, { St.yPark = it }),
    Slot("paths", "Двор: дорожки", { St.yPaths }, { St.yPaths = it }),
    Slot("flood", "Двор: прожекторы", { St.yFlood }, { St.yFlood = it })
)

private class Cam(val id: String, val def: String, val group: String)

private val CAMS = listOf(
    Cam("hall", "Зал", "house"),
    Cam("boiler", "Котельная", "house"),
    Cam("shop", "Мастерская", "shop"),
    Cam("y1", "Двор 1", "yard"),
    Cam("y2", "Двор 2", "yard"),
    Cam("y3", "Двор 3", "yard"),
    Cam("y4", "Двор 4", "yard"),
    Cam("y5", "Двор 5", "yard"),
    Cam("y6", "Двор 6", "yard"),
    Cam("gate", "Ворота", "gate")
)

private val EPS = listOf(
    "Центральная Европа" to "https://openapi.tuyaeu.com",
    "Западная Европа" to "https://openapi-weaz.tuyaeu.com",
    "Западная Америка" to "https://openapi.tuyaus.com",
    "Индия" to "https://openapi.tuyain.com",
    "Китай" to "https://openapi.tuyacn.com"
)

class MainActivity : Activity() {
    private var u = 1f
    private var screen = "main"
    private lateinit var root: FrameLayout
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        Store.init(this)
        EwelinkLan.init(this)
        if (Store.get("ewl_devs").isNotEmpty()) EwelinkLan.refresh()
        restoreState()
        applyKeepScreen()
        // планшет — альбомная ориентация, телефон — книжная (поворот по желанию пользователя)
        requestedOrientation = if (resources.configuration.smallestScreenWidthDp >= 600)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        updateScale()
        root = FrameLayout(this)
        root.setBackgroundColor(BG)
        setContentView(root)
        installCrashLog()
        show("main")
        showLastCrash()
    }

    // ---------- журнал вылетов: причина сохраняется в файл и показывается при следующем запуске ----------
    private fun installCrashLog() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        val dir = filesDir
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                java.io.File(dir, "crash.txt").writeText("Версия " + CODE_VER + ", экран «" + screen + "»\n" + sw.toString().take(3000))
            } catch (x: Throwable) {
            }
            if (prev != null) prev.uncaughtException(t, e)
        }
    }

    private fun showLastCrash() {
        try {
            val f = java.io.File(filesDir, "crash.txt")
            if (!f.exists()) return
            val msg = f.readText()
            f.delete()
            AlertDialog.Builder(this)
                .setTitle("Приложение закрывалось с ошибкой")
                .setMessage(msg.take(1500))
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Throwable) {
        }
    }

    // ошибка при построении экрана не закрывает приложение, а показывается на экране
    private fun errorPage(name: String, e: Throwable): View {
        val pg = page()
        pg.add(header("Ошибка", R.drawable.ic_gear), MATCH, px(56), mb = 12)
        val sw = java.io.StringWriter()
        e.printStackTrace(java.io.PrintWriter(sw))
        val sv = ScrollView(this)
        sv.addView(txt("Экран «" + name + "», версия " + CODE_VER + "\n" + sw.toString().take(2500), 15, MUTED), FrameLayout.LayoutParams(MATCH, WRAP))
        pg.add(sv, MATCH, 0, 1f, mb = 12)
        val back = card(PANEL, LINE, 1, 22, false) { show("main") }
        back.add(txt("На главный экран", 24, TEXT, true))
        pg.add(back, MATCH, px(72))
        return pg
    }

    private var portrait = false

    private fun updateScale() {
        val dm = resources.displayMetrics
        portrait = dm.heightPixels > dm.widthPixels
        u = if (portrait) dm.widthPixels / 412f else minOf(dm.widthPixels, dm.heightPixels) / 800f
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScale()
        if (screen != "ewl_login") show(screen, false)
    }

    override fun onBackPressed() {
        if (screen == "main") super.onBackPressed()
        else if (screen == "camera") camGoBack()
        else show("main")
    }

    override fun onStop() {
        super.onStop()
        stopPlayer()
    }

    override fun onResume() {
        super.onResume()
        if (screen == "camera" || screen == "gate") show(screen, false)
    }

    // экран не гаснет, пока приложение открыто (по умолчанию включено)
    private fun applyKeepScreen() {
        if (Store.get("keep_screen") != "0") window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ---------- сохранение положения приборов между запусками ----------
    private fun saveState() {
        for (sl in SLOTS) Store.put("st_" + sl.id, if (sl.get()) "1" else "0")
        Store.put("st_houseHeat", if (St.houseHeat) "1" else "0")
        Store.put("st_houseSet", St.houseSet.toString())
        Store.put("st_houseTemp", St.houseTemp)
        Store.put("st_shopTemp", St.shopTemp)
        Store.put("st_shopSet", St.shopSet.toString())
        Store.put("st_selected", St.selected.toString())
    }

    private fun restoreState() {
        for (sl in SLOTS) {
            val v = Store.get("st_" + sl.id)
            if (v.isNotEmpty()) sl.set(v == "1")
        }
        val hh = Store.get("st_houseHeat")
        if (hh.isNotEmpty()) St.houseHeat = hh == "1"
        Store.get("st_houseSet").toIntOrNull()?.let { St.houseSet = it }
        val ht = Store.get("st_houseTemp")
        if (ht.isNotEmpty()) {
            St.houseTemp = ht
            ht.replace(',', '.').toDoubleOrNull()?.let { St.houseTempV = it }
        }
        val stt = Store.get("st_shopTemp")
        if (stt.isNotEmpty()) {
            St.shopTemp = stt
            stt.replace(',', '.').toDoubleOrNull()?.let { St.shopTempV = it }
        }
        Store.get("st_shopSet").toDoubleOrNull()?.let { St.shopSet = it }
        val sel = Store.get("st_selected").toIntOrNull()
        if (sel != null && sel in 0..4) St.selected = sel
    }

    // лампочка на плитке объекта: горит, если включён любой свет этого объекта
    private fun lightOf(i: Int): Boolean {
        return when (i) {
            0 -> St.garland || St.spotBalcony || St.spotGarden
            1 -> St.shopLight || St.facade || St.awning
            4 -> St.yPark || St.yPaths || St.yFlood
            else -> St.light[i]
        }
    }

    private fun show(name: String, fetch: Boolean = true) {
        saveState()
        if (name != "camera" && name != "gate") stopPlayer()
        screen = name
        root.removeAllViews()
        val v: View = try { when (name) {
            "house" -> if (portrait) buildHouseP() else buildHouse()
            "shop" -> if (portrait) buildShopP() else buildShop()
            "yard" -> if (portrait) buildYardP() else buildYard()
            "settings" -> buildSettings()
            "bind" -> buildBind()
            "ewl_login" -> buildEwlLogin()
            "camera" -> buildCamera()
            "gate" -> buildGate()
            else -> if (portrait) buildMainP() else buildMain()
        } } catch (e: Throwable) {
            stopPlayer()
            if (name == "main") throw e
            errorPage(name, e)
        }
        root.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        if (fetch && name != "settings" && name != "bind" && name != "ewl_login" && name != "camera" && name != "gate") refresh()
    }

    // ---------- помощники ----------
    private fun px(v: Int): Int = (v * u).toInt()

    private fun bg(fill: Int, stroke: Int, sw: Int, r: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = px(r).toFloat()
        if (sw > 0) g.setStroke(maxOf(px(sw), 1), stroke)
        return g
    }

    private fun txt(t: String, size: Int, color: Int, bold: Boolean = false, center: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u)
        v.setTextColor(color)
        v.includeFontPadding = false
        if (bold) v.typeface = Typeface.DEFAULT_BOLD
        if (center) v.gravity = Gravity.CENTER_HORIZONTAL
        return v
    }

    private fun icon(res: Int, color: Int): ImageView {
        val v = ImageView(this)
        v.setImageResource(res)
        v.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        return v
    }

    private fun lin(vertical: Boolean, g: Int = Gravity.NO_GRAVITY): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        l.gravity = g
        return l
    }

    private fun LinearLayout.add(v: View, w: Int = WRAP, h: Int = WRAP, weight: Float = 0f,
                                 mt: Int = 0, mr: Int = 0, mb: Int = 0, ml: Int = 0) {
        val p = LinearLayout.LayoutParams(w, h, weight)
        p.setMargins(px(ml), px(mt), px(mr), px(mb))
        addView(v, p)
    }

    private fun flp(g: Int, m: Int): FrameLayout.LayoutParams {
        val p = FrameLayout.LayoutParams(WRAP, WRAP, g)
        p.setMargins(px(m), px(m), px(m), px(m))
        return p
    }

    private fun page(): LinearLayout {
        val l = lin(true)
        l.setPadding(px(20), px(20), px(20), px(20))
        return l
    }

    private fun toast(t: String) {
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    }

    private fun card(fill: Int, stroke: Int, sw: Int, r: Int, vertical: Boolean, click: (() -> Unit)?): LinearLayout {
        val c = lin(vertical, Gravity.CENTER)
        c.background = bg(fill, stroke, sw, r)
        if (click != null) {
            c.isClickable = true
            c.setOnClickListener { click() }
        }
        return c
    }

    private fun chip(res: Int, size: Int, isize: Int, fill: Int, color: Int): FrameLayout {
        val f = FrameLayout(this)
        f.background = bg(fill, 0, 0, if (size >= 60) 16 else 14)
        f.addView(icon(res, color), FrameLayout.LayoutParams(px(isize), px(isize), Gravity.CENTER))
        return f
    }

    private fun header(title: String, res: Int): LinearLayout {
        val h = lin(false, Gravity.CENTER_VERTICAL)
        h.add(chip(res, 52, 30, AMBER_BG, AMBER), px(52), px(52), mr = 16)
        h.add(txt(title, 40, TEXT, true))
        return h
    }

    private fun dot(color: Int): View {
        val v = View(this)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(color)
        v.background = g
        return v
    }

    private fun stepBtn(res: Int, click: () -> Unit): FrameLayout {
        val f = FrameLayout(this)
        f.background = bg(CHIP, LINE, 1, 16)
        f.addView(icon(res, TEAL), FrameLayout.LayoutParams(px(28), px(28), Gravity.CENTER))
        f.isClickable = true
        f.setOnClickListener { click() }
        return f
    }

    private fun tog(res: Int, label: String, state: String, on: Boolean, accent: Int, click: () -> Unit): LinearLayout {
        val s = sty(on, accent)
        val c = card(s.fill, s.stroke, s.sw, 18, true, click)
        c.add(icon(res, s.fg), px(36), px(36))
        c.add(txt(label, 22, TEXT, true), mt = 6)
        c.add(txt(state, 17, s.fg), mt = 2)
        return c
    }

    private fun spot(name: String, on: Boolean, click: () -> Unit): LinearLayout {
        val s = sty(on, AMBER)
        val c = card(s.fill, s.stroke, s.sw, 18, true, click)
        c.add(txt(name, 19, TEXT, true, true), MATCH, WRAP, ml = 6, mr = 6)
        c.add(txt(if (on) "включён" else "выключен", 15, s.fg), mt = 2)
        return c
    }

    private fun backBtn(): LinearLayout {
        val c = card(PANEL, LINE, 1, 22, false) { show("main") }
        c.add(icon(R.drawable.ic_back, TEAL), px(30), px(30), mr = 14)
        c.add(txt("Назад", 24, TEXT, true))
        return c
    }

    private fun settingsBtn(): LinearLayout {
        val c = card(PANEL, LINE, 1, 22, false) { show("settings") }
        c.add(icon(R.drawable.ic_gear, TEAL), px(30), px(30), mr = 14)
        c.add(txt("Настройки", 24, TEXT, true))
        return c
    }

    private fun indicator(res: Int, name: String): LinearLayout {
        val c = card(PANEL, LINE, 1, 22, false, null)
        c.add(icon(res, TEAL), px(36), px(36), mr = 14)
        val t = lin(true)
        t.add(txt(name, 21, TEXT))
        val st = lin(false, Gravity.CENTER_VERTICAL)
        st.add(dot(TEAL), px(10), px(10), mr = 8)
        st.add(txt("норма", 18, TEAL))
        t.add(st, mt = 4)
        c.add(t)
        return c
    }

    // кнопка насоса скважины (Smart Life) — на месте бывшего индикатора «Пожарный извещатель»
    private fun pumpCard(): LinearLayout {
        val on = St.pump
        val s = sty(on, TEAL)
        val c = card(s.fill, s.stroke, s.sw, 22, false) { flip("pump") }
        c.add(icon(R.drawable.ic_pump, if (on) TEAL else MUTED), px(36), px(36), mr = 14)
        val t = lin(true)
        t.add(txt("Насос скважины", 21, TEXT))
        val st = lin(false, Gravity.CENTER_VERTICAL)
        st.add(dot(if (on) TEAL else MUTED), px(10), px(10), mr = 8)
        st.add(txt(if (on) "включён" else "выключен", 18, if (on) TEAL else MUTED))
        t.add(st, mt = 4)
        c.add(t)
        return c
    }

    private fun gaugeCard(label: String, frac: Float): LinearLayout {
        val c = lin(true, Gravity.CENTER_HORIZONTAL)
        c.background = bg(PANEL, LINE, 1, 22)
        c.setPadding(0, px(18), 0, px(18))
        c.add(txt(label, 20, TEAL, true), mb = 12)
        c.add(GaugeBar(this, frac, u), px(120), 0, 1f)
        c.add(txt("0–40 °C", 16, MUTED), mt = 12)
        return c
    }

    private fun tile(res: Int, title: String, sub: String, body: View): LinearLayout {
        val t = lin(true)
        t.background = bg(PANEL, LINE, 1, 22)
        t.setPadding(px(20), px(20), px(20), px(20))
        val h = lin(false, Gravity.CENTER_VERTICAL)
        h.add(chip(res, 52, 28, CHIP, SOFT), px(52), px(52), mr = 14)
        val tx = lin(true)
        tx.add(txt(title, 26, TEXT, true))
        tx.add(txt(sub, 17, MUTED))
        h.add(tx)
        t.add(h, MATCH, WRAP, mb = 14)
        t.add(body, MATCH, 0, 1f)
        return t
    }

    private fun rowCard(res: Int, name: String, state: String, on: Boolean, click: () -> Unit): LinearLayout {
        val s = sty(on, AMBER)
        val c = card(s.fill, s.stroke, s.sw, 18, false, click)
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(px(20), 0, px(20), 0)
        c.add(chip(res, 52, 28, if (on) AMBER_CHIP else CHIP, if (on) AMBER else SOFT), px(52), px(52), mr = 16)
        val t = lin(true)
        t.add(txt(name, 28, TEXT, true))
        t.add(txt(state, 18, s.fg), mt = 2)
        c.add(t)
        return c
    }

    private fun pillBox(): LinearLayout {
        val p = lin(false, Gravity.CENTER_VERTICAL)
        p.background = bg(PILL, LINE, 1, 40)
        p.setPadding(px(20), px(10), px(20), px(10))
        return p
    }

    // ---------- главный экран ----------
    private fun buildMain(): View {
        val pg = page()
        val top = lin(false)
        val nav = lin(true)
        for (i in 0..4) nav.add(navTile(i), MATCH, 0, 1f, mb = if (i < 4) 10 else 0)
        top.add(nav, px(340), MATCH, mr = 16)
        top.add(photo(), 0, MATCH, 1f)
        pg.add(top, MATCH, 0, 1f, mb = 16)

        val right = lin(false)
        right.add(setpointCard(), 0, MATCH, 1.4f, mr = 16)
        right.add(alarm("дома", St.alarmHouse) { flip("alarm_house") }, 0, MATCH, 1f, mr = 16)
        right.add(alarm("мастерской", St.alarmShop) { flip("alarm_shop") }, 0, MATCH, 1f)
        val bottom = lin(false)
        bottom.add(gateCard(), px(340), MATCH, mr = 16)
        bottom.add(right, 0, MATCH, 1f)
        pg.add(bottom, MATCH, px(150))
        return pg
    }

    // одна строка: если название не помещается, шрифт уменьшается (не меньше minPx)
    private fun fitOneLine(v: TextView, maxPx: Float, minPx: Float) {
        v.setSingleLine(true)
        v.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val w = r - l - v.paddingLeft - v.paddingRight
            if (w > 0) {
                val p = android.text.TextPaint(v.paint)
                val text = v.text.toString()
                var s = maxPx
                p.textSize = s
                while (p.measureText(text) > w && s > minPx) {
                    s -= 1f
                    p.textSize = s
                }
                if (v.textSize != s) v.post { v.setTextSize(TypedValue.COMPLEX_UNIT_PX, s) }
            }
        }
    }

    private fun navTile(i: Int): View {
        val sel = i == St.selected
        val c = lin(false, Gravity.CENTER_VERTICAL)
        c.background = bg(if (sel) AMBER_BG else PANEL, if (sel) AMBER else LINE, if (sel) 2 else 1, 18)
        c.setPadding(px(20), 0, px(20), 0)
        c.isClickable = true
        c.setOnClickListener { onNav(i) }
        c.add(chip(NAV_ICONS[i], 52, 28, if (sel) AMBER_CHIP else CHIP, if (sel) AMBER else SOFT), px(52), px(52), mr = 16)
        val nm = txt(NAMES[i], 28, TEXT, true)
        fitOneLine(nm, 28 * u, 16 * u)
        c.add(nm, 0, WRAP, 1f)
        if (tempOf(i).isNotEmpty()) c.add(txt(tempOf(i), 20, SOFT), WRAP, WRAP, mr = 10)
        c.add(icon(R.drawable.ic_bulb, if (lightOf(i)) AMBER else OFFI), px(26), px(26))
        return c
    }

    private fun onNav(i: Int) {
        St.selected = i
        when (i) {
            0 -> show("house")
            1 -> show("shop")
            4 -> show("yard")
            else -> flip(if (i == 2) "shed" else "bonfire")
        }
    }

    // фото выбранного объекта; пока есть только фото дома
    private fun addPhoto(f: FrameLayout): Boolean {
        val res = if (St.selected == 0) R.drawable.photo_house else 0
        if (res == 0) return false
        val iv = ImageView(this)
        iv.setImageResource(res)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        f.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
        f.clipToOutline = true
        return true
    }

    private fun photo(): View {
        val f = FrameLayout(this)
        f.background = bg(PHOTO, LINE, 1, 22)
        if (!addPhoto(f)) {
            val c = lin(true, Gravity.CENTER)
            c.add(txt("Фото объекта управления", 34, FAINT, true))
            c.add(txt("место под ваше фото или кадр с камеры", 18, FAINT), mt = 10)
            f.addView(c, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        val nm = pillBox()
        nm.addView(txt(NAMES[St.selected], 24, TEXT, true))
        f.addView(nm, flp(Gravity.START or Gravity.TOP, 20))
        val cam = pillBox()
        cam.add(icon(R.drawable.ic_camera, TEAL), px(26), px(26), mr = 10)
        cam.add(txt("Камера", 20, TEXT))
        cam.isClickable = true
        cam.setOnClickListener { openCamFor(St.selected) }
        f.addView(cam, flp(Gravity.END or Gravity.TOP, 20))
        val gear = pillBox()
        gear.add(icon(R.drawable.ic_gear, TEAL), px(26), px(26), mr = 10)
        gear.add(txt("Подключения", 20, TEXT))
        gear.isClickable = true
        gear.setOnClickListener { show("settings") }
        f.addView(gear, flp(Gravity.END or Gravity.BOTTOM, 20))
        if (tempOf(St.selected).isNotEmpty()) {
            val t = pillBox()
            t.addView(txt("Сейчас " + tempOf(St.selected), 20, SOFT))
            f.addView(t, flp(Gravity.START or Gravity.BOTTOM, 20))
        }
        return f
    }

    private fun gateCard(): LinearLayout {
        val c = card(PANEL, LINE, 1, 22, false) { show("gate") }
        c.add(chip(R.drawable.ic_gate, 68, 40, CHIP, SOFT), px(68), px(68), mr = 18)
        val t = lin(true)
        t.add(txt("Ворота", 32, TEXT, true))
        t.add(txt("Камера и открытие", 18, MUTED), mt = 4)
        c.add(t)
        return c
    }

    private fun setpointCard(): LinearLayout {
        val c = card(PANEL, LINE, 1, 22, false, null)
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(px(24), 0, px(24), 0)
        val l = lin(true)
        l.add(txt("Отопление дома, уставка", 18, MUTED))
        l.add(txt(St.houseSet.toString() + " °C", 56, TEXT, true), mt = 4)
        c.add(l, 0, WRAP, 1f)
        val st = lin(true)
        st.add(stepBtn(R.drawable.ic_up) { changeSet(1) }, px(56), px(56), mb = 8)
        st.add(stepBtn(R.drawable.ic_down) { changeSet(-1) }, px(56), px(56))
        c.add(st)
        return c
    }

    private fun alarm(l2: String, on: Boolean, click: () -> Unit): LinearLayout {
        val s = sty(on, AMBER)
        val c = card(s.fill, s.stroke, s.sw, 22, true, click)
        c.add(icon(if (on) R.drawable.ic_shield_on else R.drawable.ic_shield, s.fg), px(38), px(38))
        c.add(txt("Охрана", 26, TEXT, true), mt = 3)
        c.add(txt(l2, 26, TEXT, true))
        c.add(txt(if (on) "включена" else "отключена", 17, s.fg), mt = 2)
        return c
    }

    // ---------- меню «Дом» ----------
    private fun buildHouse(): View {
        val pg = page()
        pg.add(header("Дом", R.drawable.ic_house), MATCH, px(64), mb = 16)
        val main = lin(false)
        main.add(gaugeCard("+" + St.houseTemp + " °C", (St.houseTempV / 40.0).toFloat().coerceIn(0f, 1f)), px(120), MATCH, mr = 16)
        main.add(breakerCard(), px(340), MATCH, mr = 16)
        val tiles = lin(true)
        val r1 = lin(false)
        r1.add(tileFloor(), 0, MATCH, 1f, mr = 16)
        r1.add(tileLight(), 0, MATCH, 1f)
        val r2 = lin(false)
        r2.add(tileHeat(), 0, MATCH, 1f, mr = 16)
        r2.add(tileCams(), 0, MATCH, 1f)
        tiles.add(r1, MATCH, 0, 1f, mb = 16)
        tiles.add(r2, MATCH, 0, 1f)
        main.add(tiles, 0, MATCH, 1f)
        pg.add(main, MATCH, 0, 1f, mb = 16)
        val bot = lin(false)
        bot.add(backBtn(), 0, MATCH, 0.8f, mr = 16)
        bot.add(pumpCard(), 0, MATCH, 1.7f, mr = 16)
        bot.add(indicator(R.drawable.ic_gas, "Сигнализатор загазованности"), 0, MATCH, 1.7f, mr = 16)
        bot.add(settingsBtn(), 0, MATCH, 0.8f)
        pg.add(bot, MATCH, px(110))
        return pg
    }

    private fun breakerCard(): LinearLayout {
        val on = St.breaker
        val c = card(BRK_BG, SOFT, 2, 22, true) {
            if (St.breaker) confirmOff() else flip("breaker")
        }
        c.add(icon(R.drawable.ic_breaker, SOFT), px(130), px(130))
        c.add(txt("Вводной автомат", 32, TEXT, true), mt = 12)
        c.add(txt(if (on) "Включён" else "Отключён", 40, if (on) TEXT else MUTED, true), mt = 12)
        c.add(txt("отключение — только с подтверждением", 17, MUTED, false, true), MATCH, WRAP, mt = 12, ml = 20, mr = 20)
        return c
    }

    private fun confirmOff() {
        AlertDialog.Builder(this)
            .setTitle("Вводной автомат")
            .setMessage("Отключить вводной автомат дома?")
            .setPositiveButton("Отключить") { _, _ -> flip("breaker") }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun tileFloor(): View {
        val b = tog(R.drawable.ic_floor, if (St.floorHeat) "Включён" else "Выключен",
            if (St.floorHeat) "нажмите, чтобы выключить" else "нажмите, чтобы включить", St.floorHeat, TEAL) {
            flip("floor")
        }
        return tile(R.drawable.ic_floor, "Тёплый пол", "Ванная", b)
    }

    private fun tileLight(): View {
        val body = lin(false)
        body.add(tog(R.drawable.ic_garland, "Гирлянда", if (St.garland) "включена" else "выключена", St.garland, AMBER) {
            flip("garland")
        }, px(128), MATCH, mr = 12)
        val col = lin(true)
        col.add(spot("Прожектор балкон", St.spotBalcony) { flip("spot_balcony") }, MATCH, 0, 1f, mb = 10)
        col.add(spot("Прожектор огород", St.spotGarden) { flip("spot_garden") }, MATCH, 0, 1f)
        body.add(col, 0, MATCH, 1f)
        return tile(R.drawable.ic_bulb, "Освещение", "гирлянда и прожекторы", body)
    }

    private fun tileHeat(): View {
        val body = lin(true)
        val row = lin(false, Gravity.CENTER_VERTICAL)
        row.add(stepBtn(R.drawable.ic_down) { changeSet(-1) }, px(56), px(56))
        val mid = lin(true, Gravity.CENTER)
        mid.add(txt(St.houseSet.toString() + " °C", 44, TEXT, true))
        mid.add(txt("сейчас " + St.houseTemp + " °C", 16, TEAL))
        row.add(mid, 0, WRAP, 1f)
        row.add(stepBtn(R.drawable.ic_up) { changeSet(1) }, px(56), px(56))
        body.add(row, MATCH, 0, 1f)
        val s = sty(St.houseHeat, TEAL)
        val b = card(s.fill, s.stroke, s.sw, 16, true) { toggleHeat() }
        b.add(txt(if (St.houseHeat) "Включена" else "Отключена", 22, s.fg, true))
        body.add(b, MATCH, px(52), mt = 10)
        return tile(R.drawable.ic_radiator, "Система отопления", "ZONT · Baxi", body)
    }

    private fun camBtn(name: String): LinearLayout {
        val c = lin(false, Gravity.CENTER_VERTICAL)
        c.background = bg(CHIP, LINE, 1, 16)
        c.setPadding(px(10), 0, px(10), 0)
        c.isClickable = true
        c.setOnClickListener { openCam(if (name == "Зал") "hall" else "boiler") }
        c.add(icon(R.drawable.ic_camera, TEAL), px(24), px(24), mr = 8)
        c.add(txt(name, 18, TEXT, true))
        return c
    }

    private fun tileCams(): View {
        val body = lin(true)
        val both = card(CHIP, TEAL, 2, 16, false) { openCamGroup("house") }
        both.add(icon(R.drawable.ic_camera, TEAL), px(30), px(30), mr = 12)
        both.add(txt("Обе камеры", 24, TEXT, true))
        body.add(both, MATCH, 0, 1.3f, mb = 10)
        val row = lin(false)
        row.add(camBtn("Зал"), 0, MATCH, 1f, mr = 10)
        row.add(camBtn("Котельная"), 0, MATCH, 1f)
        body.add(row, MATCH, 0, 1f)
        return tile(R.drawable.ic_camera, "Камеры", "Зал и Котельная", body)
    }

    // ---------- меню «Мастерская» ----------
    private fun buildShop(): View {
        val pg = page()
        pg.add(header("Мастерская", R.drawable.ic_shop), MATCH, px(64), mb = 16)
        val main = lin(false)
        main.add(gaugeCard("+" + St.shopTemp + " °C", (St.shopTempV / 40.0).toFloat().coerceIn(0f, 1f)), px(120), MATCH, mr = 16)
        main.add(shopThermoCard(false), px(400), MATCH, mr = 16)
        val act = lin(true)
        act.add(tog(R.drawable.ic_bulb, "Свет", if (St.shopLight) "включён" else "выключен", St.shopLight, AMBER) {
            flip("shop_light")
        }, MATCH, 0, 1f, mb = 16)
        act.add(tog(R.drawable.ic_radiator, "Отопление", if (St.shopHeat) "включено" else "выключено", St.shopHeat, TEAL) {
            flip("shop_heat")
        }, MATCH, 0, 1f, mb = 16)
        act.add(tog(R.drawable.ic_camera, "Камера", "смотреть", false, AMBER) { openCam("shop") }, MATCH, 0, 1f)
        main.add(act, px(220), MATCH, mr = 16)
        val lt = lin(true)
        lt.add(lightRow(R.drawable.ic_bulb, "Освещение фасада", St.facade) { flip("facade") }, MATCH, px(170), mb = 16)
        lt.add(lightRow(R.drawable.ic_awning, "Освещение навеса", St.awning) { flip("awning") }, MATCH, px(170))
        main.add(lt, 0, MATCH, 1f)
        pg.add(main, MATCH, 0, 1f, mb = 16)
        val bot = lin(false)
        bot.add(backBtn(), 0, MATCH, 1f, mr = 16)
        bot.add(indicator(R.drawable.ic_flame, "Пожарная сигнализация"), 0, MATCH, 1f, mr = 16)
        bot.add(settingsBtn(), 0, MATCH, 1f)
        pg.add(bot, MATCH, px(110))
        return pg
    }

    private fun lightRow(res: Int, name: String, on: Boolean, click: () -> Unit): LinearLayout {
        val s = sty(on, AMBER)
        val c = card(s.fill, s.stroke, s.sw, 22, false, click)
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(px(28), 0, px(28), 0)
        c.add(chip(res, 64, 36, if (on) AMBER_CHIP else CHIP, if (on) AMBER else SOFT), px(64), px(64), mr = 20)
        val t = lin(true)
        t.add(txt(name, 30, TEXT, true))
        t.add(txt(if (on) "включено" else "выключено", 18, s.fg), mt = 4)
        c.add(t)
        return c
    }

    // ---------- меню «Двор» ----------
    private fun buildYard(): View {
        val pg = page()
        pg.add(header("Двор", R.drawable.ic_tree), MATCH, px(64), mb = 16)
        val main = lin(false)
        val nav = lin(true)
        nav.add(rowCard(R.drawable.ic_parking, "Парковка", if (St.yPark) "включено" else "выключено", St.yPark) {
            flip("park")
        }, MATCH, 0, 1f, mb = 10)
        nav.add(rowCard(R.drawable.ic_route, "Дорожки", if (St.yPaths) "включено" else "выключено", St.yPaths) {
            flip("paths")
        }, MATCH, 0, 1f, mb = 10)
        nav.add(rowCard(R.drawable.ic_flood, "Прожекторы", if (St.yFlood) "включено" else "выключено", St.yFlood) {
            flip("flood")
        }, MATCH, 0, 1f, mb = 10)
        val all = St.yPark && St.yPaths && St.yFlood
        nav.add(rowCard(R.drawable.ic_bulb, "Весь двор", if (all) "выключить всё" else "включить всё", all) {
            yardAll(!all)
        }, MATCH, 0, 1f)
        main.add(nav, px(340), MATCH, mr = 16)
        val sc = FrameLayout(this)
        val yv = YardView(this, St.yPark, St.yPaths, St.yFlood)
        yv.background = bg(SCENE, LINE, 1, 22)
        sc.addView(yv, FrameLayout.LayoutParams(MATCH, MATCH))
        val pl = pillBox()
        pl.addView(txt("Фото двора: место под ваш снимок", 18, MUTED))
        sc.addView(pl, flp(Gravity.END or Gravity.TOP, 20))
        main.add(sc, 0, MATCH, 1f)
        pg.add(main, MATCH, 0, 1f, mb = 16)
        val bot = lin(false)
        bot.add(backBtn(), px(340), MATCH, mr = 16)
        val cam = card(PANEL, LINE, 1, 22, false) { openCamGroup("yard") }
        cam.add(icon(R.drawable.ic_camera, TEAL), px(36), px(36), mr = 14)
        cam.add(txt("Камеры двора", 24, TEXT, true))
        bot.add(cam, 0, MATCH, 1f)
        pg.add(bot, MATCH, px(110))
        return pg
    }
    // ---------- подключение устройств ----------
    private var refreshing = false
    private var lastRefresh = 0L
    private var lastGate = 0L
    private var ewlHandled = false

    private fun slotOf(id: String): Slot = SLOTS.first { it.id == id }

    private fun flip(id: String) {
        val sl = slotOf(id)
        val want = !sl.get()
        val b = Store.get("bind_" + id)
        if (b.isEmpty()) {
            sl.set(want)
            show(screen, false)
            return
        }
        if (b.startsWith("ewlp|")) {
            val now = System.currentTimeMillis()
            if (now - lastGate < 5000L) {
                toast("Подождите несколько секунд перед повторной командой воротам")
                return
            }
            lastGate = now
            Net.bg {
                val gerr = Drivers.set(b, true)
                Net.ui { toast(if (gerr == null) "Ворота: команда отправлена" else "Ошибка: " + gerr) }
            }
            return
        }
        Net.bg {
            val err = Drivers.set(b, want)
            Net.ui {
                if (err == null) sl.set(want) else toast("Ошибка: " + err)
                show(screen, false)
            }
        }
    }

    private fun yardAll(v: Boolean) {
        for (id in listOf("park", "paths", "flood")) {
            if (slotOf(id).get() != v) flip(id)
        }
    }

    // ---------- камеры (Dahua, RTSP) ----------
    private val players = ArrayList<ExoPlayer>()
    private var camList: List<Cam> = ArrayList()
    private var camIdx = 0
    private var camBack = "main"
    private var camMain = false
    private var camGrid = false
    private var camFromGrid = false

    private fun configured(group: String): List<Cam> =
        CAMS.filter { it.group == group && Store.get("cam_ip_" + it.id).isNotEmpty() }

    private fun needCamSetup(msg: String) {
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("Подключения") { _, _ -> show("settings") }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openCam(id: String) {
        val c = CAMS.first { it.id == id }
        val list = configured(c.group)
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) {
            needCamSetup("Камера «" + c.def + "» не настроена. Укажите её IP-адрес (и порт, если он не 554) в «Подключениях».")
            return
        }
        camList = list
        camIdx = idx
        camGrid = false
        camFromGrid = false
        camBack = screen
        show("camera", false)
    }

    // все настроенные камеры группы открываются сразу, плитками
    private fun openCamGroup(group: String) {
        val list = configured(group)
        if (list.isEmpty()) {
            needCamSetup("Камеры не настроены. Укажите IP-адреса (и порты, если они не 554) в «Подключениях».")
            return
        }
        camList = list
        camIdx = 0
        camGrid = list.size > 1
        camFromGrid = false
        camBack = screen
        if (group == "house") {
            val missing = CAMS.filter { it.group == group && Store.get("cam_ip_" + it.id).isEmpty() }
            if (missing.isNotEmpty()) toast("Не настроена камера: " + missing.joinToString(", ") { it.def })
        }
        show("camera", false)
    }

    private fun openCamFor(i: Int) {
        when (i) {
            0 -> openCamGroup("house")
            1 -> openCam("shop")
            4 -> openCamGroup("yard")
            else -> toast("Для этого объекта камеры нет")
        }
    }

    private fun camGoBack() {
        if (camFromGrid) {
            camFromGrid = false
            camGrid = true
            show("camera", false)
        } else {
            show(camBack, false)
        }
    }

    // в поле камеры: «IP», «IP:порт» (камеры Dahua) или полный адрес потока rtsp://IP:порт/путь (камеры других марок)
    private fun camUrl(c: Cam, main: Boolean): String {
        val input = Store.get("cam_ip_" + c.id).trim()
        val user = Store.get("cam_user").ifEmpty { "admin" }
        val pass = Store.get("cam_pass")
        val auth = if (pass.isEmpty()) Uri.encode(user) else Uri.encode(user) + ":" + Uri.encode(pass)
        if (input.lowercase().startsWith("rtsp://") && input.substring(7).contains("/")) {
            var rest = input.substring(7)
            if (!rest.substringBefore("/").contains("@")) rest = auth + "@" + rest
            // типичные пути: /11 — основной поток, /12 — дополнительный
            if (main && rest.endsWith("/12")) rest = rest.dropLast(3) + "/11"
            if (!main && rest.endsWith("/11")) rest = rest.dropLast(3) + "/12"
            return "rtsp://" + rest
        }
        val raw = input.removePrefix("rtsp://").substringBefore("/")
        val ip = raw.substringBefore(":")
        val port = raw.substringAfter(":", "554").ifEmpty { "554" }
        return "rtsp://" + auth + "@" + ip + ":" + port + "/cam/realmonitor?channel=1&subtype=" + (if (main) "0" else "1")
    }

    private fun stopPlayer() {
        for (p in players) {
            try {
                p.release()
            } catch (e: Exception) {
            }
        }
        players.clear()
    }

    // быстрая проверка: отвечает ли камера на порту RTSP и что отвечает на запрос
    private fun probeCamera(host: String, port: Int, path: String, out: (String) -> Unit) {
        Net.bg {
            val res: String = try {
                val s = java.net.Socket()
                s.connect(java.net.InetSocketAddress(host, port), 4000)
                s.soTimeout = 4000
                val req = "DESCRIBE rtsp://" + host + ":" + port + path + " RTSP/1.0\r\nCSeq: 1\r\nAccept: application/sdp\r\n\r\n"
                s.getOutputStream().write(req.toByteArray(Charsets.UTF_8))
                val line = s.getInputStream().bufferedReader(Charsets.UTF_8).readLine() ?: ""
                s.close()
                "Порт " + port + " открыт, ответ камеры: " + line
            } catch (e: java.net.SocketTimeoutException) {
                "Камера не отвечает (таймаут): проверьте IP-адрес и что устройство в той же сети, что и камера"
            } catch (e: java.net.ConnectException) {
                "Порт " + port + " закрыт или IP неверный: " + (e.message ?: "")
            } catch (e: Exception) {
                "Ошибка проверки: " + (e.message ?: e.toString())
            }
            Net.ui { out(res) }
        }
    }

    private fun startPlayer(sv: SurfaceView, status: TextView, url: String, compact: Boolean) {
        val pass = Store.get("cam_pass")
        val uri = Uri.parse(url)
        val host = uri.host ?: ""
        val port = if (uri.port > 0) uri.port else 554
        val q = uri.query
        val path = (uri.path ?: "") + (if (q != null) "?" + q else "")
        val p = ExoPlayer.Builder(this).build()
        p.setVideoSurfaceView(sv)
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    status.visibility = View.GONE
                } else if (playbackState == Player.STATE_BUFFERING) {
                    status.text = "Подключаюсь…"
                    status.visibility = View.VISIBLE
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (compact) {
                    status.text = "Нет изображения\n" + error.errorCodeName + "\nНажмите, чтобы подробнее"
                    status.visibility = View.VISIBLE
                    return
                }
                var msg = error.errorCodeName + ": " + (error.message ?: "")
                val cause = error.cause
                if (cause != null) msg += " / " + (cause.message ?: cause.javaClass.simpleName)
                if (pass.isNotEmpty()) msg = msg.replace(pass, "***")
                val shown = msg
                status.text = "Нет изображения.\n" + shown + "\nПроверяю камеру…"
                status.visibility = View.VISIBLE
                probeCamera(host, port, path) { r -> status.text = "Нет изображения.\n" + shown + "\n" + r }
            }
        })
        players.add(p)
        try {
            val src = RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(MediaItem.fromUri(url))
            p.setMediaSource(src)
            p.prepare()
            p.playWhenReady = true
        } catch (e: Throwable) {
            var m = e.javaClass.simpleName + ": " + (e.message ?: "")
            if (pass.isNotEmpty()) m = m.replace(pass, "***")
            status.text = "Нет изображения.\n" + m
            status.visibility = View.VISIBLE
        }
    }

    private fun buildCamera(): View {
        stopPlayer()
        if (camList.isEmpty()) return buildMain()
        if (camIdx >= camList.size) camIdx = 0
        return if (camGrid && camList.size > 1) buildCameraGrid() else buildCameraSingle()
    }

    private fun camCell(i: Int): View {
        val c = camList[i]
        val fr = FrameLayout(this)
        fr.setBackgroundColor(0xFF000000.toInt())
        val sv = SurfaceView(this)
        fr.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
        val st = txt("Подключаюсь…", 14, TEXT, false, true)
        val lp = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER)
        lp.setMargins(px(8), px(8), px(8), px(8))
        fr.addView(st, lp)
        fr.addView(pillP(null, c.def, TEXT, null), flp(Gravity.START or Gravity.TOP, 6))
        fr.isClickable = true
        fr.setOnClickListener {
            camIdx = i
            camGrid = false
            camFromGrid = true
            show("camera", false)
        }
        startPlayer(sv, st, camUrl(c, false), true)
        return fr
    }

    private fun buildCameraGrid(): View {
        val n = camList.size
        val cols = if (portrait) (if (n <= 2) 1 else 2) else (if (n <= 2) n else if (n <= 4) 2 else 3)
        val rows = (n + cols - 1) / cols
        val pg = page()
        val title = if (camList[0].group == "house") "Камеры дома" else "Камеры двора"
        pg.add(header(title, R.drawable.ic_camera), MATCH, px(56), mb = 12)
        val grid = lin(true)
        var k = 0
        for (r in 0 until rows) {
            val row = lin(false)
            for (ci in 0 until cols) {
                val mr = if (ci < cols - 1) 8 else 0
                if (k < n) {
                    row.add(camCell(k), 0, MATCH, 1f, mr = mr)
                    k++
                } else {
                    row.add(View(this), 0, MATCH, 1f, mr = mr)
                }
            }
            grid.add(row, MATCH, 0, 1f, mb = if (r < rows - 1) 8 else 0)
        }
        pg.add(grid, MATCH, 0, 1f, mb = 12)
        val bar = lin(false)
        val back = card(PANEL, LINE, 1, 22, false) { camGoBack() }
        back.add(icon(R.drawable.ic_back, TEAL), px(26), px(26), mr = 10)
        back.add(txt("Назад", 22, TEXT, true))
        bar.add(back, 0, MATCH, 1f, mr = 10)
        val ref = card(PANEL, LINE, 1, 22, true) { show("camera", false) }
        ref.add(txt("Обновить", 22, TEXT, true))
        bar.add(ref, 0, MATCH, 1f)
        pg.add(bar, MATCH, px(64))
        return pg
    }

    private fun buildCameraSingle(): View {
        val c = camList[camIdx]
        val pg = page()
        pg.add(header(c.def, R.drawable.ic_camera), MATCH, px(56), mb = 12)
        val fr = FrameLayout(this)
        fr.setBackgroundColor(0xFF000000.toInt())
        val sv = SurfaceView(this)
        fr.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
        val st = txt("Подключаюсь…", 18, TEXT, false, true)
        val lp = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER)
        lp.setMargins(px(20), px(20), px(20), px(20))
        fr.addView(st, lp)
        pg.add(fr, MATCH, 0, 1f, mb = 12)
        val bar = lin(false)
        val back = card(PANEL, LINE, 1, 22, false) { camGoBack() }
        back.add(icon(R.drawable.ic_back, TEAL), px(26), px(26), mr = 10)
        back.add(txt("Назад", 22, TEXT, true))
        bar.add(back, 0, MATCH, 1.4f, mr = 10)
        if (camList.size > 1) {
            val prev = card(PANEL, LINE, 1, 22, true) { camIdx = (camIdx + camList.size - 1) % camList.size; show("camera", false) }
            prev.add(icon(R.drawable.ic_back, TEAL), px(26), px(26))
            bar.add(prev, 0, MATCH, 1f, mr = 10)
            val next = card(PANEL, LINE, 1, 22, true) { camIdx = (camIdx + 1) % camList.size; show("camera", false) }
            val ni = icon(R.drawable.ic_back, TEAL)
            ni.rotation = 180f
            next.add(ni, px(26), px(26))
            bar.add(next, 0, MATCH, 1f, mr = 10)
            val all = card(PANEL, LINE, 1, 22, true) { camGrid = true; camFromGrid = false; show("camera", false) }
            all.add(txt("Все", 22, TEXT, true))
            bar.add(all, 0, MATCH, 1f, mr = 10)
        }
        val q = card(if (camMain) AMBER_BG else PANEL, if (camMain) AMBER else LINE, if (camMain) 2 else 1, 22, true) {
            camMain = !camMain
            show("camera", false)
        }
        q.add(txt(if (camMain) "HD" else "SD", 22, if (camMain) AMBER else TEXT, true))
        bar.add(q, 0, MATCH, 1f)
        pg.add(bar, MATCH, px(64))
        startPlayer(sv, st, camUrl(c, camMain), false)
        return pg
    }

    // ---------- экран ворот: камера + открытие удержанием ----------
    private fun gateBound(): Boolean = Store.get("bind_gate").isNotEmpty()

    private fun gateHint(): String = if (gateBound()) "удерживайте полсекунды" else "ожидает подключения eWeLink"

    // импульс 0,5 с задаёт само реле («Включение на время» в eWeLink), приложение отправляет только «включить»
    private fun sendGate(status: TextView) {
        val b = Store.get("bind_gate")
        if (b.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now - lastGate < 5000L) {
            status.text = "подождите несколько секунд"
            status.postDelayed({ status.text = gateHint() }, 2500L)
            return
        }
        lastGate = now
        status.text = "отправляю команду…"
        Net.bg {
            val err = Drivers.set(b, true)
            Net.ui {
                status.text = if (err == null) "команда отправлена" else "ошибка: " + err
                if (err != null) toast("Ворота: " + err)
                status.postDelayed({ status.text = gateHint() }, 4000L)
            }
        }
    }

    private fun gateHoldButton(): View {
        val on = gateBound()
        val f = FrameLayout(this)
        f.background = bg(if (on) AMBER_BG else PANEL, if (on) AMBER else LINE, if (on) 2 else 1, 22)
        f.clipToOutline = true
        val fill = View(this)
        fill.setBackgroundColor(0x66F0B35A)
        fill.pivotX = 0f
        fill.scaleX = 0f
        f.addView(fill, FrameLayout.LayoutParams(MATCH, MATCH))
        val row = lin(false, Gravity.CENTER)
        row.add(icon(R.drawable.ic_gate, if (on) AMBER else MUTED), px(44), px(44), mr = 16)
        val tx = lin(true)
        tx.add(txt(if (on) "Открыть / закрыть" else "Ворота не подключены", 26, if (on) AMBER else MUTED, true))
        val hint = txt(gateHint(), 17, if (on) AMBER else MUTED)
        tx.add(hint, mt = 4)
        row.add(tx)
        f.addView(row, FrameLayout.LayoutParams(MATCH, MATCH))
        var anim: ValueAnimator? = null
        val fire = Runnable {
            f.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            sendGate(hint)
            f.postDelayed({ fill.scaleX = 0f }, 500L)
        }
        f.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!on) {
                            toast("Кнопка заработает после подключения eWeLink и привязки «Ворота» в «Подключениях»")
                            return true
                        }
                        anim?.cancel()
                        val a = ValueAnimator.ofFloat(0f, 1f)
                        a.duration = 500L
                        a.addUpdateListener { fill.scaleX = it.animatedValue as Float }
                        a.start()
                        anim = a
                        f.postDelayed(fire, 500L)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (fill.scaleX < 0.99f) {
                            f.removeCallbacks(fire)
                            anim?.cancel()
                            fill.scaleX = 0f
                        }
                    }
                }
                return true
            }
        })
        return f
    }

    private fun buildGate(): View {
        stopPlayer()
        val pg = lin(true)
        val m = if (portrait) 16 else 20
        pg.setPadding(px(m), px(m), px(m), px(m))
        pg.add(header("Ворота", R.drawable.ic_gate), MATCH, px(56), mb = 12)
        val c = CAMS.first { it.id == "gate" }
        val hasCam = Store.get("cam_ip_gate").isNotEmpty()
        val fr = FrameLayout(this)
        fr.setBackgroundColor(0xFF000000.toInt())
        var sv: SurfaceView? = null
        val st = txt("Подключаюсь…", 18, TEXT, false, true)
        if (hasCam) {
            val s = SurfaceView(this)
            fr.addView(s, FrameLayout.LayoutParams(MATCH, MATCH))
            val lp = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER)
            lp.setMargins(px(20), px(20), px(20), px(20))
            fr.addView(st, lp)
            sv = s
        } else {
            val col = lin(true, Gravity.CENTER)
            col.add(txt("Камера ворот не указана", 24, TEXT, true, true), MATCH, WRAP)
            col.add(txt("Подключения → Камеры → поле «Ворота»: IP:порт камеры, которая видит ворота. Нажмите, чтобы открыть", 17, MUTED, false, true), MATCH, WRAP, mt = 10, ml = 20, mr = 20)
            fr.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
            fr.isClickable = true
            fr.setOnClickListener { show("settings") }
        }
        pg.add(fr, MATCH, 0, 1f, mb = 12)
        pg.add(gateHoldButton(), MATCH, px(if (portrait) 92 else 104), mb = 12)
        val bar = lin(false)
        val back = card(PANEL, LINE, 1, 22, false) { show("main") }
        back.add(icon(R.drawable.ic_back, TEAL), px(26), px(26), mr = 10)
        back.add(txt("Назад", 22, TEXT, true))
        bar.add(back, 0, MATCH, 1.4f, mr = 10)
        if (hasCam) {
            val ref = card(PANEL, LINE, 1, 22, true) { show("gate", false) }
            ref.add(txt("Обновить", 22, TEXT, true))
            bar.add(ref, 0, MATCH, 1.2f, mr = 10)
            val q = card(if (camMain) AMBER_BG else PANEL, if (camMain) AMBER else LINE, if (camMain) 2 else 1, 22, true) {
                camMain = !camMain
                show("gate", false)
            }
            q.add(txt(if (camMain) "HD" else "SD", 22, if (camMain) AMBER else TEXT, true))
            bar.add(q, 0, MATCH, 1f)
        }
        pg.add(bar, MATCH, px(64))
        if (sv != null) startPlayer(sv, st, camUrl(c, camMain), false)
        return pg
    }

    private fun zontReady(): Boolean = Store.get("zont_login").isNotEmpty() && Store.get("zont_dev").isNotEmpty()

    private fun tempOf(i: Int): String = if (i == 0) St.houseTemp + " °C" else if (i == 1) St.shopTemp + " °C" else TEMPS[i]

    private fun thermoId(): String {
        val p = Store.get("bind_shop_heat").split("|")
        return if (p.size >= 2 && p[0] == "tuya") p[1] else ""
    }

    private fun modeName(md: String): String {
        return when (md) {
            "hot", "heat", "heating" -> "обогрев"
            "cold", "cool", "cooling" -> "охлаждение"
            else -> md
        }
    }

    private fun changeShopSet(d: Int) {
        val tid = thermoId()
        if (tid.isEmpty()) {
            toast("Привяжите «Мастерская: отопление» к терморегулятору")
            return
        }
        val cur = St.shopSet
        Net.bg {
            var nv = cur
            val err: String? = try {
                val th = Drivers.thermo(tid)
                if (th.setCode.isEmpty()) throw Exception("у устройства не найдена уставка")
                nv = minOf(th.setMax, maxOf(th.setMin, cur + d * th.setStep))
                val raw = Math.round(nv * Math.pow(10.0, th.setScale.toDouble()))
                Drivers.tuya().setRaw(tid, th.setCode, raw.toString())
                null
            } catch (e: Exception) {
                e.message ?: e.toString()
            }
            val v = nv
            Net.ui {
                if (err == null) St.shopSet = v else toast("Уставка: " + err)
                show(screen, false)
            }
        }
    }

    private fun shopThermoCard(compact: Boolean): LinearLayout {
        val k = if (compact) 0.75f else 1f
        fun sz(v: Int): Int = (v * k).toInt()
        val c = card(PANEL, LINE, 1, 22, true, null)
        c.add(txt("Терморегулятор", sz(22), MUTED))
        c.add(txt(St.shopTemp + " °C", sz(56), TEXT, true), mt = sz(6))
        c.add(txt("сейчас в мастерской", sz(16), MUTED))
        val row = lin(false, Gravity.CENTER_VERTICAL)
        row.add(stepBtn(R.drawable.ic_down) { changeShopSet(-1) }, px(sz(56)), px(sz(56)), mr = sz(14))
        val mid = lin(true, Gravity.CENTER_HORIZONTAL)
        mid.add(txt("уставка", sz(15), MUTED))
        mid.add(txt(fmt1(St.shopSet) + " °C", sz(34), TEAL, true))
        row.add(mid)
        row.add(stepBtn(R.drawable.ic_up) { changeShopSet(1) }, px(sz(56)), px(sz(56)), ml = sz(14))
        c.add(row, WRAP, WRAP, mt = sz(18))
        if (St.shopMode.isNotEmpty()) c.add(txt("режим: " + modeName(St.shopMode), sz(16), MUTED), mt = sz(10))
        val relay = St.shopRelay
        if (relay != null) c.add(txt(if (relay) "нагрев идёт" else "нагрев не идёт", sz(16), if (relay) AMBER else MUTED, relay), mt = sz(6))
        return c
    }

    private fun pickDiag(out: TextView) {
        val devs = Dev.fromJson(Store.get("tuya_devs"))
        if (devs.isEmpty()) {
            toast("Сначала загрузите устройства Smart Life")
            return
        }
        val names = devs.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Устройство Smart Life")
            .setItems(names) { _, i ->
                val d = devs[i]
                out.text = "Читаю «" + d.name + "»…"
                Net.bg {
                    val msg: String = try {
                        d.name + "\n" + Drivers.tuya().describeDevice(d.id)
                    } catch (e: Exception) {
                        "Ошибка: " + (e.message ?: e.toString())
                    }
                    Net.ui { out.text = msg }
                }
            }
            .show()
    }

    private fun refresh() {
        if (refreshing) return
        if (System.currentTimeMillis() - lastRefresh < 3000L) return
        val bound = SLOTS.filter { Store.get("bind_" + it.id).isNotEmpty() }
        val zOn = zontReady()
        if (bound.isEmpty() && !zOn) return
        refreshing = true
        lastRefresh = System.currentTimeMillis()
        Net.bg {
            val binds = HashMap<String, String>()
            for (sl in bound) binds[sl.id] = Store.get("bind_" + sl.id)
            val res: Map<String, Boolean> = if (binds.isEmpty()) HashMap() else Drivers.states(binds)
            var z: ZontInfo? = null
            if (zOn) {
                try {
                    val want = Store.get("zont_dev")
                    val arr = Drivers.zont().devices()
                    for (i in 0 until arr.length()) {
                        val d = arr.getJSONObject(i)
                        if (d.optLong("device_id").toString() == want) z = ZontInfo.from(d)
                    }
                } catch (e: Exception) {
                }
            }
            var tCur: Double? = null
            var tSet: Double? = null
            var tMode = ""
            var tRelay: Boolean? = null
            val tid = thermoId()
            if (tid.isNotEmpty()) {
                try {
                    val th = Drivers.thermo(tid)
                    val st = Drivers.tuya().statusAll(tid)
                    tCur = th.real(st[th.curCode], th.curScale)
                    tSet = th.real(st[th.setCode], th.setScale)
                    if (th.modeCode.isNotEmpty()) tMode = st[th.modeCode]?.toString() ?: ""
                    // реле нагрузки: switch_1, если кнопка «Отопление» привязана к другому переключателю
                    val pc = Store.get("bind_shop_heat").split("|").getOrElse(2) { "" }
                    if (pc != "switch_1") tRelay = st["switch_1"] as? Boolean
                } catch (e: Exception) {
                }
            }
            val tc = tCur
            val ts = tSet
            val tm = tMode
            val tr = tRelay
            val zi = z
            Net.ui {
                refreshing = false
                var changed = false
                for (sl in bound) {
                    val v = res[sl.id]
                    if (v != null && v != sl.get()) {
                        sl.set(v)
                        changed = true
                    }
                }
                if (zi != null && zi.mode.isNotEmpty()) {
                    val on = zi.mode != "idle"
                    if (on != St.houseHeat) {
                        St.houseHeat = on
                        changed = true
                    }
                    if (!zi.comfort.isNaN()) {
                        val c = Math.round(zi.comfort).toInt()
                        if (c != St.houseSet) {
                            St.houseSet = c
                            changed = true
                        }
                    }
                    val cur = zi.current
                    if (cur != null) {
                        val t = fmt1(cur)
                        if (t != St.houseTemp) {
                            St.houseTemp = t
                            St.houseTempV = cur
                            changed = true
                        }
                    }
                }
                if (tc != null) {
                    val t = fmt1(tc)
                    if (t != St.shopTemp) {
                        St.shopTemp = t
                        St.shopTempV = tc
                        changed = true
                    }
                }
                if (ts != null && ts != St.shopSet) {
                    St.shopSet = ts
                    changed = true
                }
                if (tm != St.shopMode) {
                    St.shopMode = tm
                    changed = true
                }
                if (tr != St.shopRelay) {
                    St.shopRelay = tr
                    changed = true
                }
                if (changed && screen != "settings" && screen != "bind" && screen != "ewl_login" && screen != "camera" && screen != "gate") show(screen, false)
            }
        }
    }

    private fun toggleHeat() {
        val want = !St.houseHeat
        if (!zontReady()) {
            St.houseHeat = want
            show(screen, false)
            return
        }
        Net.bg {
            val err: String? = try {
                Drivers.zont().update(Store.get("zont_dev").toLong(), JSONObject().put("thermostat_mode", if (want) "comfort" else "idle"))
                null
            } catch (e: Exception) {
                e.message ?: e.toString()
            }
            Net.ui {
                if (err == null) St.houseHeat = want else toast("ZONT: " + err)
                show(screen, false)
            }
        }
    }

    private fun changeSet(d: Int) {
        val v = minOf(35, maxOf(5, St.houseSet + d))
        St.houseSet = v
        show(screen, false)
        if (!zontReady()) return
        val heatOn = St.houseHeat
        Net.bg {
            val err: String? = try {
                val p = JSONObject()
                p.put("thermostat_mode_temps", JSONObject().put("comfort", v))
                if (heatOn) p.put("thermostat_mode", "comfort")
                Drivers.zont().update(Store.get("zont_dev").toLong(), p)
                null
            } catch (e: Exception) {
                e.message ?: e.toString()
            }
            if (err != null) Net.ui { toast("ZONT: " + err) }
        }
    }

    private fun runZontCheck(out: TextView) {
        out.text = "Подключаюсь…"
        Net.bg {
            val msg: String = try {
                val arr = Drivers.zont().devices()
                val sb = StringBuilder("Устройств ZONT: " + arr.length() + "\n")
                for (i in 0 until arr.length()) {
                    val z = ZontInfo.from(arr.getJSONObject(i))
                    if (i == 0) Store.put("zont_dev", z.id.toString())
                    sb.append(z.describe()).append("\n")
                }
                sb.toString()
            } catch (e: Exception) {
                "Ошибка: " + (e.message ?: e.toString())
            }
            Store.put("zont_log", msg)
            Net.ui { out.text = msg }
        }
    }

    private fun field(label: String, key: String, secret: Boolean = false): LinearLayout {
        val r = lin(false, Gravity.CENTER_VERTICAL)
        r.add(txt(label, 20, MUTED), px(if (portrait) 130 else 220), WRAP)
        val e = EditText(this)
        e.setText(Store.get(key))
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * u)
        e.setTextColor(TEXT)
        e.setSingleLine(true)
        if (secret) e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        e.background = bg(CHIP, LINE, 1, 12)
        e.setPadding(px(14), px(10), px(14), px(10))
        e.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                Store.put(key, (s?.toString() ?: "").trim())
                Drivers.reset()
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        r.add(e, 0, WRAP, 1f)
        return r
    }

    private fun epLabel(): String {
        val e = Store.get("tuya_ep")
        val f = EPS.firstOrNull { it.second == e }
        return if (f != null) f.first else EPS[0].first
    }

    private fun pickEndpoint() {
        val names = EPS.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Дата-центр Tuya")
            .setItems(names) { _, i ->
                Store.put("tuya_ep", EPS[i].second)
                Drivers.reset()
                show("settings", false)
            }
            .show()
    }

    private fun runTuyaCheck(out: TextView) {
        out.text = "Подключаюсь…"
        Net.bg {
            val msg: String = try {
                val list = Drivers.tuya().devices()
                Store.put("tuya_devs", Dev.toJson(list))
                val sb = StringBuilder("Найдено устройств: " + list.size + "\n")
                for (d in list) {
                    sb.append(d.name)
                    sb.append(if (d.online) " · в сети · " else " · не в сети · ")
                    sb.append(if (d.codes.isEmpty()) d.info else d.codes.joinToString(","))
                    sb.append("\n")
                }
                sb.toString()
            } catch (e: Exception) {
                "Ошибка: " + (e.message ?: e.toString())
            }
            Store.put("tuya_log", msg)
            Net.ui { out.text = msg }
        }
    }

    private fun appVersion(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun buildSettings(): View {
        val pg = page()
        pg.add(header("Подключения", R.drawable.ic_gear), MATCH, px(64), mb = 12)
        val sv = ScrollView(this)
        val col = lin(true)
        val keep = Store.get("keep_screen") != "0"
        val ks = card(if (keep) AMBER_BG else PANEL, if (keep) AMBER else LINE, if (keep) 2 else 1, 12, false) {
            Store.put("keep_screen", if (keep) "0" else "1")
            applyKeepScreen()
            show("settings", false)
        }
        ks.setPadding(px(12), px(14), px(12), px(14))
        ks.add(txt(if (keep) "Не гасить экран: включено" else "Не гасить экран: выключено", 20, if (keep) AMBER else TEXT, true))
        col.add(ks, MATCH, WRAP, mb = 16)
        col.add(txt("Smart Life (Tuya, облако)", 24, TEXT, true), mb = 10)
        col.add(field("Access ID", "tuya_id"), MATCH, WRAP, mb = 10)
        col.add(field("Access Secret", "tuya_secret", true), MATCH, WRAP, mb = 10)
        col.add(field("UID аккаунта", "tuya_uid"), MATCH, WRAP, mb = 10)
        val dc = card(CHIP, LINE, 1, 12, false) { pickEndpoint() }
        dc.add(txt("Дата-центр: " + epLabel() + " (нажмите, чтобы изменить)", 20, TEXT))
        col.add(dc, MATCH, px(52), mb = 10)
        val out = txt(Store.get("tuya_log").ifEmpty { "Нажмите «Проверить»" }, 17, MUTED)
        val check = card(AMBER_BG, AMBER, 2, 12, false) { runTuyaCheck(out) }
        check.add(txt("Проверить и загрузить устройства", 20, AMBER, true))
        col.add(check, MATCH, px(52), mb = 10)
        col.add(out, MATCH, WRAP, mb = 10)
        val diag = card(PANEL, LINE, 1, 12, false) { pickDiag(out) }
        diag.add(txt("Сведения об устройстве Smart Life", 20, TEXT, true))
        col.add(diag, MATCH, px(52), mb = 20)
        col.add(txt("eWeLink (Sonoff)", 24, TEXT, true), mt = 10, mb = 10)
        col.add(field("APPID", "ewl_appid"), MATCH, WRAP, mb = 10)
        col.add(field("APP SECRET", "ewl_secret", true), MATCH, WRAP, mb = 10)
        col.add(txt("Redirect URL в eWeLink Console: " + EWL_REDIRECT, 17, MUTED), mb = 10)
        val eout = txt(Store.get("ewl_log").ifEmpty { if (Store.get("ewl_at").isEmpty()) "Вход не выполнен" else "Вход выполнен" }, 17, MUTED)
        val erow = lin(false)
        val elogin = card(AMBER_BG, AMBER, 2, 12, false) { startEwlLogin() }
        elogin.add(txt("Войти в eWeLink", 20, AMBER, true))
        erow.add(elogin, 0, px(52), 1f, mr = 10)
        val eload = card(AMBER_BG, AMBER, 2, 12, false) { runEwlLoad(eout) }
        eload.add(txt("Загрузить устройства", 20, AMBER, true))
        erow.add(eload, 0, px(52), 1f)
        col.add(erow, MATCH, WRAP, mb = 10)
        col.add(eout, MATCH, WRAP, mb = 10)
        val lout = txt(Store.get("ewl_lan_log").ifEmpty { "Локальная сеть: поиск не выполнялся" }, 17, MUTED)
        val lan = card(PANEL, LINE, 1, 12, false) { runEwlLan(lout) }
        lan.add(txt("Найти в локальной сети", 20, TEXT, true))
        col.add(lan, MATCH, px(52), mb = 10)
        col.add(lout, MATCH, WRAP, mb = 20)
        col.add(txt("ZONT (котёл)", 24, TEXT, true), mt = 10, mb = 10)
        col.add(field("Логин ZONT", "zont_login"), MATCH, WRAP, mb = 10)
        col.add(field("Пароль ZONT", "zont_pass", true), MATCH, WRAP, mb = 10)
        col.add(field("E-mail для API", "zont_mail"), MATCH, WRAP, mb = 10)
        val zout = txt(Store.get("zont_log").ifEmpty { "Нажмите «Проверить ZONT»" }, 17, MUTED)
        val zcheck = card(AMBER_BG, AMBER, 2, 12, false) { runZontCheck(zout) }
        zcheck.add(txt("Проверить ZONT", 20, AMBER, true))
        col.add(zcheck, MATCH, px(52), mb = 10)
        col.add(zout, MATCH, WRAP, mb = 20)
        col.add(txt("Камеры (Dahua, RTSP)", 24, TEXT, true), mt = 10, mb = 10)
        col.add(field("Логин камер", "cam_user"), MATCH, WRAP, mb = 10)
        col.add(field("Пароль камер", "cam_pass", true), MATCH, WRAP, mb = 10)
        col.add(txt("Dahua: IP камеры, если порт не 554 — через двоеточие (192.168.1.50:8554). Камеры других марок: полный адрес потока, например rtsp://192.168.1.88:554/12. Пустое поле — камеры нет", 17, MUTED), mb = 10)
        for (cm in CAMS) col.add(field(cm.def, "cam_ip_" + cm.id), MATCH, WRAP, mb = 10)
        val bind = card(PANEL, LINE, 1, 12, false) { show("bind") }
        bind.add(txt("Привязка кнопок к устройствам", 20, TEXT, true))
        col.add(bind, MATCH, px(52), mb = 10)
        val av = appVersion()
        col.add(txt("Умный дом · версия " + av + " · код " + CODE_VER, 16, FAINT), mt = 6)
        if (av != CODE_VER) col.add(txt("Внимание: версия сборки и версия кода MainActivity.kt не совпадают — один из файлов обновления не загружен", 16, AMBER), mt = 6)
        sv.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        pg.add(sv, MATCH, 0, 1f, mb = 12)
        val back = card(PANEL, LINE, 1, 22, false) { show("main") }
        back.add(txt("Назад", 24, TEXT, true))
        pg.add(back, MATCH, px(72))
        return pg
    }

    private fun refFor(d: Dev, c: String): String {
        val parts = c.split("#")
        val isProp = parts[0].endsWith("@p")
        val code = if (isProp) parts[0].substring(0, parts[0].length - 2) else parts[0]
        return (if (isProp) "tuyap" else "tuya") + "|" + d.id + "|" + code + "|" + d.name + " · " + code +
            (if (parts.size == 3) "|" + parts[1] + "|" + parts[2] else "")
    }

    private fun norm(v: String): String = v.lowercase().replace('ё', 'е').trim()

    // автопривязка известных устройств Smart Life и eWeLink; уже заданные привязки не трогает
    private fun autoBind() {
        val devs = Dev.fromJson(Store.get("tuya_devs"))
        val edevs = Dev.fromJson(Store.get("ewl_devs"))
        if (devs.isEmpty() && edevs.isEmpty()) {
            toast("Сначала загрузите устройства на экране «Подключения»")
            return
        }
        val rules = listOf(
            Triple("breaker", "kws-306wf", "switch_1@p"),
            Triple("alarm_house", "сигнализация", "master_mode#arm#disarmed"),
            Triple("shop_heat", "ик отопление мастерская", "switch_all"),
            Triple("pump", "насос скважины", "switch")
        )
        var n = 0
        for ((slot, namePart, code) in rules) {
            if (Store.get("bind_" + slot).isNotEmpty()) continue
            val d = devs.firstOrNull { it.online && it.name.lowercase().contains(namePart) && it.codes.contains(code) }
                ?: devs.firstOrNull { it.name.lowercase().contains(namePart) && it.codes.contains(code) }
            if (d == null) continue
            Store.put("bind_" + slot, refFor(d, code))
            n++
        }
        val erules = listOf(
            "gate" to listOf("дача"),
            "bonfire" to listOf("костров"),
            "floor" to listOf("теплый пол ванн"),
            "garland" to listOf("гирлянд"),
            "spot_garden" to listOf("прожектор огород"),
            "facade" to listOf("свет. мастерская", "фасад"),
            "awning" to listOf("навес"),
            "paths" to listOf("освещение двор", "освещение улица"),
            "flood" to listOf("прожектор ворота")
        )
        for ((slot, parts) in erules) {
            if (Store.get("bind_" + slot).isNotEmpty()) continue
            val d = edevs.firstOrNull { dv -> parts.any { p -> norm(dv.name) == p } }
                ?: edevs.firstOrNull { dv -> parts.any { p -> norm(dv.name).contains(p) } } ?: continue
            Store.put("bind_" + slot, (if (slot == "gate") "ewlp" else "ewl") + "|" + d.id + "|switch|" + d.name)
            n++
        }
        toast("Привязано кнопок: " + n)
        show("bind", false)
    }

    // ---------- вход в eWeLink ----------
    private fun startEwlLogin() {
        if (Store.get("ewl_appid").isEmpty() || Store.get("ewl_secret").isEmpty()) {
            toast("Сначала введите APPID и APP SECRET")
            return
        }
        ewlHandled = false
        show("ewl_login", false)
    }

    private fun buildEwlLogin(): View {
        val pg = page()
        pg.add(header("Вход в eWeLink", R.drawable.ic_gear), MATCH, px(64), mb = 12)
        val url = try {
            Drivers.ewl().authUrl("sh" + System.currentTimeMillis())
        } catch (e: Exception) {
            ""
        }
        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = catchRedirect(url)

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (catchRedirect(url)) view?.stopLoading() else super.onPageStarted(view, url, favicon)
            }
        }
        pg.add(wv, MATCH, 0, 1f, mb = 12)
        val back = card(PANEL, LINE, 1, 22, false) { show("settings", false) }
        back.add(txt("Отмена", 24, TEXT, true))
        pg.add(back, MATCH, px(72))
        if (url.isNotEmpty()) wv.loadUrl(url)
        return pg
    }

    private fun catchRedirect(url: String?): Boolean {
        if (url == null || !url.startsWith(EWL_REDIRECT)) return false
        if (ewlHandled) return true
        ewlHandled = true
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code") ?: ""
        val region = uri.getQueryParameter("region") ?: "eu"
        if (code.isEmpty()) {
            Store.put("ewl_log", "Вход не выполнен: eWeLink не вернул код")
            Net.ui { show("settings", false) }
            return true
        }
        Store.put("ewl_log", "Вход выполнен, загружаю устройства…")
        Net.ui { show("settings", false) }
        Net.bg {
            val msg: String = try {
                Drivers.ewl().exchange(code, region)
                ewlListText()
            } catch (e: Exception) {
                "Ошибка входа: " + (e.message ?: e.toString())
            }
            Store.put("ewl_log", msg)
            Net.ui { if (screen == "settings") show("settings", false) }
        }
        return true
    }

    private fun ewlListText(): String {
        val list = Drivers.ewl().devices()
        Store.put("ewl_devs", Dev.toJson(list))
        val sb = StringBuilder("Устройств eWeLink: " + list.size + "\n")
        for (d in list) {
            sb.append(d.name).append(if (d.online) " · в сети" else " · не в сети")
            if (d.info.isNotEmpty()) sb.append(" · ").append(d.info)
            sb.append(if (Store.get("ewl_key_" + d.id).isNotEmpty()) " · ключ есть" else " · нет ключа")
            sb.append("\n")
        }
        return sb.toString()
    }

    // поиск реле eWeLink в локальной сети (LAN mode), без облака
    private fun runEwlLan(out: TextView) {
        out.text = "Ищу реле в локальной сети (около 10 секунд)…"
        EwelinkLan.discover(this, 10000L) { found ->
            val devs = Dev.fromJson(Store.get("ewl_devs"))
            val sb = StringBuilder("Найдено в локальной сети: " + found.size + "\n")
            if (devs.isEmpty()) sb.append("Список устройств eWeLink не загружен — нажмите «Загрузить устройства»\n")
            for (d in devs) {
                val f = found[d.id]
                sb.append(d.name).append(if (f != null) " · " + f.host + ":" + f.port else " · не найдено").append("\n")
            }
            for ((id, f) in found) {
                if (devs.none { it.id == id }) sb.append("Неизвестное устройство ").append(id).append(" · ").append(f.host).append("\n")
            }
            val msg = sb.toString().trimEnd()
            Store.put("ewl_lan_log", msg)
            out.text = msg
        }
    }

    private fun runEwlLoad(out: TextView) {
        out.text = "Загружаю…"
        Net.bg {
            val msg: String = try {
                ewlListText()
            } catch (e: Exception) {
                "Ошибка: " + (e.message ?: e.toString())
            }
            Store.put("ewl_log", msg)
            Net.ui { out.text = msg }
        }
    }

    private fun pickDevice(sl: Slot) {
        val devs = Dev.fromJson(Store.get("tuya_devs"))
        if (devs.isEmpty()) toast("Сначала загрузите устройства на экране «Подключения»")
        val names = ArrayList<String>()
        val refs = ArrayList<String>()
        names.add("— не подключено (демо) —")
        refs.add("")
        for (d in devs) {
            for (c in d.codes) {
                val parts = c.split("#")
                val isProp = parts[0].endsWith("@p")
                val code = if (isProp) parts[0].substring(0, parts[0].length - 2) else parts[0]
                val extra = if (parts.size == 3) " (" + parts[1] + "/" + parts[2] + ")" else ""
                names.add(d.name + " · " + code + (if (isProp) " (свойство)" else "") + extra)
                refs.add(refFor(d, c))
            }
        }
        for (d in Dev.fromJson(Store.get("ewl_devs"))) {
            names.add("eWeLink · " + d.name)
            refs.add((if (sl.id == "gate") "ewlp" else "ewl") + "|" + d.id + "|switch|" + d.name)
        }
        AlertDialog.Builder(this)
            .setTitle(sl.label)
            .setItems(names.toTypedArray()) { _, i ->
                Store.put("bind_" + sl.id, refs[i])
                show("bind", false)
            }
            .show()
    }

    private fun buildBind(): View {
        val pg = page()
        pg.add(header("Привязка кнопок", R.drawable.ic_gear), MATCH, px(64), mb = 12)
        val sv = ScrollView(this)
        val col = lin(true)
        val auto = card(AMBER_BG, AMBER, 2, 12, false) { autoBind() }
        auto.add(txt("Привязать автоматически", 20, AMBER, true))
        col.add(auto, MATCH, px(56), mb = 12)
        for (sl in SLOTS) {
            val b = Store.get("bind_" + sl.id)
            val shown = if (b.isEmpty()) "не подключено (демо)" else b.split("|").getOrElse(3) { "?" }
            val row = card(PANEL, LINE, 1, 14, false) { pickDevice(sl) }
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(px(20), 0, px(20), 0)
            row.add(txt(sl.label, 22, TEXT, true), 0, WRAP, 1f)
            row.add(txt(shown, 18, if (b.isEmpty()) MUTED else TEAL))
            col.add(row, MATCH, px(60), mb = 8)
        }
        sv.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        pg.add(sv, MATCH, 0, 1f, mb = 12)
        val back = card(PANEL, LINE, 1, 22, false) { show("settings", false) }
        back.add(txt("Назад", 24, TEXT, true))
        pg.add(back, MATCH, px(72))
        return pg
    }
    // ---------- книжная ориентация (телефон) ----------
    private fun scrollPage(title: String, res: Int, body: LinearLayout, bar: LinearLayout): View {
        val pg = lin(true)
        pg.setPadding(px(16), px(16), px(16), px(16))
        pg.add(header(title, res), MATCH, px(56), mb = 12)
        val sv = ScrollView(this)
        sv.addView(body, FrameLayout.LayoutParams(MATCH, WRAP))
        pg.add(sv, MATCH, 0, 1f, mb = 12)
        pg.add(bar, MATCH, px(64))
        return pg
    }

    private fun bottomBar(right: View): LinearLayout {
        val bar = lin(false)
        bar.add(backBtn(), 0, MATCH, 1f, mr = 12)
        bar.add(right, 0, MATCH, 1f)
        return bar
    }

    private fun pillP(res: Int?, text: String, color: Int, click: (() -> Unit)?): LinearLayout {
        val p = lin(false, Gravity.CENTER_VERTICAL)
        p.background = bg(PILL, LINE, 1, 40)
        p.setPadding(px(12), px(6), px(12), px(6))
        if (res != null) p.add(icon(res, TEAL), px(18), px(18), mr = 6)
        p.add(txt(text, 14, color, res == null && color == TEXT))
        if (click != null) {
            p.isClickable = true
            p.setOnClickListener { click() }
        }
        return p
    }

    private fun photoP(): View {
        val f = FrameLayout(this)
        f.background = bg(PHOTO, LINE, 1, 18)
        if (!addPhoto(f)) {
            val c = lin(true, Gravity.CENTER)
            c.add(txt("Фото объекта управления", 20, FAINT, true))
            c.add(txt("место под ваше фото или кадр с камеры", 13, FAINT), mt = 6)
            f.addView(c, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        f.addView(pillP(null, NAMES[St.selected], TEXT, null), flp(Gravity.START or Gravity.TOP, 12))
        f.addView(pillP(R.drawable.ic_camera, "Камера", TEXT) { openCamFor(St.selected) }, flp(Gravity.END or Gravity.TOP, 12))
        val t = tempOf(St.selected)
        if (t.isNotEmpty()) f.addView(pillP(null, "Сейчас " + t, SOFT, null), flp(Gravity.START or Gravity.BOTTOM, 12))
        f.addView(pillP(R.drawable.ic_gear, "Подключения", TEXT) { show("settings") }, flp(Gravity.END or Gravity.BOTTOM, 12))
        return f
    }

    private fun navTileP(i: Int): View {
        val sel = i == St.selected
        val c = lin(false, Gravity.CENTER_VERTICAL)
        c.background = bg(if (sel) AMBER_BG else PANEL, if (sel) AMBER else LINE, if (sel) 2 else 1, 14)
        c.setPadding(px(10), 0, px(10), 0)
        c.isClickable = true
        c.setOnClickListener { onNav(i) }
        c.add(chip(NAV_ICONS[i], 34, 18, if (sel) AMBER_CHIP else CHIP, if (sel) AMBER else SOFT), px(34), px(34), mr = 8)
        val tx = lin(true)
        tx.add(txt(NAMES[i], 16, TEXT, true))
        val t = tempOf(i)
        if (t.isNotEmpty()) tx.add(txt(t, 13, SOFT), mt = 2)
        c.add(tx, 0, WRAP, 1f)
        c.add(icon(R.drawable.ic_bulb, if (lightOf(i)) AMBER else OFFI), px(18), px(18))
        return c
    }

    private fun gateCardP(): LinearLayout {
        val c = card(PANEL, LINE, 1, 16, false) { show("gate") }
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(px(16), 0, px(16), 0)
        c.add(chip(R.drawable.ic_gate, 44, 26, CHIP, SOFT), px(44), px(44), mr = 14)
        val t = lin(true)
        t.add(txt("Ворота", 20, TEXT, true))
        t.add(txt("Камера и открытие", 14, MUTED), mt = 2)
        c.add(t)
        return c
    }

    private fun setpointCardP(): LinearLayout {
        val c = card(PANEL, LINE, 1, 16, false, null)
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(px(16), 0, px(16), 0)
        val l = lin(true)
        l.add(txt("Отопление дома, уставка", 14, MUTED))
        l.add(txt(St.houseSet.toString() + " °C", 34, TEXT, true), mt = 2)
        c.add(l, 0, WRAP, 1f)
        c.add(stepBtn(R.drawable.ic_down) { changeSet(-1) }, px(48), px(48), mr = 10)
        c.add(stepBtn(R.drawable.ic_up) { changeSet(1) }, px(48), px(48))
        return c
    }

    private fun alarmP(l2: String, on: Boolean, click: () -> Unit): LinearLayout {
        val s = sty(on, AMBER)
        val c = card(s.fill, s.stroke, s.sw, 16, true, click)
        c.add(icon(if (on) R.drawable.ic_shield_on else R.drawable.ic_shield, s.fg), px(30), px(30))
        c.add(txt("Охрана " + l2, 17, TEXT, true, true), MATCH, WRAP, mt = 4, ml = 6, mr = 6)
        c.add(txt(if (on) "включена" else "отключена", 14, s.fg), mt = 2)
        return c
    }

    private fun buildMainP(): View {
        val pg = lin(true)
        pg.setPadding(px(16), px(16), px(16), px(16))
        pg.add(photoP(), MATCH, px(250), mb = 12)
        val r1 = lin(false)
        r1.add(navTileP(0), 0, MATCH, 1f, mr = 10)
        r1.add(navTileP(1), 0, MATCH, 1f)
        val r2 = lin(false)
        r2.add(navTileP(2), 0, MATCH, 1f, mr = 10)
        r2.add(navTileP(3), 0, MATCH, 1f)
        pg.add(r1, MATCH, px(66), mb = 10)
        pg.add(r2, MATCH, px(66), mb = 10)
        pg.add(navTileP(4), MATCH, px(66), mb = 12)
        pg.add(gateCardP(), MATCH, px(64), mb = 10)
        pg.add(setpointCardP(), MATCH, px(84), mb = 10)
        val ar = lin(false)
        ar.add(alarmP("дома", St.alarmHouse) { flip("alarm_house") }, 0, MATCH, 1f, mr = 10)
        ar.add(alarmP("мастерской", St.alarmShop) { flip("alarm_shop") }, 0, MATCH, 1f)
        pg.add(ar, MATCH, 0, 1f)
        return pg
    }

    private fun buildHouseP(): View {
        val body = lin(true)
        val top = lin(false)
        top.add(gaugeCard("+" + St.houseTemp + " °C", (St.houseTempV / 40.0).toFloat().coerceIn(0f, 1f)), px(100), MATCH, mr = 12)
        top.add(breakerCard(), 0, MATCH, 1f)
        body.add(top, MATCH, px(300), mb = 12)
        body.add(tileFloor(), MATCH, px(230), mb = 12)
        body.add(tileLight(), MATCH, px(250), mb = 12)
        body.add(tileHeat(), MATCH, px(250), mb = 12)
        body.add(tileCams(), MATCH, px(230), mb = 12)
        val ind = lin(false)
        ind.add(pumpCard(), 0, MATCH, 1f, mr = 12)
        ind.add(indicator(R.drawable.ic_gas, "Сигнализатор загазованности"), 0, MATCH, 1f)
        body.add(ind, MATCH, px(100))
        return scrollPage("Дом", R.drawable.ic_house, body, bottomBar(settingsBtn()))
    }

    private fun buildShopP(): View {
        val body = lin(true)
        val top = lin(false)
        top.add(gaugeCard("+" + St.shopTemp + " °C", (St.shopTempV / 40.0).toFloat().coerceIn(0f, 1f)), px(100), MATCH, mr = 12)
        top.add(shopThermoCard(true), 0, MATCH, 1f)
        body.add(top, MATCH, px(280), mb = 12)
        val act = lin(false)
        act.add(tog(R.drawable.ic_bulb, "Свет", if (St.shopLight) "включён" else "выключен", St.shopLight, AMBER) { flip("shop_light") }, 0, MATCH, 1f, mr = 10)
        act.add(tog(R.drawable.ic_radiator, "Отопление", if (St.shopHeat) "включено" else "выключено", St.shopHeat, TEAL) { flip("shop_heat") }, 0, MATCH, 1f, mr = 10)
        act.add(tog(R.drawable.ic_camera, "Камера", "смотреть", false, AMBER) { openCam("shop") }, 0, MATCH, 1f)
        body.add(act, MATCH, px(150), mb = 12)
        body.add(lightRow(R.drawable.ic_bulb, "Освещение фасада", St.facade) { flip("facade") }, MATCH, px(120), mb = 12)
        body.add(lightRow(R.drawable.ic_awning, "Освещение навеса", St.awning) { flip("awning") }, MATCH, px(120), mb = 12)
        body.add(indicator(R.drawable.ic_flame, "Пожарная сигнализация"), MATCH, px(90))
        return scrollPage("Мастерская", R.drawable.ic_shop, body, bottomBar(settingsBtn()))
    }

    private fun buildYardP(): View {
        val body = lin(true)
        val sc = FrameLayout(this)
        val yv = YardView(this, St.yPark, St.yPaths, St.yFlood)
        yv.background = bg(SCENE, LINE, 1, 18)
        sc.addView(yv, FrameLayout.LayoutParams(MATCH, MATCH))
        body.add(sc, MATCH, px(250), mb = 12)
        val all = St.yPark && St.yPaths && St.yFlood
        body.add(rowCard(R.drawable.ic_parking, "Парковка", if (St.yPark) "включено" else "выключено", St.yPark) { flip("park") }, MATCH, px(80), mb = 10)
        body.add(rowCard(R.drawable.ic_route, "Дорожки", if (St.yPaths) "включено" else "выключено", St.yPaths) { flip("paths") }, MATCH, px(80), mb = 10)
        body.add(rowCard(R.drawable.ic_flood, "Прожекторы", if (St.yFlood) "включено" else "выключено", St.yFlood) { flip("flood") }, MATCH, px(80), mb = 10)
        body.add(rowCard(R.drawable.ic_bulb, "Весь двор", if (all) "выключить всё" else "включить всё", all) { yardAll(!all) }, MATCH, px(80))
        val cam = card(PANEL, LINE, 1, 22, false) { openCamGroup("yard") }
        cam.add(icon(R.drawable.ic_camera, TEAL), px(30), px(30), mr = 10)
        cam.add(txt("Камеры", 22, TEXT, true))
        return scrollPage("Двор", R.drawable.ic_tree, body, bottomBar(cam))
    }
}

class GaugeBar(c: Context, private val frac: Float, private val u: Float) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bw = 44f * u
        val l = (w - bw) / 2f
        val colors = intArrayOf(0xFF3B6FD8.toInt(), 0xFF3FB8A6.toInt(), 0xFFE8D44D.toInt(), 0xFFE8884B.toInt(), 0xFFD94B4B.toInt())
        p.shader = LinearGradient(0f, h, 0f, 0f, colors, null, Shader.TileMode.CLAMP)
        cv.drawRoundRect(RectF(l, 0f, l + bw, h), 12f * u, 12f * u, p)
        p.shader = null
        p.color = 0xFFE8EDF2.toInt()
        val y = h * (1f - frac)
        cv.drawRoundRect(RectF(l - 10f * u, y - 2f * u, l + bw + 10f * u, y + 2f * u), 2f * u, 2f * u, p)
    }
}

class YardView(c: Context, private val park: Boolean, private val paths: Boolean, private val flood: Boolean) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val amber = 0xFFF0B35A.toInt()

    override fun onDraw(cv: Canvas) {
        val sx = width / 884f
        val sy = height / 546f
        val rad = 14f * sx

        fun rc(x: Float, y: Float, w: Float, h: Float): RectF = RectF(x * sx, y * sy, (x + w) * sx, (y + h) * sy)

        fun block(r: RectF, fill: Int, label: String) {
            p.shader = null
            p.style = Paint.Style.FILL
            p.color = fill
            cv.drawRoundRect(r, rad, rad, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.5f
            p.color = 0xFF2A3541.toInt()
            cv.drawRoundRect(r, rad, rad, p)
            p.style = Paint.Style.FILL
            p.color = 0xFF6F7D8C.toInt()
            p.textSize = 16f * sx
            cv.drawText(label, r.left + 14f * sx, r.top + 28f * sy, p)
        }

        fun glow(r: RectF) {
            p.shader = null
            p.style = Paint.Style.FILL
            p.color = 0x18F0B35A
            for (k in 4 downTo 1) {
                val d = k * 9f * sx
                cv.drawRoundRect(RectF(r.left - d, r.top - d, r.right + d, r.bottom + d), rad + d, rad + d, p)
            }
            p.color = 0x38F0B35A
            cv.drawRoundRect(r, rad, rad, p)
        }

        fun lamp(x: Float, y: Float, on: Boolean) {
            val cx = x * sx
            val cy = y * sy
            p.style = Paint.Style.FILL
            if (on) {
                p.shader = RadialGradient(cx, cy, 34f * sx, intArrayOf(0x99F0B35A.toInt(), 0x00F0B35A), null, Shader.TileMode.CLAMP)
                cv.drawCircle(cx, cy, 34f * sx, p)
                p.shader = null
            }
            p.color = if (on) amber else 0xFF3C4753.toInt()
            cv.drawCircle(cx, cy, 7f * sx, p)
        }

        block(rc(40f, 36f, 250f, 140f), 0xFF19222C.toInt(), "Дом")
        block(rc(590f, 36f, 220f, 120f), 0xFF19222C.toInt(), "Мастерская")
        block(rc(640f, 330f, 170f, 110f), 0xFF19222C.toInt(), "Хозблок")
        val parking = rc(40f, 330f, 320f, 170f)
        block(parking, 0xFF151C24.toInt(), "Парковка")
        if (park) glow(parking)

        val ways = listOf(rc(150f, 176f, 44f, 154f), rc(194f, 190f, 530f, 40f), rc(680f, 156f, 44f, 74f))
        for (w in ways) {
            p.shader = null
            p.style = Paint.Style.FILL
            p.color = 0xFF121A22.toInt()
            cv.drawRoundRect(w, 6f * sx, 6f * sx, p)
        }
        if (paths) for (w in ways) glow(w)

        val lamps = listOf(172f to 250f, 172f to 300f, 280f to 210f, 380f to 210f, 480f to 210f, 580f to 210f, 702f to 185f)
        for (l in lamps) lamp(l.first, l.second, paths)

        val fl = listOf(440f to 420f, 560f to 110f)
        for (f in fl) {
            if (flood) {
                p.style = Paint.Style.FILL
                p.shader = RadialGradient(f.first * sx, f.second * sy, 210f * sx,
                    intArrayOf(0x55FFECBE, 0x00FFECBE), null, Shader.TileMode.CLAMP)
                cv.drawCircle(f.first * sx, f.second * sy, 210f * sx, p)
                p.shader = null
            }
            p.style = Paint.Style.FILL
            p.color = if (flood) amber else 0xFF3C4753.toInt()
            cv.drawRect(f.first * sx - 8f * sx, f.second * sy - 8f * sx, f.first * sx + 8f * sx, f.second * sy + 8f * sx, p)
        }
    }
}
