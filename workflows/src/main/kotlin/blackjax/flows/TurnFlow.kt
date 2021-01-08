package blackjax.flows

import co.paralleluniverse.fibers.Suspendable
import blackjax.contracts.GameContract
import blackjax.states.BetState
import blackjax.states.TurnState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import java.lang.IllegalArgumentException


abstract class TurnFlow(val type: TurnState.Type) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Try to find last turn of current game
        val unconsumed = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val turns = serviceHub.vaultService.queryBy(TurnState::class.java, unconsumed).states

        if (turns.isNotEmpty()) {
            // Add the turn to the existing chain
            addToTurnChain(turns[0]);
        } else {
            // There is no turn yet, find the last bet
            val bets = serviceHub.vaultService.queryBy(BetState::class.java, unconsumed).states

            // Create the first turn of the chain
            if(bets.isNotEmpty()) {
                createTurnChain(bets[0])
            } else {
                throw IllegalArgumentException("All bets have not yet been placed or no game started.")
            }
        }
    }

    @Suspendable
    fun addToTurnChain(previousTurn: StateAndRef<TurnState>) {
        val notary = previousTurn.state.notary;
        val players = previousTurn.state.data.participants;
        val outputState =
                TurnState(previousTurn.state.data.lastBet, type, players, previousTurn)
        val command = Command(GameContract.Commands.AddTurnToChain(), ourIdentity.owningKey)

        val txBuilder = TransactionBuilder(notary = notary)
                .addInputState(previousTurn)
                .addOutputState(outputState, GameContract.ID)
                .addCommand(command)

        txBuilder.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        val sessions = (players - ourIdentity).map { initiateFlow(it) }
        subFlow(FinalityFlow(signedTx, sessions))
    }

    @Suspendable
    fun createTurnChain(lastBet: StateAndRef<BetState>) {
        val notary = lastBet.state.notary;
        val players = lastBet.state.data.participants;
        val outputState =
                TurnState(lastBet, type, players)
        val command = Command(GameContract.Commands.CreateTurnChain(), ourIdentity.owningKey)

        val txBuilder = TransactionBuilder(notary = notary)
                .addInputState(lastBet)
                .addOutputState(outputState, GameContract.ID)
                .addCommand(command)

        txBuilder.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        val sessions = (players - ourIdentity).map { initiateFlow(it) }
        subFlow(FinalityFlow(signedTx, sessions))
    }
}

@InitiatingFlow
@StartableByRPC
class DealFlow() : TurnFlow(TurnState.Type.DEAL)

@InitiatedBy(DealFlow::class)
class AcknowledgeDeal(private val initiator: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(initiator))
    }
}

@InitiatingFlow
@StartableByRPC
class HitFlow() : TurnFlow(TurnState.Type.HIT)

@InitiatedBy(HitFlow::class)
class AcknowledgeHit(private val initiator: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(initiator))
    }
}

@InitiatingFlow
@StartableByRPC
class StandFlow() : TurnFlow(TurnState.Type.STAND)

@InitiatedBy(StandFlow::class)
class AcknowledgeStand(private val initiator: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(initiator))
    }
}