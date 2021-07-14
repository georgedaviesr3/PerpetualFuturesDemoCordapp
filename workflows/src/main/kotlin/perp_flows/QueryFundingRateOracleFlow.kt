package perp_flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import services.FundingRateOracle
import java.time.Instant

/**
 * Initiating flow to query the price of an asset
 * @param assetTicker ticker representing the asset to be queries
 * @param oracle the asset price provider
 * @param instant the time to get the funding rate at
 */

@InitiatingFlow
class QueryFundingRateOracleFlow(private val oracle: Party, private val assetTicker: String, private val instant: Instant): FlowLogic<Double>() {
    @Suspendable
    override fun call(): Double{
        val queryResult = try{
            serviceHub.cordaService(FundingRateOracle::class.java).query(assetTicker, instant)
        } catch (e: Exception){
            throw FlowException(e)
        }
        return queryResult
    }
}