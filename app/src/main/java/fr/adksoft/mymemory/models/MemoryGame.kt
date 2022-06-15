package fr.adksoft.mymemory.models

import android.content.SharedPreferences
import fr.adksoft.mymemory.utils.DEFAULT_ICONS
import fr.adksoft.mymemory.utils.LocalAPI

//public var scoreToShow: Int = 50
public var score: Int = 50


class MemoryGame(
    private val boardSize: BoardSize,
    private val customImages: List<String>?,


    ) {

    val cards: List<MemoryCard>
    var numPairsFound = 0

    private var numCardFlips = 0
    private var indexOfSingleSelectedCard: Int? = null
    init {
        if (customImages == null) {
            val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
            val randomizedImages = (chosenImages + chosenImages).shuffled()
            cards = randomizedImages.map { MemoryCard(it) }
        } else {
            val randomizedImages = (customImages + customImages).shuffled()
            cards = randomizedImages.map { MemoryCard(it.hashCode(), it) }
        }

    }
        fun flipcard(position: Int): Boolean {
            numCardFlips++
            val card: MemoryCard = cards[position]
            // Three cases:
            // 0 cards previously flipped over -> flip over the selected card + flip over the selected card
            // 1 card previously flipped over -> flip over the selected card + check if the images match
            // 2 cards previously flipped over -> restore cards + flip over the selected card
            var foundMatch = false
            if (indexOfSingleSelectedCard == null) {
                // 0 or 2 cards previously flipped over
                restoreCards()
                indexOfSingleSelectedCard = position
            } else {
                // exactly 1 card previously flipped over
                 foundMatch = checkForMatch(indexOfSingleSelectedCard!!, position)
                indexOfSingleSelectedCard = null
            }

            card.isFaceUp = !card.isFaceUp
            return foundMatch
    }
    private fun checkForMatch(position1: Int, position2: Int): Boolean {
        if (cards[position1].identifier != cards[position2].identifier) {
            if(boardSize == BoardSize.EASY) {
                score += -1
            } else if (boardSize == BoardSize.MEDIUM) {
                score += -2
            } else if (boardSize == BoardSize.HARD) {
                score += -3
            }
            return  false
        }
        cards[position1].isMatched = true
        cards[position2].isMatched = true
        numPairsFound++
        if (cards[position1].isMatched && cards[position2].isMatched) {
           if(boardSize == BoardSize.EASY) {
               score += 2
            } else if (boardSize == BoardSize.MEDIUM) {
               score += 5
            } else if (boardSize == BoardSize.HARD) {
               score += 10
            }
        }
        return  true
    }

    private fun restoreCards() {
        for (card: MemoryCard in cards) {
            if (!card.isMatched) {
                card.isFaceUp = false
            }
        }
    }

    fun haveWonGame(): Boolean {
        return  numPairsFound == boardSize.getNumPairs()
    }

    fun isCardFaceUp(position: Int): Boolean {
        return  cards[position].isFaceUp
    }

    fun getNumMoves(): Int {
       return numCardFlips / 2
    }
    fun haveLostGame(): Boolean {
        return score < 1
    }
}