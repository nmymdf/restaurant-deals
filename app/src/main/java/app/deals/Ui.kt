package app.deals

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 兩個分頁（優惠、附近）共用的標題列、類別卡片、目前條件。 */
object Cats {
    const val ALL = "全部類別"

    /** 預設：固定類別 → 資料裡新出現的類別 → 其他最後；再套用使用者拖動後記住的順序。 */
    fun order(ctx: Context, known: Collection<String>, selected: String): List<String> {
        val extra = known.filter { it !in Theme.PRESET_CATS && it != "其他" && it != ALL }.distinct().sorted()
        val base = ArrayList<String>()
        base.addAll(Theme.PRESET_CATS)
        base.addAll(extra)
        if (selected != ALL && selected !in base && selected != "其他") base.add(selected)
        base.add("其他")
        val saved = (ctx.getSharedPreferences("deals", Context.MODE_PRIVATE).getString("catorder", "") ?: "")
            .split("\n").filter { it in base }.distinct()
        return saved + base.filter { it !in saved }
    }

    fun save(ctx: Context, list: List<String>) {
        ctx.getSharedPreferences("deals", Context.MODE_PRIVATE).edit().putString("catorder", list.joinToString("\n")).apply()
    }

    fun reset(ctx: Context) {
        ctx.getSharedPreferences("deals", Context.MODE_PRIVATE).edit().remove("catorder").apply()
    }
}

class Header(val view: LinearLayout, val caption: TextView)

class Ui(private val act: AppCompatActivity, val p: Pal) {

    fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()
    fun rect(fill: Int, r: Int, stroke: Int = 0) = Theme.roundRect(fill, r, stroke, 1)

    /** 記錄最近一次成功更新的時間，顯示成「今天 14:05」 */
    fun markFetched(key: String) {
        act.getSharedPreferences("deals", Context.MODE_PRIVATE).edit().putLong(key, System.currentTimeMillis()).apply()
    }

