package app.deals

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors

data class Deal(
    val brand: String,
    val offer: String,
    val region: String,
    val districts: List<String>,
    val until: LocalDate,
    val added: LocalDate,
    val tags: List<String>,
    val note: String,
    val url: String
)

class Pal(
    val pg: Int, val cd: Int, val tx: Int, val sb: Int, val ln: Int,
    val ch: Int, val rd: Int, val rdbg: Int, val hi: Int
)

class MainActivity : AppCompatActivity() {

    companion object {
        const val DATA_URL = "https://raw.githubusercontent.com/nmymdf/restaurant-deals/main/data.json"
        const val VERSION = "v0.4"
        val GOLD = Color.parseColor("#C9A227")
        val INK = Color.parseColor("#1F1B16")
        val TAIPEI = listOf(
            "中正區", "大同區", "中山區", "松山區", "大安區", "萬華區",
            "信義區", "士林區", "北投區", "內湖區", "南港區", "文山區"
        )
        val NEW_TAIPEI = listOf(
            "板橋區", "三重區", "中和區", "永和區", "新莊區", "新店區", "土城區", "蘆洲區", "汐止區", "樹林區",
            "淡水區", "鶯歌區", "三峽區", "五股區", "泰山區", "林口區", "深坑區", "石碇區", "坪林區", "三芝區",
            "石門區", "八里區", "平溪區", "雙溪區", "貢寮區", "金山區", "萬里區", "烏來區", "瑞芳區"
        )
        val HIGHLIGHT = Regex("[0-9,]+\\s*元|[0-9.]+\\s*折|[一二三四五六七八九十]+折|買[一二1]送[一二1]|[四五六七八九十]人同行[一二]人免費|免費|半價")
    }

    private lateinit var p: Pal
    private var district = "全部"
    private var region = "全台"
    private var tab = "即將到期"
    private var deals: List<Deal> = emptyList()
    private var updated = ""

    private lateinit var infoText: TextView
    private lateinit var segBox: LinearLayout
    private lateinit var districtLabel: TextView
    private lateinit var districtScroll: HorizontalScrollView
    private lateinit var districtRow: LinearLayout
    private lateinit var tabRow: LinearLayout
    private lateinit var listBox: LinearLayout

    private val io = Executors.newSingleThreadExecutor()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun c(hex: String) = Color.parseColor(hex)

