package app.deals

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import org.json.JSONObject
import java.time.LocalDate

data class Rating(val score: Double, val count: Int, val source: String, val date: String)

data class Deal(
    val brand: String,
    val offer: String,
    val region: String,
    val districts: List<String>,
    val category: String,
    val until: LocalDate,
    val added: LocalDate,
    val tags: List<String>,
    val note: String,
    val url: String,
    val address: String,
    val phone: String,
    val hours: String,
    val rating: Rating?,
    val raw: String
)

class Pick(
    val id: String,
    val name: String,
    val city: String,
    val district: String,
    val category: String,
    val address: String,
    val phone: String,
    val hours: String,
    val rating: Rating?,
    val views: Long,
    val viewsSource: String,
    val viewsDate: String,
    val award: String,
    val url: String,
    val lat: Double?,
    val lng: Double?,
    val veg: String,
    val recommend: String,
    val raw: String
)

class Pal(
    val pg: Int, val cd: Int, val tx: Int, val sb: Int, val ln: Int,
    val ch: Int, val rd: Int, val rdbg: Int, val hi: Int,
    val vg: Int, val vgbg: Int
)

object Theme {
    const val VERSION = "v1.1"
    val GOLD = Color.parseColor("#C9A227")
    val INK = Color.parseColor("#1F1B16")

    /** 類別選單固定顯示的項目（吃什麼）。素食和其他類別一樣。 */
    val PRESET_CATS = listOf(
        "自助餐", "牛排西餐", "燒肉", "火鍋", "日式料理", "中式港式", "台菜", "小吃麵食",
        "韓式料理", "泰式東南亞", "義式法式", "居酒屋燒烤", "海鮮", "速食", "早午餐咖啡",
        "飲料甜點", "麵包烘焙", "飯店餐廳", "鐵板燒", "便當", "親子餐廳", "餐酒館", "素食"
    )

    private val HIGHLIGHT = Regex(
        "[0-9,]+\\s*元|[0-9.]+\\s*折|[一二三四五六七八九十]+折|買[一二1]送[一二1]|[四五六七八九十]人同行[一二]人免費|免費|半價"
    )

