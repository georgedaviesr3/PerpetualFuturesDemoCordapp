package com.template.states

import com.template.contracts.PerpFuturesContract
import com.template.schema.PerpFuturesSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

/**
 * The state object recording a perpetual futures agreement between two parties.
 *
 * @param assetTicker the ticker symbol representing the asset the taker wants to open a contract for
 * @param currentAssetPrice the current price of the asset
 * @param initialAssetPrice the value of the asset price at the point of the contract agreement
 * @param positionSize the number of assets the contract the taker wants to buy
 * @param collateralPosted amount of collateral taker has posted - can be used to derive leverage
 * @param taker the party buying the contract
 * @param exchange the party issuing the contract
 */
@BelongsToContract(PerpFuturesContract::class)
data class PerpFuturesState(
    val assetTicker: String,
    val currentAssetPrice: Double,
    val initialAssetPrice: Double,
    val positionSize: Double,
    val collateralPosted: Double,
    val taker: Party,
    val exchange: Party,

    override val participants: List<AbstractParty> = listOf(taker,exchange)
) : QueryableState{
    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when(schema){
            is PerpFuturesSchemaV1 -> PerpFuturesSchemaV1.PerpSchema(
                    assetTicker = this.assetTicker,
                    taker = this.taker
                    )
            else -> throw IllegalStateException("Unrecognised schema ${schema.name} passed.")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> {
        return listOf(PerpFuturesSchemaV1)
    }

}