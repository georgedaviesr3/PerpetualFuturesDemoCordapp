package perp_flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

object MintUSDFlow {
    @StartableByRPC
    class Initiator(val amount: Long, val recipient: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object VALIDATE_MINTER : ProgressTracker.Step("Validating party is the US Mint.")
            object FINALISING_TRANSACTION :
                ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                VALIDATE_MINTER,
                FINALISING_TRANSACTION
            )
        }
        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): SignedTransaction {

            val token = FiatCurrency.getInstance("USD")

            progressTracker.currentStep = VALIDATE_MINTER
            if (ourIdentity.name != CordaX500Name.parse("O=US Mint,L=Washington D.C.,C=US"))
                throw FlowException("Cannot mint USD unless you are the official US Mint")

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(IssueTokens(listOf(amount of token issuedBy ourIdentity heldBy recipient)))
        }
    }
}