    fun fetchedText(key: String): String {
        val t = act.getSharedPreferences("deals", Context.MODE_PRIVATE).getLong(key, 0L)
        if (t == 0L) return "尚未更新"
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = t }
        val same = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        val d = Date(t)
        return if (same) "今天 " + SimpleDateFormat("HH:mm", Locale.TAIWAN).format(d)
        else SimpleDateFormat("M/d HH:mm", Locale.TAIWAN).format(d)
    }

    /** tabIdx：0 優惠、1 附近 */
    fun header(tabIdx: Int, captionLabel: String, onUpdate: () -> Unit): Header {
        val head = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(0))
        }
        val titleRow = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(act).apply {
            setImageResource(R.drawable.ic_launcher_fg)
            background = rect(Theme.INK, dp(10))
            clipToOutline = true
        }
        titleRow.addView(logo, LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(10) })
        // 標題與作者放同一欄，不換行
        val titleBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(TextView(act).apply {
            text = "餐廳優惠日報"
            textSize = 18f
            maxLines = 1
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
        })
        titleBox.addView(TextView(act).apply {
            text = "作者：ArchieKUO ${Theme.VERSION}"
            textSize = 11f
            maxLines = 1
            setTypeface(null, Typeface.ITALIC)
            setTextColor(Theme.GOLD)
        })
        titleRow.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(TextView(act).apply {
            text = "↻更新"
            textSize = 10.5f
            setTextColor(p.hi)
            setTypeface(null, Typeface.BOLD)
            background = rect(p.cd, dp(16), Theme.GOLD)
            setPadding(dp(9), dp(4), dp(9), dp(4))
            setOnClickListener { onUpdate() }
        })
        head.addView(titleRow)
        val cap = TextView(act).apply {
            textSize = 11f
            setTextColor(p.sb)
            gravity = Gravity.END
            setPadding(0, dp(4), 0, 0)
        }
        cap.visibility = View.GONE
        head.addView(cap, LinearLayout.LayoutParams(-1, -2))

        val tabs = LinearLayout(act).apply { setPadding(0, dp(10), 0, 0) }
        val names = listOf("優惠", "附近")
        for (i in names.indices) {
            val sel = i == tabIdx
            val col = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                setOnClickListener { if (!sel) switchTo(i) }
            }
            col.addView(TextView(act).apply {
                text = names[i]
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                setTextColor(if (sel) p.tx else p.sb)
                if (sel) setTypeface(null, Typeface.BOLD)
            })
            col.addView(View(act).apply {
                setBackgroundColor(if (sel) Theme.GOLD else p.ln)
            }, LinearLayout.LayoutParams(-1, dp(if (sel) 3 else 1)))
            tabs.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        }
        head.addView(tabs)
        head.addView(TextView(act).apply {
            text = if (tabIdx == 0) "30 天內有效的優惠，依到期日排序" else "你所在位置周圍的餐廳，依距離排序"
            textSize = 12f
            setTextColor(p.sb)
            setPadding(0, dp(8), 0, dp(8))
        })
        cap.text = "$captionLabel　" + fetchedText(if (tabIdx == 0) "fetched_deals" else "fetched_picks")
        return Header(head, cap)
    }

    private fun switchTo(i: Int) {
        if (i == 0) {
            act.startActivity(Intent(act, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            if (act !is MainActivity) act.finish()
        } else {
            act.startActivity(Intent(act, NearbyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
    }

    fun filterButton(label: String, value: String, open: Boolean, onClick: () -> Unit): LinearLayout {
        val sp = android.text.SpannableString("$label　$value")
        sp.setSpan(android.text.style.ForegroundColorSpan(p.sb), 0, label.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(12), dp(10))
            background = rect(p.cd, dp(12), if (open) Theme.GOLD else p.ln)
            setOnClickListener { onClick() }
            addView(TextView(act).apply {
                text = sp
                textSize = 13f
                setTextColor(p.tx)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(act).apply { text = if (open) "▴" else "▾"; textSize = 13f; setTextColor(p.sb) })
        }
    }

    /** 目前條件（可逐項 ✕ 移除）＋ 結果筆數 */
    fun conditions(items: List<Pair<String, () -> Unit>>, countLine: String): View {
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(6))
        }
        if (items.isNotEmpty()) {
            val row = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(act).apply { text = "目前條件"; textSize = 12f; setTextColor(p.sb) },
                LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
            for ((label, remove) in items) {
                row.addView(TextView(act).apply {
                    text = "$label  ✕"
                    textSize = 12.5f
                    setTextColor(p.hi)
                    background = rect(p.ch, dp(14), Theme.GOLD)
                    setPadding(dp(11), dp(5), dp(11), dp(5))
                    setOnClickListener { remove() }
                }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
            }
            box.addView(HorizontalScrollView(act).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            })
        }
        box.addView(TextView(act).apply {
            text = countLine
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
            setPadding(0, dp(if (items.isEmpty()) 0 else 8), 0, 0)
        })
        return box
    }

    fun sectionTitle(text: String, n: Int): TextView = TextView(act).apply {
        this.text = "$text　$n"
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        setTextColor(p.hi)
        setPadding(dp(2), dp(8), 0, dp(6))
    }

    fun emptyState(msg: String, btnLabel: String?, onClick: (() -> Unit)?): View {
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(36), dp(16), dp(16))
        }
        box.addView(TextView(act).apply {
            text = msg
            textSize = 14f
            setTextColor(p.sb)
            setLineSpacing(0f, 1.25f)
            gravity = Gravity.CENTER
        })
        if (btnLabel != null && onClick != null) box.addView(TextView(act).apply {
            text = btnLabel
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Theme.INK)
            setTypeface(null, Typeface.BOLD)
            background = rect(Theme.GOLD, dp(12))
            setPadding(dp(20), dp(11), dp(20), dp(11))
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
        return box
    }

    /**
     * 類別卡片面板。counts 只含這次條件下有符合的類別；筆數是 0 的不顯示（目前選到的除外）。
     * known：資料裡出現過的所有類別，用來決定排序。
     */
    fun categoryPanel(
        counts: Map<String, Int>, known: Collection<String>, selected: String,
        onPick: (String) -> Unit, onReorder: () -> Unit
    ): View {
        val panel = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val all = selected == Cats.ALL
        val total = counts.values.sum()
        panel.addView(LinearLayout(act).apply {
            gravity = Gravity.CENTER
            background = rect(p.ch, dp(10), if (all) Theme.GOLD else 0)
            setPadding(0, dp(12), dp(12), dp(12))
            setOnClickListener { onPick(Cats.ALL) }
            addView(TextView(act).apply {
                text = if (all) "${Cats.ALL} ✓" else Cats.ALL
                textSize = 15f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.hi)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(act).apply { text = total.toString(); textSize = 13f; setTextColor(p.hi) })
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        val fullOrder = Cats.order(act, known + counts.keys, selected)
        val names = fullOrder.filter { (counts[it] ?: 0) > 0 || it == selected }
        var i = 0
        while (i < names.size) {
            val row = LinearLayout(act)
            for (j in 0..1) {
                val lp = LinearLayout.LayoutParams(0, -2, 1f).apply { if (j == 0) rightMargin = dp(6) }
                if (i + j < names.size) row.addView(cell(names[i + j], counts[names[i + j]] ?: 0, selected, fullOrder, onPick, onReorder), lp)
                else row.addView(View(act), lp)
            }
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            i += 2
        }
        panel.addView(TextView(act).apply {
            text = "長按卡片可以拖動排序　·　恢復預設順序"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(p.hi)
            setPadding(0, dp(10), 0, dp(6))
            setOnClickListener { Cats.reset(act); onReorder() }
        })
        return ScrollView(act).apply {
            background = rect(p.cd, dp(12), p.ln)
            addView(panel)
        }
    }

    private fun cell(
        n: String, count: Int, selected: String, fullOrder: List<String>,
        onPick: (String) -> Unit, onReorder: () -> Unit
    ): View {
        val sel = n == selected
        val bg = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            if (sel) { setColor(p.ch); setStroke(dp(1), Theme.GOLD) }
            else { setColor(p.pg); setStroke(dp(1), p.ln) }
        }
        return LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = bg
            setPadding(dp(12), dp(13), dp(12), dp(13))
            addView(TextView(act).apply {
                text = if (sel) "$n ✓" else n
                textSize = 15f
                if (sel) setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) p.hi else p.tx)
                maxLines = 1
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(act).apply { text = count.toString(); textSize = 13f; setTextColor(p.sb) })
            setOnClickListener { onPick(n) }
            setOnLongClickListener { v ->
                v.startDragAndDrop(ClipData.newPlainText("cat", n), View.DragShadowBuilder(v), null, 0)
                true
            }
            setOnDragListener { v, e ->
                when (e.action) {
                    DragEvent.ACTION_DRAG_ENTERED -> { v.alpha = 0.45f; true }
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { v.alpha = 1f; true }
                    DragEvent.ACTION_DROP -> {
                        v.alpha = 1f
                        val from = e.clipData.getItemAt(0).text.toString()
                        if (from != n) {
                            val l = ArrayList(fullOrder)
                            l.remove(from)
                            val idx = l.indexOf(n)
                            l.add(if (idx < 0) l.size else idx, from)
                            Cats.save(act, l)
                            v.post { onReorder() }
                        }
                        true
                    }
                    else -> true
                }
            }
        }
    }
}
