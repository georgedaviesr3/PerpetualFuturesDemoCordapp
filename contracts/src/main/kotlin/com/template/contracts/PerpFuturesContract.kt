package com.template.contracts

import com.template.states.PerpFuturesState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.contracts.requireThat

class PerpFuturesContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.contracts.PerpFuturesContract"
    }

    /** A transaction is valid if the verify() function of the contract of all the transaction's input and output states
     * does not throw an exception.
     */
    override fun verify(tx: LedgerTransaction) {
        // Verification logic goes here.
        val command = tx.commands.requireSingleCommand<Commands>()

        when(command.value){
            is Commands.Create -> requireThat{
                val output = tx.outputsOfType<PerpFuturesState>().first()
                //Generic constraints
                "No inputs should be consumed when issuing." using (tx.inputs.isEmpty())
                "Only one output state should be created." using (tx.outputs.size == 1)
                "Contract issuer must not also be contract taker" using (output.exchange != output.taker)
                "All participants must sign" using (command.signers.containsAll(output.participants.map {it.owningKey}))
                "The output must be of type PerpFuturesState" using (output is PerpFuturesState)

                //Perp contract specific constraints.
                "Position size must be greater than 0" using (output.positionSize > 0)
                "Collateral must be positive" using (output.collateralPosted > 0)

                val leverage = (output.positionSize * output.initialAssetPrice) / output.collateralPosted
                "Cannot use leverage greater than 5x" using (leverage < 5)
            }

            is Commands.Close -> requireThat{
                val input = tx.inputsOfType<PerpFuturesState>().first()
                "There should be one input state." using (tx.inputs.size == 1)
                "There should be no output state." using (tx.outputs.isEmpty())
                "The input must be of type PerpFuturesState" using (input is PerpFuturesState)
                "All participants must sign" using (command.signers.containsAll(input.participants.map {it.owningKey}))
            }

            is Commands.PartialClose -> requireThat{
                val input = tx.inputsOfType<PerpFuturesState>().first()
                val output = tx.outputsOfType<PerpFuturesState>().first()

                //General
                "There should be one input state." using (tx.inputs.size == 1)
                "Only one output state should be created." using (tx.outputs.size == 1)
                "The input must be of type PerpFuturesState" using (input is PerpFuturesState)
                "The output must be of type PerpFuturesState" using (output is PerpFuturesState)
                "All participants must sign" using (command.signers.containsAll(output.participants.map {it.owningKey}))

                //Specific
                "Position size must decrease." using (input.positionSize > output.positionSize)
                "Position size must be greater than 0, if closing the contract please use the close flow" using (output.positionSize > 0)
                "Collateral must be greater than 0, if closing the contract please use the close flow" using (output.collateralPosted > 0)

                //Make sure follows from input
                "Ticker must stay the same" using (input.assetTicker == output.assetTicker)
                "Taker must stay the same" using (input.taker == output.taker)
                "Exchange must stay the same" using (input.exchange == output.exchange)
                "Initial asset price must stay the same" using (input.initialAssetPrice == output.initialAssetPrice)
            }

        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Create(val ticker: String, val price: Double, val fundingRate: Double) : Commands
        class Close(val ticker: String, val price: Double ) : Commands
        class PartialClose(val ticker: String, val price: Double): Commands
    }
}