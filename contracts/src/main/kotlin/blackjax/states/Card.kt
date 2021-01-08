package blackjax.states

import net.corda.core.crypto.SecureHash
import kotlin.math.abs

data class Card(val index: Int) {

    companion object {
        const val symbols = "\uD83C\uDCA1\uD83C\uDCA2\uD83C\uDCA3\uD83C\uDCA4\uD83C\uDCA5\uD83C\uDCA6\uD83C\uDCA7\uD83C\uDCA8\uD83C\uDCA9\uD83C\uDCAA\uD83C\uDCAB\uD83C\uDCAD\uD83C\uDCAE\uD83C\uDCD1\uD83C\uDCD2\uD83C\uDCD3\uD83C\uDCD4\uD83C\uDCD5\uD83C\uDCD6\uD83C\uDCD7\uD83C\uDCD8\uD83C\uDCD9\uD83C\uDCDA\uD83C\uDCDB\uD83C\uDCDD\uD83C\uDCDE\uD83C\uDCB1\uD83C\uDCB2\uD83C\uDCB3\uD83C\uDCB4\uD83C\uDCB5\uD83C\uDCB6\uD83C\uDCB7\uD83C\uDCB8\uD83C\uDCB9\uD83C\uDCBA\uD83C\uDCBB\uD83C\uDCBD\uD83C\uDCBE\uD83C\uDCC1\uD83C\uDCC2\uD83C\uDCC3\uD83C\uDCC4\uD83C\uDCC5\uD83C\uDCC6\uD83C\uDCC7\uD83C\uDCC8\uD83C\uDCC9\uD83C\uDCCA\uD83C\uDCCB\uD83C\uDCCD\uD83C\uDCCE"
        fun fromHash(hash: SecureHash) = Card(abs(hash.hashCode()) % 52)
    }

    val symbol = symbols.substring(index*2, index*2+2)

    val value = index % 13 + 1

    val points = if(value == 1) 11 else if(value < 10) value else 10
}