    fun palette(ctx: Context): Pal {
        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) {
            Pal(
                Color.parseColor("#14120F"), Color.parseColor("#1F1B16"), Color.parseColor("#F2EEE4"),
                Color.parseColor("#A59E8C"), Color.parseColor("#332E25"), Color.parseColor("#2A2519"),
                Color.parseColor("#FF8A80"), Color.parseColor("#3A1F1C"), Color.parseColor("#C9A227"),
                Color.parseColor("#8FD19A"), Color.parseColor("#1E2E20")
            )
        } else {
            Pal(
                Color.parseColor("#F7F4EC"), Color.parseColor("#FFFFFF"), Color.parseColor("#1F1B16"),
                Color.parseColor("#6B6558"), Color.parseColor("#E8E2D2"), Color.parseColor("#EFE9D6"),
                Color.parseColor("#B3261E"), Color.parseColor("#FBE9E7"), Color.parseColor("#A8860F"),
                Color.parseColor("#2F6B33"), Color.parseColor("#E3F0E2")
            )
        }
    }

    fun roundRect(fill: Int, radiusPx: Int, strokeColor: Int = 0, strokePx: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radiusPx.toFloat()
            if (strokeColor != 0) setStroke(strokePx, strokeColor)
        }

    fun highlighted(s: String, color: Int): SpannableString {
        val sp = SpannableString(s)
        for (m in HIGHLIGHT.findAll(s)) {
            sp.setSpan(ForegroundColorSpan(color), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sp.setSpan(StyleSpan(Typeface.BOLD), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sp
    }

    fun parseDeal(d: JSONObject): Deal? {
        fun str(key: String): String = if (d.isNull(key)) "" else d.optString(key, "")
        fun list(key: String): List<String> {
            val a = d.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { a.getString(it) }
        }
        val until = runCatching { LocalDate.parse(str("until")) }.getOrNull() ?: return null
        val added = runCatching { LocalDate.parse(str("added")) }.getOrDefault(until)
        val r = d.optJSONObject("rating")
        val rating = if (r != null && r.optDouble("score", 0.0) > 0.0 && r.optInt("count", 0) > 0) {
            Rating(r.optDouble("score", 0.0), r.optInt("count", 0), r.optString("source", ""), r.optString("date", ""))
        } else null
        return Deal(
            brand = str("brand"),
            offer = str("offer"),
            region = str("region").ifEmpty { "全台" },
            districts = list("districts"),
            category = str("category").ifEmpty { "其他" },
            until = until,
            added = added,
            tags = list("tags"),
            note = str("note"),
            url = str("url"),
            address = str("address"),
            phone = str("phone"),
            hours = str("hours"),
            rating = rating,
            raw = d.toString()
        )
    }

    fun num(n: Long): String = String.format(java.util.Locale.US, "%,d", n)

    fun dist(m: Double): String =
        if (m < 1000) "${(Math.round(m / 10.0) * 10).toInt()} 公尺" else String.format(java.util.Locale.US, "%.1f 公里", m / 1000.0)

    fun parsePick(d: JSONObject): Pick? {
        fun str(key: String): String = if (d.isNull(key)) "" else d.optString(key, "")
        val name = str("name")
        if (name.isEmpty()) return null
        val r = d.optJSONObject("rating")
        val rating = if (r != null && r.optDouble("score", 0.0) > 0.0 && r.optInt("count", 0) > 0) {
            Rating(r.optDouble("score", 0.0), r.optInt("count", 0), r.optString("source", ""), r.optString("date", ""))
        } else null
        val v = d.optJSONObject("views")
        val lat = d.optDouble("lat", Double.NaN)
        val lng = d.optDouble("lng", Double.NaN)
        return Pick(
            id = str("id").ifEmpty { name },
            name = name,
            city = Regions.normalize(str("city")),
            district = str("district"),
            category = str("category").ifEmpty { "其他" },
            address = str("address"),
            phone = str("phone"),
            hours = str("hours"),
            rating = rating,
            views = v?.optLong("count", 0L) ?: 0L,
            viewsSource = v?.optString("source", "") ?: "",
            viewsDate = v?.optString("date", "") ?: "",
            award = str("award"),
            url = str("url"),
            lat = if (lat.isNaN()) null else lat,
            lng = if (lng.isNaN()) null else lng,
            veg = str("veg"),
            recommend = d.optJSONObject("recommend")?.optString("source", "") ?: str("recommend"),
            raw = d.toString()
        )
    }

    /** 1 好餐廳、2 口碑不錯、3 附近餐廳（尚無足夠評價資料） */
    fun tier(p: Pick): Int {
        val r = p.rating
        if (p.award.isNotEmpty() || p.views >= 10000) return 1
        if (r != null && ((r.score >= 4.0 && r.count >= 300) || (r.score >= 3.8 && r.count >= 3000))) return 1
        if (r != null && r.score >= 3.8 && r.count >= 100) return 2
        if (p.recommend.isNotEmpty()) return 2
        return 3
    }

    fun tierName(t: Int) = when (t) { 1 -> "好餐廳"; 2 -> "口碑不錯"; else -> "附近餐廳" }

    /** 這家店入選的理由（依資料欄位產生，每項都附來源與日期） */
    fun reasons(p: Pick): List<String> {
        val out = ArrayList<String>()
        p.rating?.let {
            out.add("評價 ${it.score} ★（${num(it.count.toLong())} 則）· ${it.source}" + if (it.date.isNotEmpty()) " ${it.date}" else "")
        }
        if (p.views > 0) {
            out.add("點閱約 ${num(p.views)}" + (if (p.viewsSource.isNotEmpty()) " · ${p.viewsSource}" else "") +
                if (p.viewsDate.isNotEmpty()) " ${p.viewsDate}" else "")
        }
        if (p.award.isNotEmpty()) out.add(p.award)
        if (p.recommend.isNotEmpty()) out.add("媒體推薦 · ${p.recommend}")
        if (out.isEmpty()) out.add("尚無評價資料")
        return out
    }
}
