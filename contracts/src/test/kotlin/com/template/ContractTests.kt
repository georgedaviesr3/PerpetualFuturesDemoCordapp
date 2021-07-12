package com.template

import com.template.contracts.PerpFuturesContract
import com.template.contracts.PerpFuturesContract.Companion.ID
import com.template.states.PerpFuturesState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class ContractTests {
    private val ledgerServices: MockServices = MockServices(listOf("com.template"))
    var exchange = TestIdentity(CordaX500Name("Binance", "New York", "US"))
    var trader1 = TestIdentity(CordaX500Name("George", "London", "US"))


    @Test
    fun createContractGenericTests() {
        val state = PerpFuturesState("BTC", 45000.0, 0.01, 5000.0, trader1.party, exchange.party)
        ledgerServices.ledger {
            // Should fail as has an input
            transaction {
                input(PerpFuturesContract.ID, state)
                output(PerpFuturesContract.ID, state)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.Create("BTC", 45000.0))
                fails()
            }
            //should fail as has two outputs
            transaction {
                output(PerpFuturesContract.ID, state)
                output(PerpFuturesContract.ID, state)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.Create("BTC", 45000.0))
                fails()
            }
            val failingState = PerpFuturesState("BTC", 45000.0, 0.01, 5000.0, exchange.party, exchange.party)
            //should fail as has taker and exchange are same person
            transaction {
                output(PerpFuturesContract.ID, failingState)
                command(exchange.publicKey,PerpFuturesContract.Commands.Create("BTC", 45000.0))
                fails()
            }
            //should fail as only the exchange signs
            transaction {
                output(PerpFuturesContract.ID, state)
                command(exchange.publicKey,PerpFuturesContract.Commands.Create("BTC", 45000.0))
                fails()
            }

            // Should pass as has only one output
            transaction {
                output(PerpFuturesContract.ID, state)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.Create("BTC", 45000.0))
                verifies()
            }
        }
    }

    @Test
    fun createContractPerpSpecificTests() {
        val state = PerpFuturesState("BTC", 45000.0, 0.01, 5000.0, trader1.party, exchange.party)
        ledgerServices.ledger {
            //Should fail as price is less than zero
            val lessThanZeroPriceState = PerpFuturesState("BTC", -45000.0, 0.01, 5000.0, trader1.party, exchange.party)
            transaction {
                output(PerpFuturesContract.ID, lessThanZeroPriceState)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.Create("BTC", 45000.0))
                fails()
            }

            //Should fail as price is less than zero
            val lessThanZeroPositionState = PerpFuturesState("BTC", 45000.0, -0.01, 5000.0, trader1.party, exchange.party)
            transaction {
                output(PerpFuturesContract.ID, lessThanZeroPositionState)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.Create("BTC", 45000.0))
                fails()
            }

            //Should fail as price is less than zero
            val lessThanZeroCollateralState = PerpFuturesState("BTC", 45000.0, 0.01, -5000.0, trader1.party, exchange.party)
            transaction {
                output(PerpFuturesContract.ID, lessThanZeroCollateralState)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.Create("BTC", 45000.0))
                fails()
            }

            val tooMuchLeverageState = PerpFuturesState("BTC", 45000.0, 1.0, 5000.0, trader1.party, exchange.party)
            transaction {
                output(PerpFuturesContract.ID, tooMuchLeverageState)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.Create("BTC", 45000.0))
                fails()
            }

            val validState = PerpFuturesState("BTC", 45000.0, 0.01, 5000.0, trader1.party, exchange.party)
            transaction {
                output(PerpFuturesContract.ID, validState)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.Create("BTC", 45000.0))
                verifies()
            }
        }
    }

    @Test
    fun closeContractTests(){
        val state = PerpFuturesState("BTC", 45000.0, 0.0, 5000.0, trader1.party, exchange.party)
        ledgerServices.ledger {
            // Should fail as has an output
            transaction {
                input(PerpFuturesContract.ID, state)
                output(PerpFuturesContract.ID, state)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.Close("BTC", 45000.0))
                fails()
            }
            //should fail as has two inputs
            transaction {
                input(PerpFuturesContract.ID, state)
                input(PerpFuturesContract.ID, state)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.Close("BTC", 45000.0))
                fails()
            }
            //should fail as only the exchange signs
            transaction {
                output(PerpFuturesContract.ID, state)
                command(exchange.publicKey, PerpFuturesContract.Commands.Close("BTC", 45000.0))
                fails()
            }
            val failingState = PerpFuturesState("BTC", 45000.0, 1.0, 5000.0, trader1.party, exchange.party)
            //should fail as final pos size isn't 0
            transaction {
                input(PerpFuturesContract.ID, failingState)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.Close("BTC", 45000.0))
                fails()
            }

            transaction{
                input(PerpFuturesContract.ID, state)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.Close("BTC", 45000.0))
                verifies()
            }
        }
    }

    @Test
    fun partialCloseGenericTests(){
        val state = PerpFuturesState("BTC", 45000.0, 0.02, 5000.0, trader1.party, exchange.party)
        val nextState = PerpFuturesState("BTC", 45000.0, 0.01, 5000.0, trader1.party, exchange.party)
        ledgerServices.ledger {
            // Should fail as it has no input
            transaction {
                output(PerpFuturesContract.ID, nextState)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.PartialClose("BTC", 45000.0))
                fails()
            }
            //should fail as has no output
            transaction {
                input(PerpFuturesContract.ID, state)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.PartialClose("BTC", 45000.0))
                fails()
            }
            //should fail as has two outputs
            transaction {
                input(PerpFuturesContract.ID, state)
                output(PerpFuturesContract.ID, nextState)
                output(PerpFuturesContract.ID, nextState)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.PartialClose("BTC", 45000.0))
                fails()
            }
            //should fail as has two inputs
            transaction {
                input(PerpFuturesContract.ID, state)
                input(PerpFuturesContract.ID, state)
                output(PerpFuturesContract.ID, nextState)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.PartialClose("BTC", 45000.0))
                fails()
            }
            //should fail as only the exchange signs
            transaction {
                input(PerpFuturesContract.ID, state)
                output(PerpFuturesContract.ID, nextState)
                command(exchange.publicKey,PerpFuturesContract.Commands.PartialClose("BTC", 45000.0))
                fails()
            }

            // Should pass
            transaction {
                input(PerpFuturesContract.ID, state)
                output(PerpFuturesContract.ID, nextState)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.PartialClose("BTC", 45000.0))
                verifies()
            }
        }
    }

    @Test
    fun partialCloseSpecificTests(){
        ledgerServices.ledger {
            val state = PerpFuturesState("BTC", 45000.0, 0.02, 5000.0, trader1.party, exchange.party)
            val nextStateBiggerSize = PerpFuturesState("BTC", 45000.0, 0.02, 5000.0, trader1.party, exchange.party)
            //Should fail as position size increases
            transaction {
                input(PerpFuturesContract.ID, state)
                output(PerpFuturesContract.ID, nextStateBiggerSize)
                command(listOf(trader1.publicKey, exchange.publicKey),PerpFuturesContract.Commands.PartialClose("BTC", 45000.0))
                fails()
            }


        }
    }
}