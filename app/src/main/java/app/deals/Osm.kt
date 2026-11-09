package app.deals

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 附近的一家店：可能是我們整理過的名單（pick 不為 null），也可能只來自開放地圖。 */
class Place(
    val key: String, val name: String, val lat: Double, val lng: Double,
    val category: String, val address: String, val phone: String, val hours: String,
    val url: String, val pick: Pick?
) {
    fun tier(): Int = pick?.let { Theme.tier(it) } ?: 3

    /** 給詳細頁用的 JSON（開放地圖來的店沒有 raw，現場組一份） */
    fun detailJson(): String = pick?.raw ?: JSONObject()
        .put("id", key).put("name", name).put("category", category)
        .put("address", address).put("phone", phone).put("hours", hours)
        .put("lat", lat).put("lng", lng).put("url", url).put("osm", true)
        .toString()
}

/** 用免費的開放地圖（OpenStreetMap / Overpass）查你周圍的餐廳。沒有評價資料。 */
object Osm {
    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )
    private const val AMEN = "restaurant|fast_food|cafe|food_court|pub|bar"

    /** 成功回傳清單（可能是空的），兩個伺服器都失敗回傳 null。 */
    fun fetch(lat: Double, lng: Double, radius: Int): List<Place>? {
        val q = "[out:json][timeout:25][maxsize:100000000];(" +
            "node(around:$radius,$lat,$lng)[\"amenity\"~\"^($AMEN)\$\"][\"name\"];" +
            "way(around:$radius,$lat,$lng)[\"amenity\"~\"^($AMEN)\$\"][\"name\"];" +
            ");out center 600;"
        for (ep in ENDPOINTS) {
            val text = runCatching {
                val conn = URL(ep).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10000
                conn.readTimeout = 35000
                conn.doOutput = true
                conn.setRequestProperty("User-Agent", "RestaurantDeals/1.2")
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
                if (conn.responseCode != 200) error("http")
                conn.inputStream.bufferedReader().use { it.readText() }
            }.getOrNull() ?: continue
            // 伺服器忙碌或超時時，會回 200 但只有 remark 沒有資料：這不是「沒有餐廳」，要換一台再試
            val bad = runCatching {
                val remark = JSONObject(text).optString("remark", "")
                remark.contains("error", true) || remark.contains("timed out", true) || remark.contains("out of memory", true)
            }.getOrDefault(true)
            if (bad) continue
            val list = runCatching { parse(text) }.getOrNull() ?: continue
            return list
        }
        return null
    }

    private fun parse(text: String): List<Place> {
        val arr = JSONObject(text).getJSONArray("elements")
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: continue
            val name = tags.optString("name:zh", "").ifEmpty { tags.optString("name", "") }
            if (name.isEmpty()) continue
            val c = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.optDouble("lat") else c?.optDouble("lat") ?: continue
            val lng = if (e.has("lon")) e.optDouble("lon") else c?.optDouble("lon") ?: continue
            val addr = tags.optString("addr:full", "").ifEmpty {
                tags.optString("addr:city", "") + tags.optString("addr:district", "") +
                    tags.optString("addr:street", "") + tags.optString("addr:housenumber", "").let { if (it.isEmpty()) "" else it + "號" }
            }
            out.add(Place(
                key = "osm" + e.optString("type", "n") + e.optLong("id"),
                name = name, lat = lat, lng = lng,
                category = mapCat(tags, name),
                address = addr,
                phone = tags.optString("phone", "").ifEmpty { tags.optString("contact:phone", "") },
                hours = tags.optString("opening_hours", ""),
                url = tags.optString("website", "").ifEmpty { tags.optString("contact:website", "") },
                pick = null
            ))
        }
        return out
    }

    /** 把開放地圖的料理標籤對應到我們的 22 個類別；對不上的歸「其他」。 */
    fun mapCat(tags: JSONObject, name: String): String {
        val cuisine = tags.optString("cuisine", "").lowercase()
        val amenity = tags.optString("amenity", "")
        fun cu(vararg k: String) = k.any { cuisine.contains(it) }
        fun nm(vararg k: String) = k.any { name.contains(it) }
        return when {
            cu("hot_pot", "hotpot") || nm("火鍋", "鍋物", "涮涮", "麻辣鍋") -> "火鍋"
            cu("vegetarian", "vegan") || nm("素食", "蔬食", "素食") -> "素食"
            cu("teppanyaki") || nm("鐵板") -> "鐵板燒"
            cu("izakaya") || nm("居酒屋") -> "居酒屋燒烤"
            cu("buffet") || nm("自助餐", "吃到飽", "百匯") -> "自助餐"
            cu("steak") || nm("牛排", "排餐") -> "牛排西餐"
            cu("korean") || nm("韓式", "韓國", "韓食") -> "韓式料理"
            cu("bbq", "barbecue", "yakiniku", "grill") || nm("燒肉", "烤肉", "燒烤") -> "燒肉"
            cu("japanese", "sushi", "ramen", "udon", "tempura", "donburi") || nm("日式", "壽司", "拉麵", "丼", "日本料理") -> "日式料理"
            cu("thai", "vietnamese", "indian", "malaysian", "indonesian", "singapore") || nm("泰式", "越南", "印度", "東南亞", "南洋") -> "泰式東南亞"
            cu("italian", "french", "pizza", "pasta", "spanish", "german") || nm("義式", "義大利", "法式", "披薩", "pizza", "pasta") -> "義式法式"
            cu("seafood", "fish") || nm("海鮮", "海產") -> "海鮮"
            cu("bento", "lunch") || nm("便當") -> "便當"
            cu("taiwanese") || nm("台菜", "熱炒", "快炒", "台式") -> "台菜"
            cu("chinese", "cantonese", "dim_sum", "hong_kong", "sichuan") || nm("港式", "粵式", "中式", "川菜", "江浙") -> "中式港式"
            cu("noodle", "dumpling", "beef_noodle") || nm("麵", "水餃", "小吃", "滷肉飯", "餃", "粥", "飯") -> "小吃麵食"
            cu("bubble_tea", "dessert", "ice_cream", "juice", "tea") || nm("手搖", "飲料", "茶", "甜點", "冰", "豆花") -> "飲料甜點"
            cu("bakery") || nm("烘焙", "麵包", "吐司") -> "麵包烘焙"
            cu("brunch", "breakfast", "coffee_shop", "coffee") || nm("早午餐", "早餐", "咖啡", "coffee", "cafe") -> "早午餐咖啡"
            amenity == "pub" || amenity == "bar" -> "餐酒館"
            amenity == "cafe" -> "早午餐咖啡"
            cu("burger", "fast_food", "chicken", "sandwich") || amenity == "fast_food" || nm("麥當勞", "肯德基", "摩斯", "漢堡", "炸雞") -> "速食"
            nm("親子") -> "親子餐廳"
            nm("飯店", "酒店") -> "飯店餐廳"
            else -> "其他"
        }
    }

    fun encode(list: List<Place>): String {
        val arr = org.json.JSONArray()
        for (p in list) arr.put(JSONObject().put("k", p.key).put("n", p.name).put("la", p.lat).put("ln", p.lng)
            .put("c", p.category).put("a", p.address).put("p", p.phone).put("h", p.hours).put("u", p.url))
        return JSONObject().put("t", System.currentTimeMillis()).put("l", arr).toString()
    }

    /** 超過 6 小時的快取視為過期，回傳 null */
    fun decode(text: String): List<Place>? = runCatching {
        val o = JSONObject(text)
        if (System.currentTimeMillis() - o.optLong("t") > 6 * 3600_000L) return null
        val arr = o.getJSONArray("l")
        (0 until arr.length()).map {
            val e = arr.getJSONObject(it)
            Place(e.getString("k"), e.getString("n"), e.getDouble("la"), e.getDouble("ln"), e.optString("c", "其他"),
                e.optString("a"), e.optString("p"), e.optString("h"), e.optString("u"), null)
        }
    }.getOrNull()
}
