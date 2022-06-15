package fr.adksoft.mymemory

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso
import fr.adksoft.mymemory.models.*
import fr.adksoft.mymemory.utils.EXTRA_BOARD_SIZE
import fr.adksoft.mymemory.utils.EXTRA_GAME_NAME
import fr.adksoft.mymemory.utils.LocalAPI

class MainActivity : AppCompatActivity() {

    companion object {
        private  const val TAG = "MainActivity"
        private val CREATE_REQUEST_CODE = 248
    }
    private lateinit var localeApi: LocalAPI
    private final var TAG = "MainActivity"
    private val db = Firebase.firestore
    private var gameName: String? = null
    private var customGameImages: List<String>? = null
    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter: MemoryBoardAdapter
    private  lateinit var rvBoard: RecyclerView
    private  lateinit var clRoot: CoordinatorLayout
    private  lateinit var tvNumMoves: TextView
    private  lateinit var tvNumPairs: TextView
    /////////ADMOB
    private var mInterstitialAd: InterstitialAd? = null
    ////////
    private var boardSize: BoardSize = BoardSize.EASY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MobileAds.initialize(this) {}
        var adRequest = AdRequest.Builder().build()

        localeApi = LocalAPI(this)
        localeApi.updateBestScore(score)
        clRoot = findViewById(R.id.clRoot)
        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)
       setupBoard()
        // AdMob
        InterstitialAd.load(this,"ca-app-pub-5147572421770230/1815668029", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, adError?.message)
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d(TAG, "Ad was loaded.")
                mInterstitialAd = interstitialAd
            }
        })
        loadAd()
    }
    fun  loadAd() {
        mInterstitialAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad was dismissed.")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError?) {
                Log.d(TAG, "Ad failed to show.")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad showed fullscreen content.")
                mInterstitialAd = null
            }
        }
    }
    fun showAd() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.show(this)
        } else {
            Log.d("TAG", "The interstitial ad wasn't ready yet.")
        }
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return  true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mi_refresh -> {
                if (memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame()) {
                    showAlertDialog(getString(R.string.QuitGame), null, View.OnClickListener {
                        setupBoard()
                    })
                    } else {
                    setupBoard()
                }
                return true
            }
            R.id.mi_new_size -> {
                showNewSizeDialog()
                return true
            }
            R.id.mi_custom -> {
                showCreationDialog()
            return true
            }
            R.id.mi_download -> {
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
            if (customGameName == null) {
                Log.e(TAG, "Got null custom game from CreateActivity")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showDownloadDialog() {
        val boardDownloadView = LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog("${getString(R.string.FetchMemory)}", boardDownloadView, View.OnClickListener {
            // Grab the text of the game name that the user wants to download
            val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.etDownloadGame)
            val gameToDownload = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        })
    }

    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
          val userImageList: UserImageList? = document.toObject(UserImageList::class.java)
            if (userImageList?.images == null) {
                Log.e(TAG, getString(R.string.InvalidGame))
                Snackbar.make(clRoot," ${getString(R.string.NoGame)}, '$gameName'", Snackbar.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            val numCards: Int = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images
            for (imageUrl in userImageList.images) {
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(clRoot, "${getString(R.string.Playing)} '$customGameName'!", Snackbar.LENGTH_LONG).show()
            gameName = customGameName
            setupBoard()
        }.addOnFailureListener { exception ->
            Log.e(TAG, "${getString(R.string.ErreurRecupJeu)}", exception)
        }
    }

    private fun showCreationDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        showAlertDialog("${getString(R.string.Creatememory)}", boardSizeView, View.OnClickListener {
            // Set a new value for the board size
           val desiredBoardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            // Navigate to a new activity
            val intent = Intent(this, CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSize)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        })
    }

    private fun showNewSizeDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        when (boardSize) {
            BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rbHard)
        }
        showAlertDialog("${getString(R.string.ChooseSize)}", boardSizeView, View.OnClickListener {
            // Set a new value for the board size
            boardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            gameName = null
            customGameImages = null
            setupBoard()
        })
    }

    private fun showAlertDialog(title: String, view: View?, positiveClickListener: View.OnClickListener) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("${getString(R.string.Cancel)}", null)
            .setPositiveButton("OK") { _, _ ->
                positiveClickListener.onClick(null)
            }.show()
    }

    private fun setupBoard() {
            supportActionBar?.title = gameName ?: getString(R.string.app_name)
        when (boardSize) {
            BoardSize.EASY -> {
//                tvNumMoves.text = "Easy: 4 x 2"
                tvNumPairs.text = getString(R.string.Best_Score)
                if (score < 50)
                {
                    score = 50
                    tvNumPairs.text = getString(R.string.Best_Score) + localeApi.getBestScore()
                    tvNumMoves.text = getString(R.string.Score) +"${score}"
                } else {
                    score = 50
                    tvNumPairs.text = getString(R.string.Best_Score) + localeApi.getBestScore()
                    tvNumMoves.text = getString(R.string.Score) +"${score}"
                }
            }
            BoardSize.MEDIUM -> {
//                tvNumMoves.text = "Medium: 6 x 3"
                tvNumPairs.text = getString(R.string.Best_Score)
                if (score < 50)
                {
                    score = 50
                    tvNumMoves.text = getString(R.string.Score) +"${score}"
                    tvNumPairs.text = getString(R.string.Best_Score) + localeApi.getBestScore()
                } else {
                    score = 50
                    tvNumMoves.text = getString(R.string.Score) +"${score}"
                    tvNumPairs.text = getString(R.string.Best_Score) + localeApi.getBestScore()
                }
            }
            BoardSize.HARD -> {
//                tvNumMoves.text = "Hard: 6 x 4"
                tvNumPairs.text = getString(R.string.Best_Score)
                if (score < 50)
                {
                    score = 50
                    tvNumPairs.text = getString(R.string.Best_Score) + localeApi.getBestScore()
                    tvNumMoves.text = getString(R.string.Score) +"${score}"
                } else {
                    score = 50
                    tvNumPairs.text = getString(R.string.Best_Score) + localeApi.getBestScore()
                    tvNumMoves.text = getString(R.string.Score) +"${score}"
                }
            }
        }
        tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))
        memoryGame = MemoryGame(boardSize, customGameImages)
        adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object : MemoryBoardAdapter.CardClickListener {
            override fun onCardClicked(position: Int) {
                updateGameWithFlip(position)
            }

        })
        rvBoard.adapter = adapter
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    private fun updateGameWithFlip(position: Int) {
        // Error checking
        if (memoryGame.haveWonGame()) {
            // Alert the user of an invalid move
                Snackbar.make(clRoot, "${getString(R.string.AlreadyWon)}", Snackbar.LENGTH_LONG).show()
            return
        }
        if (memoryGame.isCardFaceUp(position)) {
            // Alert the user of an invalid move
            Snackbar.make(clRoot, "${getString(R.string.InvalidMove)}", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (memoryGame.haveLostGame()) {
            Snackbar.make(clRoot, "${getString(R.string.AlreadyLost)}", Snackbar.LENGTH_LONG).show()
            return
        }
        // Actually flip over the card
       if (memoryGame.flipcard(position)) {
           Log.i(TAG, "Found a match! Num pairs found: ${memoryGame.numPairsFound}")
           val color = ArgbEvaluator().evaluate(
               memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
               ContextCompat.getColor(this, R.color.color_progress_none),
               ContextCompat.getColor(this, R.color.color_progress_full)
           ) as Int
           tvNumPairs.setTextColor(color)
//           tvNumPairs.text = "Pairs: ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"
           if (memoryGame.haveWonGame()) {
               Snackbar.make(clRoot, "Won, score: $score", Snackbar.LENGTH_LONG).show()
               localeApi.updateBestScore(score)
               CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.YELLOW, Color.GREEN, Color.MAGENTA)).oneShot()
               loadAd()
               showAd()
           }
           if (memoryGame.haveLostGame()) {
               Snackbar.make(clRoot, "Lost, score: $score", Snackbar.LENGTH_LONG).show()
//               CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.YELLOW, Color.GREEN, Color.MAGENTA)).oneShot()
               loadAd()
               showAd()
           }
       }
        tvNumMoves.text = getString(R.string.Score) + "${score}"
        tvNumPairs.text = getString(R.string.Best_Score) + localeApi.getBestScore()
        adapter.notifyDataSetChanged()
        // memoryGame.getNumMoves()
    }
    }










