package app.deals

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class NearbyActivity : AppCompatActivity() {

    companion object {
        const val PICKS_URL = "https://raw.githubusercontent.com/nmymdf/restaurant-deals/main/picks.json"
        val RADII = listOf(1000.0, 2000.0, 5000.0, 10000.0)
    }

    private lateinit var p: Pal
    private lateinit var ui: Ui
    private lateinit var header: Header
    private lateinit var filterBar: LinearLayout
    private lateinit var panelHolder: LinearLayout
    private lateinit var condHolder: LinearLayout
    private lateinit var listBox: LinearLayout
    private var openPanel = 0 // 0 收合、1 範圍、2 類別
    private var category = Cats.ALL
    private var lastCounts: Map<String, Int> = emptyMap()
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var picks: List<Pick> = emptyList()
    private var picksReady = false
    private var loc: Location? = null
    private var radiusIdx = 0
    private var checked = 0
    private var geocoding = false
    private var updated = ""
    private val coords = HashMap<String, DoubleArray>()
    private var userCity = ""
    private val osmCache = HashMap<Int, List<Place>>()
    private var osmLoading = false
    private var osmFailed = false
    private var shown = 20
    private var widenedFrom = -1

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rect(fill: Int, r: Int, stroke: Int = 0) = Theme.roundRect(fill, r, stroke, 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Theme.palette(this)
        ui = Ui(this, p)
        Regions.load(this)
        buildUi()
        loadPicks()
        if (hasPermission()) locate() else requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 7
        )
    }

    private fun hasPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (hasPermission()) locate()
        else showMessage("需要位置權限才能找附近的店。\n請到手機設定 → 應用程式 → 餐廳優惠日報 → 權限，開啟「位置」。", "重新授權") {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 7
            )
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.pg)
        }
        header = ui.header(1, "名單更新") { osmCache.clear(); loadOsm(); loadPicks(true) }
        root.addView(header.view)
        filterBar = LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        root.addView(filterBar)
        panelHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(panelHolder, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(4)
        })
        condHolder = LinearLayout(this)
        root.addView(condHolder, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(28))
        }
        root.addView(ScrollView(this).apply { addView(listBox) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        drawFilters()
        showMessage("正在取得你的位置…")
    }

    private fun togglePanel(which: Int) {
        openPanel = if (openPanel == which) 0 else which
        drawFilters()
    }

    private fun drawFilters() {
        filterBar.removeAllViews()
        filterBar.addView(ui.filterButton("範圍", "${(RADII[radiusIdx] / 1000).toInt()} 公里", openPanel == 1) { togglePanel(1) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        filterBar.addView(ui.filterButton("類別", if (category == Cats.ALL) "全部" else category, openPanel == 2) { togglePanel(2) },
            LinearLayout.LayoutParams(0, -2, 1f))
        panelHolder.removeAllViews()
        if (openPanel == 1) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rect(p.cd, dp(12), p.ln)
            }
            for (i in RADII.indices) {
                val sel = i == radiusIdx
                box.addView(TextView(this).apply {
                    text = "${(RADII[i] / 1000).toInt()} 公里內" + if (sel) "  ✓" else ""
                    textSize = 16f
                    setTextColor(if (sel) p.hi else p.tx)
                    if (sel) setTypeface(null, Typeface.BOLD)
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    setOnClickListener { radiusIdx = i; widenedFrom = -1; shown = 20; openPanel = 0; drawFilters(); loadOsm(); refresh() }
                })
            }
            panelHolder.addView(box)
        } else if (openPanel == 2) {
            val known = picks.map { it.category } + lastCounts.keys
            panelHolder.addView(
                ui.categoryPanel(lastCounts, known, category,
                    onPick = { category = it; shown = 20; openPanel = 0; drawFilters(); refresh() },
                    onReorder = { drawFilters() }),
                LinearLayout.LayoutParams(-1, dp(380))
            )
        }
    }

    private fun showMessage(msg: String, btn: String? = null, onClick: (() -> Unit)? = null) {
        condHolder.removeAllViews()
        listBox.removeAllViews()
        listBox.addView(TextView(this).apply {
            text = msg
            textSize = 16f
            setTextColor(p.sb)
            setLineSpacing(0f, 1.25f)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(40), dp(16), dp(16))
        })
        if (btn != null && onClick != null) listBox.addView(button(btn) { onClick() })
    }

    private fun button(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(Theme.INK)
        setTypeface(null, Typeface.BOLD)
        background = rect(Theme.GOLD, dp(12))
        setPadding(0, dp(11), 0, dp(11))
        setOnClickListener { onClick() }
    }

    // ---- 精選名單 ----
    private fun loadPicks(manual: Boolean = false) {
        val prefs = getSharedPreferences("deals", MODE_PRIVATE)
        io.execute {
            val remote = runCatching {
                val conn = URL("$PICKS_URL?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text)
                text
            }.getOrNull()
            if (remote != null) prefs.edit().putString("picks", remote).apply()
            if (remote != null) ui.markFetched("fetched_picks")
            val text = remote ?: prefs.getString("picks", null)
            val parsed = ArrayList<Pick>()
            var upd = ""
            if (text != null) runCatching {
                val obj = JSONObject(text)
                upd = obj.optString("updated")
                val arr = obj.getJSONArray("picks")
                for (i in 0 until arr.length()) Theme.parsePick(arr.getJSONObject(i))?.let { parsed.add(it) }
            }
            handler.post {
                picks = parsed
                updated = upd
                picksReady = true
                header.caption.text = "名單更新　" + ui.fetchedText("fetched_picks")
                if (manual) android.widget.Toast.makeText(this, if (remote != null) "已更新" else "無法更新，先顯示上次的名單", android.widget.Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    // ---- 定位 ----
    private fun locate() {
        showMessage("正在取得你的位置…")
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            val l = runCatching { lm.getLastKnownLocation(prov) }.getOrNull() ?: continue
            if (best == null || l.time > best.time) best = l
        }
        val b = best
        if (b != null && System.currentTimeMillis() - b.time < 5 * 60_000L) {
            gotLocation(b)
            return
        }
        var done = false
        val listeners = ArrayList<LocationListener>()
        fun deliver(l: Location?) {
            if (done) return
            done = true
            listeners.forEach { runCatching { lm.removeUpdates(it) } }
            when {
                l != null -> gotLocation(l)
                b != null -> gotLocation(b)
                else -> showMessage("取不到你的位置。\n請確認手機的定位服務已開啟，到戶外或窗邊再試一次。", "再試一次") { locate() }
            }
        }
        for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            val ls = object : LocationListener {
                override fun onLocationChanged(location: Location) { deliver(location) }
                @Deprecated("old api") override fun onStatusChanged(provider: String?, s: Int, e: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            val ok = runCatching {
                if (lm.isProviderEnabled(prov)) {
                    lm.requestLocationUpdates(prov, 0L, 0f, ls, Looper.getMainLooper())
                    true
                } else false
            }.getOrDefault(false)
            if (ok) listeners.add(ls)
        }
        if (listeners.isEmpty()) { deliver(null); return }
        handler.postDelayed({ deliver(null) }, 15000)
    }

    private fun gotLocation(l: Location) {
        loc = l
        osmCache.clear()
        loadOsm()
        io.execute {
            // 反查所在縣市，用來決定先處理哪些店（失敗也沒關係）
            val city = runCatching {
                @Suppress("DEPRECATION")
                val a: List<Address>? = Geocoder(this, Locale.TAIWAN).getFromLocation(l.latitude, l.longitude, 1)
                Regions.normalize(a?.firstOrNull()?.adminArea ?: "")
            }.getOrDefault("")
            handler.post { userCity = city; refresh() }
        }
        refresh()
    }

    // ---- 座標 ----
    private fun coordOf(k: Pick): DoubleArray? {
        if (k.lat != null && k.lng != null) return doubleArrayOf(k.lat, k.lng)
        return coords[k.id]
    }

    private fun startGeocoding() {
        if (geocoding) return
        val todo = picks.filter { coordOf(it) == null && it.address.isNotEmpty() }
        if (todo.isEmpty()) return
        geocoding = true
        val prefs = getSharedPreferences("geo", MODE_PRIVATE)
        val order = todo.sortedBy { if (userCity.isNotEmpty() && it.city == userCity) 0 else 1 }
        io.execute {
            val present = Geocoder.isPresent()
            var n = 0
            for (k in order) {
                val cached = prefs.getString(k.address, null)
                var arr: DoubleArray? = null
                if (cached != null) {
                    if (cached != "none") {
                        val parts = cached.split(",")
                        if (parts.size == 2) arr = doubleArrayOf(parts[0].toDouble(), parts[1].toDouble())
                    }
                } else if (present) {
                    val res = runCatching {
                        @Suppress("DEPRECATION")
                        Geocoder(this, Locale.TAIWAN).getFromLocationName(k.address, 1)
                    }
                    val list = res.getOrNull()
                    if (res.isSuccess) {
                        if (!list.isNullOrEmpty()) {
                            arr = doubleArrayOf(list[0].latitude, list[0].longitude)
                            prefs.edit().putString(k.address, "${arr[0]},${arr[1]}").apply()
                        } else prefs.edit().putString(k.address, "none").apply()
                    }
                }
                n++
                val a = arr
                val cur = n
                handler.post {
                    if (a != null) coords[k.id] = a
                    checked = cur
                    if (cur % 5 == 0 || cur == order.size) refresh()
                }
            }
            handler.post { geocoding = false; refresh() }
        }
    }

    // ---- 開放地圖：附近所有餐廳 ----
    private fun loadOsm() {
        val l = loc ?: return
        val idx = radiusIdx
        if (osmCache.containsKey(idx)) { refresh(); return }
        val key = String.format(Locale.US, "osm_%.3f_%.3f_%d.json", l.latitude, l.longitude, RADII[idx].toInt())
        val file = java.io.File(cacheDir, key)
        // 同一個地方 6 小時內查過，直接用手機裡的結果，幾乎立刻出現
        val cached = runCatching { if (file.exists()) Osm.decode(file.readText()) else null }.getOrNull()
        if (cached != null) {
            osmCache[idx] = cached
            osmFailed = false
            refresh()
            return
        }
        osmLoading = true
        osmFailed = false
        refresh()
        io.execute {
            val res = Osm.fetch(l.latitude, l.longitude, RADII[idx].toInt())
            if (res != null) runCatching { file.writeText(Osm.encode(res)) }
            handler.post {
                osmLoading = false
                if (res != null) osmCache[idx] = res else osmFailed = true
                // 這個範圍真的沒有店（不是查詢失敗），才自動往外擴大一級
                if (res != null && idx == radiusIdx && idx < RADII.size - 1 && merged(idx).isEmpty() && !geocoding) {
                    widenedFrom = idx
                    radiusIdx = idx + 1
                    drawFilters()
                    loadOsm()
                } else refresh()
            }
        }
    }

    private fun normName(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** 我們整理過的名單 + 開放地圖查到的店（同一家只留名單那筆），都帶距離，限制在範圍內 */
    private fun merged(idx: Int): List<Pair<Place, Double>> {
        val l = loc ?: return emptyList()
        val own = picks.mapNotNull { k ->
            coordOf(k)?.let { c -> Place("p" + k.id, k.name, c[0], c[1], k.category, k.address, k.phone, k.hours, k.url, k) }
        }
        val ownNames = own.map { normName(it.name) to it }
        val out = ArrayList<Pair<Place, Double>>()
        fun dist(p: Place): Double {
            val r = FloatArray(1)
            Location.distanceBetween(l.latitude, l.longitude, p.lat, p.lng, r)
            return r[0].toDouble()
        }
        for (p in own) { val d = dist(p); if (d <= RADII[idx]) out.add(p to d) }
        for (o in osmCache[idx] ?: emptyList()) {
            val n = normName(o.name)
            val dup = ownNames.any { (nm, pl) ->
                nm == n && run {
                    val r = FloatArray(1)
                    Location.distanceBetween(o.lat, o.lng, pl.lat, pl.lng, r)
                    r[0] < 400
                }
            }
            if (dup) continue
            val d = dist(o)
            if (d <= RADII[idx]) out.add(o to d)
        }
        return out
    }

    private fun note(t: String) = TextView(this).apply {
        text = t
        textSize = 16f
        setTextColor(p.sb)
        background = rect(p.ch, dp(10))
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    // ---- 顯示 ----
    private fun refresh() {
        val l = loc ?: return
        startGeocoding()
        val idx = radiusIdx
        val all = merged(idx)
        if (all.isEmpty() && (osmLoading || (!osmCache.containsKey(idx) && !osmFailed))) {
            showMessage("正在查附近的餐廳…")
            return
        }
        val withCat = if (category == Cats.ALL) all else all.filter { it.first.category == category }
        lastCounts = all.groupingBy { it.first.category }.eachCount()

        // 好餐廳、口碑不錯：依評價高低；其他：依距離
        val ordered = withCat.sortedWith(Comparator { a, b ->
            val ta = a.first.tier()
            val tb = b.first.tier()
            if (ta != tb) return@Comparator ta.compareTo(tb)
            if (ta < 3) {
                val ra = a.first.pick?.rating?.score ?: 0.0
                val rb = b.first.pick?.rating?.score ?: 0.0
                if (ra != rb) return@Comparator rb.compareTo(ra)
            }
            a.second.compareTo(b.second)
        })
        val tierCount = ordered.groupingBy { it.first.tier() }.eachCount()
        val km = (RADII[idx] / 1000).toInt()

        val conds = ArrayList<Pair<String, () -> Unit>>()
        if (radiusIdx > 0) conds.add("$km 公里" to { radiusIdx = 0; widenedFrom = -1; shown = 20; drawFilters(); loadOsm(); refresh() })
        if (category != Cats.ALL) conds.add(category to { category = Cats.ALL; shown = 20; drawFilters(); refresh() })
        val parts = (1..3).filter { (tierCount[it] ?: 0) > 0 }.joinToString("、") { "${Theme.tierName(it)} ${tierCount[it]}" }
        val progress = if (geocoding) "　· 正在比對名單地址" else ""
        val countLine = "符合 ${ordered.size} 家" + (if (ordered.isNotEmpty()) "（$parts）" else "") + progress
        condHolder.removeAllViews()
        condHolder.addView(ui.conditions(conds, countLine), LinearLayout.LayoutParams(-1, -2))

        listBox.removeAllViews()
        val sp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        if (osmLoading) listBox.addView(note("正在查更多附近的餐廳…先顯示我們整理的名單。"), sp)
        if (osmFailed) listBox.addView(note("查詢逾時，暫時只有我們整理的名單。請按右上角「更新」再試一次。"), sp)
        if (widenedFrom >= 0) listBox.addView(note("${(RADII[widenedFrom] / 1000).toInt()} 公里內沒有餐廳，已自動擴大到 $km 公里。"), sp)
        if (ordered.isEmpty()) {
            when {
                osmFailed ->
                    listBox.addView(ui.emptyState("查詢逾時，暫時查不到附近餐廳。\n這不代表附近沒有店。", "再試一次") {
                        osmCache.remove(idx); loadOsm()
                    })
                category != Cats.ALL && all.isNotEmpty() ->
                    listBox.addView(ui.emptyState("$km 公里內沒有「$category」的店", "移除$category，看全部 ${all.size} 家") {
                        category = Cats.ALL; shown = 20; drawFilters(); refresh()
                    })
                idx < RADII.size - 1 ->
                    listBox.addView(ui.emptyState("$km 公里內找不到餐廳", "範圍擴大到 ${(RADII[idx + 1] / 1000).toInt()} 公里") {
                        radiusIdx = idx + 1; widenedFrom = -1; drawFilters(); loadOsm()
                    })
                else -> listBox.addView(ui.emptyState("$km 公里內找不到餐廳。\n開放地圖在這個地區可能還沒有資料。", null, null))
            }
            return
        }
        var lastTier = 0
        for ((p, d) in ordered.take(shown)) {
            val t = p.tier()
            if (t != lastTier) {
                lastTier = t
                listBox.addView(ui.sectionTitle(Theme.tierName(t), tierCount[t] ?: 0).apply { textSize = 17f })
            }
            listBox.addView(card(p, d, t))
        }
        if (ordered.size > shown) {
            val more = minOf(20, ordered.size - shown)
            listBox.addView(button("顯示更多（再 $more 家）") { shown += 20; refresh() },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        } else if (idx < RADII.size - 1) {
            val next = (RADII[idx + 1] / 1000).toInt()
            listBox.addView(button("範圍擴大到 $next 公里") { radiusIdx = idx + 1; widenedFrom = -1; shown = 20; drawFilters(); loadOsm() },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }

    private fun chip(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(p.sb)
        background = rect(p.ch, dp(9))
        setPadding(dp(9), dp(3), dp(9), dp(3))
    }

    private fun isNight() = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun card(pl: Place, dist: Double, tier: Int): View {
        val night = isNight()
        val blue = android.graphics.Color.parseColor(if (night) "#7FA6F0" else "#3F6FC6")
        val goldBg = android.graphics.Color.parseColor(if (night) "#2E2711" else "#FFF4D0")
        val blueBg = android.graphics.Color.parseColor(if (night) "#1A2438" else "#EAF1FF")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = when (tier) {
                1 -> Theme.roundRect(goldBg, dp(14), Theme.GOLD, dp(2))
                2 -> Theme.roundRect(blueBg, dp(14), blue, dp(2))
                else -> rect(p.cd, dp(14), p.ln)
            }
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnClickListener {
                startActivity(Intent(this@NearbyActivity, PickActivity::class.java)
                    .putExtra("pick", pl.detailJson()).putExtra("dist", dist))
            }
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = pl.name
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(TextView(this).apply {
            text = Theme.dist(dist)
            textSize = 16f
            setTextColor(p.hi)
            setTypeface(null, Typeface.BOLD)
        })
        box.addView(top)
        if (tier < 3) {
            box.addView(TextView(this).apply {
                text = if (tier == 1) "★ 好餐廳" else "✓ 口碑不錯"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (tier == 1) Theme.INK else android.graphics.Color.WHITE)
                background = rect(if (tier == 1) Theme.GOLD else blue, dp(10))
                setPadding(dp(10), dp(3), dp(10), dp(3))
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        }
        val lines = pl.pick?.let { Theme.reasons(it) } ?: listOf("尚無評價資料")
        for (r in lines) {
            box.addView(TextView(this).apply {
                text = r
                textSize = 16f
                setTextColor(if (pl.pick == null) p.sb else p.tx)
                setPadding(0, dp(5), 0, 0)
            })
        }
        if (pl.address.isNotEmpty()) box.addView(TextView(this).apply {
            text = pl.address
            textSize = 15f
            setTextColor(p.sb)
            setPadding(0, dp(4), 0, 0)
        })
        val tags = LinearLayout(this)
        tags.addView(chip(pl.category), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        box.addView(tags, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        return wrap
    }
}
