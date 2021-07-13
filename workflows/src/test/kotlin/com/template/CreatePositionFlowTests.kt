package com.template

import com.template.schema.PerpFuturesSchemaV1
import com.template.states.PerpFuturesState
import groovy.util.GroovyTestCase.assertEquals
import perp_flows.CreatePositionFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import perp_flows.BasicFlow
import kotlin.test.assertEquals


class CreatePositionFlowTests {

    private lateinit var network: MockNetwork
    private lateinit var oracle: StartedMockNode
    private lateinit var exchange: StartedMockNode
    private lateinit var trader1: StartedMockNode
    private lateinit var trader2: StartedMockNode


    @Before
    fun setup() {
        val myNetworkParameters = testNetworkParameters(minimumPlatformVersion = 4)
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.template.contracts"),
            TestCordapp.findCordapp("perp_flows")
        ),
            networkParameters = myNetworkParameters))
        oracle = network.createPartyNode()
        exchange = network.createPartyNode()
        trader1 = network.createPartyNode()
        trader2 = network.createPartyNode()
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun createPositionTest() {
        //Create State
        val createPositionFlow = CreatePositionFlow.Initiator("BTC", 1000.0, 0.01, exchange.info.legalIdentities[0], oracle.info.legalIdentities[0])
        val future1 = trader1.startFlow(createPositionFlow)

        network.runNetwork()
        future1.getOrThrow()

        //Retrieve new state from vault
        val tickerQuery = PerpFuturesSchemaV1.PerpSchema::assetTicker.equal("BTC")
        val takerQuery = PerpFuturesSchemaV1.PerpSchema::taker.equal(trader1.info.legalIdentities[0])

        val queryCriteria = QueryCriteria.VaultCustomQueryCriteria(tickerQuery).and(QueryCriteria.VaultCustomQueryCriteria(takerQuery))

        val state = trader1.services.vaultService.queryBy<PerpFuturesState>(queryCriteria).states[0]

        assertEquals(50400.0,state.state.data.initialAssetPrice, "Wrong oracle price")
       // val matchingStates = serviceHub.vaultService.queryBy<PerpFuturesState>(queryCriteria).states

        //assertEquals(501.0, 501.0)

        //network.runNetwork()
        //future1.getOrThrow()

    }
}
