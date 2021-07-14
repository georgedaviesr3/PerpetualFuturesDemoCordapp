package perp_flows

import api.QueryPriceOracleFlow
import api.SignPriceOracleFlow
import api.getPerpFuturesStateByIDAndTicker
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
import java.time.Instant
import java.util.function.Predicate


object PartialClosePositionFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        private val assetTicker: String,
        private val amountToClose: Double,
        private val exchange: Party,
        private val oracle: Party
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
        override val progressTracker = tracker()

        /**Initiating flow logic*/
        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            //Get current time for oracle queries
            val instant: Instant = Instant.now()

            // Get current Futures State with vals for this asset ticker and taker(ourIdentity)
            progressTracker.currentStep = GETTING_STATE
            val perpFuturesStateRefToEnd = getPerpFuturesStateByIDAndTicker(serviceHub,ourIdentity, assetTicker)
            val inputState = perpFuturesStateRefToEnd.state.data
            val positionSize = inputState.positionSize - amountToClose

            //Oracle is passed as a param for testing
            /* val oracleName = CordaX500Name("Price Oracle", "London", "GB")
            val oracle = serviceHub.networkMapCache.getNodeByLegalName(oracleName)?.legalIdentities?.first()
                 ?: throw IllegalArgumentException("Requested oracle: $oracleName not found on network")*/

            // Get current price from oracle
            progressTracker.currentStep = GETTING_ORACLE_PRICE
            val requestedPrice = subFlow(QueryPriceOracleFlow(oracle, assetTicker, instant))

            // Compose the futures contract state.
            progressTracker.currentStep = GENERATING_TRANSACTION
            val command = Command(PerpFuturesContract.Commands.PartialClose(assetTicker, requestedPrice), listOf(ourIdentity.owningKey, exchange.owningKey))
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

            //Generate filtered tx
            val ftx = ptx.buildFilteredTransaction(Predicate{
                when (it){
                    is Command<*> -> oracle.owningKey in it.signers && it.value is PerpFuturesContract.Commands.PartialClose
                    else -> false
                }

            })
            //Get oracle to sign
            val oracleSig = subFlow(SignPriceOracleFlow(oracle, ftx, instant))
            val usAndOracleSigned = ptx.withAdditionalSignature(oracleSig)

            // Collect exchanges sig
            progressTracker.currentStep = GATHERING_SIGS
            val exchangePartySession = initiateFlow(exchange)
            val fullySignedTx =
                subFlow(CollectSignaturesFlow(usAndOracleSigned, setOf(exchangePartySession),
                    GATHERING_SIGS.childProgressTracker()
                ))


            // Finalise the tx
            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(fullySignedTx, setOf(exchangePartySession),
                FINALISING_TRANSACTION.childProgressTracker()
            ))
        }
    }

    /** Exchange Response Logic */
    @InitiatedBy(PartialClosePositionFlow.Initiator::class)
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