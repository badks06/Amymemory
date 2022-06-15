package fr.adksoft.mymemory

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus

class SplashScreen : AppCompatActivity() {

    lateinit var mAdView : AdView
    lateinit var mAdViewT : AdView

//    private val timeout: Long = 3000
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

    MobileAds.initialize(this) {}

    mAdView = findViewById(R.id.adView)
    mAdViewT = findViewById(R.id.adViewTop)
    val adRequest = AdRequest.Builder().build()
    mAdView.loadAd(adRequest)
    mAdViewT.loadAd(adRequest)

//        Handler(Looper.getMainLooper()).postDelayed({ }, 1000)
    val handler  = Handler(Looper.getMainLooper())
    handler.postDelayed({
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }, 5000)

    }
    }


// we used the postDelayed(Runnable, time) method
        // to send a message with a delayed time.
//        Handler().postDelayed({
//            val intent = Intent(this, MainActivity::class.java)
//            startActivity(intent)
//            finish()
//        }, 5000) // 5000 is the delayed time in milliseconds.

//        Handler(Looper.getMainLooper()).postDelayed({
//            // Your Code
//            val intent = Intent(this, MainActivity::class.java)
////            startActivity(intent)
////            finish()
//        },5000)




