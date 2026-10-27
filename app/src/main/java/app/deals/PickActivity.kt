package app.deals

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class PickActivity : AppCompatActivity() {

    private lateinit var p: Pal

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rect(fill: Int, r: Int, stroke: Int = 0) = Theme.roundRect(fill, r, stroke, 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Theme.palette(this)
        Regions.load(this)
        val raw = intent.getStringExtra("pick")
        val k = if (raw == null) null else runCatching { Theme.parsePick(JSONObject(raw)) }.getOrNull()
        if (k == null) { finish(); return }
        build(k, intent.getDoubleExtra("dist", -1.0))
    }

    private fun chip(text: String) = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(p.sb)
        background = rect(p.ch, dp(9))
        setPadding(dp(8), dp(2), dp(8), dp(2))
    }

    private fun build(k: Pick, dist: Double) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
        }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(TextView(this).apply {
            text = "←"
            textSize = 26f
            setTextColor(p.tx)
            contentDescription = "返回"
            setPadding(0, dp(2), dp(14), dp(2))
            setOnClickListener { finish() }
        })
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(TextView(this).apply {
            text = "作者：ArchieKUO　${Theme.VERSION}"
            textSize = 12f
            setTypeface(null, Typeface.ITALIC)
            setTextColor(Theme.GOLD)
        })
        content.addView(bar)

        val titleRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(8))
        }
        titleRow.addView(TextView(this).apply {
            text = k.name
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (dist >= 0) titleRow.addView(TextView(this).apply {
            text = Theme.dist(dist)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.hi)
            background = rect(p.ch, dp(10))
            setPadding(dp(9), dp(3), dp(9), dp(3))
        })
        content.addView(titleRow)

        val tags = LinearLayout(this)
        tags.addView(chip(Theme.tierName(Theme.tier(k))), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        tags.addView(chip(k.category), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        if (k.district.isNotEmpty()) tags.addView(chip(k.city + " " + k.district))
        content.addView(tags)

        val why = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(p.cd, dp(14), p.ln)
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        why.addView(TextView(this).apply { text = "為什麼推薦"; textSize = 11f; setTextColor(p.sb) })
        val rs = Theme.reasons(k)
        for (r in if (rs.isEmpty()) listOf("尚無評價資料") else rs) {
            why.addView(TextView(this).apply {
                text = r
                textSize = 14.5f
                setTextColor(p.tx)
                setLineSpacing(0f, 1.2f)
                setPadding(0, dp(5), 0, 0)
            })
        }
        content.addView(why, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(p.cd, dp(14), p.ln)
            setPadding(dp(14), dp(2), dp(14), dp(2))
        }
        info.addView(row("地址", if (k.address.isNotEmpty()) k.address + "　›" else "未提供", k.address.isNotEmpty()) { openMap(k) })
        info.addView(row("電話", if (k.phone.isNotEmpty()) k.phone + "　›" else "未提供", k.phone.isNotEmpty()) { dial(k) })
        info.addView(row("營業時間", k.hours.ifEmpty { "未提供" }, last = true))
        content.addView(info, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val btns = LinearLayout(this)
        btns.addView(button("導航", true) { openMap(k) }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        btns.addView(button("撥打", false) { dial(k) }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        btns.addView(button("資料來源", false) { openUrl(k) }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(btns, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        content.addView(TextView(this).apply {
            text = "評價與點閱數為整理當日的公開資料，可能與現況略有差異。"
            textSize = 11f
            setTextColor(p.sb)
            setPadding(0, dp(14), 0, 0)
        })
        setContentView(ScrollView(this).apply { setBackgroundColor(p.pg); addView(content) })
    }

    private fun row(label: String, value: CharSequence, tappable: Boolean = false, last: Boolean = false, onClick: (() -> Unit)? = null): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            if (onClick != null) setOnClickListener { onClick() }
        }
        box.addView(TextView(this).apply { text = label; textSize = 11f; setTextColor(p.sb) })
        box.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(if (tappable) p.hi else p.tx)
            if (tappable) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(2), 0, 0)
        })
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(box)
        if (!last) wrap.addView(View(this).apply { setBackgroundColor(p.ln) }, LinearLayout.LayoutParams(-1, 1))
        return wrap
    }

    private fun button(label: String, primary: Boolean, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(0, dp(11), 0, dp(11))
        if (primary) {
            setTextColor(Theme.INK)
            setTypeface(null, Typeface.BOLD)
            background = rect(Theme.GOLD, dp(12))
        } else {
            setTextColor(p.tx)
            background = rect(p.cd, dp(12), p.ln)
        }
        setOnClickListener { onClick() }
    }

    private fun openMap(k: Pick) {
        val q = if (k.address.isNotEmpty()) k.address else k.name
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q))))
        }
    }

    private fun dial(k: Pick) {
        val num = k.phone.filter { it.isDigit() || it == '+' }
        if (num.isEmpty()) { Toast.makeText(this, "沒有電話資料", Toast.LENGTH_SHORT).show(); return }
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
    }

    private fun openUrl(k: Pick) {
        if (k.url.isEmpty()) { Toast.makeText(this, "沒有來源網址", Toast.LENGTH_SHORT).show(); return }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(k.url)))
    }
}
