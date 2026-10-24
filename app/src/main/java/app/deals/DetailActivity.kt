package app.deals

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
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
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class DetailActivity : AppCompatActivity() {

    private lateinit var p: Pal

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rect(fill: Int, r: Int, stroke: Int = 0) = Theme.roundRect(fill, r, stroke, 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Theme.palette(this)
        val raw = intent.getStringExtra("deal")
        val deal = if (raw == null) null else runCatching { Theme.parseDeal(JSONObject(raw)) }.getOrNull()
        if (deal == null) {
            finish()
            return
        }
        build(deal)
    }

    private fun chip(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(p.sb)
        background = rect(p.ch, dp(9))
        setPadding(dp(8), dp(2), dp(8), dp(2))
    }

    private fun build(d: Deal) {
        val today = LocalDate.now()
        val days = ChronoUnit.DAYS.between(today, d.until).toInt()

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
            text = d.brand
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val hot = days <= 7
        titleRow.addView(TextView(this).apply {
            text = if (days <= 0) "今天到期" else "剩 $days 天"
            textSize = 11f
            setTextColor(if (hot) p.rd else p.sb)
            background = rect(if (hot) p.rdbg else p.ch, dp(10))
            setPadding(dp(9), dp(3), dp(9), dp(3))
        })
        content.addView(titleRow)

        val tagRow = LinearLayout(this)
        val where = if (d.region == "全台") "全台連鎖" else
            d.region + (if (d.districts.isNotEmpty()) " · " + d.districts.joinToString("、") else "")
        tagRow.addView(chip(where), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        tagRow.addView(chip(d.category), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        for (t in d.tags) tagRow.addView(chip(t), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        content.addView(tagRow)

        val offerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(p.cd, dp(14), p.ln)
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        offerBox.addView(TextView(this).apply {
            text = "優惠內容"
            textSize = 11f
            setTextColor(p.sb)
        })
        offerBox.addView(TextView(this).apply {
            text = Theme.highlighted(d.offer, p.hi)
            textSize = 15f
            setTextColor(p.tx)
            setLineSpacing(0f, 1.25f)
            setPadding(0, dp(4), 0, 0)
        })
        content.addView(offerBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(p.cd, dp(14), p.ln)
            setPadding(dp(14), dp(2), dp(14), dp(2))
        }
        info.addView(row("優惠期間", "即日起至 ${d.until.monthValue}/${d.until.dayOfMonth}"))
        info.addView(row("使用條件", d.note.ifEmpty { "未註明" }))
        val addrText = when {
            d.address.isNotEmpty() -> d.address + "　›"
            d.region == "全台" -> "全台各分店，詳情請看原始頁面"
            else -> "未提供"
        }
        info.addView(row("地址", addrText, d.address.isNotEmpty()) { openMap(d) })
        info.addView(row("電話", if (d.phone.isNotEmpty()) d.phone + "　›" else "未提供", d.phone.isNotEmpty()) { dial(d) })
        info.addView(row("營業時間", d.hours.ifEmpty { "未提供" }))
        val rt = d.rating
        val ratingText = if (rt != null) {
            "${rt.score} ★（${rt.count} 則）· 來源：${rt.source}" + if (rt.date.isNotEmpty()) "，${rt.date} 整理" else ""
        } else "未提供"
        info.addView(row("評價", ratingText, last = true))
        content.addView(info, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val btns = LinearLayout(this)
        btns.addView(button("導航", true) { openMap(d) }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        btns.addView(button("撥打", false) { dial(d) }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        btns.addView(button("原始頁面", false) { openUrl(d) }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(btns, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        val scroll = ScrollView(this).apply { setBackgroundColor(p.pg) }
        scroll.addView(content)
        setContentView(scroll)
    }

    private fun row(
        label: String,
        value: CharSequence,
        tappable: Boolean = false,
        last: Boolean = false,
        onClick: (() -> Unit)? = null
    ): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            if (onClick != null) setOnClickListener { onClick() }
        }
        box.addView(TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(p.sb)
        })
        box.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(if (tappable) p.hi else p.tx)
            if (tappable) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(2), 0, 0)
        })
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(box)
        if (!last) {
            wrap.addView(View(this).apply { setBackgroundColor(p.ln) }, LinearLayout.LayoutParams(-1, 1))
        }
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

    private fun openMap(d: Deal) {
        val q = if (d.address.isNotEmpty()) d.address else d.brand
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))))
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q)))
            )
        }
    }

    private fun dial(d: Deal) {
        val num = d.phone.filter { it.isDigit() || it == '+' }
        if (num.isEmpty()) {
            Toast.makeText(this, "這筆優惠沒有電話資料", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
    }

    private fun openUrl(d: Deal) {
        if (d.url.isEmpty()) {
            Toast.makeText(this, "這筆優惠沒有原始頁面", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.url)))
    }
}
