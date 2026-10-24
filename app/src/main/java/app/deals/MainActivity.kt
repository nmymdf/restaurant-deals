package app.deals

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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

class MainActivity : AppCompatActivity() {

    companion object {
        const val DATA_URL = "https://raw.githubusercontent.com/nmymdf/restaurant-deals/main/data.json"
        const val ALL_CAT = "全部類別"
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

    private lateinit var p: Pal
    private var district = "全部"
    private var region = "全台"
    private var category = ALL_CAT
    private var catOpen = false
    private var tab = "即將到期"
    private var deals: List<Deal> = emptyList()
    private var updated = ""

    private lateinit var infoText: TextView
    private lateinit var segBox: LinearLayout
    private lateinit var districtLabel: TextView
    private lateinit var districtScroll: HorizontalScrollView
    private lateinit var districtRow: LinearLayout
    private lateinit var catBtn: LinearLayout
    private lateinit var catLabel: TextView
    private lateinit var catArrow: TextView
    private lateinit var catPanel: LinearLayout
    private lateinit var tabRow: LinearLayout
    private lateinit var listBox: LinearLayout

    private val io = Executors.newSingleThreadExecutor()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rect(fill: Int, r: Int, stroke: Int = 0) = Theme.roundRect(fill, r, stroke, 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Theme.palette(this)
        buildUi()
        val cached = getSharedPreferences("deals", MODE_PRIVATE).getString("json", null)
        val first = cached ?: runCatching {
            assets.open("data.json").bufferedReader().use { it.readText() }
        }.getOrNull()
        if (first != null) runCatching { applyJson(first) }
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
            background = rect(Theme.INK, dp(10))
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
            text = "作者：ArchieKUO　${Theme.VERSION}"
            textSize = 12f
            setTypeface(null, Typeface.ITALIC)
            setTextColor(Theme.GOLD)
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

        val catWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), 0)
        }
        catBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(12), dp(10))
            setOnClickListener { catOpen = !catOpen; render() }
        }
        catLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(p.tx)
        }
        catArrow = TextView(this).apply {
            textSize = 13f
            setTextColor(p.sb)
        }
        catBtn.addView(catLabel, LinearLayout.LayoutParams(0, -2, 1f))
        catBtn.addView(catArrow)
        catWrap.addView(catBtn)
        catPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(p.cd, dp(12), p.ln)
            setPadding(0, dp(4), 0, dp(4))
        }
        catWrap.addView(catPanel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        root.addView(catWrap)

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
            val deal = Theme.parseDeal(arr.getJSONObject(i)) ?: continue
            out.add(deal)
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
                    runCatching { applyJson(text) }
                    render()
                    if (manual) Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show()
                } else if (manual) {
                    Toast.makeText(this, "無法更新，先顯示上次的資料", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun categories(): List<String> {
        val today = LocalDate.now()
        val counts = deals.filter { !it.until.isBefore(today) }.groupingBy { it.category }.eachCount()
        val keep = counts.filter { it.value >= 2 }.keys.toMutableList()
        if (category != ALL_CAT && category !in keep) keep.add(category)
        return keep.sortedWith(
            compareBy<String> { it == "其他" }.thenByDescending { counts[it] ?: 0 }.thenBy { it }
        )
    }

    private fun visibleDeals(): List<Deal> {
        val today = LocalDate.now()
        var list = deals.filter { !it.until.isBefore(today) }
        if (region != "全台") list = list.filter { it.region == region || it.region == "全台" }
        if (region != "全台" && district != "全部") {
            list = list.filter { it.region == "全台" || it.districts.isEmpty() || district in it.districts }
        }
        if (category != ALL_CAT) list = list.filter { it.category == category }
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

        val parts = ArrayList<String>()
        if (region != "全台") parts.add(region)
        if (region != "全台" && district != "全部") parts.add(district)
        if (category != ALL_CAT) parts.add(category)
        val scope = if (parts.isEmpty()) "共 $total 筆" else parts.joinToString(" · ") + " · ${items.size} 筆"
        infoText.text = (if (updated.isNotEmpty()) "$updated 更新 · " else "") + scope

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
                    setTextColor(Theme.INK)
                    setTypeface(null, Typeface.BOLD)
                    background = rect(Theme.GOLD, dp(15))
                } else {
                    setTextColor(p.sb)
                    background = rect(p.ch, dp(15))
                }
                setOnClickListener { district = n; render() }
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        }

        val label = SpannableString("類別　$category")
        label.setSpan(ForegroundColorSpan(p.sb), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        catLabel.text = label
        catArrow.text = if (catOpen) "▴" else "▾"
        catBtn.background = rect(p.cd, dp(12), if (catOpen) Theme.GOLD else p.ln)
        catPanel.visibility = if (catOpen) View.VISIBLE else View.GONE
        catPanel.removeAllViews()
        if (catOpen) {
            for (n in listOf(ALL_CAT) + categories()) {
                val sel = n == category
                catPanel.addView(TextView(this).apply {
                    text = if (sel) "$n　✓" else n
                    textSize = 13f
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    setTextColor(if (sel) p.hi else p.tx)
                    if (sel) {
                        setTypeface(null, Typeface.BOLD)
                        setBackgroundColor(p.ch)
                    }
                    setOnClickListener { category = n; catOpen = false; render() }
                })
            }
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
                setBackgroundColor(if (sel) Theme.GOLD else Color.TRANSPARENT)
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
            text = "點一筆看詳細資訊"
            textSize = 12f
            setTextColor(p.sb)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
        })
    }

    private fun card(d: Deal, today: LocalDate): View {
        val days = ChronoUnit.DAYS.between(today, d.until).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(p.cd, dp(14), p.ln)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener {
                startActivity(Intent(this@MainActivity, DetailActivity::class.java).putExtra("deal", d.raw))
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
            small(if (days <= 0) "今天到期" else "剩 $days 天", if (hot) p.rdbg else p.ch, if (hot) p.rd else p.sb)
        )
        box.addView(top)
        box.addView(TextView(this).apply {
            text = Theme.highlighted(d.offer, p.hi)
            textSize = 13f
            setTextColor(p.tx)
            setLineSpacing(0f, 1.2f)
            setPadding(0, dp(6), 0, dp(8))
        })
        val tagRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val where = if (d.region == "全台") "全台連鎖" else
            d.region + (if (d.districts.isNotEmpty()) " · " + d.districts.joinToString("、") else "")
        tagRow.addView(small(where, p.ch, p.sb), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        tagRow.addView(small(d.category, p.ch, p.sb), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
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
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        return wrap
    }
}
