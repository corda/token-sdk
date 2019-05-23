package com.r3.corda.sdk.token.contracts.commands.internal

import net.corda.core.contracts.CommandData

// TODO This is a nasty hack because identity sync flow has api takes only transaction, not input state refs in a constructor.
//   This should be removed after confidential identities refactor.
class DummyCommand : CommandData