package perp_flows

import co.paralleluniverse.fibers.Suspendable

import com.template.contracts.PerpFuturesContract
import com.template.states.PerpFuturesState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FlowSession
import net.corda.core.identity.CordaX500Name
import java.util.function.Predicate


object PartialClosePositionFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        private val assetTicker: String,
        private val amountToClose: Double,
        private val exchange: Party
    ) : FlowLogic<SignedTransaction>() {

        companion object {
            object GETTING_STATE : ProgressTracker.Step("Getting Perp Futures State.")
            object GETTING_ORACLE_PRICE : ProgressTracker.Step("Getting asset price from the oracle.")
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction to consume contract.")
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
                GETTING_STATE,
                GETTING_ORACLE_PRICE,
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
            )
        }
        override val progressTracker = ProgressTracker()

        /**Initiating flow logic*/
        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Get current Futures State with vals for this asset ticker and taker(ourIdentity)
            progressTracker.currentStep = GETTING_STATE
            val perpFuturesStateRefToEnd = getPerpFuturesStateByIDAndTicker(serviceHub,ourIdentity, assetTicker)
            val inputState = perpFuturesStateRefToEnd.state.data
            val positionSize = inputState.positionSize - amountToClose

            // Get oracle price here
            progressTracker.currentStep = GETTING_ORACLE_PRICE
            val oracleName = CordaX500Name("Price Oracle", "London", "UK")
            val oracle = serviceHub.networkMapCache.getNodeByLegalName(oracleName)?.legalIdentities?.first()
                ?: throw IllegalArgumentException("Requested oracle: $oracleName not found on network")

            //Query the oracle
            val requestedPrice = subFlow(QueryPriceOracleFlow(oracle, assetTicker))

            // Compose the futures contract state.
            progressTracker.currentStep = GENERATING_TRANSACTION
            val command = Command(PerpFuturesContract.Commands.PartialClose(assetTicker, requestedPrice), listOf(ourIdentity.owningKey, exchange.owningKey
            ,oracle.owningKey))
            val output = PerpFuturesState(assetTicker, inputState.initialAssetPrice, positionSize, inputState.collateralPosted, ourIdentity, exchange)

            // Step 3. Create a new TransactionBuilder object.
            val builder = TransactionBuilder(notary)
                .addCommand(command)
                .addInputState(perpFuturesStateRefToEnd)
                .addOutputState(output)

            // Verify and sign it with our KeyPair.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            builder.verify(serviceHub)
            progressTracker.currentStep = SIGNING_TRANSACTION
            val ptx = serviceHub.signInitialTransaction(builder)

//Get oracle to sign
            val ftx = ptx.buildFilteredTransaction(Predicate{
                when (it){
                    is Command<*> -> oracle.owningKey in it.signers && it.value is PerpFuturesContract.Commands.PartialClose
                    else -> false
                }

            })
            val oracleSig = subFlow(SignPriceOracleFlow(oracle, ftx)) // can use build filtered tx to hide data
            val usAndOracleSigned = ptx.withAdditionalSignature(oracleSig)

            // Collect the other party's signature using the SignTransactionFlow.
            // will only ever be one counterparty (exchange)
            progressTracker.currentStep = CreatePositionFlow.Initiator.Companion.GATHERING_SIGS
            val exchangePartySession = initiateFlow(exchange)
            val fullySignedTx =
                subFlow(CollectSignaturesFlow(usAndOracleSigned, setOf(exchangePartySession),
                    CreatePositionFlow.Initiator.Companion.GATHERING_SIGS.childProgressTracker()
                ))


            // Finalise the tx
            progressTracker.currentStep = CreatePositionFlow.Initiator.Companion.FINALISING_TRANSACTION
            return subFlow(FinalityFlow(fullySignedTx, setOf(exchangePartySession),
                CreatePositionFlow.Initiator.Companion.FINALISING_TRANSACTION.childProgressTracker()
            ))
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