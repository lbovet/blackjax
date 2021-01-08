package blackjax.contracts

import blackjax.states.BetState
import blackjax.states.Card
import blackjax.states.GameState
import blackjax.states.TurnState
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

// ************
// * Contract *
// ************
class GameContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "blackjax.contracts.GameContract"
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>();

        requireThat {
            when (command.value) {

                is Commands.NewGame -> {
                    "There should be one output state of type GameState." using (tx.outputs.size == 1)

                    val output = tx.outputsOfType<GameState>().single()
                    "The minimal bet must be positive." using (output.minimalBet > 0)
                    "The minimal bet must be even." using (output.minimalBet % 2 == 0)
                    "Each player appear only once" using
                            (output.participants.distinct().toList().size == output.participants.toList().size)
                }

                is Commands.Bet -> {
                    val bet = tx.outputsOfType<BetState>().single()
                    "The bet must be positive" using (bet.amount > 0)
                    "The bet must be even" using (bet.amount % 2 == 0)
                    "The bet player must be a signer" using (command.signers.contains(bet.player.owningKey));

                    when (command.value) {
                        is Commands.CreateBetChain -> {
                            val game = tx.inRefsOfType<GameState>().single()
                            "The bet must be for the game given as input" using
                                    (bet.game == game)
                            "Participants must remain the same during the whole game" using
                                    (bet.participants == game.state.data.participants)
                        }
                        is Commands.AddBetToChain -> {
                            val previousBet = tx.inputsOfType<BetState>().single()
                            "All bets of the chain must be for the same game" using (bet.game == previousBet.game)
                            "A player can place only one bet" using
                                    (previousBet.betChain.none { it.player == bet.player })
                            "Participants must remain the same during the whole game" using
                                    (bet.participants == previousBet.participants)
                        }
                    }
                }

                is Commands.Turn -> {
                    val turn = tx.outputsOfType<TurnState>().single()
                    "It must be the turn of the current player" using
                            (TurnState.nextPlayer(turn.previous, turn.participants) == turn.player)
                    "Only the current player can hit or stand" using
                            (turn.type == TurnState.Type.DEAL || turn.player == null ||
                                    command.signers.contains(turn.player.owningKey));

                    when(command.value) {
                        is Commands.CreateTurnChain -> {
                            val inputBet = tx.inRefsOfType<BetState>().single()
                            "The turn must reference the bet given as input" using (turn.lastBet == inputBet)
                            "All players must have placed a bet before dealing" using
                                    (inputBet.state.data.betChain.size == turn.participants.size)
                            "Participants must remain the same during the whole game" using
                                    (inputBet.state.data.participants == turn.participants)

                        }
                        is Commands.AddTurnToChain -> {
                            val inputTurn = tx.inRefsOfType<TurnState>().single()
                            val previousTurn = turn.previous
                            "The turn must follow the one given as input" using (previousTurn == inputTurn)
                            "Participants must remain the same during the whole game" using
                                    (inputTurn.state.data.participants == turn.participants)
                            "Only deal is allowed until all players have two cards" using
                                    (turn.player == null ||
                                            previousTurn != null && TurnState.hand(previousTurn, turn.player).size >= 2 ||
                                            turn.type == TurnState.Type.DEAL)
                            "Only deal is allowed until dealer has one card" using
                                    (turn.player != null ||
                                            previousTurn != null && TurnState.hand(previousTurn, turn.player).isNotEmpty() ||
                                            turn.type == TurnState.Type.DEAL)

                            if(previousTurn != null) {
                                "Only hit or stand is allowed when players have two cards or more" using
                                        (turn.player == null || TurnState.hand(previousTurn, turn.player).size < 2 ||
                                                turn.type != TurnState.Type.DEAL)
                                "Dealer cannot stand" using
                                        (turn.player != null || turn.type != TurnState.Type.STAND)
                                "Only hit is allowed for dealer when he has cards" using
                                        (turn.player != null || TurnState.hand(previousTurn, turn.player).isEmpty() ||
                                                turn.type == TurnState.Type.HIT)
                            }
                            "When the dealer has 17 or more, the game is finished" using
                                    (previousTurn == null || TurnState.points(previousTurn, null) < 17)
                        }
                    }
                }
            }
        }
    }

    interface Commands : CommandData {
        class NewGame : Commands

        interface Bet : Commands
        class CreateBetChain : Bet
        class AddBetToChain : Bet

        interface Turn : Commands
        class CreateTurnChain : Turn
        class AddTurnToChain : Turn
    }
}