    private fun rect(fill: Int, radius: Int, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
        if (stroke != 0) setStroke(maxOf(1, dp(1) / 2), stroke)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        p = if (night) {
            Pal(c("#14120F"), c("#1F1B16"), c("#F2EEE4"), c("#A59E8C"), c("#332E25"),
                c("#2A2519"), c("#FF8A80"), c("#3A1F1C"), c("#C9A227"))
        } else {
            Pal(c("#F7F4EC"), c("#FFFFFF"), c("#1F1B16"), c("#6B6558"), c("#E8E2D2"),
                c("#EFE9D6"), c("#B3261E"), c("#FBE9E7"), c("#A8860F"))
        }
        buildUi()
        val cached = getSharedPreferences("deals", MODE_PRIVATE).getString("json", null)
        val first = cached ?: runCatching {
            assets.open("data.json").bufferedReader().use { it.readText() }
        }.getOrNull()
        if (first != null) applyJson(first)
        render()
        fetchRemote(false)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.pg)
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(4))
        }
        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_fg)
            background = rect(INK, dp(10))
            clipToOutline = true
        }
        titleRow.addView(logo, LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(10) })
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(TextView(this).apply {
            text = "餐廳優惠日報"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
        })
        titleBox.addView(TextView(this).apply {
            text = "作者：ArchieKUO　$VERSION"
            textSize = 12f
            setTypeface(null, Typeface.ITALIC)
            setTextColor(GOLD)
        })
        titleRow.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(TextView(this).apply {
            text = "↻"
            textSize = 24f
            setTextColor(p.sb)
            gravity = Gravity.CENTER
            contentDescription = "重新整理"
            setPadding(dp(10), dp(4), dp(4), dp(4))
            setOnClickListener { fetchRemote(true) }
        })
        head.addView(titleRow)
        infoText = TextView(this).apply {
            textSize = 12f
            setTextColor(p.sb)
            setPadding(0, dp(8), 0, dp(10))
        }
        head.addView(infoText)
        root.addView(head)

        segBox = LinearLayout(this).apply {
            background = rect(p.ch, dp(12))
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        root.addView(segBox, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(16); rightMargin = dp(16)
        })

        districtLabel = TextView(this).apply {
            text = "區域（可左右滑動）"
            textSize = 11f
            setTextColor(p.sb)
            setPadding(dp(18), dp(10), dp(16), dp(4))
        }
        root.addView(districtLabel)
        districtScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        districtRow = LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), dp(2)) }
        districtScroll.addView(districtRow)
        root.addView(districtScroll)

        tabRow = LinearLayout(this).apply { setPadding(dp(18), dp(8), dp(16), dp(6)) }
        root.addView(tabRow)

        val scroll = ScrollView(this)
        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(20))
        }
        scroll.addView(listBox)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun applyJson(text: String) {
        val obj = JSONObject(text)
        updated = obj.optString("updated")
        val arr = obj.getJSONArray("deals")
        val out = ArrayList<Deal>()
        for (i in 0 until arr.length()) {
            val d = arr.getJSONObject(i)
            val tags = ArrayList<String>()
            val ta = d.optJSONArray("tags")
            if (ta != null) for (j in 0 until ta.length()) tags.add(ta.getString(j))
            val dists = ArrayList<String>()
            val da = d.optJSONArray("districts")
            if (da != null) for (j in 0 until da.length()) dists.add(da.getString(j))
            val until = runCatching { LocalDate.parse(d.getString("until")) }.getOrNull() ?: continue
            val added = runCatching { LocalDate.parse(d.optString("added")) }.getOrDefault(until)
            out.add(
                Deal(
                    d.optString("brand"), d.optString("offer"), d.optString("region", "全台"), dists,
                    until, added, tags, d.optString("note"), d.optString("url")
                )
            )
        }
        deals = out
    }

    private fun fetchRemote(manual: Boolean) {
        io.execute {
            val result = runCatching {
                val conn = URL("$DATA_URL?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text)
                text
            }
            runOnUiThread {
                val text = result.getOrNull()
                if (text != null) {
                    getSharedPreferences("deals", MODE_PRIVATE).edit().putString("json", text).apply()
                    applyJson(text)
                    render()
                    if (manual) Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show()
                } else if (manual) {
                    Toast.makeText(this, "無法更新，先顯示上次的資料", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun visibleDeals(): List<Deal> {
        val today = LocalDate.now()
        var list = deals.filter { !it.until.isBefore(today) }
        if (region != "全台") list = list.filter { it.region == region || it.region == "全台" }
        if (region != "全台" && district != "全部") {
            list = list.filter { it.region == "全台" || it.districts.isEmpty() || district in it.districts }
        }
        list = when (tab) {
            "吃到飽" -> list.filter { "吃到飽" in it.tags }
            "壽星" -> list.filter { "壽星" in it.tags }
            else -> list
        }
        return when (tab) {
            "最新" -> list.sortedByDescending { it.added }
            else -> list.sortedBy { it.until }
        }
    }

    private fun small(textValue: String, bg: Int, fg: Int): TextView = TextView(this).apply {
        text = textValue
        textSize = 11f
        setTextColor(fg)
        background = rect(bg, dp(9))
        setPadding(dp(8), dp(2), dp(8), dp(2))
    }

    private fun render() {
        val today = LocalDate.now()
        val total = deals.count { !it.until.isBefore(today) }
        val items = visibleDeals()
        val scope = when {
            region == "全台" -> ""
            district == "全部" -> "$region · "
            else -> "$region · $district · "
        }
        infoText.text = (if (updated.isNotEmpty()) "$updated 更新 · " else "") +
            if (scope.isEmpty()) "共 $total 筆" else "${scope}${items.size} 筆"

        segBox.removeAllViews()
        for (r in listOf("全台", "台北", "新北")) {
            val sel = r == region
            segBox.addView(TextView(this).apply {
                text = r
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, dp(7))
                setTextColor(if (sel) p.tx else p.sb)
                if (sel) {
                    setTypeface(null, Typeface.BOLD)
                    background = rect(p.cd, dp(9), p.ln)
                }
                setOnClickListener {
                    if (region != r) district = "全部"
                    region = r
                    render()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }

        val names = when (region) {
            "台北" -> TAIPEI
            "新北" -> NEW_TAIPEI
            else -> emptyList()
        }
        val vis = if (names.isEmpty()) View.GONE else View.VISIBLE
        districtLabel.visibility = vis
        districtScroll.visibility = vis
        districtRow.removeAllViews()
        for (n in listOf("全部") + names) {
            val sel = n == district
            districtRow.addView(TextView(this).apply {
                text = n
                textSize = 12f
                setPadding(dp(12), dp(5), dp(12), dp(5))
                if (sel) {
                    setTextColor(INK)
                    setTypeface(null, Typeface.BOLD)
                    background = rect(GOLD, dp(15))
                } else {
                    setTextColor(p.sb)
                    background = rect(p.ch, dp(15))
                }
                setOnClickListener { district = n; render() }
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        }

        tabRow.removeAllViews()
        for (t in listOf("即將到期", "最新", "吃到飽", "壽星")) {
            val sel = t == tab
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setOnClickListener { tab = t; render() }
            }
            col.addView(TextView(this).apply {
                text = t
                textSize = 13f
                setPadding(0, dp(4), 0, dp(4))
                setTextColor(if (sel) p.tx else p.sb)
                if (sel) setTypeface(null, Typeface.BOLD)
            })
            col.addView(View(this).apply {
                setBackgroundColor(if (sel) GOLD else Color.TRANSPARENT)
            }, LinearLayout.LayoutParams(-1, dp(2)))
            tabRow.addView(col, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(20) })
        }

        listBox.removeAllViews()
        if (items.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "目前沒有符合的優惠"
                textSize = 14f
                setTextColor(p.sb)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(48), dp(16), dp(48))
            })
            return
        }
        for (d in items) listBox.addView(card(d, today))
        listBox.addView(TextView(this).apply {
            text = "點一筆開啟原始頁面"
            textSize = 12f
            setTextColor(p.sb)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
        })
    }

    private fun highlighted(s: String): SpannableString {
        val sp = SpannableString(s)
        for (m in HIGHLIGHT.findAll(s)) {
            sp.setSpan(ForegroundColorSpan(p.hi), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sp.setSpan(StyleSpan(Typeface.BOLD), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sp
    }

    private fun card(d: Deal, today: LocalDate): View {
        val days = ChronoUnit.DAYS.between(today, d.until).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(p.cd, dp(14), p.ln)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener {
                if (d.url.isNotEmpty()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.url)))
            }
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = d.brand
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val hot = days <= 7
        top.addView(
            small(if (days == 0) "今天到期" else "剩 $days 天", if (hot) p.rdbg else p.ch, if (hot) p.rd else p.sb)
        )
        box.addView(top)
        box.addView(TextView(this).apply {
            text = highlighted(d.offer)
            textSize = 13f
            setTextColor(p.tx)
            setLineSpacing(0f, 1.2f)
            setPadding(0, dp(6), 0, dp(8))
        })
        val tagRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val where = if (d.region == "全台") "全台連鎖" else
            d.region + (if (d.districts.isNotEmpty()) " · " + d.districts.joinToString("、") else "")
        tagRow.addView(small(where, p.ch, p.sb), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        for (t in d.tags) {
            tagRow.addView(small(t, p.ch, p.sb), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        }
        tagRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        tagRow.addView(TextView(this).apply {
            text = "到 ${d.until.monthValue}/${d.until.dayOfMonth}"
            textSize = 12f
            setTextColor(p.sb)
        })
        box.addView(tagRow)
        if (d.note.isNotEmpty()) {
            box.addView(TextView(this).apply {
                text = d.note
                textSize = 12f
                setTextColor(p.sb)
                setPadding(0, dp(6), 0, 0)
            })
        }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        return wrap
    }
}
