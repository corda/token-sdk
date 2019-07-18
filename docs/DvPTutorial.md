# Simple delivery versus payment tutorial

## Overview
This section will walk you step by step through the extremely simple delivery versus payment flow using token-sdk.

At the end of this section you will know how to:

* create, issue and update `EvolvableTokenType`s,
* issue fungible and non-fungible tokens,
* move tokens,
* use confidential identities,
* sign and finalise token transaction,
* construct business logic using the building blocks from `tokens-sdk`.

If you would like to see how to write simple integration tests using similar example take a look at: 
[driver test](../workflows/src/test/kotlin/com/r3/corda/lib/tokens/workflows/TokenDriverTest.kt)

## Basic components

Before doing this tutorial you should be familiar with the `FungibleToken`, `NonFungibleToken` and `IssuedTokenType` concepts
(see [Introduction](OVERVIEW.md) and [Design](../design/design.md) for more information). You could use `tokens-template`
to set up a project as described in [README](../README.md).

We will issue a simple `House` token onto the ledger (non-fungible token), then some money (fungible tokens). Then two parties can perform
`House` swap in exchange for money. We will build a flow that constructs simple transaction that combines two movements of tokens.


## Define your states and contracts

First let's define `House` state using `EvolvableTokenType`. We add `address` and `valuation` fields, `maintainers` and 
`fractionDigits` are required by `EvolvableTokenType`. `EvolvableTokenType`s are for storing token reference data that
we expect to change over time. In our use case house valuation can change.

```kotlin
// A token representing a house on ledger.
@BelongsToContract(HouseContract::class)
data class House(
        val address: String,
        val valuation: Amount<FiatCurrency>,
        override val maintainers: List<Party>,
        override val fractionDigits: Int = 0
) : EvolvableTokenType()
```

We should make sure that when transferring house we don't change the address in a state, also valuation should be greater than zero
(both when we create and update the state). Let's write some additional checks in `HouseContract`:

```kotlin
// House contract that adds additional checks on create and update of the token. 
class HouseContract : EvolvableTokenContract(), Contract {

    override fun additionalCreateChecks(tx: LedgerTransaction) {
        // Not much to do for this example token.
        val newHouse = tx.outputStates.single() as House
        newHouse.apply {
            require(valuation > Amount.zero(valuation.token)) { "Valuation must be greater than zero." }
        }
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {
        val oldHouse = tx.inputStates.single() as House
        val newHouse = tx.outputStates.single() as House
        require(oldHouse.address == newHouse.address) { "The address cannot change." }
        require(newHouse.valuation > Amount.zero(newHouse.valuation.token)) { "Valuation must be greater than zero." }
    }
}
```

**Note** In later Corda releases your evolvable states won't have to implement `Contract` only `EvolvableTokenContract`.

## Create and issue house onto the ledger. Issue some money for tests.

Let's take a look how to create and issue `EvolvableTokenType` onto the ledger.

```kotlin
    // From within the flow.
    val house: House = House(...)
    val notary: Party = getPreferredNotary(serviceHub) // Or provide notary party using your favourite function from NotaryUtilities.
    // We need to create the evolvable token first.
    subFlow(CreateEvolvableToken(house withNotary notary))
```

`CreateEvolvableToken` flow creates the evolvable state and shares it with maintainers and potential observers. To issue
a token that references the `EvolvableTokenType` we have to call one of the flows from the family of `IssueTokens` flows.

**Note** Because `EvolvableTokenType` is a `State` but not a `TokenType`, to issue it onto the ledger you need to convert it into `TokenPointer` first.
This way, the token can evolve independently to which party currently owns (some amount) of the token.


Let's issue `NonFungibleToken` referencing `House` held by Alice party.

```kotlin
    val aliceParty: Party = ...
    val issuerParty: Party = ourIdentity
    val housePtr = house.toPointer<House>()
    // Create NonFungibleToken referencing house with Alice party as an owner.
    val houseToken: NonFungibleToken> = housePtr issuedBy issuerParty heldBy aliceParty
    subFlow(ConfidentialIssueTokens(listOf(houseToken)))
```

There are some flows from issue tokens family that let you issue a token onto the ledger. In this case we used
`ConfidentialIssueTokens` this flow generates confidential identities first to use them in the transaction instead of well known
legal identities. Notice that issuer is always well known.

For testing delivery versus payment it would be great to have some money tokens issued onto the ledger as well. Issuing
fungible `GBP` tokens is straightforward:

```kotlin
    // Let's print some money!
    subFlow(IssueTokens(1_000_00.GBP issuedBy issuerParty heldBy otherParty)) // Initiating version of IssueFlow
```

**Note** There are different versions of `IssueFlow` inlined (that require you to pass in flow sessions) and initiating (callable via RPC),
 it's worth checking API documentation.

## Write initiator flow

After the initial setup phase let's write a simple flow that involves two parties that want to swap a house for some `GBP`.

```kotlin
    @StartableByRPC
    @InitiatingFlow
    class SellHouseFlow(val house: House, val newOwner: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            TODO("Implement delivery versus payment logic.")
        }
    }
```

