package com.example.sampleapp

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import java.util.Calendar
import java.util.Locale

private val ORANGE = Color.parseColor("#FF9F0A")
private val GRAY = Color.argb(190, 235, 235, 245)
private val GREEN = Color.parseColor("#34C759")
private val TRACK_OFF = Color.argb(90, 120, 120, 128)

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun fmt(h: Int, m: Int): String = String.format(Locale.US, "%02d:%02d", h, m)

/** Liquid glass: gjennomsiktig flate med lysende kant (rim light) */
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

/** Fargerik bakgrunn med uskarpe lysflekker som glasset ligger oppå */
fun Context.backdrop(): FrameLayout {
    val f = FrameLayout(this)
    f.background = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            Color.parseColor("#0A0B1E"),
            Color.parseColor("#1A1040"),
            Color.parseColor("#2B0E3A")
        )
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

    blob(Color.parseColor("#FF9F0A"), 260, Gravity.TOP or Gravity.END, 0, -60, -90, 0)
    blob(Color.parseColor("#BF5AF2"), 300, Gravity.CENTER_VERTICAL or Gravity.START, -120, 0, 0, 0)
    blob(Color.parseColor("#0A84FF"), 280, Gravity.BOTTOM or Gravity.END, 0, 0, -100, -70)
    blob(Color.parseColor("#FF375F"), 200, Gravity.BOTTOM or Gravity.START, -40, 0, 0, -50)
    return f
}

data class Alarm(val id: Int, var hour: Int, var minute: Int, var enabled: Boolean)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<Alarm> {
        val s = prefs(c).getString("list", "") ?: ""
        return s.split(";").filter { it.isNotBlank() }.mapNotNull {
            val p = it.split(",")
            if (p.size == 4) Alarm(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3] == "1") else null
        }.toMutableList()
    }

    fun save(c: Context, list: List<Alarm>) {
        val s = list.joinToString(";") {
            "${it.id},${it.hour},${it.minute},${if (it.enabled) 1 else 0}"
        }
        prefs(c).edit().putString("list", s).apply()
    }
}

object Scheduler {
    private fun pi(c: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, id,
            Intent(c, AlarmReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun nextTrigger(h: Int, m: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun scheduleAt(c: Context, id: Int, at: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi(c, id))
    }

    fun cancel(c: Context, id: Int) {
        c.getSystemService(AlarmManager::class.java).cancel(pi(c, id))
    }
}

class MainActivity : Activity() {

    private lateinit var list: LinearLayout
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0A0B1E")
        window.navigationBarColor = Color.parseColor("#2B0E3A")

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = backdrop()

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(24), dp(20), dp(80))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(this)
        title.text = "Alarmer"
        title.setTextColor(Color.WHITE)
        title.textSize = 34f
        title.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        header.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val add = TextView(this)
        add.text = "+"
        add.textSize = 28f
        add.setTextColor(ORANGE)
        add.gravity = Gravity.CENTER
        add.background = glass(22)
        add.setOnClickListener { pickTime(null) }
        header.addView(add, LinearLayout.LayoutParams(dp(44), dp(44)))
        col.addView(header)

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        col.addView(list)

        empty = TextView(this)
        empty.text = "Ingen alarmer.\nTrykk + for å legge til.\n\nTrykk på en alarm for å endre den, hold inne for å slette."
        empty.setTextColor(GRAY)
        empty.textSize = 15f
        empty.gravity = Gravity.CENTER
        empty.setPadding(0, dp(60), 0, 0)
        col.addView(empty)

        scroll.addView(col)
        root.addView(scroll)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        list.removeAllViews()
        val alarms = Store.load(this).sortedBy { it.hour * 60 + it.minute }
        empty.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE
        for (a in alarms) {
            val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            lp.topMargin = dp(12)
            list.addView(card(a), lp)
        }
    }

    private fun card(a: Alarm): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = glass(28)
        row.setPadding(dp(22), dp(14), dp(22), dp(14))
        row.setOnClickListener { pickTime(a) }
        row.setOnLongClickListener {
            confirmDelete(a)
            true
        }

        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL

        val time = TextView(this)
        time.text = fmt(a.hour, a.minute)
        time.textSize = 52f
        time.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        time.setTextColor(if (a.enabled) Color.WHITE else GRAY)
        left.addView(time)

        val label = TextView(this)
        label.text = "Alarm"
        label.textSize = 14f
        label.setTextColor(GRAY)
        left.addView(label)

