package app.deals

import android.content.Context
import org.json.JSONObject

class City(val name: String, val key: String, val districts: List<String>)

class Group(val name: String, val cities: List<City>)

object Regions {
    var groups: List<Group> = emptyList()
        private set

    fun load(ctx: Context) {
        if (groups.isNotEmpty()) return
        groups = runCatching {
            val text = ctx.assets.open("regions.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(text).getJSONArray("groups")
            (0 until arr.length()).map { gi ->
                val g = arr.getJSONObject(gi)
                val cs = g.getJSONArray("cities")
                Group(g.getString("name"), (0 until cs.length()).map { ci ->
                    val c = cs.getJSONObject(ci)
                    val ds = c.getJSONArray("districts")
                    City(c.getString("name"), c.optString("key", c.getString("name")), (0 until ds.length()).map { ds.getString(it) })
                })
            }
        }.getOrDefault(emptyList())
    }

    fun districts(city: String): List<String> =
        groups.flatMap { it.cities }.firstOrNull { it.name == city }?.districts ?: emptyList()

    /** 台北 / 臺北市 / 台北市 都統一成「台北市」；全台維持「全台」 */
    fun normalize(raw: String): String {
        val s = raw.trim().replace('臺', '台')
        if (s.isEmpty() || s == "全台") return "全台"
        for (g in groups) for (c in g.cities) if (c.name == s || c.key == s) return c.name
        return s
    }
}
