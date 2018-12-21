package net.corda.sdk.token.commands

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.TypeOnlyCommandData

interface TokenCommands : CommandData {
    class Create : OwnedTokenCommands, TypeOnlyCommandData()
    class Update : OwnedTokenCommands, TypeOnlyCommandData()
    class Delete : OwnedTokenCommands, TypeOnlyCommandData()
}