        row.addView(left, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val sw = Switch(this)
        sw.isChecked = a.enabled
        sw.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(GREEN, TRACK_OFF)
        )
        sw.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        sw.setOnCheckedChangeListener { _, on ->
            toggle(a.id, on)
            list.post { render() }
        }
        row.addView(sw)
        return row
    }

    private fun toggle(id: Int, on: Boolean) {
        val all = Store.load(this)
        val a = all.firstOrNull { it.id == id } ?: return
        a.enabled = on
        Store.save(this, all)
        if (on) {
            Scheduler.scheduleAt(this, id, Scheduler.nextTrigger(a.hour, a.minute))
            Toast.makeText(this, untilText(a.hour, a.minute), Toast.LENGTH_SHORT).show()
        } else {
            Scheduler.cancel(this, id)
        }
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

    private fun pickTime(existing: Alarm?) {
        val listener = TimePickerDialog.OnTimeSetListener { _, h, m ->
            val all = Store.load(this)
            val id: Int
            if (existing == null) {
                id = (System.currentTimeMillis() / 1000 % 1000000).toInt() + 1
                all.add(Alarm(id, h, m, true))
            } else {
                id = existing.id
                val a = all.firstOrNull { it.id == id }
                if (a != null) {
                    a.hour = h
                    a.minute = m
                    a.enabled = true
                } else {
                    all.add(Alarm(id, h, m, true))
                }
            }
            Store.save(this, all)
            Scheduler.scheduleAt(this, id, Scheduler.nextTrigger(h, m))
            Toast.makeText(this, untilText(h, m), Toast.LENGTH_SHORT).show()
            render()
        }
        val dlg = TimePickerDialog(
            this,
            android.R.style.Theme_DeviceDefault_Dialog_Alert,
            listener,
            existing?.hour ?: 7,
            existing?.minute ?: 0,
            true
        )
        dlg.show()
        blurBehind(dlg)
    }

    private fun confirmDelete(a: Alarm) {
        val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Slett alarm " + fmt(a.hour, a.minute) + "?")
            .setPositiveButton("Slett") { _, _ ->
                Scheduler.cancel(this, a.id)
                val all = Store.load(this)
                all.removeAll { it.id == a.id }
                Store.save(this, all)
                render()
            }
            .setNegativeButton("Avbryt", null)
            .create()
        dlg.show()
        blurBehind(dlg)
    }

    private fun untilText(h: Int, m: Int): String {
        val diff = Scheduler.nextTrigger(h, m) - System.currentTimeMillis()
        val total = (diff + 59999) / 60000
        return "Alarm om ${total / 60} t ${total % 60} min"
    }
}

class RingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.parseColor("#0A0B1E")
        window.navigationBarColor = Color.parseColor("#2B0E3A")

        val id = intent.getIntExtra("id", 0)
        val a = Store.load(this).firstOrNull { it.id == id }
        val now = Calendar.getInstance()
        val timeText = if (a != null) fmt(a.hour, a.minute)
        else fmt(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))

        val root = backdrop()

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER
        content.setPadding(dp(28), dp(28), dp(28), dp(28))

        val label = TextView(this)
        label.text = "Alarm"
        label.setTextColor(ORANGE)
        label.textSize = 22f
        label.gravity = Gravity.CENTER
        content.addView(label)

        val time = TextView(this)
        time.text = timeText
        time.setTextColor(Color.WHITE)
        time.textSize = 86f
        time.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        time.gravity = Gravity.CENTER
        time.setPadding(0, dp(8), 0, dp(60))
        content.addView(time)

        content.addView(button("Slumre 9 min", glass(32), Color.WHITE) {
            sendBroadcast(
                Intent(this, AlarmReceiver::class.java)
                    .setAction(AlarmReceiver.ACTION_SNOOZE).putExtra("id", id)
            )
            finish()
        })
        content.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, dp(14)))
        content.addView(
            button(
                "Stopp",
                glass(
                    32,
                    Color.argb(255, 255, 179, 64),
                    Color.argb(255, 255, 159, 10)
                ),
                Color.BLACK
            ) {
                sendBroadcast(
                    Intent(this, AlarmReceiver::class.java)
                        .setAction(AlarmReceiver.ACTION_STOP).putExtra("id", id)
                )
                finish()
            }
        )

        root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
    }

    private fun button(text: String, bg: Drawable, fg: Int, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = text
        b.setTextColor(fg)
        b.textSize = 20f
        b.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        b.gravity = Gravity.CENTER
        b.background = bg
        b.setPadding(0, dp(20), 0, dp(20))
        b.setOnClickListener { onClick() }
        return b
    }
}

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL = "alarm_v1"
        const val NOTIF_ID = 4242
        const val ACTION_STOP = "com.example.sampleapp.STOP"
        const val ACTION_SNOOZE = "com.example.sampleapp.SNOOZE"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        when (intent.action) {
            ACTION_STOP -> nm.cancel(NOTIF_ID)
            ACTION_SNOOZE -> {
                nm.cancel(NOTIF_ID)
                Scheduler.scheduleAt(ctx, id, System.currentTimeMillis() + 9 * 60 * 1000L)
            }
            else -> ring(ctx, nm, id)
        }
    }

    private fun ring(ctx: Context, nm: NotificationManager, id: Int) {
        val all = Store.load(ctx)
        val a = all.firstOrNull { it.id == id }
        if (a != null) {
            a.enabled = false
            Store.save(ctx, all)
        }
        val timeText = if (a != null) fmt(a.hour, a.minute) else "Alarm"

        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Alarmer", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIC)
                    .build()
            )
            ch.enableVibration(true)
            ch.vibrationPattern = longArrayOf(0, 600, 400, 600)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val fullScreen = PendingIntent.getActivity(
            ctx, id + 3_000_000,
            Intent(ctx, RingActivity::class.java)
                .putExtra("id", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags
        )
        val stop = PendingIntent.getBroadcast(
            ctx, id + 1_000_000,
            Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_STOP).putExtra("id", id),
            flags
        )
        val snooze = PendingIntent.getBroadcast(
            ctx, id + 2_000_000,
            Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_SNOOZE).putExtra("id", id),
            flags
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText(timeText)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "Slumre 9 min", snooze)
            .addAction(0, "Stopp", stop)
            .setTimeoutAfter(10 * 60 * 1000L)
            .setSound(sound, AudioManager.STREAM_ALARM)
            .setVibrate(longArrayOf(0, 600, 400, 600))
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(NOTIF_ID, n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        for (a in Store.load(ctx)) {
            if (a.enabled) {
                Scheduler.scheduleAt(ctx, a.id, Scheduler.nextTrigger(a.hour, a.minute))
            }
        }
    }
}
