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
    }

    private lateinit var p: Pal
    private var region = "全台"
    private var districts: Set<String> = emptySet()
    private var pendCity = "全台"
    private val pendDistricts = LinkedHashSet<String>()
    private var category = ALL_CAT
    private var openPanel = 0 // 0 收合、1 地區、2 類別
    private var tab = "即將到期"
    private var deals: List<Deal> = emptyList()
    private var updated = ""

    private lateinit var infoText: TextView
    private lateinit var regBtn: LinearLayout
    private lateinit var regLabel: TextView
    private lateinit var regArrow: TextView
    private lateinit var regPanel: LinearLayout
    private lateinit var leftCol: LinearLayout
    private lateinit var rightCol: LinearLayout
    private lateinit var rightScroll: ScrollView
    private lateinit var applyBtn: TextView
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
        Regions.load(this)
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

        val nearby = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rect(p.cd, dp(14), Theme.GOLD)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            setOnClickListener { startActivity(Intent(this@MainActivity, NearbyActivity::class.java)) }
        }
        val nbText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nbText.addView(TextView(this).apply {
            text = "附近好餐廳"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
        })
        nbText.addView(TextView(this).apply {
            text = "你所在位置 2 公里內，評價高或人氣旺的店"
            textSize = 12f
            setTextColor(p.sb)
        })
        nearby.addView(nbText, LinearLayout.LayoutParams(0, -2, 1f))
        nearby.addView(TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(p.hi)
        })
        root.addView(nearby, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(10)
        })

        val bar = LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        regLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(p.tx)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        regArrow = TextView(this).apply { textSize = 13f; setTextColor(p.sb) }
        regBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(12), dp(10))
            setOnClickListener { togglePanel(1) }
        }
        regBtn.addView(regLabel, LinearLayout.LayoutParams(0, -2, 1f))
        regBtn.addView(regArrow)
        bar.addView(regBtn, LinearLayout.LayoutParams(0, -2, 1.25f).apply { rightMargin = dp(8) })
        catLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(p.tx)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        catArrow = TextView(this).apply { textSize = 13f; setTextColor(p.sb) }
        catBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(12), dp(10))
            setOnClickListener { togglePanel(2) }
        }
        catBtn.addView(catLabel, LinearLayout.LayoutParams(0, -2, 1f))
        catBtn.addView(catArrow)
        bar.addView(catBtn, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(bar)

        regPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(p.cd, dp(14), p.ln)
        }
        val body = LinearLayout(this)
        val leftScroll = ScrollView(this).apply {
            setBackgroundColor(p.pg)
            isVerticalScrollBarEnabled = false
        }
        leftCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        leftScroll.addView(leftCol)
        rightScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        rightCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rightScroll.addView(rightCol)
        body.addView(leftScroll, LinearLayout.LayoutParams(0, dp(300), 0.85f))
        body.addView(rightScroll, LinearLayout.LayoutParams(0, dp(300), 1.15f))
        regPanel.addView(body)
        regPanel.addView(View(this).apply { setBackgroundColor(p.ln) }, LinearLayout.LayoutParams(-1, 1))
        val foot = LinearLayout(this).apply { setPadding(dp(12), dp(10), dp(12), dp(10)) }
        foot.addView(TextView(this).apply {
            text = "清除"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(p.tx)
            background = rect(p.cd, dp(12), p.ln)
            setPadding(0, dp(11), 0, dp(11))
            setOnClickListener { pendCity = "全台"; pendDistricts.clear(); renderRegionPanel() }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        applyBtn = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Theme.INK)
            setTypeface(null, Typeface.BOLD)
            background = rect(Theme.GOLD, dp(12))
            setPadding(0, dp(11), 0, dp(11))
            setOnClickListener {
                region = pendCity
                districts = LinkedHashSet(pendDistricts)
                openPanel = 0
                render()
            }
        }
        foot.addView(applyBtn, LinearLayout.LayoutParams(0, -2, 2f))
        regPanel.addView(foot)
        root.addView(regPanel, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(4)
        })

        catPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(p.cd, dp(12), p.ln)
            setPadding(0, dp(4), 0, dp(4))
        }
        root.addView(catPanel, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(4)
        })

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

    private fun cityOf(d: Deal) = Regions.normalize(d.region)

    private fun filtered(city: String, ds: Set<String>): List<Deal> {
        val today = LocalDate.now()
        var list = deals.filter { !it.until.isBefore(today) }
        if (city != "全台") {
            list = list.filter { d ->
                val dc = cityOf(d)
                dc == "全台" || (dc == city && (ds.isEmpty() || d.districts.isEmpty() || d.districts.any { it in ds }))
            }
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

    private fun visibleDeals(): List<Deal> = filtered(region, districts)

    private fun togglePanel(which: Int) {
        if (openPanel == which) {
            openPanel = 0
        } else {
            openPanel = which
            if (which == 1) {
                pendCity = region
                pendDistricts.clear()
                pendDistricts.addAll(districts)
            }
        }
        render()
    }

    private fun regionSummary(): String {
        if (region == "全台") return "全台"
        if (districts.isEmpty()) return region
        val ds = if (districts.size <= 2) districts.joinToString("、") else "${districts.size} 區"
        return "$region · $ds"
    }

    private fun renderRegionPanel() {
        leftCol.removeAllViews()
        fun label(t: String) = TextView(this).apply {
            text = t
            textSize = 11f
            setTextColor(p.sb)
            setPadding(dp(14), dp(10), dp(14), dp(4))
        }
        fun cityRow(name: String, sub: String? = null): View {
            val sel = name == pendCity
            return LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(if (sel) p.cd else Color.TRANSPARENT)
                setOnClickListener {
                    if (pendCity != name) { pendCity = name; pendDistricts.clear() }
                    renderRegionPanel()
                    rightScroll.scrollTo(0, 0)
                }
                addView(View(this@MainActivity).apply {
                    setBackgroundColor(if (sel) Theme.GOLD else Color.TRANSPARENT)
                }, LinearLayout.LayoutParams(dp(3), dp(24)))
                addView(TextView(this@MainActivity).apply {
                    text = name
                    textSize = 14f
                    setTextColor(if (sel) p.tx else p.sb)
                    if (sel) setTypeface(null, Typeface.BOLD)
                    setPadding(dp(11), dp(11), dp(8), dp(11))
                })
            }
        }
        leftCol.addView(cityRow("全台"))
        for (g in Regions.groups) {
            leftCol.addView(label(if (g.name == "六都") "六都" else "其他縣市 · ${g.name}"))
            for (c in g.cities) leftCol.addView(cityRow(c.name))
        }

        rightCol.removeAllViews()
        fun optRow(text: String, checked: Boolean, onClick: () -> Unit): View = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            setOnClickListener { onClick() }
            addView(TextView(this@MainActivity).apply {
                this.text = text
                textSize = 14f
                setTextColor(if (checked) p.hi else p.tx)
                if (checked) setTypeface(null, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (checked) addView(TextView(this@MainActivity).apply {
                this.text = "✓"
                textSize = 14f
                setTextColor(p.hi)
            })
        }
        if (pendCity == "全台") {
            rightCol.addView(TextView(this).apply {
                text = "顯示全台的優惠\n\n想看特定地區，請從左邊選縣市"
                textSize = 13f
                setTextColor(p.sb)
                setLineSpacing(0f, 1.2f)
                setPadding(dp(16), dp(16), dp(16), dp(16))
            })
        } else {
            rightCol.addView(optRow("不限", pendDistricts.isEmpty()) {
                pendDistricts.clear(); renderRegionPanel()
            })
            for (d in Regions.districts(pendCity)) {
                rightCol.addView(View(this).apply { setBackgroundColor(p.ln) }, LinearLayout.LayoutParams(-1, 1))
                rightCol.addView(optRow(d, d in pendDistricts) {
                    if (!pendDistricts.remove(d)) pendDistricts.add(d)
                    renderRegionPanel()
                })
            }
        }
        applyBtn.text = "套用，看 ${filtered(pendCity, pendDistricts).size} 筆"
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
        if (region != "全台") parts.add(regionSummary())
        if (category != ALL_CAT) parts.add(category)
        val scope = if (parts.isEmpty()) "共 $total 筆" else parts.joinToString(" · ") + " · ${items.size} 筆"
        infoText.text = (if (updated.isNotEmpty()) "$updated 更新 · " else "") + scope

        val regSpan = SpannableString("地區　${regionSummary()}")
        regSpan.setSpan(ForegroundColorSpan(p.sb), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        regLabel.text = regSpan
        regArrow.text = if (openPanel == 1) "▴" else "▾"
        regBtn.background = rect(p.cd, dp(12), if (openPanel == 1) Theme.GOLD else p.ln)
        regPanel.visibility = if (openPanel == 1) View.VISIBLE else View.GONE
        if (openPanel == 1) renderRegionPanel()

        val label = SpannableString("類別　$category")
        label.setSpan(ForegroundColorSpan(p.sb), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        catLabel.text = label
        catArrow.text = if (openPanel == 2) "▴" else "▾"
        catBtn.background = rect(p.cd, dp(12), if (openPanel == 2) Theme.GOLD else p.ln)
        catPanel.visibility = if (openPanel == 2) View.VISIBLE else View.GONE
        catPanel.removeAllViews()
        if (openPanel == 2) {
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
                    setOnClickListener { category = n; openPanel = 0; render() }
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
        if (region != "全台" && items.none { cityOf(it) == region }) {
            listBox.addView(TextView(this).apply {
                text = "${region}目前沒有在地優惠" + if (items.isNotEmpty()) "，以下是全台連鎖優惠" else ""
                textSize = 12f
                setTextColor(p.sb)
                background = rect(p.ch, dp(10))
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
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
        val where = if (cityOf(d) == "全台") "全台連鎖" else
            cityOf(d) + (if (d.districts.isNotEmpty()) " · " + d.districts.joinToString("、") else "")
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
