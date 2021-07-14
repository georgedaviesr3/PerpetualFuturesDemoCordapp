package api

import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.contracts.StateAndRef
import com.template.schema.PerpFuturesSchemaV1
import com.template.states.PerpFuturesState
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria

fun getPerpFuturesStateByIDAndTicker(serviceHub: ServiceHub, taker: Party, ticker: String): StateAndRef<PerpFuturesState>{

    val tickerQuery = PerpFuturesSchemaV1.PerpSchema::assetTicker.equal(ticker)
    val takerQuery = PerpFuturesSchemaV1.PerpSchema::taker.equal(taker)
    val queryCriteria = QueryCriteria.VaultCustomQueryCriteria(tickerQuery).and(QueryCriteria.VaultCustomQueryCriteria(takerQuery))

    val matchingStates = serviceHub.vaultService.queryBy<PerpFuturesState>(queryCriteria).states

    require(matchingStates.isNotEmpty()){
        "No Perpetual Futures Contract found with that ID"
    }

    //Get most recent elem in vault <- can this be done with not comsumed?
    return matchingStates[(matchingStates.size - 1)]

}
