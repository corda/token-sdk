package net.corda.sdk.token.commands

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.TypeOnlyCommandData

interface OwnedTokenAmountCommands : CommandData {
    class Issue : OwnedTokenAmountCommands, TypeOnlyCommandData()
    class Move : OwnedTokenAmountCommands, TypeOnlyCommandData()
    class Redeem : OwnedTokenAmountCommands, TypeOnlyCommandData()
}