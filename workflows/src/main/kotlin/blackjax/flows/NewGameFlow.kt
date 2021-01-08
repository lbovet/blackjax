package blackjax.flows

import co.paralleluniverse.fibers.Suspendable
import blackjax.contracts.GameContract
import blackjax.states.BetState
import blackjax.states.GameState
import blackjax.states.TurnState
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import java.util.*

@InitiatingFlow
@StartableByRPC
class NewGameFlow(val minimalBet: Int) : FlowLogic<Unit>() {

    constructor() : this(2)

    /** The flow logic is encapsulated within the call() method. */
    @Suspendable
    override fun call() {

        // Current game will be aborted
        val games = serviceHub.vaultService.queryBy<GameState>(
                QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)).states
        val bets = serviceHub.vaultService.queryBy<BetState>(
                QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)).states
        val turns = serviceHub.vaultService.queryBy<TurnState>(
                QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)).states

        // We retrieve the notary identity from the network map.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // Everyone at the table plays
        val players = serviceHub.networkMapCache.allNodes.map { it.legalIdentities[0] } - notary

        // We create the transaction components.
        val outputState = GameState(UUID.randomUUID(), minimalBet, players.toList())
        val command = Command(GameContract.Commands.NewGame(), ourIdentity.owningKey)

        // We create a transaction builder and add the components.
        val txBuilder = TransactionBuilder(notary = notary)

        games.forEach { txBuilder.addInputState(it) }
        bets.forEach { txBuilder.addInputState(it) }
        turns.forEach { txBuilder.addInputState(it) }

        txBuilder
                .addOutputState(outputState, GameContract.ID)
                .addCommand(command)

        // We verify and sign the transaction.
        txBuilder.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        // Initiate session with all other parties
        val sessions = (players - ourIdentity).map { initiateFlow(it) }

        // We finalise the transaction and then send it to the counterparties.
        subFlow(FinalityFlow(signedTx, sessions))
    }
}

@InitiatedBy(NewGameFlow::class)
class JoinGame(private val initiator: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(initiator))
    }
}
