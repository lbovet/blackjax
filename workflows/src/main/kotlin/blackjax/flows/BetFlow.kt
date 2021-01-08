package blackjax.flows

import co.paralleluniverse.fibers.Suspendable
import blackjax.contracts.GameContract
import blackjax.states.BetState
import blackjax.states.GameState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import java.lang.IllegalArgumentException

@InitiatingFlow
@StartableByRPC
class BetFlow(val amount: Int) : FlowLogic<Unit>() {

    constructor() : this(2)

    @Suspendable
    override fun call() {

        // Try to find last bet of current game
        val unconsumed = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val bets = serviceHub.vaultService.queryBy(BetState::class.java, unconsumed).states

        if (bets.isNotEmpty()) {
            // Add the bet to the existing chain
            addToBetChain(bets[0]);
        } else {
            // There is no bet yet, find the game
            val games = serviceHub.vaultService.queryBy(GameState::class.java, unconsumed).states

            // Create the first bet of the chain
            if(games.isNotEmpty()) {
                createBetChain(games[0])
            } else {
                throw IllegalArgumentException("No game currently open. Please create one.")
            }
        }
    }

    @Suspendable
    fun addToBetChain(previousBet: StateAndRef<BetState>) {
        val notary = previousBet.state.notary;
        val players = previousBet.state.data.participants;
        val outputState =
                BetState(previousBet.state.data.game, ourIdentity, amount, players, previousBet)
        val command = Command(GameContract.Commands.AddBetToChain(), ourIdentity.owningKey)

        val txBuilder = TransactionBuilder(notary = notary)
                .addInputState(previousBet)
                .addOutputState(outputState, GameContract.ID)
                .addCommand(command)

        txBuilder.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        val sessions = (players - ourIdentity).map { initiateFlow(it) }
        subFlow(FinalityFlow(signedTx, sessions))
    }

    @Suspendable
    fun createBetChain(game: StateAndRef<GameState>) {
        val notary = game.state.notary;
        val players = game.state.data.participants;
        val outputState = BetState(game, ourIdentity, amount, players)
        val command = Command(GameContract.Commands.CreateBetChain(), ourIdentity.owningKey)

        val txBuilder = TransactionBuilder(notary = notary)
                .addInputState(game)
                .addOutputState(outputState, GameContract.ID)
                .addCommand(command)

        txBuilder.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        val sessions = (players - ourIdentity).map { initiateFlow(it) }
        subFlow(FinalityFlow(signedTx, sessions))
    }
}

@InitiatedBy(BetFlow::class)
class AcknowledgeBet(private val initiator: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(initiator))
    }
}