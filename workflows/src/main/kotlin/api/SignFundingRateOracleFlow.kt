package api

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.ProgressTracker
import services.FundingRateOracle
import java.time.Instant

/**
 * Initiating flow to query the funding rate of an asset
 * @param oracle asset funding rate provider
 * @param ftx filtered tx exposing only command containing the asset ticker data - returns a digital sig over tx merkle tree
 * @param instant the moment in time the funding rate was calculated at
 */

@InitiatingFlow
class SignFundingRateOracleFlow(private val oracle: Party, private val ftx: FilteredTransaction, private val instant: Instant): FlowLogic<TransactionSignature>() {
    companion object {
        object SIGNING : ProgressTracker.Step("Signing filtered transaction.")
        object SENDING : ProgressTracker.Step("Sending sign response.")
    }

    override val progressTracker = ProgressTracker(SIGNING, SENDING)

    @Suspendable override fun call(): TransactionSignature {
        val session = initiateFlow(oracle)

        progressTracker.currentStep = SIGNING
        val signedTx = try{
            serviceHub.cordaService(FundingRateOracle::class.java).sign(ftx, instant)
        } catch (e: Exception){
            throw FlowException(e)
        }

        progressTracker.currentStep = SENDING
        return signedTx
    }

}