package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.*
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object ClosePositionFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        private val assetTicker: String,
        private val exchange: Party
    ) : FlowLogic<SignedTransaction>() {

        companion object {
            object GETTING_STATE : ProgressTracker.Step("Getting Perp Futures State.")
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction to consume contract.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")

            //ORACLE HERE??
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            //or maybe oracle here
            object FINALISING_TRANSACTION :
                ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
            )
        }
        override val progressTracker = ProgressTracker() // not sure what this does

        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Get current Futures State with vals for this asset ticker and taker(ourIdentity)
            progressTracker.currentStep = PartialClosePositionFlow.Initiator.Companion.GETTING_STATE
            val perpFuturesStateRefToEnd = getPerpFuturesStateByIDAndTicker(serviceHub,ourIdentity, assetTicker)
            val inputState = perpFuturesStateRefToEnd.state.data

            // Get oracle price
            // determine tokens to send each way
            val initialAssetPrice = inputState.initialAssetPrice

            //Could also pass this token data to exchange so they can verify?
            //checks if liquidated?

            // Build tx
            progressTracker.currentStep = GENERATING_TRANSACTION
            val command = Command(PerpFuturesContract.Commands.Close(), listOf(ourIdentity.owningKey, exchange.owningKey))
            val builder = TransactionBuilder(notary)
                .addCommand(command)
                .addInputState(perpFuturesStateRefToEnd)

            // Verify and sign it with our KeyPair.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            builder.verify(serviceHub)
            progressTracker.currentStep = SIGNING_TRANSACTION
            val ptx = serviceHub.signInitialTransaction(builder)

            // Collect the other party's signature using the SignTransactionFlow.
            // will only ever be one counterparty (binance)
            progressTracker.currentStep = GATHERING_SIGS
            val exchangePartySession = initiateFlow(exchange)
            val fullySignedTx = subFlow(CollectSignaturesFlow(ptx, setOf(exchangePartySession), GATHERING_SIGS.childProgressTracker()))

            // finalise the transaction
            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(fullySignedTx, setOf(exchangePartySession), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    /** Exchange Response Logic */
    @InitiatedBy(CreatePositionFlow.Initiator::class)
    class Acceptor(val exchangePartySession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(exchangePartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    // Verify tx meets exchange constraints

                }
            }

            //Sign and return tx
            val txId = subFlow(signTransactionFlow).id
            return subFlow(ReceiveFinalityFlow(exchangePartySession, expectedTxId = txId))
        }
    }
}
