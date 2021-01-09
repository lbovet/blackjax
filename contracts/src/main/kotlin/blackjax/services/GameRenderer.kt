package blackjax.services

import blackjax.states.BetState
import blackjax.states.Card
import blackjax.states.GameState
import blackjax.states.TurnState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.ServiceLifecycleEvent
import net.corda.core.serialization.SingletonSerializeAsToken
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.ansi
import rx.Observable
import rx.Observable.from
import rx.Observable.just
import java.util.concurrent.TimeUnit

@CordaService
class GameRenderer(private val serviceHub: AppServiceHub) : SingletonSerializeAsToken() {

    init {
        // Optional: Express interest in receiving lifecycle events
        serviceHub.register { processEvent(it) }
    }

    private fun processEvent(event: ServiceLifecycleEvent) {
        val ourIdentity = serviceHub.identityService.getAllIdentities().first().party

        // Lifecycle event handling code including full use of serviceHub
        when (event) {
            ServiceLifecycleEvent.STATE_MACHINE_STARTED -> {
                println(ansi().eraseScreen(Ansi.Erase.BACKWARD).bgBrightCyan().a("""
                    
                 [  BLACKJAX  ]
                    
                    flows:
                        Game [ minimalBet: <n> ]
                        Bet [ amount: <n> ]
                        Deal
                        Hit | Stand
                    
                """.trimIndent()).reset());
                print(">>> ")

                Observable.merge(
                        serviceHub.vaultService.trackBy(GameState::class.java)
                                .updates
                                .flatMap { from(it.produced).first() }
                                .map { it.state.data }
                                .delay(1, TimeUnit.SECONDS)
                                .map { render(it.participants, emptyList(), null, ourIdentity) },
                        serviceHub.vaultService.trackBy(BetState::class.java)
                                .updates
                                .flatMap { from(it.produced).first() }
                                .map { it.state.data }
                                .map { render(it.participants, it.betChain, null, ourIdentity) },
                        serviceHub.vaultService.trackBy(TurnState::class.java)
                                .updates
                                .flatMap { from(it.produced).first() }
                                .flatMap { just(it).concatWith(just(it).delaySubscription(1000, TimeUnit.MILLISECONDS)) }
                                .map {
                                    render(it.state.data.participants,
                                            it.state.data.lastBet.state.data.betChain,
                                            it,
                                            ourIdentity)
                                })
                        .switchMap {
                            just(it).concatWith(just(it)
                                    .delaySubscription(500, TimeUnit.MILLISECONDS)
                                    .repeat(2))
                        }
                        .doOnNext {
                            println(it)
                            print(">>> ")
                        }
                        .subscribe()
            }
        }
    }

    private fun render(players: List<Party>,
                       bets: List<BetState>,
                       lastTurn: StateAndRef<TurnState>? = null,
                       ourIdentity: Party): String =
            Table(players.map { player ->
                Player(player.name.organisation,
                        bets.filter { it.player == player }.map { it.amount }.firstOrNull(),
                        if (lastTurn != null) TurnState.hand(lastTurn, player) else emptyList(),
                        false,
                        (bets.size == players.size) && TurnState.nextPlayer(lastTurn, players) == player,
                        if (lastTurn != null) TurnState.points(lastTurn, null) else 0,
                        ourIdentity == player)
            } + Player("Dealer",
                    null,
                    if (lastTurn != null) TurnState.hand(lastTurn, null) else emptyList(),
                    true,
                    TurnState.nextPlayer(lastTurn, players) == null,
                    if (lastTurn != null) TurnState.points(lastTurn, null) else 0,
                    false)).render()


    data class Player(val name: String,
                      val bet: Int?,
                      val cards: List<Card>,
                      val dealer: Boolean,
                      val current: Boolean,
                      val dealerPoints: Int,
                      val myself: Boolean) {
        fun render(): String {
            val points = TurnState.points(cards, dealer)
            return ansi()
                    .fgBrightMagenta()
                    .a("  ")
                    .a(if (current && (cards.size >= 2 || dealer && cards.size == 1) &&
                            dealerPoints < 17) "\uD83E\uDC1E️" else " ")
                    .a("  ")
                    .a(if (dealer) ansi().fg(Ansi.Color.WHITE) else ansi().fgBrightCyan())
                    .a(if (myself) ansi().bold() else "")
                    .a(name.padEnd(10))
                    .boldOff()
                    .a("  ")
                    .fgBrightYellow()
                    .a((if (!dealer && bet != null) "$$bet" else "").padEnd(4))
                    .a("  ")
                    .a(when {
                        points == 0 -> " "
                        dealer && dealerPoints >= 17 -> ansi().fg(Ansi.Color.WHITE).a("\uD83D\uDE14")
                        dealer -> ansi().fg(Ansi.Color.WHITE).a("\uD83D\uDE11")
                        points > 21 -> ansi().fgRed().a("\uD83D\uDE20").toString()
                        points == 21 -> ansi().fgBrightGreen().a("\uD83D\uDE04").toString()
                        dealerPoints > 21 -> ansi().fgGreen().a("\uD83D\uDE42").toString()
                        points == dealerPoints -> ansi().fg(Ansi.Color.WHITE).a("\uD83D\uDE10").toString()
                        points > dealerPoints -> ansi().fgGreen().a("\uD83D\uDE42").toString()
                        else -> ansi().fgRed().a("\uD83D\uDE41").toString()
                    })
                    .a("  ")
                    .a(cards.map {
                        (if (it.index < 26) ansi().fgBright(Ansi.Color.WHITE) else ansi().fgBrightRed()).toString() + it.symbol
                    }.joinToString(" "))
                    .toString()
        }
    }

    data class Table(val players: List<Player>) {
        fun render(): String {
            return ansi()
                    .eraseScreen()
                    .a("\n")
                    .a(players.filter { !it.dealer }.map { it.render() }.joinToString("\n\n"))
                    .a("\n\n\n")
                    .a(players.filter { it.dealer }.map { it.render() }.joinToString(""))
                    .a("\n")
                    .reset()
                    .toString()
        }
    }
}

fun main(args: Array<String>) {
    println(GameRenderer.Table(listOf(
            GameRenderer.Player("Alice", null, listOf(Card(23), Card(23), Card(45)),
                    dealer = false, current = true, dealerPoints = 16, myself = true),
            GameRenderer.Player("Bob", 2, listOf(Card(1)),
                    dealer = false, current = true, dealerPoints = 3, myself = true),
            GameRenderer.Player("Dealer", 2, listOf(Card(3)),
                    dealer = true, current = true, dealerPoints = 4, myself = false))).render())
}
