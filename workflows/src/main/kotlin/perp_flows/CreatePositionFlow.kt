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
import net.corda.core.identity.CordaX500Name
import java.util.function.Predicate
import java.time.Instant
import java.time.Instant.now

object CreatePositionFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        private val assetTicker: String,
        private val collateralPosted: Double,
        private val positionSize: Double,
        private val exchange: Party,
        private val oracle: Party // just passed here for testing -> in prod the oracle node would be found in the network map
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
        override val progressTracker = tracker()

        /**Trader open position logic */
        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            //Get current time for oracle queries
            val instant: Instant = now()

           //Oracle is passed as a param for testing
           /* val oracleName = CordaX500Name("Price Oracle", "London", "GB")
           val oracle = serviceHub.networkMapCache.getNodeByLegalName(oracleName)?.legalIdentities?.first()
                ?: throw IllegalArgumentException("Requested oracle: $oracleName not found on network")*/

            // Get current price from oracle
            progressTracker.currentStep = GETTING_ORACLE_PRICE
            val requestedPrice = subFlow(QueryPriceOracleFlow(oracle, assetTicker, instant))

            //Get current funding rate from funding rate oracle
            val requestedFundingRate = subFlow(QueryFundingRateOracleFlow(oracle, assetTicker, instant))

            // Compose the futures contract state and new transaction builder object
            progressTracker.currentStep = GENERATING_TRANSACTION
            val output = PerpFuturesState(assetTicker, requestedPrice, positionSize, collateralPosted, ourIdentity, exchange)
            val builder = TransactionBuilder(notary)
                .addCommand(PerpFuturesContract.Commands.Create(assetTicker, requestedPrice, requestedFundingRate), listOf(ourIdentity.owningKey, exchange.owningKey))
                .addOutputState(output)

            // Verify and sign it with our KeyPair.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            builder.verify(serviceHub)
            progressTracker.currentStep = SIGNING_TRANSACTION
            val ptx = serviceHub.signInitialTransaction(builder)

            //Generate filtered tx
            val ftx = ptx.buildFilteredTransaction(Predicate{
                when (it){
                    is Command<*> -> oracle.owningKey in it.signers && it.value is PerpFuturesContract.Commands.Create
                    else -> false
                }

            })

            //Get price/ funding rate oracle to sign
            //Add sigs to tx
            val priceOracleSig = subFlow(SignPriceOracleFlow(oracle, ftx, instant))
            val fundingRateOracleSig = subFlow(SignFundingRateOracleFlow(oracle, ftx, instant))
            val usAndOracleSigned = ptx.withAdditionalSignature(priceOracleSig).withAdditionalSignature(fundingRateOracleSig)

            // Collect the exchanges signature
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
    @InitiatedBy(CreatePositionFlow.Initiator::class)
    class Acceptor(val exchangePartySession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(exchangePartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    // Verify tx meets exchange constraints
                    val output = stx.tx.outputsOfType<PerpFuturesState>().first()
                    "Must be a PerpFuture State" using (output is PerpFuturesState)

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

