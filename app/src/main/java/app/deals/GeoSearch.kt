package app.deals

import android.content.Context
import android.location.Geocoder
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** 一個可以當「中心」的地點 */
class Spot(val name: String, val label: String, val lat: Double, val lng: Double)

/** 用地址、地標、捷運站或 Google 地圖連結找出位置（全部免費）。 */
object GeoSearch {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) RestaurantDeals/1.6"

    fun isLink(s: String) = Regex("https?://", RegexOption.IGNORE_CASE).containsMatchIn(s)

    /** 打字搜尋：先用手機內建查詢（Google 資料），查不到再用開放地圖。最多 5 個候選。 */
    fun search(ctx: Context, q: String): List<Spot> {
        val out = ArrayList<Spot>()
        if (Geocoder.isPresent()) runCatching {
            @Suppress("DEPRECATION")
            val list = Geocoder(ctx, Locale.TAIWAN).getFromLocationName(q, 5, 21.8, 119.3, 25.4, 122.1)
            for (a in list ?: emptyList()) {
                val line = cleanAddr(a.getAddressLine(0) ?: "")
                val feat = a.featureName ?: ""
                val name = if (feat.isNotEmpty() && !feat.all { it.isDigit() || it == '-' } && !line.startsWith(feat)) feat else q
                val label = when {
                    line.isEmpty() -> name
                    line.contains(name) -> line
                    else -> "$name・$line"
                }
                out.add(Spot(name, label, a.latitude, a.longitude))
            }
        }
        if (out.isEmpty()) out.addAll(nominatim(q))
        return dedupe(out).take(5)
    }

    private fun dedupe(list: List<Spot>): List<Spot> {
        val res = ArrayList<Spot>()
        for (s in list) {
            if (res.any { it.label == s.label || (Math.abs(it.lat - s.lat) < 0.0005 && Math.abs(it.lng - s.lng) < 0.0005) }) continue
            res.add(s)
        }
        return res
    }

    private fun cleanAddr(s: String): String =
        s.replace(Regex("^\\d{3,6}"), "").replace("台灣", "").replace("臺灣", "").trim().trimStart(',', ' ')

    fun nominatim(q: String): List<Spot> = runCatching {
        val u = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&countrycodes=tw&accept-language=zh-TW&q=" +
            URLEncoder.encode(q, "UTF-8")
        val conn = URL(u).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", UA)
        val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        val out = ArrayList<Spot>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val disp = o.optString("display_name")
            val parts = disp.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val name = o.optString("name").ifEmpty { parts.firstOrNull() ?: q }
            val area = parts.filter { it.endsWith("區") || it.endsWith("市") || it.endsWith("縣") || it.endsWith("鄉") || it.endsWith("鎮") }
                .reversed().joinToString("")
            out.add(Spot(name, if (area.isNotEmpty()) "$name・$area" else name, o.getString("lat").toDouble(), o.getString("lon").toDouble()))
        }
        out
    }.getOrDefault(emptyList())

    // ---- Google 地圖連結 ----

    /** 從網址裡直接讀出座標（地點本身的座標優先，其次是畫面中心） */
    fun coordsIn(raw: String): DoubleArray? {
        val s = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        val pats = listOf(
            Regex("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)"),
            Regex("[?&](?:q|ll|query|center|destination|daddr)=(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)"),
            Regex("/maps/(?:search|place|dir)/[^@]*?(-?\\d{1,2}\\.\\d{3,})\\s*,\\s*\\+?(-?\\d{2,3}\\.\\d{3,})"),
            Regex("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)"),
            Regex("geo:(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
        )
        for (re in pats) {
            val m = re.find(s) ?: continue
            val la = m.groupValues[1].toDoubleOrNull() ?: continue
            val ln = m.groupValues[2].toDoubleOrNull() ?: continue
            if (la in -90.0..90.0 && ln in -180.0..180.0 && !(la == 0.0 && ln == 0.0)) return doubleArrayOf(la, ln)
        }
        return null
    }

    /** 從網址裡讀出地點名稱（/maps/place/名稱/ 或 q=名稱） */
    fun nameIn(raw: String): String? {
        val s = runCatching { URLDecoder.decode(raw.replace("+", "%20"), "UTF-8") }.getOrDefault(raw)
        Regex("/maps/place/([^/@?]+)").find(s)?.let { return it.groupValues[1].trim() }
        Regex("[?&](?:q|query)=([^&]+)").find(s)?.let {
            val v = it.groupValues[1].trim()
            if (!Regex("^-?\\d+\\.\\d+\\s*,").containsMatchIn(v)) return v
        }
        return null
    }

    /** 短網址（maps.app.goo.gl）要連網一層層解開 */
    private fun expand(url: String): List<String> {
        val seen = arrayListOf(url)
        var cur = url
        repeat(6) {
            val next = runCatching {
                val conn = URL(cur).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", UA)
                val code = conn.responseCode
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (code in 300..399 && loc != null) URL(URL(cur), loc).toString() else null
            }.getOrNull() ?: return seen
            seen.add(next)
            if (coordsIn(next) != null) return seen
            cur = next
        }
        return seen
    }

    /** 分享進來的文字或貼上的連結 → 地點。找不到回傳 null。 */
    fun fromShared(ctx: Context, text: String): Spot? {
        val url = Regex("https?://\\S+").find(text)?.value
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !isLink(it) }
        var name = lines.firstOrNull()
        if (url != null) {
            val urls = if (coordsIn(url) == null && nameIn(url) == null) expand(url) else listOf(url)
            for (u in urls.reversed()) {
                if (name == null) name = nameIn(u)
                val c = coordsIn(u) ?: continue
                val nm = name ?: nameIn(u) ?: "Google 地圖的地點"
                return Spot(nm, nm, c[0], c[1])
            }
            if (name == null) name = urls.firstNotNullOfOrNull { nameIn(it) }
        }
        // 沒有座標：用分享文字裡的地址或名稱再查一次
        val tries = (lines.drop(1) + listOfNotNull(name)).distinct()
        for (t in tries) {
            val r = search(ctx, t).firstOrNull() ?: continue
            val nm = name ?: r.name
            return Spot(nm, nm, r.lat, r.lng)
        }
        return null
    }

    // ---- 最近搜過的地點 ----
    fun recent(ctx: Context): List<Spot> = runCatching {
        val arr = JSONArray(ctx.getSharedPreferences("deals", Context.MODE_PRIVATE).getString("recent_places", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Spot(o.getString("n"), o.optString("l", o.getString("n")), o.getDouble("a"), o.getDouble("o"))
        }
    }.getOrDefault(emptyList())

    fun remember(ctx: Context, s: Spot) {
        val list = listOf(s) + recent(ctx).filter { it.name != s.name }
        val arr = JSONArray()
        for (x in list.take(5)) arr.put(JSONObject().put("n", x.name).put("l", x.label).put("a", x.lat).put("o", x.lng))
        ctx.getSharedPreferences("deals", Context.MODE_PRIVATE).edit().putString("recent_places", arr.toString()).apply()
    }
}
