package api

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import services.PriceOracle
import java.time.Instant

/**
 * Initiating flow to query the price of an asset
 * @param assetTicker ticker representing the asset to be queries
 * @param oracle the asset price provider
 * @param instant the instant at which to get the asset price
 */

@InitiatingFlow
class QueryPriceOracleFlow(private val oracle: Party, private val assetTicker: String, private val instant: Instant): FlowLogic<Double>() {

    @Suspendable
    override fun call(): Double{
        val queryResult = try{
            serviceHub.cordaService(PriceOracle::class.java).query(assetTicker, instant)
        } catch (e: Exception){
            throw FlowException(e)
        }

        return queryResult
    }
}