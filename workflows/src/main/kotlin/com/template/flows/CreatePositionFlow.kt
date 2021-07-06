package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.PerpFuturesContract
import com.template.states.PerpFuturesState
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.identity.CordaX500Name


object CreatePositionFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        private val assetTicker: String,
        private val collateralPosted: Double,
        private val positionSize: Double,
        private val exchange: Party //Is the exchange ever going to change?
    ) : FlowLogic<SignedTransaction>() {

        companion object {
            object GETTING_ORACLE_PRICE : ProgressTracker.Step("Getting current asset price from the oracle")
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new PerpFuture Contract.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }
            object FINALISING_TRANSACTION :
                ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                GETTING_ORACLE_PRICE,
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
            )
        }
        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Get current price through oracle
            progressTracker.currentStep = GETTING_ORACLE_PRICE
            val currentAssetPrice = 40000.0 // will equal oracle value here

            //Get oracle node
            val oracleName = CordaX500Name("Price Oracle", "London", "UK")
            val oracle = serviceHub.networkMapCache.getNodeByLegalName(oracleName)?.legalIdentities?.first()
                ?: throw IllegalArgumentException("Requested oracle: $oracleName not found on network")

            //Query the oracle
            val requestedPrice = 500.0//subFlow(QueryPrice(oracle, assetTicker))

            // Compose the futures contract state and new transaction builder object
            progressTracker.currentStep = GENERATING_TRANSACTION
            val output = PerpFuturesState(assetTicker, requestedPrice, positionSize, collateralPosted, ourIdentity, exchange)

            //3 req signers - me, exchange, oracle
            val builder = TransactionBuilder(notary)
                .addCommand(PerpFuturesContract.Commands.Create(assetTicker,requestedPrice), listOf(ourIdentity.owningKey, exchange.owningKey, oracle.owningKey))
                .addOutputState(output)

            // Verify and sign it with our KeyPair.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            builder.verify(serviceHub)
            progressTracker.currentStep = SIGNING_TRANSACTION
            val ptx = serviceHub.signInitialTransaction(builder)

            // Collect the other party's signature using the SignTransactionFlow.
            // will only ever be one counterparty (exchange)
            progressTracker.currentStep = GATHERING_SIGS
            val exchangePartySession = initiateFlow(exchange)
            val partSignedTx =
                subFlow(CollectSignaturesFlow(ptx, setOf(exchangePartySession), GATHERING_SIGS.childProgressTracker()))

            //Get oracle to sign
            val oracleSig = subFlow(SignPrice(oracle, partSignedTx)) // can use build filtered tx to hide data
            val fullySignedTx = partSignedTx.withAdditionalSignature(oracleSig)


            // Finalise the tx
            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(fullySignedTx, setOf(exchangePartySession), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    /** Exchange Response Logic */
    @InitiatedBy(Initiator::class)
    class Acceptor(val exchangePartySession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(exchangePartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    // Verify tx meets exchange constraints
                    val output = stx.tx.outputsOfType<PerpFuturesState>().first()

                    "Must be a PerpFuture State" using (output is PerpFuturesState)
                    "Only BTC futures are accepted" using(output.assetTicker == "BTC")

                    val totalValue = output.initialAssetPrice * output.positionSize
                    "Total position size must be less than $1m" using (totalValue < 1000000)

                    val leverage = (output.initialAssetPrice * output.positionSize) / output.collateralPosted
                    "Leverage must be below 5x" using (leverage < 5)
                }
            }

            //Sign and return tx
            val txId = subFlow(signTransactionFlow).id
            return subFlow(ReceiveFinalityFlow(exchangePartySession, expectedTxId = txId))
        }
    }
}

