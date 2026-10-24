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
        val RADII = listOf(2000.0, 5000.0, 10000.0)
    }

    private lateinit var p: Pal
    private lateinit var status: TextView
    private lateinit var listBox: LinearLayout
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var picks: List<Pick> = emptyList()
    private var picksReady = false
    private var loc: Location? = null
    private var radiusIdx = 0
    private var checked = 0
    private var geocoding = false
    private var updated = ""
    private val coords = HashMap<String, DoubleArray>()
    private var userCity = ""

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rect(fill: Int, r: Int, stroke: Int = 0) = Theme.roundRect(fill, r, stroke, 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Theme.palette(this)
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
        content.addView(TextView(this).apply {
            text = "附近好餐廳"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
            setPadding(0, dp(12), 0, dp(4))
        })
        status = TextView(this).apply {
            textSize = 12f
            setTextColor(p.sb)
            setPadding(0, 0, 0, dp(10))
        }
        content.addView(status)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listBox)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(p.pg)
            addView(content)
        })
        status.text = "正在取得你的位置…"
    }

    private fun showMessage(msg: String, btn: String? = null, onClick: (() -> Unit)? = null) {
        status.text = ""
        listBox.removeAllViews()
        listBox.addView(TextView(this).apply {
            text = msg
            textSize = 14f
            setTextColor(p.sb)
            setLineSpacing(0f, 1.25f)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(40), dp(16), dp(16))
        })
        if (btn != null && onClick != null) listBox.addView(button(btn) { onClick() })
    }

    private fun button(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Theme.INK)
        setTypeface(null, Typeface.BOLD)
        background = rect(Theme.GOLD, dp(12))
        setPadding(0, dp(11), 0, dp(11))
        setOnClickListener { onClick() }
    }

    // ---- 精選名單 ----
    private fun loadPicks() {
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
            val text = remote ?: prefs.getString("picks", null)
            val parsed = ArrayList<Pick>()
            var upd = ""
            if (text != null) runCatching {
                val obj = JSONObject(text)
                upd = obj.optString("updated")
                val arr = obj.getJSONArray("picks")
                for (i in 0 until arr.length()) Theme.parsePick(arr.getJSONObject(i))?.let { parsed.add(it) }
            }
            ui.post {
                picks = parsed
                updated = upd
                picksReady = true
                refresh()
            }
        }
    }

    // ---- 定位 ----
    private fun locate() {
        status.text = "正在取得你的位置…"
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
        ui.postDelayed({ deliver(null) }, 15000)
    }

    private fun gotLocation(l: Location) {
        loc = l
        io.execute {
            // 反查所在縣市，用來決定先處理哪些店（失敗也沒關係）
            val city = runCatching {
                @Suppress("DEPRECATION")
                val a: List<Address>? = Geocoder(this, Locale.TAIWAN).getFromLocation(l.latitude, l.longitude, 1)
                Regions.normalize(a?.firstOrNull()?.adminArea ?: "")
            }.getOrDefault("")
            ui.post { userCity = city; refresh() }
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
                ui.post {
                    if (a != null) coords[k.id] = a
                    checked = cur
                    if (cur % 5 == 0 || cur == order.size) refresh()
                }
            }
            ui.post { geocoding = false; refresh() }
        }
    }

    // ---- 顯示 ----
    private fun refresh() {
        val l = loc ?: return
        if (!picksReady) { status.text = "正在載入精選名單…"; return }
        if (picks.isEmpty()) {
            showMessage("精選名單還在整理中，每天早上會自動更新。\n過一陣子再來看看。")
            return
        }
        startGeocoding()
        val radius = RADII[radiusIdx]
        val out = ArrayList<Pair<Pick, Double>>()
        for (k in picks) {
            val c = coordOf(k) ?: continue
            val r = FloatArray(1)
            Location.distanceBetween(l.latitude, l.longitude, c[0], c[1], r)
            if (r[0] <= radius) out.add(k to r[0].toDouble())
        }
        out.sortBy { it.second }
        val km = (radius / 1000).toInt()
        val progress = if (geocoding) "　· 正在比對地址（第一次較久）" else ""
        status.text = "$km 公里內 ${out.size} 家" + (if (updated.isNotEmpty()) "　· 名單 $updated 更新" else "") + progress
        listBox.removeAllViews()
        if (out.isEmpty() && !geocoding) {
            val noGeo = picks.none { coordOf(it) != null }
            listBox.addView(TextView(this).apply {
                text = if (noGeo) "這台手機無法把地址轉成位置，暫時無法計算距離。\n你仍可在首頁的地區選單找優惠。"
                else "$km 公里內目前沒有精選的店。\n偏遠地區的資料較少，名單會持續擴充。"
                textSize = 14f
                setTextColor(p.sb)
                setLineSpacing(0f, 1.25f)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(36), dp(16), dp(16))
            })
        }
        if (out.isEmpty() && geocoding) {
            listBox.addView(TextView(this).apply {
                text = "已檢查 $checked 家，請稍候…"
                textSize = 14f
                setTextColor(p.sb)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(36), dp(16), dp(16))
            })
        }
        for ((k, d) in out) listBox.addView(card(k, d))
        if (radiusIdx < RADII.size - 1 && !geocoding) {
            val next = (RADII[radiusIdx + 1] / 1000).toInt()
            listBox.addView(button("範圍擴大到 $next 公里") { radiusIdx++; refresh() },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }

    private fun chip(text: String, hot: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(if (hot) p.hi else p.sb)
        background = rect(p.ch, dp(9))
        setPadding(dp(8), dp(2), dp(8), dp(2))
    }

    private fun card(k: Pick, dist: Double): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(p.cd, dp(14), p.ln)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener {
                startActivity(Intent(this@NearbyActivity, PickActivity::class.java)
                    .putExtra("pick", k.raw).putExtra("dist", dist))
            }
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = k.name
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(TextView(this).apply {
            text = Theme.dist(dist)
            textSize = 12f
            setTextColor(p.hi)
            setTypeface(null, Typeface.BOLD)
        })
        box.addView(top)
        for (r in Theme.reasons(k)) {
            box.addView(TextView(this).apply {
                text = r
                textSize = 12.5f
                setTextColor(p.tx)
                setPadding(0, dp(4), 0, 0)
            })
        }
        val tags = LinearLayout(this)
        tags.addView(chip(k.category), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        if (k.district.isNotEmpty()) tags.addView(chip(k.city + " " + k.district))
        box.addView(tags, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        return wrap
    }
}
