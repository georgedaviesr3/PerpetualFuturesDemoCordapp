package com.template.schema

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

object PerpFuturesSchema

object PerpFuturesSchemaV1: MappedSchema(schemaFamily = PerpFuturesSchema.javaClass,
                                         version = 1,
                                         mappedTypes = listOf(PerpSchema::class.java)){
    @Entity
    @Table(
        name = "query_criteria", indexes = [
            Index(name ="assetTickerIdx", columnList = "assetTicker"),
            Index(name ="takerIdx", columnList = "taker")
        ]
    )
    class PerpSchema(
        @Column(name = "assetTicker", nullable = false)
        var assetTicker: String? = null,
        @Column(name = "taker", nullable = false)
        var taker: Party? = null
    ): PersistentState()

}