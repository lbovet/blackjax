package blackjax.states

import blackjax.contracts.GameContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@BelongsToContract(GameContract::class)
data class TurnState(val lastBet: StateAndRef<BetState>,
                     val type: Type,
                     override val participants: List<Party>,
                     val previous: StateAndRef<TurnState>? = null) : ContractState {

    val player: Party? = nextPlayer(previous, participants)

    companion object {

        fun turnChain(lastTurn: StateAndRef<TurnState>): List<StateAndRef<TurnState>> {
            var current = lastTurn
            val result = ArrayList<StateAndRef<TurnState>>()
            result.add(current)
            while (current.state.data.previous != null) {
                current = current.state.data.previous!!
                result.add(current)
            }
            return result.reversed()
        }

        fun hand(turn: StateAndRef<TurnState>, player: Party?): List<Card> =
                turnChain(turn)
                        .filter { it.state.data.player == player }
                        .filter { it.state.data.type != Type.STAND }
                        .map { Card.fromHash(it.ref.txhash) }

        fun nextPlayer(previousTurn: StateAndRef<TurnState>?, players: List<Party>): Party? {
            if (previousTurn == null) {
                return players[0]
            } else {
                val turn = previousTurn.state.data
                val cardCount = hand(previousTurn, turn.player).size
                return if (turn.type != Type.STAND &&
                        (cardCount > 2 || turn.player == null && cardCount == 2) &&
                        points(previousTurn, turn.player) < 21)
                    turn.player
                else
                    when (turn.player) {
                        null -> players[0] // dealer played, start again
                        players.last() -> if (cardCount == 2 && turn.type != Type.STAND)
                            players.first() // skip dealer
                        else
                            null // dealer card
                        else -> players[players.indexOf(turn.player) + 1]
                    }
            }
        }

        fun points(turn: StateAndRef<TurnState>, player: Party?): Int =
                points(hand(turn, player), player == null)

        fun points(cards: List<Card>, dealer: Boolean): Int {
            var sum = cards.map { it.points }.filter { it < 11 }.sum()
            val aceCount = cards.map { it.points }.filter { it == 11 }.count()
            for (i in 1..aceCount) {
                if (sum + 11 <= 21 - (aceCount - i) || dealer && sum >= 6) {
                    sum += 11
                } else {
                    sum++
                }
            }
            return sum
        }
    }

    @CordaSerializable
    enum class Type {
        DEAL,
        STAND,
        HIT
    }
}



