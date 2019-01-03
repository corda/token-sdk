package net.corda.sdk.token.services

import net.corda.core.node.AppServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken

/**
 * TODO Add a service which keeps track of how much of a particular token has been issued. This will be pretty basic and
 * not good enough for production but something like this is necessary for demos.
 */
class IssuanceTrackerService(val services: AppServiceHub) : SingletonSerializeAsToken()