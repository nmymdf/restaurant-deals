package app.deals

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.splash)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "餐廳優惠日報"
            },
            FrameLayout.LayoutParams(-1, -1)
        )
        val ver = TextView(this).apply {
            text = Theme.VERSION
            textSize = 13f
            letterSpacing = 0.15f
            setTypeface(null, Typeface.ITALIC)
            setTextColor(Theme.GOLD)
        }
        root.addView(
            ver,
            FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (resources.displayMetrics.heightPixels * 0.05f).toInt()
            }
        )
        setContentView(root)

        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 800)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
