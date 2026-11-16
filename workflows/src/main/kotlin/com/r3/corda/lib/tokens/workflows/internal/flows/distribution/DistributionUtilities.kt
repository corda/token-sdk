@file:JvmName("DistributionUtilities")
package com.r3.corda.lib.tokens.workflows.internal.flows.distribution

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.workflows.internal.schemas.DistributionRecord
import com.r3.corda.lib.tokens.workflows.utilities.addPartyToDistributionList
import com.r3.corda.lib.tokens.workflows.utilities.requireKnownConfidentialIdentity
import com.r3.corda.lib.tokens.workflows.utilities.toParty
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import java.lang.reflect.InvocationTargetException

@CordaSerializable
data class DistributionListUpdate(val sender: Party, val receiver: Party, val linearId: UniqueIdentifier)

// The entity manager code below binds no persistence API type, in the source or in the compiled classes: Corda 4.14 and earlier hand
// over a javax.persistence.EntityManager, Corda 4.15 a jakarta.persistence one. A block typed on the EntityManager of the Corda the SDK
// is built against (javax.persistence for 4.12) casts to that interface. A Corda 4.15 node converts such a class to jakarta.persistence
// as it loads the CorDapp jar, but where the classes are not converted (MockNetwork, MockServices and in-process driver nodes of a
// CorDapp tested on Corda 4.15) it fails with NoClassDefFoundError: javax/persistence/EntityManager. So the blocks take the entity
// manager as Any and call it through the persistence API interface it implements.

// Gets the distribution list for a particular token.
fun getDistributionList(services: ServiceHub, linearId: UniqueIdentifier): List<DistributionRecord> {
    return services.queryDistributionRecords(
            "select r from DistributionRecord r where r.linearId = :linearId",
            "linearId" to linearId.id
    )
}

// Gets the distribution record for a particular token and party.
fun getDistributionRecord(serviceHub: ServiceHub, linearId: UniqueIdentifier, party: Party): DistributionRecord? {
    return serviceHub.queryDistributionRecords(
            "select r from DistributionRecord r where r.linearId = :linearId and r.party = :party",
            "linearId" to linearId.id,
            "party" to party
    ).singleOrNull()
}

fun hasDistributionRecord(serviceHub: ServiceHub, linearId: UniqueIdentifier, party: Party): Boolean {
    return getDistributionRecord(serviceHub, linearId, party) != null
}

/**
 * Utility function to persist a new entity pertaining to a distribution record.
 * TODO: Add some error handling.
 * TODO: Don't duplicate pairs of linearId and party.
 */
fun ServiceHub.addPartyToDistributionList(party: Party, linearId: UniqueIdentifier) {
    // Create an persist a new entity.
    persistDistributionRecord(DistributionRecord(linearId.id, party))
}

internal fun ServiceHub.persistDistributionRecord(distributionRecord: DistributionRecord) {
    withUntypedEntityManager { entityManager -> entityManager.callPersistenceApi("persist", arrayOf(Any::class.java), distributionRecord) }
}

private fun ServiceHub.queryDistributionRecords(jpql: String, vararg parameters: Pair<String, Any>): List<DistributionRecord> {
    return withUntypedEntityManager { entityManager ->
        val query = entityManager.callPersistenceApi("createQuery", arrayOf(String::class.java, Class::class.java), jpql, DistributionRecord::class.java)!!
        for ((name, value) in parameters) {
            query.callPersistenceApi("setParameter", arrayOf(String::class.java, Any::class.java), name, value)
        }
        @Suppress("UNCHECKED_CAST")
        query.callPersistenceApi("getResultList", emptyArray()) as List<DistributionRecord>
    }
}

/**
 * [ServiceHub.withEntityManager] with a block that takes the entity manager as [Any]: a `(Any) -> T` is an `EntityManager.() -> T`, but
 * its compiled code does not cast the entity manager to either EntityManager interface.
 */
private fun <T> ServiceHub.withUntypedEntityManager(block: (Any) -> T): T = withEntityManager(block)

/** Calls the method [name] of a persistence API object through the `javax.persistence` or `jakarta.persistence` interface that declares it. */
private fun Any.callPersistenceApi(name: String, parameterTypes: Array<Class<*>>, vararg arguments: Any?): Any? {
    val method = persistenceApiInterfaces(javaClass).mapNotNull { api ->
        try {
            api.getMethod(name, *parameterTypes)
        } catch (e: NoSuchMethodException) {
            null
        }
    }.firstOrNull() ?: throw IllegalStateException(
            "${javaClass.name} implements no persistence API interface with $name(${parameterTypes.joinToString { it.simpleName }})")
    return try {
        method.invoke(this, *arguments)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}

/** The `javax.persistence` and `jakarta.persistence` interfaces that [type] implements, directly or through its supertypes. */
private fun persistenceApiInterfaces(type: Class<*>): Sequence<Class<*>> {
    val interfaces = LinkedHashSet<Class<*>>()
    fun collect(from: Class<*>) {
        from.interfaces.forEach { if (interfaces.add(it)) collect(it) }
    }
    generateSequence(type) { it.superclass }.forEach(::collect)
    return interfaces.asSequence().filter { it.name.startsWith("javax.persistence.") || it.name.startsWith("jakarta.persistence.") }
}

@Suspendable
fun FlowLogic<*>.addToDistributionList(tokens: List<AbstractToken>) {
    tokens.filter { it.tokenType as? TokenPointer<*> != null }.forEach { token ->
        val tokenType = token.tokenType as TokenPointer<*>
        val pointer = tokenType.pointer.pointer
        val holder = token.holder.toParty(serviceHub)
        addPartyToDistributionList(holder, pointer)
    }
}

@Suspendable
fun FlowLogic<*>.updateDistributionList(tokens: List<AbstractToken>) {
    tokens.filter { it.tokenType as? TokenPointer<*> != null }.forEach { token ->
        val tokenPointer = token.tokenType as TokenPointer<*>
        val holderParty = serviceHub.identityService.requireKnownConfidentialIdentity(token.holder)
        val evolvableToken = tokenPointer.pointer.resolve(serviceHub).state.data
        val distributionListUpdate = DistributionListUpdate(ourIdentity, holderParty, evolvableToken.linearId)
        val maintainers = evolvableToken.maintainers
        val maintainersSessions = maintainers.map(::initiateFlow)
        maintainersSessions.forEach {
            it.send(distributionListUpdate)
        }
    }
}