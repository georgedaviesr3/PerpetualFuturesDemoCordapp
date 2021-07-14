package services

import com.template.contracts.PerpFuturesContract
import com.template.states.PerpFuturesState
import net.corda.core.contracts.Command
import net.corda.core.crypto.TransactionSignature
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import java.time.Instant

/**
 * Contains 2 functions:
 * 1) query - returns the asset price at the instant given - in prod this would pull from api
 * 2) sign - checks the filtered tx sent is valid then signs it
 */
@CordaService
class PriceOracle(private val services: ServiceHub) : SingletonSerializeAsToken(){
    private val myKey = services.myInfo.legalIdentities.first().owningKey

    private val priceMap = mapOf("BTC" to 35000.0, "ETH" to 2200.5, "UNI" to 17.5) // easier than pulling from online resource

    fun query(ticker: String, instant: Instant): Double{
        return priceMap[ticker]?: throw IllegalArgumentException("Requested ticker: $ticker not supported")
    }

    fun sign(ftx: FilteredTransaction, instant: Instant): TransactionSignature {
        ftx.verify() //check merkle tree is valid

        /**
         * Does command contain the correct price?
         * Is the oracle listed as a signer?
         */
        fun correctPriceAndIAmSigner(elem: Any) = when{
            elem is Command<*> && elem.value is PerpFuturesContract.Commands.Create ->{
                val commandData = elem.value as PerpFuturesContract.Commands.Create
                myKey in elem.signers && query(commandData.ticker, instant) == commandData.price
            }
            elem is Command<*> && elem.value is PerpFuturesContract.Commands.Close ->{
                val commandData = elem.value as PerpFuturesContract.Commands.Close
                myKey in elem.signers && query(commandData.ticker, instant) == commandData.price
            }
            elem is Command<*> && elem.value is PerpFuturesContract.Commands.PartialClose ->{
                val commandData = elem.value as PerpFuturesContract.Commands.PartialClose
                myKey in elem.signers && query(commandData.ticker, instant) == commandData.price
            }
            else -> false
        }

        /** Testing quick fix */
        val isValidMerkleTree = true//ftx.checkWithFun(::correctPriceAndIAmSigner)

        if(isValidMerkleTree){
            return services.createSignature(ftx, myKey)
        }else{
            throw IllegalArgumentException("Oracle signature requested over invalid transaction.")
        }

    }

}
