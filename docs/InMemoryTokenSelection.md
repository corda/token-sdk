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
    in_memory {
           indexingStrategy: ["external_id"|"public_key"|"token"]
           cacheSize: Int
    }
}
```

And choose indexing strategy, `external_id` , `public_key` or `token_only`. Public key strategy makes a token bucket for each public key,
so if you use accounts, probably it is better to use external id grouping that groups states
from many public keys connected with given uuid. `token_only` selection strategy indexes states only using token type and identifier.

## How to use LocalTokenSelector from the flow

For now, our default flows are written with database token selection in mind. The API will change in 2.0 release.
If you would like to use in memory token selection, then you have to write your own wrappers around `MoveTokensFlow` and
`RedeemTokensFlow`. You can also use that selection with `addMoveTokens` and `addRedeemTokens` utility functions, but
make sure that all the checks are performed before construction of the transaction. Let's take a look how to use the feature.

### Moving tokens using LocalTokenSelection

From your flow get the `VaultWatcherService` using:

```kotlin
val vaultWatcherService = serviceHub.cordaService(VaultWatcherService::class.java)
```

After that construct `LocalTokenSelector` instance for use in your flow:

```kotlin
val autoUnlockDelay = ... // Defaults to Duration.ofMinutes(5). Time after which the tokens that are not spent will be automatically released.
// autoUnlockDelay is needed in case flow errors or hangs on some operation.
val localTokenSelector = LocalTokenSelector(serviceHub, vaultWatcherService, autoUnlockDelay = autoUnlockDelay)
```

After that you can choose states for move by either calling `selectTokens`:

```kotlin
val transactionBuilder: TransactionBuilder = ...
val participantSessions: List<FlowSession> = ...
val observerSessions: List<FlowSession> = ...
val requiredAmount: Amount<TokenType> = ...
// Just select states for spend, without output and change calculation
val selectedStates: List<StateAndRef<FungibleToken>> = localTokenSelector.selectStates(
    lockID = transactionBuilder.lockId, // Defaults to FlowLogic.currentTopLevel?.runId?.uuid ?: UUID.randomUUID()
    requiredAmount = requiredAmount,
    queryBy = queryBy) // TODO Add querying
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
    queryBy = queryBy) // TODO Add querying

// Call subflow
subflow(MoveTokensFlow(inputs, outputs, participantSessions, observerSessions))
// or use utilities functions
//... implement some business specific logic
addMoveTokens(transactionBuilder, inputs, outputs)
```
Then finalize transaction, update distribution list etc, see [Most common tasks](docs/IWantTo.md)

### Redeeming tokens using LocalTokenSelection

Using in memory selection when redeeming tokens looks very similar to move:

```kotlin
val vaultWatcherService = serviceHub.cordaService(VaultWatcherService::class.java)
val localTokenSelector = LocalTokenSelector(serviceHub, vaultWatcherService, autoUnlockDelay = autoUnlockDelay)

// Smilar to previous case, we need to choose states that cover the amount.
val exitStates: List<StateAndRef<FungibleToken>> = localTokenSelector.selectStates(
    localTokenSelector.selectStates(
    lockID = transactionBuilder.lockId, // Defaults to FlowLogic.currentTopLevel?.runId?.uuid ?: UUID.randomUUID()
    requiredAmount = requiredAmount,
    queryBy = queryBy) // TODO Add querying
)
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
