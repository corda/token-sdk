package net.corda.sdk.token.commands

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.TypeOnlyCommandData

interface TokenCommands : CommandData {
    class Issue : TokenCommands, TypeOnlyCommandData()
    class Move : TokenCommands, TypeOnlyCommandData()
    class Redeem : TokenCommands, TypeOnlyCommandData()
}