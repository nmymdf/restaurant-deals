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
            textSize = 20f
            maxLines = 1
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
        })
        titleBox.addView(TextView(act).apply {
            text = "作者：ArchieKUO ${Theme.VERSION}"
            textSize = 14f
            maxLines = 1
            setTypeface(null, Typeface.ITALIC)
            setTextColor(Theme.GOLD)
        })
        titleRow.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(TextView(act).apply {
            text = "↻更新"
            textSize = 14f
            setTextColor(p.hi)
            setTypeface(null, Typeface.BOLD)
            background = rect(p.cd, dp(16), Theme.GOLD)
            setPadding(dp(9), dp(4), dp(9), dp(4))
            setOnClickListener { onUpdate() }
        })
        head.addView(titleRow)
        val cap = TextView(act).apply {
            textSize = 14f
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
                textSize = 18f
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
            text = if (tabIdx == 0) "30 天內有效的優惠，依到期日排序" else ""
            textSize = 14f
            setTextColor(p.sb)
            setPadding(0, dp(if (tabIdx == 0) 8 else 4), 0, dp(if (tabIdx == 0) 8 else 0))
            if (tabIdx != 0) visibility = View.GONE
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
                textSize = 15f
                setTextColor(p.tx)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(act).apply { text = if (open) "▴" else "▾"; textSize = 15f; setTextColor(p.sb) })
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
            row.addView(TextView(act).apply { text = "目前條件"; textSize = 14f; setTextColor(p.sb) },
                LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
            for ((label, remove) in items) {
                row.addView(TextView(act).apply {
                    text = "$label  ✕"
                    textSize = 14.5f
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
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
            setPadding(0, dp(if (items.isEmpty()) 0 else 8), 0, 0)
        })
        return box
    }

    fun sectionTitle(text: String, n: Int): TextView = TextView(act).apply {
        this.text = "$text　$n"
        textSize = 14f
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
            textSize = 16f
            setTextColor(p.sb)
            setLineSpacing(0f, 1.25f)
            gravity = Gravity.CENTER
        })
        if (btnLabel != null && onClick != null) box.addView(TextView(act).apply {
            text = btnLabel
            textSize = 16f
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
                textSize = 17f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.hi)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(act).apply { text = total.toString(); textSize = 15f; setTextColor(p.hi) })
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
            textSize = 14f
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

    // ================= 四步流程共用元件（優惠、附近長得一樣） =================

    val night: Boolean get() = (act.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val goldBg: Int get() = Color.parseColor(if (night) "#2E2711" else "#FFF4D0")
    val goldInk: Int get() = Color.parseColor(if (night) "#F2D675" else "#6B4E00")
    val blue: Int get() = Color.parseColor(if (night) "#7FA6F0" else "#3F6FC6")
    val blueBg: Int get() = Color.parseColor(if (night) "#1A2438" else "#EAF1FF")

    private fun card(pad: Int = 18): LinearLayout = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        background = rect(p.cd, dp(18), p.ln)
        setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    }

    fun goldButton(label: String, onClick: () -> Unit): TextView = TextView(act).apply {
        text = label
        textSize = 19f
        gravity = Gravity.CENTER
        setTextColor(Theme.INK)
        setTypeface(null, Typeface.BOLD)
        background = rect(Theme.GOLD, dp(14))
        setPadding(0, dp(15), 0, dp(15))
        setOnClickListener { onClick() }
    }

    fun outlineButton(label: String, onClick: () -> Unit): TextView = TextView(act).apply {
        text = label
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(p.tx)
        setTypeface(null, Typeface.BOLD)
        background = rect(p.pg, dp(12), p.ln)
        setPadding(0, dp(11), 0, dp(11))
        setOnClickListener { onClick() }
    }

    /** 第 1 步：條件都選好之後，按這張卡的大按鈕才開始搜尋 */
    fun startCard(title: String, summary: List<String>, button: String, onStart: () -> Unit): View {
        val c = card(20)
        c.addView(TextView(act).apply {
            text = title
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
        })
        for (s in summary) c.addView(TextView(act).apply {
            text = s
            textSize = 16f
            setTextColor(p.sb)
            setLineSpacing(0f, 1.2f)
            setPadding(0, dp(6), 0, 0)
        })
        c.addView(goldButton(button, onStart), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        return c
    }

    /** 第 2 步：階段進度。stageIdx 之前的打勾，stageIdx 是進行中。 */
    fun progressCard(title: String, stages: List<String>, stageIdx: Int, elapsed: String, onCancel: (() -> Unit)?): View {
        val c = card(20)
        c.addView(TextView(act).apply {
            text = title
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
        })
        val bar = LinearLayout(act)
        for (i in stages.indices) {
            bar.addView(View(act).apply {
                background = rect(if (i <= stageIdx) Theme.GOLD else p.ln, dp(4))
                alpha = if (i == stageIdx) 0.55f else 1f
            }, LinearLayout.LayoutParams(0, dp(8), 1f).apply { if (i > 0) leftMargin = dp(5) })
        }
        c.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16); bottomMargin = dp(12) })
        for (i in stages.indices) {
            val mark = if (i < stageIdx) "✓" else if (i == stageIdx) "●" else "○"
            c.addView(TextView(act).apply {
                text = "$mark　${stages[i]}" + if (i == stageIdx) "…" else ""
                textSize = 17f
                setTextColor(if (i < stageIdx) p.vg else if (i == stageIdx) p.tx else p.sb)
                if (i == stageIdx) setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(5), 0, dp(5))
            })
        }
        if (elapsed.isNotEmpty()) c.addView(TextView(act).apply {
            text = elapsed
            textSize = 15f
            setTextColor(p.sb)
            setPadding(0, dp(8), 0, 0)
        })
        if (onCancel != null) c.addView(outlineButton("取消", onCancel), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        return c
    }

    /** 失敗或沒結果時的提示卡（有按鈕可以重來） */
    fun noticeCard(title: String, body: String, button: String?, onClick: (() -> Unit)?, secondary: String? = null, onSecondary: (() -> Unit)? = null): View {
        val c = card(20)
        c.addView(TextView(act).apply {
            text = title
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
        })
        c.addView(TextView(act).apply {
            text = body
            textSize = 16f
            setTextColor(p.sb)
            setLineSpacing(0f, 1.25f)
            setPadding(0, dp(6), 0, 0)
        })
        if (button != null && onClick != null) c.addView(goldButton(button, onClick), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        if (secondary != null && onSecondary != null) c.addView(outlineButton(secondary, onSecondary), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return c
    }

    /** 結果畫面最上面的一行：條件摘要 ＋「改條件」 */
    fun infoStrip(text: String, action: String, onAction: () -> Unit): View = LinearLayout(act).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = rect(p.ch, dp(12))
        setPadding(dp(14), dp(10), dp(8), dp(10))
        addView(TextView(act).apply {
            this.text = text
            textSize = 15f
            setTextColor(p.tx)
            setLineSpacing(0f, 1.15f)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(act).apply {
            this.text = action
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.hi)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rect(p.cd, dp(10), Theme.GOLD)
            setOnClickListener { onAction() }
        })
    }

    /** 第 4 步最上面：‹ 回類別　＋　類別名稱與筆數 */
    fun backBar(title: String, count: Int, unit: String, onBack: () -> Unit): View = LinearLayout(act).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(act).apply {
            text = "‹ 類別"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.hi)
            background = rect(p.cd, dp(12), Theme.GOLD)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(12) })
        addView(TextView(act).apply {
            text = "$title　$count $unit"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.tx)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    /**
     * 第 3 步：類別格。special 是排在最前面的特別一格（好評推薦／全台連鎖），沒有就傳 null。
     * 筆數是 0 的類別不顯示；順序照使用者拖動後記住的。
     */
    fun categoryTiles(
        counts: Map<String, Int>, known: Collection<String>,
        special: Triple<String, Int, Boolean>?, onPick: (String) -> Unit, onReorder: () -> Unit
    ): View {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        if (special != null && special.second > 0) {
            val gold = special.third
            box.addView(LinearLayout(act).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = if (gold) Theme.roundRect(goldBg, dp(16), Theme.GOLD, dp(2)) else rect(p.cd, dp(16), p.ln)
                setPadding(dp(18), dp(18), dp(18), dp(18))
                setOnClickListener { onPick(special.first) }
                addView(TextView(act).apply {
                    text = special.first
                    textSize = 20f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (gold) goldInk else p.tx)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(act).apply {
                    text = "${special.second}　›"
                    textSize = 19f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (gold) goldInk else p.hi)
                })
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        val fullOrder = Cats.order(act, known + counts.keys, Cats.ALL)
        val names = fullOrder.filter { (counts[it] ?: 0) > 0 }
        var i = 0
        while (i < names.size) {
            val row = LinearLayout(act)
            for (j in 0..1) {
                val lp = LinearLayout.LayoutParams(0, -2, 1f).apply { if (j == 0) rightMargin = dp(10) }
                if (i + j < names.size) row.addView(cell(names[i + j], counts[names[i + j]] ?: 0, "", fullOrder, onPick, onReorder, true), lp)
                else row.addView(View(act), lp)
            }
            box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            i += 2
        }
        box.addView(TextView(act).apply {
            text = "長按類別可以拖動排序　·　恢復預設順序"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(p.hi)
            setPadding(0, dp(8), 0, dp(6))
            setOnClickListener { Cats.reset(act); onReorder() }
        })
        return box
    }

    private fun cell(
        n: String, count: Int, selected: String, fullOrder: List<String>,
        onPick: (String) -> Unit, onReorder: () -> Unit, big: Boolean = false
    ): View {
        val sel = n == selected
        val bg = GradientDrawable().apply {
            cornerRadius = dp(if (big) 14 else 10).toFloat()
            if (sel) { setColor(p.ch); setStroke(dp(1), Theme.GOLD) }
            else if (big) { setColor(p.cd); setStroke(dp(1), p.ln) }
            else { setColor(p.pg); setStroke(dp(1), p.ln) }
        }
        return LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = bg
            setPadding(dp(14), dp(if (big) 20 else 13), dp(14), dp(if (big) 20 else 13))
            addView(TextView(act).apply {
                text = if (sel) "$n ✓" else n
                textSize = if (big) 18f else 17f
                if (sel) setTypeface(null, Typeface.BOLD)
                setTextColor(if (sel) p.hi else p.tx)
                maxLines = 1
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(act).apply {
                text = count.toString()
                textSize = if (big) 18f else 15f
                if (big) setTypeface(null, Typeface.BOLD)
                setTextColor(if (big) p.hi else p.sb)
            })
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
