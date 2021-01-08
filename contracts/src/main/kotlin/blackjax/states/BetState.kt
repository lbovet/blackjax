package blackjax.states

import blackjax.contracts.GameContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party

@BelongsToContract(GameContract::class)
data class BetState(val game: StateAndRef<GameState>,
                    val player: Party,
                    val amount: Int,
                    override val participants: List<Party>,
                    val previous: StateAndRef<BetState>? = null) : ContractState {

    val betChain: List<BetState> get() {
        var current = this
        val result = ArrayList<BetState>()
        result.add(current)
        while (current.previous != null) {
            current = current.previous!!.state.data
            result.add(current)
        }
        return result.reversed()
    }
}