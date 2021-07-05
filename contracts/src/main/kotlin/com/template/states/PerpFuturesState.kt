package com.template.states

import com.template.contracts.PerpFuturesContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/**
 * The state object recording a perpetual futures agreement between two parties.
 *
 * @param assetTicker the ticker symbol representing the asset the taker wants to open a contract for
 * @param currentAssetPrice the current price of the asset
 * @param initialAssetPrice the value of the asset price at the point of the contract agreement
 * @param positionSize the number of assets the contract the taker wants to buy
 * @param taker the party buying the contract
 * @param exchange the party issuing the contract
 */
@BelongsToContract(PerpFuturesContract::class)
data class PerpFuturesState(
    val assetTicker: String,
    val currentAssetPrice: Double,
    val initialAssetPrice: Double,
    val positionSize: Double,
    val taker: Party,
    val exchange: Party,
    //val linearId: UniqueIdentifier = UniqueIdentifier(),
    override val participants: List<AbstractParty> = listOf(taker,exchange)
) : ContractState