package app.deals

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
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

class MainActivity : AppCompatActivity() {

    companion object {
        const val DATA_URL = "https://raw.githubusercontent.com/nmymdf/restaurant-deals/main/data.json"
        const val GOLD = "#C9A227"
        const val VERSION = "v0.2"
        val TAIPEI = listOf(
            "中正區", "大同區", "中山區", "松山區", "大安區", "萬華區",
            "信義區", "士林區", "北投區", "內湖區", "南港區", "文山區"
        )
        val NEW_TAIPEI = listOf(
            "板橋區", "三重區", "中和區", "永和區", "新莊區", "新店區", "土城區", "蘆洲區", "汐止區", "樹林區",
            "淡水區", "鶯歌區", "三峽區", "五股區", "泰山區", "林口區", "深坑區", "石碇區", "坪林區", "三芝區",
            "石門區", "八里區", "平溪區", "雙溪區", "貢寮區", "金山區", "萬里區", "烏來區", "瑞芳區"
        )
    }

    private var district = "全部"
    private var region = "全台"
    private var tab = "即將到期"
    private var deals: List<Deal> = emptyList()
    private var updated = ""

    private lateinit var infoText: TextView
    private lateinit var chipRow: LinearLayout
    private lateinit var districtLabel: TextView
    private lateinit var districtScroll: HorizontalScrollView
    private lateinit var districtRow: LinearLayout
    private lateinit var tabRow: LinearLayout
    private lateinit var listBox: LinearLayout

    private val io = Executors.newSingleThreadExecutor()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            setBackgroundColor(Color.WHITE)
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(8))
        }
        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "餐廳優惠日報"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val refresh = TextView(this).apply {
            text = "重新整理"
            textSize = 13f
            setTextColor(Color.parseColor("#555555"))
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = pill(Color.WHITE, Color.parseColor("#BBBBBB"))
            setOnClickListener { fetchRemote(true) }
        }
        titleRow.addView(refresh)
        head.addView(titleRow)

        infoText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
        }
        head.addView(infoText)
        val credit = TextView(this).apply {
            text = "作者：ArchieKUO　$VERSION"
            textSize = 12f
            setTypeface(null, Typeface.ITALIC)
            setTextColor(Color.parseColor(GOLD))
        }
        head.addView(credit)
        root.addView(head)

        chipRow = LinearLayout(this).apply {
            setPadding(dp(16), 0, dp(16), dp(10))
        }
        root.addView(chipRow)

        districtLabel = TextView(this).apply {
            text = "區域（可左右滑動）"
            textSize = 11f
            setTextColor(Color.parseColor("#999999"))
            setPadding(dp(16), 0, dp(16), dp(4))
        }
        root.addView(districtLabel)
        districtScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        districtRow = LinearLayout(this).apply {
            setPadding(dp(16), 0, dp(16), dp(10))
        }
        districtScroll.addView(districtRow)
        root.addView(districtScroll)

        tabRow = LinearLayout(this).apply {
            setPadding(dp(16), 0, dp(16), dp(6))
        }
        root.addView(tabRow)

        val scroll = ScrollView(this)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listBox)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun pill(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(18).toFloat()
        setStroke(dp(1), stroke)
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

    private fun render() {
        val today = LocalDate.now()
        val total = deals.count { !it.until.isBefore(today) }
        val shown = visibleDeals().size
        val scope = when {
            region == "全台" -> ""
            district == "全部" -> "$region · "
            else -> "$region · $district · "
        }
        infoText.text = (if (updated.isNotEmpty()) "$updated 更新 · " else "") +
            if (scope.isEmpty()) "共 $total 筆" else "${scope}${shown} 筆"

        chipRow.removeAllViews()
        for (r in listOf("全台", "台北", "新北")) {
            val sel = r == region
            val chip = TextView(this).apply {
                text = r
                textSize = 13f
                setPadding(dp(14), dp(6), dp(14), dp(6))
                setTextColor(if (sel) Color.WHITE else Color.BLACK)
                background = pill(if (sel) Color.BLACK else Color.WHITE, if (sel) Color.BLACK else Color.parseColor("#BBBBBB"))
                setOnClickListener {
                    if (region != r) district = "全部"
                    region = r
                    render()
                }
            }
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.rightMargin = dp(8)
            chipRow.addView(chip, lp)
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
            val chip = TextView(this).apply {
                text = n
                textSize = 12f
                setPadding(dp(11), dp(4), dp(11), dp(4))
                setTextColor(Color.BLACK)
                if (sel) setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(14).toFloat()
                    setStroke(if (sel) dp(2) else dp(1), if (sel) Color.BLACK else Color.parseColor("#BBBBBB"))
                }
                setOnClickListener { district = n; render() }
            }
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.rightMargin = dp(6)
            districtRow.addView(chip, lp)
        }

        tabRow.removeAllViews()
        for (t in listOf("即將到期", "最新", "吃到飽", "壽星")) {
            val sel = t == tab
            val tv = TextView(this).apply {
                text = t
                textSize = 13f
                setPadding(0, dp(6), 0, dp(6))
                setTextColor(if (sel) Color.BLACK else Color.parseColor("#777777"))
                if (sel) setTypeface(null, Typeface.BOLD)
                setOnClickListener { tab = t; render() }
            }
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.rightMargin = dp(18)
            tabRow.addView(tv, lp)
        }

        listBox.removeAllViews()
        val items = visibleDeals()
        if (items.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "目前沒有符合的優惠"
                textSize = 14f
                setTextColor(Color.parseColor("#777777"))
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(40), dp(16), dp(40))
            })
            return
        }
        for (d in items) listBox.addView(row(d, today))
        listBox.addView(TextView(this).apply {
            text = "點一筆開啟原始頁面"
            textSize = 12f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(24))
        })
    }

    private fun row(d: Deal, today: LocalDate): View {
        val days = ChronoUnit.DAYS.between(today, d.until).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener {
                if (d.url.isNotEmpty()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.url)))
            }
        }
        val line = View(this).apply { setBackgroundColor(Color.parseColor("#E6E6E6")) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = d.brand
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(TextView(this).apply {
            text = if (days == 0) "今天到期" else "剩 $days 天"
            textSize = 12f
            setTextColor(if (days <= 7) Color.parseColor("#C62828") else Color.parseColor("#777777"))
        })
        box.addView(top)
        box.addView(TextView(this).apply {
            text = d.offer
            textSize = 13f
            setTextColor(Color.BLACK)
            setPadding(0, dp(3), 0, 0)
        })
        val meta = buildString {
            append(if (d.region == "全台") "全台連鎖" else d.region)
            if (d.districts.isNotEmpty()) append(" · ").append(d.districts.joinToString("、"))
            append(" · 到 ")
            append("${d.until.monthValue}/${d.until.dayOfMonth}")
            if (d.note.isNotEmpty()) append(" · ").append(d.note)
        }
        box.addView(TextView(this).apply {
            text = meta
            textSize = 12f
            setTextColor(Color.parseColor("#777777"))
            setPadding(0, dp(3), 0, 0)
        })
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
        wrap.addView(box)
        return wrap
    }
}
