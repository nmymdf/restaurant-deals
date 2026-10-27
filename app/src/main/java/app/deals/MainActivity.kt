package app.deals

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
    }

    private lateinit var p: Pal
    private lateinit var ui: Ui
    private lateinit var header: Header
    private var region = "全台"
    private var districts: Set<String> = emptySet()
    private var pendCity = "全台"
    private val pendDistricts = LinkedHashSet<String>()
    private var category = Cats.ALL
    private var openPanel = 0 // 0 收合、1 地區、2 類別
    private var deals: List<Deal> = emptyList()

    private lateinit var filterBar: LinearLayout
    private lateinit var regPanel: LinearLayout
    private lateinit var leftCol: LinearLayout
    private lateinit var rightCol: LinearLayout
    private lateinit var rightScroll: ScrollView
    private lateinit var applyBtn: TextView
    private lateinit var catHolder: LinearLayout
    private lateinit var condHolder: LinearLayout
    private lateinit var listBox: LinearLayout

    private val io = Executors.newSingleThreadExecutor()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rect(fill: Int, r: Int, stroke: Int = 0) = Theme.roundRect(fill, r, stroke, 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Theme.palette(this)
        ui = Ui(this, p)
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
        header = ui.header(0, "資料更新") { fetchRemote(true) }
        root.addView(header.view)

        filterBar = LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        root.addView(filterBar)

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

        catHolder = LinearLayout(this)
        root.addView(catHolder, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(4)
        })

        condHolder = LinearLayout(this)
        root.addView(condHolder, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

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
                    ui.markFetched("fetched_deals")
                    runCatching { applyJson(text) }
                    render()
                    if (manual) Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show()
                } else if (manual) {
                    Toast.makeText(this, "無法更新，先顯示上次的資料", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun liveDeals(): List<Deal> {
        val today = LocalDate.now()
        return deals.filter { !it.until.isBefore(today) }
    }

    private fun cityOf(d: Deal) = Regions.normalize(d.region)

    private fun filtered(city: String, ds: Set<String>, cat: String): List<Deal> {
        var list = liveDeals()
        if (city != "全台") {
            list = list.filter { d ->
                val dc = cityOf(d)
                dc == "全台" || (dc == city && (ds.isEmpty() || d.districts.isEmpty() || d.districts.any { it in ds }))
            }
        }
        if (cat != Cats.ALL) list = list.filter { it.category == cat }
        return list.sortedBy { it.until }
    }

    /** 每個類別在目前地區下有幾筆（不受已選類別影響） */
    private fun categoryCounts(): Map<String, Int> =
        filtered(region, districts, Cats.ALL).groupingBy { it.category }.eachCount()

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
        applyBtn.text = "套用，看 ${filtered(pendCity, pendDistricts, category).size} 筆"
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
        val items = filtered(region, districts, category)
        val local = items.filter { cityOf(it) != "全台" }
        val chain = items.filter { cityOf(it) == "全台" }

        header.caption.text = "資料更新　" + ui.fetchedText("fetched_deals")

        filterBar.removeAllViews()
        filterBar.addView(ui.filterButton("地區", regionSummary(), openPanel == 1) { togglePanel(1) },
            LinearLayout.LayoutParams(0, -2, 1.25f).apply { rightMargin = dp(8) })
        filterBar.addView(ui.filterButton("類別", category, openPanel == 2) { togglePanel(2) },
            LinearLayout.LayoutParams(0, -2, 1f))

        regPanel.visibility = if (openPanel == 1) View.VISIBLE else View.GONE
        if (openPanel == 1) renderRegionPanel()

        catHolder.removeAllViews()
        if (openPanel == 2) {
            val known = deals.map { it.category }.distinct()
            catHolder.addView(
                ui.categoryPanel(categoryCounts(), known, category,
                    onPick = { category = it; openPanel = 0; render() },
                    onReorder = { render() }),
                LinearLayout.LayoutParams(-1, dp(380))
            )
        }

        val conds = ArrayList<Pair<String, () -> Unit>>()
        if (region != "全台") conds.add(regionSummary() to { region = "全台"; districts = emptySet(); render() })
        if (category != Cats.ALL) conds.add(category to { category = Cats.ALL; render() })
        val countLine = if (items.isEmpty()) "符合 0 筆"
            else "符合 ${items.size} 筆（在地 ${local.size}、全台連鎖 ${chain.size}）"
        condHolder.removeAllViews()
        condHolder.addView(ui.conditions(conds, countLine), LinearLayout.LayoutParams(-1, -2))

        listBox.removeAllViews()
        if (items.isEmpty()) {
            val noCat = if (category != Cats.ALL) filtered(region, districts, Cats.ALL) else emptyList()
            val where = if (region == "全台") "全台" else regionSummary()
            if (category != Cats.ALL && noCat.isNotEmpty()) {
                listBox.addView(ui.emptyState("$where 目前沒有「$category」的優惠", "移除$category，看 ${noCat.size} 筆") {
                    category = Cats.ALL; render()
                })
            } else if (region != "全台") {
                val all = filtered("全台", emptySet(), category)
                listBox.addView(ui.emptyState("$where 目前沒有符合的優惠",
                    if (all.isNotEmpty()) "移除地區，看全台 ${all.size} 筆" else null) {
                    region = "全台"; districts = emptySet(); render()
                })
            } else {
                listBox.addView(ui.emptyState("目前沒有優惠", null, null))
            }
            return
        }
        if (local.isNotEmpty()) {
            listBox.addView(ui.sectionTitle("在地優惠", local.size))
            for (d in local) listBox.addView(card(d, today))
        } else if (region != "全台") {
            listBox.addView(TextView(this).apply {
                text = "${regionSummary()}目前沒有在地優惠，以下是全台連鎖"
                textSize = 12f
                setTextColor(p.sb)
                background = rect(p.ch, dp(10))
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        if (chain.isNotEmpty()) {
            listBox.addView(ui.sectionTitle("全台連鎖", chain.size))
            for (d in chain) listBox.addView(card(d, today))
        }
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
