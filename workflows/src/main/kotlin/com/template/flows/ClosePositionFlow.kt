package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.*
import com.template.states.*
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.node.services.queryBy

object ClosePositionFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        private val perpFuturesID: UniqueIdentifier,
        private val exchange: Party,
        private val positionSize: Double,
        private val assetTicker: String
        //private val stateReference
    ) : FlowLogic<SignedTransaction>() {

        /**Extra vals*/
        private val initialAssetPrice = 40000.0
        private val taker = ourIdentity
        private val currentAssetPrice = 42000.0 // ORACLE HERE

        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
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

        /**Initiating flow logic*/
        @Suspendable
        override fun call(): SignedTransaction {
            // Step 1. Get a reference to the notary service on our network and our key pair.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]


            // Get futures state
            progressTracker.currentStep = GETTING_STATE
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                null, listOf(perpFuturesID),
                Vault.StateStatus.UNCONSUMED, null
            )

            val perpFuturesStateRefToEnd = serviceHub.vaultService.queryBy<PerpFuturesState>(queryCriteria)
                .states.singleOrNull() ?: throw FlowException("Futures Perp with id $perpFuturesID not found.")

            // Step 2. Compose the futures contract state.
            progressTracker.currentStep = GENERATING_TRANSACTION
            val command = Command(PerpFuturesContract.Commands.Close(), listOf(taker.owningKey, exchange.owningKey))

            // Step 3. Create a new TransactionBuilder object.
            val builder = TransactionBuilder(notary)
                .addCommand(command)
                .addInputState(perpFuturesStateRefToEnd)

            // Step 4. Verify and sign it with our KeyPair.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            builder.verify(serviceHub)

            //Step 5. Sign transaction
            progressTracker.currentStep = SIGNING_TRANSACTION
            val ptx = serviceHub.signInitialTransaction(builder)

            // Step 6. Collect the other party's signature using the SignTransactionFlow.
            //         will only ever be one counterparty
            progressTracker.currentStep = GATHERING_SIGS
            val exchangePartySession = initiateFlow(exchange)
            val fullySignedTx =
                subFlow(CollectSignaturesFlow(ptx, setOf(exchangePartySession), GATHERING_SIGS.childProgressTracker()))


            // Step 7. Assuming no exceptions, we can now finalise the transaction
            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(
                FinalityFlow(
                    fullySignedTx,
                    setOf(exchangePartySession),
                    FINALISING_TRANSACTION.childProgressTracker()
                )
            )
        }
    }

    /** Exchange response logic */
    @InitiatedBy(Initiator::class)
    class Acceptor(val exchangePartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        //Initiated flow now signs tx
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(exchangePartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    //Addition checks
                    "Must be no output state" using (stx.tx.outputs.isEmpty())
                }
            }
            val txId = subFlow(signTransactionFlow).id
            return subFlow(ReceiveFinalityFlow(exchangePartySession, expectedTxId = txId))
        }
    }
}
