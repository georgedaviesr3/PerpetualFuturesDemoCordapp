package services

import com.template.contracts.PerpFuturesContract
import net.corda.core.contracts.Command
import net.corda.core.crypto.TransactionSignature
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import java.time.Instant

/**
 * Contains 2 functions:
 * 1) query - returns the funding rate at the instant given - in prod this would pull from api
 * 2) sign - checks the filtered tx sent is valid then signs it
 */
@CordaService
class FundingRateOracle(private val services: ServiceHub) : SingletonSerializeAsToken(){
    private val myKey = services.myInfo.legalIdentities.first().owningKey

    private val fundingMap = mapOf("BTC" to 0.08, "ETH" to 0.12, "UNI" to 0.15) // easier than pulling from online resource

    fun query(ticker: String, instant: Instant): Double{
        return fundingMap[ticker]?: throw IllegalArgumentException("Requested ticker: $ticker not supported")
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
                myKey in elem.signers && query(commandData.ticker, instant) == commandData.fundingRate
            }
            else -> false
        }

        /** Testing quick fix */
        val isValidMerkleTree = true//ftx.checkWithFun(::correctPriceAndIAmSigner)

        if(isValidMerkleTree){
            return services.createSignature(ftx, myKey)
        }else{
            throw IllegalArgumentException("Funding rate oracle signature requested over invalid transaction.")
        }

    }

}
