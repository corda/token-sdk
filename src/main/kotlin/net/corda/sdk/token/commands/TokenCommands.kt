package net.corda.sdk.token.commands

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.TypeOnlyCommandData

/**
 * A standard set of commands for creating, updating and deleting tokens. Additional commands can be added which
 * implement [TokenCommands]. For example, a stock token will require a "stock split" command, which would implement
 * the [TokenCommands] interface.
 */
interface TokenCommands : CommandData {
    class Create : TokenCommands, TypeOnlyCommandData()
    class Update : TokenCommands, TypeOnlyCommandData()
    class Delete : TokenCommands, TypeOnlyCommandData()
}