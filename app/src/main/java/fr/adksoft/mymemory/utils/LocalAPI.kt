package fr.adksoft.mymemory.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity

class LocalAPI(activity: AppCompatActivity) {
    private var sharedPreferences: SharedPreferences = activity.getPreferences(Context.MODE_PRIVATE)

    fun updateBestScore(score: Int) {
       val bestScore = getBestScore()
        if (score > bestScore) {
            sharedPreferences.edit().putInt(BEST_SCORE_KEY, score).apply()
        }
    }

    fun getBestScore() = sharedPreferences.getInt(BEST_SCORE_KEY, 0)

    companion object {
        const val BEST_SCORE_KEY = "Best_Score"
    }
}