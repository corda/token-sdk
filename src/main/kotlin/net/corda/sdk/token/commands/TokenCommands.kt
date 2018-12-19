package net.corda.sdk.token.commands

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.TypeOnlyCommandData

interface TokenCommands : CommandData {
    class Create : CommandData, TypeOnlyCommandData()
    class Update : CommandData, TypeOnlyCommandData()
}