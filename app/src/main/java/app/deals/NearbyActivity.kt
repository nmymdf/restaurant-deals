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
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
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
    private var openPanel = 0 // 0 收合、1 範圍
    private var category = Cats.ALL
    private val GOOD = "★ 好評推薦"
    // 四步流程：0 設條件、1 搜尋中、2 選類別、3 看清單
    private var phase = 0
    private var stage = 0
    private var startedAt = 0L
    private var searchedAt = 0L
    private var errorKind = 0 // 1 逾時、2 範圍內沒有店
    private lateinit var scrollView: ScrollView
    private val tick = object : Runnable {
        override fun run() {
            if (phase == 1) { render(); handler.postDelayed(this, 1000) }
        }
    }
    private val io = Executors.newSingleThreadExecutor()
    private val geoIo = Executors.newSingleThreadExecutor()   // 找地點，不要排在慢的查詢後面
    private val osmIo = Executors.newCachedThreadPool()
    private var osmGen = 0
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

    // 中心：null＝我的位置，否則是搜尋／地圖選點／分享進來的地點名稱
    private var gpsLoc: Location? = null
    private var centerName: String? = null
    private lateinit var searchBox: EditText
    private lateinit var searchMsg: TextView
    private lateinit var suggestBox: LinearLayout
    private lateinit var centerText: TextView
    private lateinit var mapBtn: TextView
    private lateinit var mapHolder: LinearLayout
    private lateinit var selBar: TextView
    private var mapView: MapView? = null
    private var mapOpen = false
    private var lastOrdered: List<Pair<Place, Double>> = emptyList()
    private val ZOOMS = listOf(15.5, 14.5, 13.0, 12.0)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rect(fill: Int, r: Int, stroke: Int = 0) = Theme.roundRect(fill, r, stroke, 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Theme.palette(this)
        ui = Ui(this, p)
        Regions.load(this)
        buildUi()
        loadPicks()
        handleShare(intent)
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
        else if (centerName == null) showMessage("需要位置權限才能找你附近的店。\n也可以在上面直接打地址或地標。\n開啟權限：手機設定 → 應用程式 → 餐廳優惠日報 → 權限 → 位置。", "重新授權") {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 7
            )
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.pg)
            isFocusableInTouchMode = true // 開啟時不要自動跳出鍵盤
        }
        header = ui.header(1, "名單更新") { osmCache.clear(); loadPicks(true); if (phase != 0) startSearch(true) }
        root.addView(header.view)
        // 只固定標題和分頁，其餘（搜尋、按鈕、範圍類別、清單）全部一起往上滑
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(buildSearch(), LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(2) })
        filterBar = LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        content.addView(filterBar)
        panelHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(panelHolder, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(4)
        })
        condHolder = LinearLayout(this)
        content.addView(condHolder, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(28))
        }
        content.addView(listBox)
        scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        root.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        drawFilters()
        showMessage("正在取得你的位置…")
    }

    private fun togglePanel(which: Int) {
        openPanel = if (openPanel == which) 0 else which
        drawFilters()
    }

    /** 範圍只在第 1 步（設條件）出現；搜尋之後就收起來，畫面只留當下需要的東西 */
    private fun drawFilters() {
        filterBar.removeAllViews()
        panelHolder.removeAllViews()
        if (phase != 0) return
        filterBar.addView(ui.filterButton("範圍", "${(RADII[radiusIdx] / 1000).toInt()} 公里內", openPanel == 1) { togglePanel(1) },
            LinearLayout.LayoutParams(-1, -2))
        if (openPanel == 1) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rect(p.cd, dp(12), p.ln)
            }
            for (i in RADII.indices) {
                val sel = i == radiusIdx
                box.addView(TextView(this).apply {
                    text = "${(RADII[i] / 1000).toInt()} 公里內" + if (sel) "  ✓" else ""
                    textSize = 17f
                    setTextColor(if (sel) p.hi else p.tx)
                    if (sel) setTypeface(null, Typeface.BOLD)
                    setPadding(dp(16), dp(13), dp(16), dp(13))
                    setOnClickListener { radiusIdx = i; openPanel = 0; onConditionChanged() }
                })
            }
            panelHolder.addView(box)
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
        if (centerName == null) showMessage("正在取得你的位置…")
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
                centerName != null -> {}
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
        gpsLoc = l
        if (centerName != null) return
        loc = l
        osmCache.clear()
        drawCenter()
        onConditionChanged()
        io.execute {
            // 反查所在縣市，用來決定先處理哪些店（失敗也沒關係）
            val city = runCatching {
                @Suppress("DEPRECATION")
                val a: List<Address>? = Geocoder(this, Locale.TAIWAN).getFromLocation(l.latitude, l.longitude, 1)
                Regions.normalize(a?.firstOrNull()?.adminArea ?: "")
            }.getOrDefault("")
            handler.post { userCity = city; render() }
        }
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
    private fun cacheFile(): java.io.File? {
        val l = loc ?: return null
        return java.io.File(cacheDir, String.format(Locale.US, "osm_%.3f_%.3f_%d.json", l.latitude, l.longitude, RADII[radiusIdx].toInt()))
    }

    /** 條件（中心、範圍）一改，舊結果作廢，回到第 1 步；但 6 小時內查過同一處，就直接顯示上次的類別 */
    private fun onConditionChanged() {
        osmGen++
        osmLoading = false
        osmFailed = false
        errorKind = 0
        category = Cats.ALL
        shown = 20
        widenedFrom = -1
        handler.removeCallbacks(tick)
        phase = 0
        val f = cacheFile()
        val cached = runCatching {
            if (f != null && f.exists() && System.currentTimeMillis() - f.lastModified() < 6 * 3600e3) Osm.decode(f.readText()) else null
        }.getOrNull()
        if (cached != null) {
            osmCache[radiusIdx] = cached
            searchedAt = f!!.lastModified()
            if (merged(radiusIdx).isNotEmpty()) phase = 2
        }
        openPanel = 0
        drawFilters()
        updateMap(true)
        render()
    }

    private fun startSearch(force: Boolean = false) {
        if (loc == null) { showMessage("還沒有取得位置。\n請稍等，或在上面打地址或地標。"); return }
        if (force) { osmCache.remove(radiusIdx); cacheFile()?.let { runCatching { it.delete() } } }
        errorKind = 0
        category = Cats.ALL
        shown = 20
        hideKeyboard()
        if (osmCache.containsKey(radiusIdx)) { finishSearch(); return }
        phase = 1
        stage = 0
        startedAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000)
        drawFilters()
        loadOsm()
        render()
    }

    private fun cancelSearch() {
        osmGen++
        osmLoading = false
        handler.removeCallbacks(tick)
        phase = 0
        drawFilters()
        render()
    }

    private fun loadOsm() {
        val l = loc ?: return
        val idx = radiusIdx
        val file = cacheFile() ?: return
        osmLoading = true
        osmFailed = false
        val gen = ++osmGen
        osmIo.execute {
            val res = Osm.fetch(l.latitude, l.longitude, RADII[idx].toInt()) { st ->
                handler.post { if (gen == osmGen && st != stage) { stage = st; render() } }
            }
            if (res != null) runCatching { file.writeText(Osm.encode(res)) }
            handler.post {
                if (gen != osmGen) return@post   // 中心或範圍已經換了，舊結果不要用
                osmLoading = false
                if (res != null) osmCache[idx] = res else osmFailed = true
                finishSearch()
            }
        }
    }

    private fun finishSearch() {
        handler.removeCallbacks(tick)
        val idx = radiusIdx
        val all = merged(idx)
        searchedAt = System.currentTimeMillis()
        category = Cats.ALL
        shown = 20
        when {
            osmFailed && all.isEmpty() -> { phase = 0; errorKind = 1 }
            all.isEmpty() -> { phase = 0; errorKind = 2 }
            else -> { phase = 2; errorKind = if (osmFailed) 1 else 0 }
        }
        drawFilters()
        updateMap(true)
        render()
        scrollView.post { scrollView.scrollTo(0, 0) }
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

    // ---- 顯示：依步驟畫不同的畫面 ----
    private fun refresh() = render()

    private val itemLp get() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }

    private fun ago(t: Long): String {
        val m = ((System.currentTimeMillis() - t) / 60000).toInt()
        return when {
            m < 1 -> "剛剛"
            m < 60 -> "$m 分鐘前"
            else -> "${m / 60} 小時前"
        }
    }

    private fun centerLabel() = centerName ?: "我的位置"

    private fun render() {
        if (loc == null) return
        startGeocoding()
        condHolder.removeAllViews()
        listBox.removeAllViews()
        if (phase != 3 && phase != 2) { lastOrdered = emptyList(); if (mapOpen) updateMap(false) }
        when (phase) {
            0 -> renderStart()
            1 -> renderProgress()
            2 -> renderCategories()
            else -> renderList()
        }
    }

    private fun renderStart() {
        val idx = radiusIdx
        val km = (RADII[idx] / 1000).toInt()
        val v = when (errorKind) {
            1 -> ui.noticeCard("查詢逾時", "開放地圖的免費伺服器現在比較忙。\n這不代表附近沒有店，請再試一次。", "再試一次", { startSearch(true) })
            2 -> ui.noticeCard("$km 公里內找不到餐廳",
                "開放地圖在這個範圍沒有資料。" + if (idx < RADII.size - 1) "可以把範圍放大再找。" else "",
                if (idx < RADII.size - 1) "範圍擴大到 ${(RADII[idx + 1] / 1000).toInt()} 公里，再搜尋" else null,
                { radiusIdx = idx + 1; onConditionChanged(); startSearch() })
            else -> ui.startCard("準備搜尋附近餐廳", listOf("中心：${centerLabel()}", "範圍：$km 公里內"), "開始搜尋") { startSearch() }
        }
        listBox.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun renderProgress() {
        val sec = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
        val el = "已等 $sec 秒" + if (sec >= 15) "　伺服器比較忙，請再等一下" else ""
        listBox.addView(
            ui.progressCard("正在搜尋附近餐廳", listOf("連線伺服器", "查詢附近餐廳", "整理分類"), stage, el) { cancelSearch() },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun renderCategories() {
        val idx = radiusIdx
        val km = (RADII[idx] / 1000).toInt()
        val all = merged(idx)
        val counts = all.groupingBy { it.first.category }.eachCount()
        val good = all.count { it.first.tier() < 3 }
        lastOrdered = all.sortedBy { it.second }
        if (mapOpen) updateMap(false)
        listBox.addView(ui.infoStrip("${centerLabel()}　·　$km 公里內共 ${all.size} 家\n上次搜尋：${ago(searchedAt)}", "改條件") {
            phase = 0; errorKind = 0; drawFilters(); render()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(12) })
        if (errorKind == 1) listBox.addView(note("開放地圖查詢逾時，目前只有我們整理的名單。按「改條件」再搜尋一次。"), itemLp)
        listBox.addView(TextView(this).apply {
            text = "請選類別"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
            setPadding(dp(2), 0, 0, dp(8))
        })
        listBox.addView(ui.categoryTiles(counts, picks.map { it.category }, Triple(GOOD, good, true),
            onPick = { category = it; shown = 20; phase = 3; render(); scrollView.post { scrollView.scrollTo(0, 0) } },
            onReorder = { render() }))
    }

    private fun renderList() {
        val idx = radiusIdx
        val all = merged(idx)
        val withCat = if (category == GOOD) all.filter { it.first.tier() < 3 } else all.filter { it.first.category == category }
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
        lastOrdered = ordered
        if (mapOpen) updateMap(false)
        val tierCount = ordered.groupingBy { it.first.tier() }.eachCount()
        listBox.addView(ui.backBar(category, ordered.size, "家") {
            phase = 2; render(); scrollView.post { scrollView.scrollTo(0, 0) }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(12) })
        if (ordered.isEmpty()) {
            listBox.addView(ui.emptyState("這個類別目前沒有店", null, null))
            return
        }
        var lastTier = 0
        for ((pl, d) in ordered.take(shown)) {
            val t = pl.tier()
            if (t != lastTier) {
                lastTier = t
                listBox.addView(ui.sectionTitle(Theme.tierName(t), tierCount[t] ?: 0).apply { textSize = 17f })
            }
            listBox.addView(card(pl, d, t))
        }
        if (ordered.size > shown) {
            val more = minOf(20, ordered.size - shown)
            listBox.addView(ui.goldButton("顯示更多（再 $more 家）") { shown += 20; render() },
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
            setOnClickListener { openDetail(pl, dist) }
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

    // ---- 找地點：搜尋框、我的位置、地圖選點 ----
    private fun outlineBtn(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(p.tx)
        setTypeface(null, Typeface.BOLD)
        background = rect(p.cd, dp(10), p.ln)
        setPadding(0, dp(10), 0, dp(10))
        setOnClickListener { onClick() }
    }

    private fun buildSearch(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        searchBox = EditText(this).apply {
            hint = "打地址、地標、捷運站，按 Enter"
            textSize = 17f
            setTextColor(p.tx)
            setHintTextColor(p.sb)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = rect(p.cd, dp(10), p.ln)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            setOnEditorActionListener { _, actionId, ev ->
                val enter = actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                    (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)
                if (enter) doSearch(text.toString())
                enter
            }
            setOnFocusChangeListener { _, has ->
                background = if (has) Theme.roundRect(p.cd, dp(10), p.hi, dp(2)) else rect(p.cd, dp(10), p.ln)
                if (has && text.isEmpty()) showRecent() else if (!has) suggestBox.removeAllViews()
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    searchMsg.visibility = View.GONE
                    if (s.isNullOrEmpty() && hasFocus()) showRecent() else suggestBox.removeAllViews()
                }
            })
        }
        box.addView(searchBox, LinearLayout.LayoutParams(-1, -2))
        searchMsg = TextView(this).apply {
            textSize = 15f
            visibility = View.GONE
            setPadding(dp(4), dp(6), dp(4), 0)
        }
        box.addView(searchMsg)
        suggestBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(suggestBox, LinearLayout.LayoutParams(-1, -2))

        val row = LinearLayout(this)
        row.addView(outlineBtn("◎ 我的位置") { useMyLocation() }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        mapBtn = outlineBtn("地圖選點") { toggleMap() }
        row.addView(mapBtn, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        mapHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        box.addView(mapHolder, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val cbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rect(p.ch, dp(10))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        centerText = TextView(this).apply {
            textSize = 16f
            setTextColor(p.tx)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        cbar.addView(centerText, LinearLayout.LayoutParams(0, -2, 1f))
        cbar.addView(TextView(this).apply {
            text = "Google 地圖 ›"
            textSize = 15f
            setTextColor(p.hi)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(10), dp(2), 0, dp(2))
            setOnClickListener { openCenterInGoogle() }
        })
        box.addView(cbar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        drawCenter()
        return box
    }

    private fun drawCenter() {
        if (!::centerText.isInitialized) return
        centerText.text = "中心：" + (centerName ?: if (loc == null) "我的位置（定位中）" else "我的位置")
    }

    private fun openCenterInGoogle() {
        val l = loc ?: return
        val nm = android.net.Uri.encode(centerName ?: "我的位置")
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:${l.latitude},${l.longitude}?q=${l.latitude},${l.longitude}($nm)")))
        }.onFailure {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=${l.latitude},${l.longitude}")))
        }
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchBox.windowToken, 0)
        searchBox.clearFocus()
    }

    private fun msg(text: String, kind: Int) { // 0 說明、1 錯誤（紅）、2 成功（綠）
        searchMsg.text = text
        searchMsg.setTextColor(when (kind) { 1 -> p.rd; 2 -> p.vg; else -> p.sb })
        searchMsg.visibility = View.VISIBLE
    }

    private fun suggestRows(title: String, list: List<Spot>) {
        suggestBox.removeAllViews()
        if (list.isEmpty()) return
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(p.cd, dp(10), p.ln)
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(p.sb)
            setPadding(dp(14), dp(8), dp(14), dp(2))
        })
        list.forEachIndexed { i, s ->
            if (i > 0) card.addView(View(this).apply { setBackgroundColor(p.ln) }, LinearLayout.LayoutParams(-1, 1))
            card.addView(TextView(this).apply {
                text = s.label
                textSize = 17f
                setTextColor(p.tx)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setOnClickListener { applySpot(s) }
            })
        }
        suggestBox.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
    }

    private fun showRecent() = suggestRows("最近搜過的地點", GeoSearch.recent(this))

    private fun doSearch(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return
        suggestBox.removeAllViews()
        msg(if (GeoSearch.isLink(q)) "正在讀取 Google 地圖連結…" else "正在找「$q」…", 0)
        geoIo.execute {
            val list = if (GeoSearch.isLink(q)) listOfNotNull(GeoSearch.fromShared(this, q)) else GeoSearch.search(this, q)
            handler.post {
                when {
                    list.isEmpty() -> msg(
                        if (GeoSearch.isLink(q)) "讀不出這個連結的位置，請改打地點名稱。"
                        else "找不到「$q」。可以加上縣市再試，例如「台中 中正路」。", 1)
                    list.size == 1 -> applySpot(list[0])
                    else -> { searchMsg.visibility = View.GONE; suggestRows("找到 ${list.size} 個地點，請點一個：", list) }
                }
            }
        }
    }

    private fun applySpot(s: Spot, note: String? = null) {
        GeoSearch.remember(this, s)
        suggestBox.removeAllViews()
        searchBox.setText("")
        hideKeyboard()
        if (note != null) msg(note, 2) else searchMsg.visibility = View.GONE
        setCenter(s.lat, s.lng, s.name)
    }

    private fun setCenter(lat: Double, lng: Double, name: String?) {
        centerName = name
        loc = Location("pick").apply { latitude = lat; longitude = lng }
        osmCache.clear()
        drawCenter()
        onConditionChanged()
    }

    private fun useMyLocation() {
        searchMsg.visibility = View.GONE
        suggestBox.removeAllViews()
        hideKeyboard()
        centerName = null
        val g = gpsLoc
        if (g != null) setCenter(g.latitude, g.longitude, null)
        else if (hasPermission()) { loc = null; drawCenter(); locate() }
        else requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 7)
    }

    // ---- 從 Google 地圖「分享」過來 ----
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(src: Intent?) {
        val it = src ?: return
        if (it.action != Intent.ACTION_SEND) return
        val text = listOfNotNull(it.getStringExtra(Intent.EXTRA_SUBJECT), it.getStringExtra(Intent.EXTRA_TEXT))
            .joinToString("\n").trim()
        if (text.isEmpty()) return
        it.action = null
        msg("正在讀取 Google 地圖分享的地點…", 0)
        geoIo.execute {
            val s = GeoSearch.fromShared(this, text)
            handler.post {
                if (s == null) msg("讀不出這個分享的位置，請改在上面打地點名稱。", 1)
                else applySpot(s, "已收到 Google 地圖的地點：${s.name}")
            }
        }
    }

    // ---- 地圖（開放地圖，免費） ----
    private fun toggleMap() {
        mapOpen = !mapOpen
        mapBtn.text = if (mapOpen) "收起地圖" else "地圖選點"
        hideKeyboard()
        if (mapOpen && mapView == null) buildMap()
        mapHolder.visibility = if (mapOpen) View.VISIBLE else View.GONE
        if (mapOpen) { mapView?.onResume(); updateMap(true) } else mapView?.onPause()
    }

    private fun buildMap() {
        val cfg = Configuration.getInstance()
        cfg.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        cfg.userAgentValue = "app.deals/1.6"
        cfg.osmdroidBasePath = java.io.File(cacheDir, "osmdroid")
        cfg.osmdroidTileCache = java.io.File(cacheDir, "osmdroid/tiles")
        val mv = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            minZoomLevel = 7.0
            maxZoomLevel = 19.0
            // 地圖在整頁捲動裡面：手指在地圖上時，拖動是移動地圖，不是捲動頁面
            setOnTouchListener { v, ev ->
                v.parent.requestDisallowInterceptTouchEvent(ev.action != android.view.MotionEvent.ACTION_UP && ev.action != android.view.MotionEvent.ACTION_CANCEL)
                false
            }
        }
        mapView = mv
        val frame = android.widget.FrameLayout(this).apply { background = rect(p.ch, dp(10)) }
        frame.addView(mv, android.widget.FrameLayout.LayoutParams(-1, -1))
        frame.addView(TextView(this).apply {
            text = "金＝好餐廳　藍＝口碑不錯　灰＝其他"
            textSize = 14f
            setTextColor(p.tx)
            background = rect(p.cd, dp(6), p.ln)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }, android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { leftMargin = dp(6); bottomMargin = dp(6) })
        mapHolder.addView(frame, LinearLayout.LayoutParams(-1, dp(300)))
        selBar = TextView(this).apply {
            textSize = 17f
            setTextColor(p.tx)
            setTypeface(null, Typeface.BOLD)
            background = Theme.roundRect(p.cd, dp(10), p.hi, dp(2))
            setPadding(dp(14), dp(11), dp(14), dp(11))
            visibility = View.GONE
        }
        mapHolder.addView(selBar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        mapHolder.addView(TextView(this).apply {
            text = "按住地圖上任一點，改用那裡找。點圖釘看店名。"
            textSize = 15f
            setTextColor(p.sb)
            setPadding(dp(2), dp(6), dp(2), 0)
        })
    }

    private fun dot(color: Int, size: Int, stroke: Int) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(stroke), android.graphics.Color.WHITE)
        setSize(dp(size), dp(size))
    }

    private fun updateMap(recenter: Boolean) {
        val mv = mapView ?: return
        if (!mapOpen) return
        val l = loc ?: return
        val c = GeoPoint(l.latitude, l.longitude)
        mv.overlays.clear()
        mv.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(pt: GeoPoint?): Boolean = false
            override fun longPressHelper(pt: GeoPoint?): Boolean {
                if (pt == null) return false
                selBar.visibility = View.GONE
                searchMsg.visibility = View.GONE
                setCenter(pt.latitude, pt.longitude, "地圖上選的點")
                return true
            }
        }))
        mv.overlays.add(Polygon().apply {
            points = Polygon.pointsAsCircle(c, RADII[radiusIdx])
            fillPaint.color = 0x1A378ADD
            outlinePaint.color = android.graphics.Color.parseColor("#378ADD")
            outlinePaint.strokeWidth = dp(2).toFloat()
            setOnClickListener { _, _, _ -> false }
        })
        val blue = android.graphics.Color.parseColor("#378ADD")
        val gray = android.graphics.Color.parseColor("#888780")
        // 灰的先畫，金的最後畫，才會疊在最上面
        for ((pl, d) in lastOrdered.take(300).reversed()) {
            val t = pl.tier()
            mv.overlays.add(Marker(mv).apply {
                position = GeoPoint(pl.lat, pl.lng)
                icon = dot(when (t) { 1 -> Theme.GOLD; 2 -> blue; else -> gray }, if (t < 3) 20 else 16, 2)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    selBar.text = "${pl.name}　${Theme.dist(d)}　看詳細 ›"
                    selBar.setOnClickListener { openDetail(pl, d) }
                    selBar.visibility = View.VISIBLE
                    true
                }
            })
        }
        mv.overlays.add(Marker(mv).apply {
            position = c
            icon = dot(android.graphics.Color.parseColor("#E24B4A"), 24, 3)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { _, _ -> true }
        })
        if (recenter) {
            selBar.visibility = View.GONE
            mv.controller.setZoom(ZOOMS[radiusIdx])
            mv.controller.setCenter(c)
        }
        mv.invalidate()
    }

    private fun openDetail(pl: Place, dist: Double) {
        startActivity(Intent(this, PickActivity::class.java).putExtra("pick", pl.detailJson()).putExtra("dist", dist))
    }

    override fun onResume() {
        super.onResume()
        if (mapOpen) mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }
}
