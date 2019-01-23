package net.corda.sdk.token.contracts.commands

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.TypeOnlyCommandData

/**
 * A standard set of commands for creating and updating a [EvolvableToken]s. Additional commands can be added which
 * implement [EvolvableTokenCommand]. For example, a stock token will require a "stock split" command, which would
 * implement the [EvolvableTokenCommand] interface.
 *
 * Note, that for the time being, [EvolvableToken]s cannot be removed from the ledger. Doing so would break the
 * evolvable token model. Given that [EvolvableToken]s are not storage hungry, this is an acceptable trade-off - they
 * can just persist on the ledger even if they are not required.
 */
interface EvolvableTokenCommand : CommandData

class Create : EvolvableTokenCommand, TypeOnlyCommandData()
class Update : EvolvableTokenCommand, TypeOnlyCommandData()