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

class Pal(
    val pg: Int, val cd: Int, val tx: Int, val sb: Int, val ln: Int,
    val ch: Int, val rd: Int, val rdbg: Int, val hi: Int
)

object Theme {
    const val VERSION = "v0.5"
    val GOLD = Color.parseColor("#C9A227")
    val INK = Color.parseColor("#1F1B16")

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
                Color.parseColor("#FF8A80"), Color.parseColor("#3A1F1C"), Color.parseColor("#C9A227")
            )
        } else {
            Pal(
                Color.parseColor("#F7F4EC"), Color.parseColor("#FFFFFF"), Color.parseColor("#1F1B16"),
                Color.parseColor("#6B6558"), Color.parseColor("#E8E2D2"), Color.parseColor("#EFE9D6"),
                Color.parseColor("#B3261E"), Color.parseColor("#FBE9E7"), Color.parseColor("#A8860F")
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
}
