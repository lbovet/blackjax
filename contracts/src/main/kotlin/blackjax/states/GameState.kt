package blackjax.states

import blackjax.contracts.GameContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.util.*

// *********
// * State *
// *********
@BelongsToContract(GameContract::class)
data class GameState(val gameId: UUID,
                     val minimalBet: Int,
                     override val participants: List<Party>) : ContractState
