package com.example.sampleapp

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

private val BG_TOP = Color.parseColor("#0A0B1E")
private val BG_BOTTOM = Color.parseColor("#2B0E3A")
private val ACCENT = Color.parseColor("#FF6482")
private val LINK = Color.parseColor("#64D2FF")
private val GRAY = Color.argb(190, 235, 235, 245)

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/** Liquid glass: gjennomsiktig flate med lysende kant */
fun Context.glass(
    r: Int,
    fillTop: Int = Color.argb(56, 255, 255, 255),
    fillBottom: Int = Color.argb(20, 255, 255, 255)
): Drawable {
    val rim = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            Color.argb(150, 255, 255, 255),
            Color.argb(16, 255, 255, 255),
            Color.argb(70, 255, 255, 255)
        )
    )
    rim.cornerRadius = dp(r).toFloat()

    val fill = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(fillTop, fillBottom)
    )
    fill.cornerRadius = (dp(r) - dp(1)).toFloat()

    val layers = LayerDrawable(arrayOf<Drawable>(rim, fill))
    val inset = dp(1)
    layers.setLayerInset(1, inset, inset, inset, inset)
    return layers
}

/** Mørk bakgrunn med uskarpe fargeflekker som glasset ligger oppå */
fun Context.backdrop(): FrameLayout {
    val f = FrameLayout(this)
    f.background = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(BG_TOP, Color.parseColor("#1A1040"), BG_BOTTOM)
    )

    fun blob(color: Int, size: Int, gravity: Int, l: Int, t: Int, r: Int, b: Int) {
        val v = View(this)
        val oval = GradientDrawable()
        oval.shape = GradientDrawable.OVAL
        oval.setColor(color)
        v.background = oval
        val blurred = Build.VERSION.SDK_INT >= 31
        if (blurred) {
            v.setRenderEffect(
                RenderEffect.createBlurEffect(
                    dp(60).toFloat(), dp(60).toFloat(), Shader.TileMode.DECAL
                )
            )
        }
        v.alpha = if (blurred) 0.75f else 0.28f
        val lp = FrameLayout.LayoutParams(dp(size), dp(size), gravity)
        lp.setMargins(dp(l), dp(t), dp(r), dp(b))
        f.addView(v, lp)
    }

    blob(Color.parseColor("#FF375F"), 260, Gravity.TOP or Gravity.END, 0, -60, -90, 0)
    blob(Color.parseColor("#BF5AF2"), 300, Gravity.CENTER_VERTICAL or Gravity.START, -120, 0, 0, 0)
    blob(Color.parseColor("#0A84FF"), 280, Gravity.BOTTOM or Gravity.END, 0, 0, -100, -70)
    blob(Color.parseColor("#FF9F0A"), 200, Gravity.BOTTOM or Gravity.START, -40, 0, 0, -50)
    return f
}

class Wish(
    val id: Long,
    var title: String,
    var url: String,
    var price: String,
    var note: String,
    var done: Boolean
)

