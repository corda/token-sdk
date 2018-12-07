package net.corda.sdk.token.types

import net.corda.core.identity.Party

interface Issuable {
    val issuer: Party
}