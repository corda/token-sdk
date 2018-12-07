package net.corda.sdk.token.types

import java.security.PublicKey

interface Redeemable {
    val exitKeys: Set<PublicKey>
}
