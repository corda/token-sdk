package com.r3.corda.lib.tokens.selection.memory.services

import com.r3.corda.lib.tokens.selection.memory.internal.Holder

enum class IndexingType(val ownerType: Class<out Holder>) {

    EXTERNAL_ID(Holder.MappedIdentity::class.java),
    PUBLIC_KEY(Holder.KeyIdentity::class.java);

    companion object {
        fun fromHolder(holder: Class<out Holder>): IndexingType {
            return when (holder) {
                Holder.MappedIdentity::class.java -> {
                    EXTERNAL_ID
                }

                Holder.KeyIdentity::class.java -> {
                    PUBLIC_KEY
                }
                else -> throw IllegalArgumentException("Unknown Holder type: $holder")
            }
        }
    }

}