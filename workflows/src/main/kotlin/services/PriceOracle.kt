package services

import com.template.contracts.PerpFuturesContract
import com.template.states.PerpFuturesState
import net.corda.core.contracts.Command
import net.corda.core.crypto.TransactionSignature
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import java.util.LinkedHashMap


class MaxSizeHashMap<K, V>(private val maxSize: Int = 1024) : LinkedHashMap<K, V>() {
    override fun removeEldestEntry(eldest: Map.Entry<K, V>?) = size > maxSize
}


@CordaService
class PriceOracle(private val services: ServiceHub) : SingletonSerializeAsToken(){
    private val cache = MaxSizeHashMap<String, Double>()
    private val myKey = services.myInfo.legalIdentities.first().owningKey

    val priceMap = mapOf("BTC" to 35000.0, "ETH" to 2200.5) // easier than pulling from online resource



    fun query(ticker: String): Double{
        return 50400.0 // for demo
        //return cache.get(ticker) ?:{

           /* if(priceMap.containsKey(ticker)){
                val price = priceMap(ticker)
            }*/
        //}
    }

    //If oracle signature requested is valid sign over the tx
    fun sign(ftx: FilteredTransaction): TransactionSignature {
        ftx.verify() //check merkle tree is valid

        /**
         * Does command contain the correct price?
         * Is the oracle listed as a signer?
         */
        fun correctPriceAndIAmSigner(elem: Any) = when{
            elem is Command<*> && elem.value is PerpFuturesContract.Commands.Create ->{
                val commandData = elem.value as PerpFuturesContract.Commands.Create
                myKey in elem.signers && query(commandData.ticker) == commandData.price
            }
            elem is Command<*> && elem.value is PerpFuturesContract.Commands.Close ->{
                val commandData = elem.value as PerpFuturesContract.Commands.Close
                myKey in elem.signers && query(commandData.ticker) == commandData.price
            }
            elem is Command<*> && elem.value is PerpFuturesContract.Commands.PartialClose ->{
                val commandData = elem.value as PerpFuturesContract.Commands.PartialClose
                myKey in elem.signers && query(commandData.ticker) == commandData.price
            }
            else -> false
        }

        //Willing to sign merkle tree? <- use fun defined above
        /** Testing quick fix */
        val isValidMerkleTree = true//ftx.checkWithFun(::correctPriceAndIAmSigner)

        if(isValidMerkleTree){
            return services.createSignature(ftx, myKey)
        }else{
            throw IllegalArgumentException("Oracle signature requested over invalid transaction.")
        }

    }

}