Flow takes house to sell and new owner party. Let's define price notification to be sent to the new owner:

```kotlin
    @CordaSerializable
    data class PriceNotification(val amount: Amount<FiatCurrency>)
```

Then construct transaction builder with house move to the new owner:

```kotlin
    @Suspendable
    override fun call(): SignedTransaction {
        val housePtr = house.toPointer<House>()
        // We can specify preferred notary in cordapp config file, otherwise the first one from network parameters is chosen.
        val txBuilder = TransactionBuilder(notary = getPreferredNotary(serviceHub))
        addMoveTokens(txBuilder, housePointer, newOwner)
        ...
    }
```

Time to contact the counterparty to collect `GBP` states in exchange for house:

```kotlin
        ...
        // Initiate new flow session. If this flow is supposed to be called as inline flow, then session should have been already passed.
        val session = initiateFlow(newOwner)
        // Ask for input stateAndRefs - send notification with the amount to exchange.
        session.send(DvPNotification(house.valuation))
        // Receive GBP states back.
        val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken<FiatCurrency>>(session))
        // Receive outputs.
        val outputs = session.receive<List<FungibleToken<FiatCurrency>>>().unwrap { it }
        // For the future we could add some checks for inputs and outputs - that they sum up to house valuation,
        // usually we would like to implement it as part of the contract
        ...
```

Add move of `GBP` tokens to the transaction builder:

```kotlin
        ...
        addMoveTokens(txBuilder, inputs, outputs)
        ...
```

It can happen that input states to the transaction have confidential identities as participants, we should synchronise any
identities before the final phase:

```kotlin
        ...
        subFlow(IdentitySyncFlow.Send(session, txBuilder.toWireTransaction(serviceHub)))
        ...
```

## Signing and finalising transaction

The last step is signing the transaction by all parties involved:

```kotlin
        ...
        // Because states on the transaction can have confidential identities on them, we need to sign them with corresponding keys.
        val ourSigningKeys = txBuilder.toLedgerTransaction(serviceHub).ourSigningKeys(serviceHub)
        val initialStx = serviceHub.signInitialTransaction(txBuilder, signingPubKeys = ourSigningKeys)
        // Collect signatures from the new house owner.
        val stx = subFlow(CollectSignaturesFlow(initialStx, listOf(session), ourSigningKeys))
        ...
```

Evolvable tokens are special, because they can change over time. To keep all the parties interested notified about the update
distribution lists are used. This is a tactical solution that will be changed in the future for more robust design.
Distribution list is a list of identities that should receive updates, it's usually kept on the issuer node (and other maintainers nodes if specified).
For this mechanism to behave correctly we need to add special `UpdateDistributionListFlow` subflow:

```kotlin
        ...
        // Update distribution list.
        subFlow(UpdateDistributionListFlow(stx))
        // Finalise transaction! If you want to have observers notified, you can pass optional observers sessions.
        return subFlow(ObserverAwareFinalityFlow(stx, listOf(session)))
    }
```

## Write responder flow

The responder flow is pretty straightforward to write calling corresponding flow handlers in order:

```kotlin
    @InitiatedBy(SellHouseFlow::class)
    class SellHouseFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // Receive notification with house price.
            val dvPNotification = otherSession.receive<PriceNotification>().unwrap { it }
            // Generate fresh key, possible change outputs will belong to this key.
            val changeHolder = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false).party.anonymise()
            // Chose state and refs to send back.
            val (inputs, outputs) = TokenSelection(serviceHub).generateMove(
                    lockId = runId.uuid,
                    partyAndAmounts = listOf(PartyAndAmount(otherSession.counterparty, dvPNotification.amount)),
                    changeHolder = changeHolder
            )
            subFlow(SendStateAndRefFlow(otherSession, inputs))
            otherSession.send(outputs)
            subFlow(IdentitySyncFlow.Receive(otherSession))
            subFlow(object : SignTransactionFlow(otherSession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    // We should perform some basic sanity checks before signing the transaction. This step was omitted for simplicity.
                }
            })
            subFlow(ObserverAwareFinalityFlowHandler(otherSession))
        }
    }
```

Notice the ```TokenSelection(serviceHub).generateMove(...)``` used for chosing tokens that cover the required amount.

## Extra advanced topic
### Updating evolvable token

So why bother with the `EvolvableTokenType` if we don't use it (apart from obscure `UpdateDistributionListFlow` usage)?
Well, let's say because of unclear political situation prices of houses in London change. We could record this event by
updating the already issued house valuation and all interested parties will get notified of that change.

It's sufficient for the issuer (or house state maintainer) to update that token by simply running: 

```kotlin
    // Update that evolvable state on issuer node.
    val oldHouse: StateAndRef<House> = ... // Query for the state.
    val newHouse: House = oldHouse.state.data.copy(valuation = 800_000L.GBP)
    subFlow(UpdateEvolvableToken(oldStateAndRef = old, newState = new))
```


It will send the transaction with new updated house state to all parties on the issuer's distribution list. They will record it locally
in their vaults. Next time `housePtr.pointer.resolve(serviceHub)` is called it will contain updated state.