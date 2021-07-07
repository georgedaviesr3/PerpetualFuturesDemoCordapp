package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.states.PerpFuturesState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap
import services.PriceOracle

/**
 * Initiating flow to query the price of an asset
 * @param assetTicker ticker representing the asset to be queries
 * @param oracle the asset price provider
 */

@InitiatingFlow
class QueryPriceOracleFlow(private val oracle: Party, private val assetTicker: String): FlowLogic<Double>() {
    @Suspendable
    //override fun call() = initiateFlow(oracle).sendAndRecieve<Double>(assetTicker).unwrap{it}

    override fun call(): Double{
        val queryResult = try{
            serviceHub.cordaService(PriceOracle::class.java).query(assetTicker)
        } catch (e: Exception){
            throw FlowException(e)
        }

        return queryResult
    }
}