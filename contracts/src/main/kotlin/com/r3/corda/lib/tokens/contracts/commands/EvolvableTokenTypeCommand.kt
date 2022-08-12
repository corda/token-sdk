package com.r3.corda.lib.tokens.contracts.commands

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.TypeOnlyCommandData

/**
 * Defines a command mechanism for creating and updating [EvolvableTokenType] instances.
 *
 * Additional commands can be added which implement [EvolvableTokenTypeCommand].
 * For example, a stock token will require a "stock split" command, which would implement the [EvolvableTokenTypeCommand] interface.
 *
 * For the time being, [EvolvableTokenType] instances cannot be removed from the ledger. Doing so would break the evolvable token model.
 * Given that [EvolvableTokenType] instances are not storage hungry, they can just persist on the ledger even if they are not required any more.
 */
interface EvolvableTokenTypeCommand : CommandData

/**
 * Represents the evolvable token command used to create new [EvolvableTokenType] instances.
 */
class Create : EvolvableTokenTypeCommand, TypeOnlyCommandData()

/**
 * Represents the evolvable token command used to update existing [EvolvableTokenType] instances.
 */
class Update : EvolvableTokenTypeCommand, TypeOnlyCommandData()