class WishList(
    val id: Long,
    var name: String,
    var emoji: String,
    val wishes: MutableList<Wish>
)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("onsk", Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<WishList> {
        val out = mutableListOf<WishList>()
        try {
            val arr = JSONArray(prefs(c).getString("lists", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ws = mutableListOf<Wish>()
                val wa = o.getJSONArray("wishes")
                for (j in 0 until wa.length()) {
                    val w = wa.getJSONObject(j)
                    ws.add(
                        Wish(
                            w.getLong("id"),
                            w.optString("title"),
                            w.optString("url"),
                            w.optString("price"),
                            w.optString("note"),
                            w.optBoolean("done")
                        )
                    )
                }
                out.add(WishList(o.getLong("id"), o.optString("name"), o.optString("emoji", "🎁"), ws))
            }
        } catch (e: Exception) {
            // tom eller ødelagt lagring: start på nytt
        }
        return out
    }

    fun save(c: Context, lists: List<WishList>) {
        val arr = JSONArray()
        for (l in lists) {
            val o = JSONObject()
            o.put("id", l.id)
            o.put("name", l.name)
            o.put("emoji", l.emoji)
            val wa = JSONArray()
            for (w in l.wishes) {
                val j = JSONObject()
                j.put("id", w.id)
                j.put("title", w.title)
                j.put("url", w.url)
                j.put("price", w.price)
                j.put("note", w.note)
                j.put("done", w.done)
                wa.put(j)
            }
            o.put("wishes", wa)
            arr.put(o)
        }
        prefs(c).edit().putString("lists", arr.toString()).apply()
    }
}

class MainActivity : Activity() {

    private var lists: MutableList<WishList> = mutableListOf()
    private lateinit var screen: FrameLayout
    private var openId: Long = -1L
    private val emojis = listOf("🎂", "🎄", "💍", "🎁", "🏠", "✨")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG_TOP
        window.navigationBarColor = BG_BOTTOM

        lists = Store.load(this)
        val root = backdrop()
        screen = FrameLayout(this)
        root.addView(screen, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
        render()
    }

    override fun onBackPressed() {
        if (openId != -1L) {
            openId = -1L
            render()
        } else {
            super.onBackPressed()
        }
    }

    private fun persist() {
        Store.save(this, lists)
    }

    private fun render() {
        screen.removeAllViews()
        val cur = lists.firstOrNull { it.id == openId }
        if (cur == null) {
            openId = -1L
            screen.addView(homeView(), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        } else {
            screen.addView(listView(cur), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
    }

    // ---------- små hjelpere ----------

    private fun text(s: String, size: Float, color: Int): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        return t
    }

    private fun column(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(20), dp(24), dp(20), dp(80))
        return c
    }

    private fun scrollOf(col: LinearLayout): ScrollView {
        val s = ScrollView(this)
        s.isVerticalScrollBarEnabled = false
        s.addView(col)
        return s
    }

    private fun headerRow(): LinearLayout {
        val h = LinearLayout(this)
        h.orientation = LinearLayout.HORIZONTAL
        h.gravity = Gravity.CENTER_VERTICAL
        return h
    }

    private fun addBtn(row: LinearLayout, label: String, onClick: () -> Unit) {
        val b = text(label, 24f, ACCENT)
        b.gravity = Gravity.CENTER
        b.background = glass(22)
        b.setOnClickListener { onClick() }
        val p = LinearLayout.LayoutParams(dp(44), dp(44))
        p.marginStart = dp(8)
        row.addView(b, p)
    }

    private fun blurBehind(d: Dialog) {
        if (Build.VERSION.SDK_INT >= 31) {
            val w = d.window ?: return
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val p = w.attributes
            p.blurBehindRadius = dp(24)
            w.attributes = p
        }
    }

    private fun form(
        title: String,
        hints: List<String>,
        initial: List<String>,
        onSave: (List<String>) -> Unit
    ) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)

        val fields = hints.mapIndexed { i, h ->
            val e = EditText(this)
            e.hint = h
            e.setText(initial[i])
            e.setHintTextColor(GRAY)
            e.setTextColor(Color.WHITE)
            e.inputType = InputType.TYPE_CLASS_TEXT
            e.setSingleLine(true)
            box.addView(e, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            e
        }

        val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Lagre") { _, _ ->
                onSave(fields.map { it.text.toString().trim() })
            }
            .setNegativeButton("Avbryt", null)
            .create()
        dlg.show()
        blurBehind(dlg)
    }

    private fun confirm(title: String, onYes: () -> Unit) {
        val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setPositiveButton("Slett") { _, _ -> onYes() }
            .setNegativeButton("Avbryt", null)
            .create()
        dlg.show()
        blurBehind(dlg)
    }

    // ---------- startside: alle lister ----------

    private fun homeView(): View {
        val col = column()

        val header = headerRow()
        val title = text("Ønsk", 34f, Color.WHITE)
        title.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        header.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addBtn(header, "+") { askNewList() }
        col.addView(header)

        col.addView(text("Dine ønskelister", 15f, GRAY))

        if (lists.isEmpty()) {
            val e = text(
                "Ingen lister ennå.\nTrykk + for å lage din første.\n\nHold inne på en liste for å slette den.",
                15f,
                GRAY
            )
            e.gravity = Gravity.CENTER
            e.setPadding(0, dp(60), 0, 0)
            col.addView(e)
        }

        for (l in lists) {
            val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            lp.topMargin = dp(12)
            col.addView(listCard(l), lp)
        }
        return scrollOf(col)
    }

    private fun listCard(l: WishList): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = glass(28)
        row.setPadding(dp(20), dp(16), dp(20), dp(16))

        row.addView(text(l.emoji, 32f, Color.WHITE))

        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL
        mid.setPadding(dp(16), 0, 0, 0)
        mid.addView(text(l.name, 20f, Color.WHITE))
        mid.addView(text("${l.wishes.count { !it.done }} ønsker", 14f, GRAY))
        row.addView(mid, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        row.addView(text("›", 28f, GRAY))

        row.setOnClickListener {
            openId = l.id
            render()
        }
        row.setOnLongClickListener {
            confirm("Slett «${l.name}»?") {
                lists.remove(l)
                persist()
                render()
            }
            true
        }
        return row
    }

    private fun askNewList() {
        form("Ny ønskeliste", listOf("Navn, f.eks. Bursdag"), listOf("")) { v ->
            if (v[0].isNotBlank()) {
                val emoji = emojis[lists.size % emojis.size]
                lists.add(WishList(System.currentTimeMillis(), v[0], emoji, mutableListOf()))
                persist()
                render()
            }
        }
    }

    // ---------- én liste med ønsker ----------

    private fun listView(l: WishList): View {
        val col = column()

        val header = headerRow()
        val back = text("‹", 30f, ACCENT)
        back.gravity = Gravity.CENTER
        back.background = glass(22)
        back.setOnClickListener {
            openId = -1L
            render()
        }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))

        val title = text("${l.emoji} ${l.name}", 24f, Color.WHITE)
        title.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        title.setPadding(dp(12), 0, 0, 0)
        header.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        addBtn(header, "↗") { share(l) }
        addBtn(header, "+") { editWish(l, null) }
        col.addView(header)

        val open = l.wishes.filter { !it.done }
        val total = open.sumOf { priceOf(it.price) }
        var summary = "${open.size} ønsker"
        if (total > 0) summary += " · sum ${total.toLong()} kr"
        col.addView(text(summary, 15f, GRAY))

        if (l.wishes.isEmpty()) {
            val e = text(
                "Ingen ønsker ennå.\nTrykk + for å legge til.\n\nTrykk på et ønske for å endre det, hold inne for å slette.",
                15f,
                GRAY
            )
            e.gravity = Gravity.CENTER
            e.setPadding(0, dp(60), 0, 0)
            col.addView(e)
        }

        for (w in l.wishes) {
            val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            lp.topMargin = dp(12)
            col.addView(wishCard(l, w), lp)
        }
        return scrollOf(col)
    }

    private fun wishCard(l: WishList, w: Wish): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = glass(26)
        row.setPadding(dp(20), dp(14), dp(16), dp(14))

        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL

        val t = text(w.title, 18f, if (w.done) GRAY else Color.WHITE)
        t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        if (w.done) t.paintFlags = t.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        mid.addView(t)

        if (w.price.isNotBlank()) mid.addView(text(w.price, 16f, ACCENT))
        if (w.note.isNotBlank()) mid.addView(text(w.note, 14f, GRAY))
        if (w.url.isNotBlank()) {
            val link = text("Åpne lenke ›", 14f, LINK)
            link.setPadding(0, dp(4), 0, dp(4))
            link.setOnClickListener { openUrl(w.url) }
            mid.addView(link)
        }
        row.addView(mid, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val tick = text(if (w.done) "✓" else "", 20f, Color.WHITE)
        tick.gravity = Gravity.CENTER
        tick.background = if (w.done) {
            glass(20, Color.argb(255, 52, 199, 89), Color.argb(255, 40, 170, 75))
        } else {
            glass(20)
        }
        tick.setOnClickListener {
            w.done = !w.done
            persist()
            render()
        }
        val tp = LinearLayout.LayoutParams(dp(40), dp(40))
        tp.marginStart = dp(12)
        row.addView(tick, tp)

        row.setOnClickListener { editWish(l, w) }
        row.setOnLongClickListener {
            confirm("Slett «${w.title}»?") {
                l.wishes.remove(w)
                persist()
                render()
            }
            true
        }
        return row
    }

    private fun editWish(l: WishList, w: Wish?) {
        val initial = if (w == null) {
            listOf("", "", "", "")
        } else {
            listOf(w.title, w.url, w.price, w.note)
        }
        form(
            if (w == null) "Nytt ønske" else "Rediger ønske",
            listOf("Hva ønsker du deg?", "Lenke (valgfritt)", "Pris (valgfritt)", "Notat (valgfritt)"),
            initial
        ) { v ->
            if (v[0].isNotBlank()) {
                if (w == null) {
                    l.wishes.add(Wish(System.currentTimeMillis(), v[0], v[1], v[2], v[3], false))
                } else {
                    w.title = v[0]
                    w.url = v[1]
                    w.price = v[2]
                    w.note = v[3]
                }
                persist()
                render()
            }
        }
    }

    private fun priceOf(s: String): Double {
        val c = s.replace(",", ".").filter { it.isDigit() || it == '.' }.trim('.')
        return c.toDoubleOrNull() ?: 0.0
    }

    private fun openUrl(u: String) {
        var s = u.trim()
        if (!s.startsWith("http")) s = "https://" + s
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s)))
        } catch (e: Exception) {
            Toast.makeText(this, "Kunne ikke åpne lenken", Toast.LENGTH_SHORT).show()
        }
    }

    private fun share(l: WishList) {
        val open = l.wishes.filter { !it.done }
        if (open.isEmpty()) {
            Toast.makeText(this, "Ingen ønsker å dele ennå", Toast.LENGTH_SHORT).show()
            return
        }
        val sb = StringBuilder()
        sb.append(l.emoji).append(" ").append(l.name).append("\n\n")
        open.forEachIndexed { i, w ->
            sb.append("${i + 1}. ${w.title}")
            if (w.price.isNotBlank()) sb.append(" – ${w.price}")
            sb.append("\n")
            if (w.note.isNotBlank()) sb.append("   ${w.note}\n")
            if (w.url.isNotBlank()) sb.append("   ${w.url}\n")
        }
        val send = Intent(Intent.ACTION_SEND)
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_TEXT, sb.toString())
        startActivity(Intent.createChooser(send, "Del ønskelisten"))
    }
}
