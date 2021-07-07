package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import services.PriceOracle

/**
 * Initiating flow to query the price of an asset
 * @param oracle asset price provider
 * @param ftx filtered tx exposing only command containing the asset ticker data - returns a digital sig over tx merkle tree
 */

@InitiatingFlow
class SignPriceOracleFlow(private val oracle: Party, private val ftx: FilteredTransaction): FlowLogic<TransactionSignature>() {
    companion object {
        object SIGNING : ProgressTracker.Step("Signing filtered transaction.")
        object SENDING : ProgressTracker.Step("Sending sign response.")
    }

    override val progressTracker = ProgressTracker(SIGNING, SENDING)

    @Suspendable override fun call(): TransactionSignature {
        val session = initiateFlow(oracle)

        progressTracker.currentStep = SIGNING
        val signedTx = try{
            serviceHub.cordaService(PriceOracle::class.java).sign(ftx)
        } catch (e: Exception){
            throw FlowException(e)
        }

        progressTracker.currentStep = SENDING
        return signedTx
        //return session.sendAndRecieve<TransactionSignature>(ftx).unwrap { it }
    }

}