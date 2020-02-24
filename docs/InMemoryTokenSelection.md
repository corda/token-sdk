# Using in memory token selection - Experimental Feature

## Overview

To remove potential performance bottleneck and remove the requirement for database specific SQL to be provided for each backend,
in memory implementation of token selection was introduced as an experimental feature of `token-sdk`.
To use it, you need to have `VaultWatcherService` installed as a `CordaService` on node startup. Indexing
strategy could be specified, by PublicKey, by ExternalId (if using accounts feature) or just by token type and identifier.

## How to switch between database based selection and in memory cache?

To be able to use in memory token selection, make sure that you have `VaultWatcherService` corda service installed
(for now it comes with `token-sdk` but may be split out in the future). There are configuration options available
for service initialisation when cordapps are loaded.
In your [CorDapp config](https://docs.corda.net/cordapp-build-systems.html#cordapp-configuration-files) put:

```text
stateSelection {
    inMemory {
           indexingStrategies: ["EXTERNAL_ID"|"PUBLIC_KEY"|"TOKEN_ONLY"]
           cacheSize: Int
    }
}
```

And choose a single indexing strategy, `EXTERNAL_ID` , `PUBLIC_KEY` or `TOKEN_ONLY`. Public key strategy makes a token bucket for each public key,
so if you use accounts, probably it is better to use external id grouping that groups states
from many public keys connected with given uuid. `TOKEN_ONLY` selection strategy indexes states only using token type and identifier.

For example, to configure token selection in your `deployNodes`:

```text
// Add under e.g.: task deployNodes(type: net.corda.plugins.Cordform)...
nodeDefaults {
    cordapp ("$corda_tokens_sdk_release_group:tokens-selection:$corda_tokens_sdk_version"){
        config '''
            stateSelection {
                inMemory {
                       indexingStrategies: ["TOKEN_ONLY"]
                       cacheSize: 1024
                }
            }
        '''
    }
}
```

## How to use LocalTokenSelector from the flow

For now, our default flows are written with database token selection in mind. The API will change in 2.0 release.
If you would like to use in memory token selection, then you have to write your own wrappers around `MoveTokensFlow` and
`RedeemTokensFlow`. You can also use that selection with `addMoveTokens` and `addRedeemTokens` utility functions, but
make sure that all the checks are performed before construction of the transaction. 

Let's take a look how to use the feature.

### Moving tokens using LocalTokenSelection

From your flow construct `LocalTokenSelector` instance:

```kotlin
val localTokenSelector = LocalTokenSelector(serviceHub)
```

After that you can choose states for move by either calling `selectTokens`:

```kotlin
val transactionBuilder: TransactionBuilder = ...
val participantSessions: List<FlowSession> = ...
val observerSessions: List<FlowSession> = ...
val requiredAmount: Amount<TokenType> = ...
val queryBy: TokenQueryBy = ...  // See section below on queries
// Just select states for spend, without output and change calculation
val selectedStates: List<StateAndRef<FungibleToken>> = localTokenSelector.selectStates(
    lockID = transactionBuilder.lockId, // Defaults to FlowLogic.currentTopLevel?.runId?.uuid ?: UUID.randomUUID()
    requiredAmount = requiredAmount,
    queryBy = queryBy)
```

or even better `generateMove` method returns list of inputs and list of output states that can be passed to `addMove` or `MoveTokensFlow`:

```kotlin
// or generate inputs, outputs with change, grouped by issuers
val partiesAndAmounts: List<PartyAndAmount<TokenType>> = ... // As in previous tutorials, list of parties that should receive amount of TokenType
val changeHolder: AbstractParty = ... // Party that should receive change
val (inputs, outputs) = localTokenSelector.generateMove(
    lockId = transactionBuilder.lockId, // Defaults to FlowLogic.currentTopLevel?.runId?.uuid ?: UUID.randomUUID()
    partiesAndAmounts = partiesAndAmounts,
    changeHolder = changeHolder,
    queryBy = queryBy) // See section below on queries

// Call subflow
subflow(MoveTokensFlow(inputs, outputs, participantSessions, observerSessions))
// or use utilities functions
//... implement some business specific logic
addMoveTokens(transactionBuilder, inputs, outputs)
```

Then finalize transaction, update distribution list etc, see [Most common tasks](docs/IWantTo.md)

**Note:** we use generic versions of `MoveTokensFlow` or `addMoveTokens` (not `addMoveFungibleTokens`), because we 
already performed selection and provide input and output states directly. Fungible versions will always use database selection.

### Redeeming tokens using LocalTokenSelection

Using in memory selection when redeeming tokens looks very similar to move:

```kotlin
val vaultWatcherService = serviceHub.cordaService(VaultWatcherService::class.java)
val localTokenSelector = LocalTokenSelector(serviceHub, vaultWatcherService, autoUnlockDelay = autoUnlockDelay)

// Smilar to previous case, we need to choose states that cover the amount.
val exitStates: List<StateAndRef<FungibleToken>> = localTokenSelector.selectStates(
    lockID = transactionBuilder.lockId, // Defaults to FlowLogic.currentTopLevel?.runId?.uuid ?: UUID.randomUUID()
    requiredAmount = requiredAmount,
    queryBy = queryBy) // See section below on queries
    
// Exit states and get possible change output.
val (inputs, changeOutput) =  generateExit(
    exitStates = exitStates,
    amount = requiredAmount,
    changeHolder = changeHolder
)
// Call subflow top redeem states with the issuer
val issuerSession: FlowSession = ...
subflow(RedeemTokensFlow(inputs, changeOutput, issuerSession, observerSessions))
// or use utilities functions.
addTokensToRedeem(
    transactionBuilder = transactionBuilder,
    inputs = inputs,
    changeOutput = changeOutput
)
```

### Providing Queries to LocalTokenSelector

You can provide additional queries to `LocalTokenSelector` by constructing `TokenQueryBy` and passing it to `generateMove`
or `selectStates` methods. `TokenQueryBy` takes `issuer` to specify selection of token from given issuing party, additionally
you can provide any states filtering as `predicate` function (don't use `queryCriteria` it will get removed as part of 2.0 release
and exists only to keep bakcwards compatibility with databse selection!).

```kotlin
val issuerParty: Party = ...
val notaryParty: Party = ...
// Get list of input and output states that can be passed to addMove or MoveTokensFlow
val (inputs, outputs) = localTokenSelector.generateMove(
        partiesAndAmounts = listOf(Pair(receivingParty,  tokensAmount)),
        changeHolder = this.ourIdentity,
        // Get tokens issued by issuerParty and notarised by notaryParty
        queryBy = TokenQueryBy(issuer = issuerParty, predicate = { it.state.notary == notaryParty }))
``` 
