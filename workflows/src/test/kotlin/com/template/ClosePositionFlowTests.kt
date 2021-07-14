package com.template

import com.template.schema.PerpFuturesSchemaV1
import com.template.states.PerpFuturesState
import groovy.util.GroovyTestCase.assertEquals
import perp_flows.CreatePositionFlow
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import perp_flows.ClosePositionFlow
import kotlin.test.assertEquals


class ClosePositionFlowTests {

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
    fun closePositionTest() {
        //create position
        val createPositionFlow = CreatePositionFlow.Initiator("BTC", 1000.0, 0.01, exchange.info.legalIdentities[0], oracle.info.legalIdentities[0])
        val future = trader1.startFlow(createPositionFlow)

        //Run network
        network.runNetwork()
        future.getOrThrow()

        //Retrieve new state from vault
        val tickerQuery = PerpFuturesSchemaV1.PerpSchema::assetTicker.equal("BTC")
        val takerQuery = PerpFuturesSchemaV1.PerpSchema::taker.equal(trader1.info.legalIdentities[0])

        val queryCriteria = QueryCriteria.VaultCustomQueryCriteria(tickerQuery).and(QueryCriteria.VaultCustomQueryCriteria(takerQuery))

        val state = trader1.services.vaultService.queryBy<PerpFuturesState>(queryCriteria).states[0]
        val stateData = state.state.data

        assertEquals(0.5,0.5)
        //Ensure values are stored in the correct variables and oracle price is correct
        assertEquals(50400.0, stateData.initialAssetPrice, "Wrong oracle price")
        assertEquals(0.01, stateData.positionSize, "Wrong position size")
        assertEquals(1000.0,stateData.collateralPosted, "Wrong collateral")
        assertEquals(trader1.info.legalIdentities[0],stateData.taker, "Wrong taker")
        assertEquals(exchange.info.legalIdentities[0],stateData.exchange, "Wrong taker")

        //Close position
        val closePositionFlow = ClosePositionFlow.Initiator("BTC", exchange.info.legalIdentities[0], oracle.info.legalIdentities[0])
        val future1 = trader1.startFlow(closePositionFlow)

        //Run network
        network.runNetwork()
        future1.getOrThrow()

        //How do i query inputs?? or txs


    }
}
