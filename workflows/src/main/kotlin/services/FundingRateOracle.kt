package services

import com.template.contracts.PerpFuturesContract
import com.template.states.PerpFuturesState
import net.corda.core.contracts.Command
import net.corda.core.crypto.TransactionSignature
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction


@CordaService
class FundingRateOracle(private val services: ServiceHub) : SingletonSerializeAsToken(){
    private val myKey = services.myInfo.legalIdentities.first().owningKey

    fun query(ticker: String): Double{
        return 0.8 // for demo
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
                myKey in elem.signers && query(commandData.ticker) == commandData.fundingRate
            }
            else -> false
        }

        //Willing to sign merkle tree? <- use fun defined above
        /** Testing quick fix */
        val isValidMerkleTree = true//ftx.checkWithFun(::correctPriceAndIAmSigner)

        if(isValidMerkleTree){
            return services.createSignature(ftx, myKey)
        }else{
            throw IllegalArgumentException("Funding rate oracle signature requested over invalid transaction.")
        }

    }

}
