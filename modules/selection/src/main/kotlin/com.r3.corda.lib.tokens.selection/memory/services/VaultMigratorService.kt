package com.r3.corda.lib.tokens.selection.memory.services

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class VaultMigratorService(appServiceHub: AppServiceHub) : SingletonSerializeAsToken() {
	//TODO - we should attempt to migrate the old vault contents. This must be done a service because we cannot guarantee
	//the order of migration scripts and therefore cannot initiate hibernate
}
