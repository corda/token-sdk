package com.r3.corda.lib.tokens.selection.memory.internal

import net.corda.core.crypto.toStringShort
import net.corda.core.node.ServiceHub
import java.security.PublicKey
import java.util.*

/** Table names. */
val publicKeyHashToExternalId = "pk_hash_to_ext_id_map"

/** Column names. */
val publicKeyHashToExternalId_externalId = "external_id"
val publicKeyHashToExternalId_publicKeyHash = "public_key_hash"

sealed class Holder {
    data class KeyIdentity(val owningKey: PublicKey) : Holder() // Just public key
    object UnmappedIdentity : Holder() // For all keys that are unmapped
    data class MappedIdentity(val uuid: UUID) : Holder() // All keys register to this uuid
    object JustToken : Holder() // This is for the case where we use token class and token identifier only

    companion object {
        fun fromUUID(uuid: UUID?): Holder {
            return if (uuid != null) {
                MappedIdentity(uuid)
            } else {
                UnmappedIdentity
            }
        }
    }
}

// TODO after corda 4.3 we will be able to use cache introduced by this PR https://github.com/corda/corda/pull/5357
fun lookupExternalIdFromKey(owningKey: PublicKey, serviceHub: ServiceHub): Holder {
    return serviceHub.withEntityManager {
        val query = createNativeQuery(
                """
                        select $publicKeyHashToExternalId_externalId
                        from $publicKeyHashToExternalId
                        where $publicKeyHashToExternalId_publicKeyHash = :hash
                    """
        )
        query.setParameter("hash", owningKey.toStringShort())
        val uuid = query.resultList.firstOrNull()?.let { UUID.fromString(it as String) }
        if (uuid != null || isKeyPartOfNodeKeyPairs(owningKey, serviceHub) || isKeyIdentityKey(owningKey, serviceHub)) {
            val signingEntity = Holder.fromUUID(uuid)
            signingEntity
        } else {
            Holder.UnmappedIdentity // TODO need to have good default for these cases with no mapping
        }
    }
}

/**
 * Establish whether a public key is one of the node's identity keys, by looking in the node's identity database table.
 */
private fun isKeyIdentityKey(key: PublicKey, services: ServiceHub): Boolean {
    val party = services.identityService.partyFromKey(key)
    return party?.owningKey == key
}

/**
 * Check to see if the key belongs to one of the key pairs in the node_our_key_pairs table. These keys may relate to confidential
 * identities.
 */
private fun isKeyPartOfNodeKeyPairs(key: PublicKey, services: ServiceHub): Boolean {
    return services.keyManagementService.filterMyKeys(listOf(key)).toList().isNotEmpty()
}
