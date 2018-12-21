package net.corda.sdk.token.commands

import net.corda.core.contracts.TypeOnlyCommandData

interface OwnedTokenCommands {
    class Issue : TokenCommands, TypeOnlyCommandData()
    class Move : TokenCommands, TypeOnlyCommandData()
    class Redeem : TokenCommands, TypeOnlyCommandData()
}