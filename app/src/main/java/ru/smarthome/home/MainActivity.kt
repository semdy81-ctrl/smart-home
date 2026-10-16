package ru.smarthome.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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

private val NAMES = arrayOf("Дом", "Мастерская", "Хозблок", "Костровая", "Двор")
private val TEMPS = arrayOf("21,4 °C", "14,0 °C", "", "", "")
private val NAV_ICONS = intArrayOf(R.drawable.ic_house, R.drawable.ic_shop, R.drawable.ic_cube, R.drawable.ic_flame, R.drawable.ic_tree)

object St {
    var selected = 0
    val light = booleanArrayOf(true, false, false, true, true)
    var setpoint = 22
    var gate = false
    var alarmHouse = true
    var alarmShop = false
    var breaker = true
    var floorHeat = true
    var garland = true
    var spotBalcony = false
    var spotGarden = true
    var houseHeat = true
    var houseSet = 22
    var houseTemp = "21,4"
    var houseTempV = 21.4
    var shopLight = false
    var shopHeat = true
    var facade = false
    var awning = true
    var yPark = true
    var yPaths = true
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
    Slot("shop_light", "Мастерская: свет", { St.shopLight }, { St.shopLight = it }),
    Slot("shop_heat", "Мастерская: отопление", { St.shopHeat }, { St.shopHeat = it }),
    Slot("facade", "Мастерская: фасад", { St.facade }, { St.facade = it }),
    Slot("awning", "Мастерская: навес", { St.awning }, { St.awning = it }),
    Slot("park", "Двор: парковка", { St.yPark }, { St.yPark = it }),
    Slot("paths", "Двор: дорожки", { St.yPaths }, { St.yPaths = it }),
    Slot("flood", "Двор: прожекторы", { St.yFlood }, { St.yFlood = it })
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
        val dm = resources.displayMetrics
        u = minOf(dm.widthPixels, dm.heightPixels) / 800f
        root = FrameLayout(this)
        root.setBackgroundColor(BG)
        setContentView(root)
        show("main")
    }

    override fun onBackPressed() {
        if (screen == "main") super.onBackPressed() else show("main")
    }

    private fun show(name: String, fetch: Boolean = true) {
        screen = name
        root.removeAllViews()
        val v: View = when (name) {
            "house" -> buildHouse()
            "shop" -> buildShop()
            "yard" -> buildYard()
            "settings" -> buildSettings()
            "bind" -> buildBind()
            else -> buildMain()
        }
        root.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        if (fetch && name != "settings" && name != "bind") refresh()
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

    private fun navTile(i: Int): View {
        val sel = i == St.selected
        val c = lin(false, Gravity.CENTER_VERTICAL)
        c.background = bg(if (sel) AMBER_BG else PANEL, if (sel) AMBER else LINE, if (sel) 2 else 1, 18)
        c.setPadding(px(20), 0, px(20), 0)
        c.isClickable = true
        c.setOnClickListener { onNav(i) }
        c.add(chip(NAV_ICONS[i], 52, 28, if (sel) AMBER_CHIP else CHIP, if (sel) AMBER else SOFT), px(52), px(52), mr = 16)
        c.add(txt(NAMES[i], 28, TEXT, true), 0, WRAP, 1f)
        if (tempOf(i).isNotEmpty()) c.add(txt(tempOf(i), 20, SOFT), WRAP, WRAP, mr = 10)
        c.add(icon(R.drawable.ic_bulb, if (St.light[i]) AMBER else OFFI), px(26), px(26))
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

    private fun photo(): View {
        val f = FrameLayout(this)
        f.background = bg(PHOTO, LINE, 1, 22)
        val c = lin(true, Gravity.CENTER)
        c.add(txt("Фото объекта управления", 34, FAINT, true))
        c.add(txt("место под ваше фото или кадр с камеры", 18, FAINT), mt = 10)
        f.addView(c, FrameLayout.LayoutParams(MATCH, MATCH))
        val nm = pillBox()
        nm.addView(txt(NAMES[St.selected], 24, TEXT, true))
        f.addView(nm, flp(Gravity.START or Gravity.TOP, 20))
        val cam = pillBox()
        cam.add(icon(R.drawable.ic_camera, TEAL), px(26), px(26), mr = 10)
        cam.add(txt("Камера", 20, TEXT))
        cam.isClickable = true
        cam.setOnClickListener { toast("Камера: видео подключим позже") }
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
        val c = card(PANEL, LINE, 1, 22, false) { flip("gate") }
        c.add(chip(R.drawable.ic_gate, 68, 40, CHIP, SOFT), px(68), px(68), mr = 18)
        val t = lin(true)
        t.add(txt("Ворота", 32, TEXT, true))
        t.add(txt(if (St.gate) "Открыты" else "Закрыты", 18, MUTED), mt = 4)
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
        bot.add(indicator(R.drawable.ic_flame, "Пожарный извещатель"), 0, MATCH, 1.7f, mr = 16)
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
        c.setPadding(px(20), 0, px(20), 0)
        c.isClickable = true
        c.setOnClickListener { toast("Камера «" + name + "»: видео подключим позже") }
        c.add(icon(R.drawable.ic_camera, TEAL), px(30), px(30), mr = 14)
        c.add(txt(name, 24, TEXT, true))
        return c
    }

    private fun tileCams(): View {
        val body = lin(true)
        body.add(camBtn("Зал"), MATCH, 0, 1f, mb = 10)
        body.add(camBtn("Котельная"), MATCH, 0, 1f)
        return tile(R.drawable.ic_camera, "Камеры", "просмотр", body)
    }

    // ---------- меню «Мастерская» ----------
    private fun buildShop(): View {
        val pg = page()
        pg.add(header("Мастерская", R.drawable.ic_shop), MATCH, px(64), mb = 16)
        val main = lin(false)
        main.add(gaugeCard("+14,0 °C", 0.35f), px(120), MATCH, mr = 16)
        val big = card(PANEL, LINE, 1, 22, true, null)
        big.add(icon(R.drawable.ic_house_thin, OFFI), px(150), px(150))
        big.add(txt("Мастерская", 46, TEXT, true), mt = 12)
        big.add(txt("14,0 °C", 40, TEAL), mt = 12)
        big.add(txt("температура в помещении", 18, MUTED), mt = 12)
        main.add(big, px(400), MATCH, mr = 16)
        val act = lin(true)
        act.add(tog(R.drawable.ic_bulb, "Свет", if (St.shopLight) "включён" else "выключен", St.shopLight, AMBER) {
            flip("shop_light")
        }, MATCH, 0, 1f, mb = 16)
        act.add(tog(R.drawable.ic_radiator, "Отопление", if (St.shopHeat) "включено" else "выключено", St.shopHeat, TEAL) {
            flip("shop_heat")
        }, MATCH, 0, 1f, mb = 16)
        act.add(tog(R.drawable.ic_camera, "Камера", "смотреть", false, AMBER) { toast("Камера: видео подключим позже") }, MATCH, 0, 1f)
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
        val cam = card(PANEL, LINE, 1, 22, false) { toast("Камеры двора: видео подключим позже") }
        cam.add(icon(R.drawable.ic_camera, TEAL), px(36), px(36), mr = 14)
        cam.add(txt("Камеры двора", 24, TEXT, true))
        bot.add(cam, 0, MATCH, 1f)
        pg.add(bot, MATCH, px(110))
        return pg
    }
    // ---------- подключение устройств ----------
    private var refreshing = false
    private var lastRefresh = 0L

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

    private fun zontReady(): Boolean = Store.get("zont_login").isNotEmpty() && Store.get("zont_dev").isNotEmpty()

    private fun tempOf(i: Int): String = if (i == 0) St.houseTemp + " °C" else TEMPS[i]

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
                if (changed && screen != "settings" && screen != "bind") show(screen, false)
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
        r.add(txt(label, 20, MUTED), px(220), WRAP)
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

    private fun buildSettings(): View {
        val pg = page()
        pg.add(header("Подключения", R.drawable.ic_gear), MATCH, px(64), mb = 12)
        val sv = ScrollView(this)
        val col = lin(true)
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
        col.add(out, MATCH, WRAP, mb = 20)
        col.add(txt("ZONT (котёл)", 24, TEXT, true), mt = 10, mb = 10)
        col.add(field("Логин ZONT", "zont_login"), MATCH, WRAP, mb = 10)
        col.add(field("Пароль ZONT", "zont_pass", true), MATCH, WRAP, mb = 10)
        col.add(field("E-mail для API", "zont_mail"), MATCH, WRAP, mb = 10)
        val zout = txt(Store.get("zont_log").ifEmpty { "Нажмите «Проверить ZONT»" }, 17, MUTED)
        val zcheck = card(AMBER_BG, AMBER, 2, 12, false) { runZontCheck(zout) }
        zcheck.add(txt("Проверить ZONT", 20, AMBER, true))
        col.add(zcheck, MATCH, px(52), mb = 10)
        col.add(zout, MATCH, WRAP, mb = 20)
        val bind = card(PANEL, LINE, 1, 12, false) { show("bind") }
        bind.add(txt("Привязка кнопок к устройствам", 20, TEXT, true))
        col.add(bind, MATCH, px(52), mb = 10)
        sv.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        pg.add(sv, MATCH, 0, 1f, mb = 12)
        val back = card(PANEL, LINE, 1, 22, false) { show("main") }
        back.add(txt("Назад", 24, TEXT, true))
        pg.add(back, MATCH, px(72))
        return pg
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
                refs.add((if (isProp) "tuyap" else "tuya") + "|" + d.id + "|" + code + "|" + d.name + " · " + code + (if (parts.size == 3) "|" + parts[1] + "|" + parts[2] else ""))
            }
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
