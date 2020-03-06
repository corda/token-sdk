# Simple delivery versus payment tutorial

## Overview

This section will walk you step by step through the extremely simple delivery versus payment flow using `token-sdk`.

At the end of this section you will know how to:

* create, issue and update `EvolvableTokenType`s,
* issue fungible and non-fungible tokens,
* move tokens,
* use confidential identities,
* sign and finalise token transaction,
* construct business logic using the building blocks from `tokens-sdk`.

If you would like to see how to write simple integration tests using similar example take a look at: 
[driver test](../workflows/src/integrationTest/kotlin/com/r3/corda/lib/tokens/integrationTest/TokenDriverTest.kt)


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
// kotlin

// A token representing a house on ledger.
@BelongsToContract(HouseContract::class)
data class House(
        val address: String,
        val valuation: Amount<TokenType>,
        override val maintainers: List<Party>,
        override val fractionDigits: Int = 0,
        override val linearId: UniqueIdentifier
) : EvolvableTokenType()
```
```java
@BelongsToContract(HouseContract.class)
public class House extends EvolvableTokenType {
    private final String address;
    private final Amount<TokenType> valuation;
    private final List<Party> maintainers;
    private final int fractionDigits = 0;
    private final UniqueIdentifier linearId;

    public House(String address, Amount<TokenType> valuation, List<Party> maintainers) {
        this.address = address;
        this.valuation = valuation;
        this.maintainers = maintainers;
        this.linearId = new UniqueIdentifier();
    }
    
    // Getters omitted.
}
```

We should make sure that when transferring house we don't change the address in a state, also valuation should be greater than zero
(both when we create and update the state). Let's write some additional checks in `HouseContract`:

```kotlin
// kotlin

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
```java
// java

// House contract that adds additional checks on create and update of the token.
public class HouseContract extends EvolvableTokenContract implements Contract {

    @Override
    public void additionalCreateChecks(@NotNull LedgerTransaction tx) {
        // Not much to do for this example token.
        House newHouse = (House) tx.getOutputStates().get(0);
        requireThat(req -> {
            req.using("Valuation must be greater than zero.", newHouse.getValuation().compareTo(Amount.zero(newHouse.getValuation().getToken())) > 0);    
            return null;
        }); 
    }

    @Override
    public void additionalUpdateChecks(@NotNull LedgerTransaction tx) {
        House oldHouse = (House) tx.getInputStates().get(0);
        House newHouse = (House) tx.getOutputStates().get(0);
        requireThat(req -> {
            req.using("The address cannot change.", oldHouse.getAddress().equals(newHouse.getAddress()));
            req.using("Valuation must be greater than zero.", newHouse.getValuation().compareTo(Amount.zero(newHouse.getValuation().getToken())) > 0);
            return null;
        });
    }
}
```

**Note** In later Corda releases your evolvable states won't have to implement `Contract` only `EvolvableTokenContract`.

## Create and issue house onto the ledger. Issue some money for tests.

Let's take a look how to create and issue `EvolvableTokenType` onto the ledger.

```kotlin
// kotlin

    // From within the flow.
    val house: House = House(...)
    val notary: Party = getPreferredNotary(serviceHub) // Or provide notary party using your favourite function from NotaryUtilities.
    // We need to create the evolvable token first.
    subFlow(CreateEvolvableTokens(house withNotary notary))
```
```java
// java

    // From within the flow.
    House house = new House(...);
    // Or provide notary party using your favourite function from NotaryUtilities.
    Party notary = NotaryUtilitiesKt.getPreferredNotary(getServiceHub(), NotaryUtilitiesKt.firstNotary());
    // We need to create the evolvable token first.
    subFlow(new CreateEvolvableTokens(new TransactionState<>(house, notary)));

```

`CreateEvolvableTokens` flow creates the evolvable state and shares it with maintainers and potential observers. To issue
a token that references the `EvolvableTokenType` we have to call one of the flows from the family of `IssueTokens` flows.

**Note** Because `EvolvableTokenType` is a `State` but not a `TokenType`, to issue it onto the ledger you need to convert it into `TokenPointer` first.
This way, the token can evolve independently to which party currently owns (some amount) of the token.


Let's issue `NonFungibleToken` referencing `House` held by Alice party.

```kotlin
// kotlin

    val aliceParty: Party = ...
    val issuerParty: Party = ourIdentity
    val housePtr = house.toPointer<House>()
    // Create NonFungibleToken referencing house with Alice party as an owner.
    val houseToken: NonFungibleToken = housePtr issuedBy issuerParty heldBy aliceParty
    subFlow(ConfidentialIssueTokens(listOf(houseToken)))
```
```java
// java

    Party aliceParty = ...;
    Party issuerParty = getOurIdentity();
    TokenPointer<House> housePtr = house.toPointer(House.class);
    // Create NonFungibleToken referencing house with Alice party as an owner.
    NonFungibleToken houseToken = TokenUtilities.heldBy(AmountUtilitiesKt.issuedBy(housePtr, issuerParty), aliceParty);
    subFlow(new ConfidentialIssueTokens(ImmutableList.of(houseToken)));
```

There are some flows from issue tokens family that let you issue a token onto the ledger. In this case we used
`ConfidentialIssueTokens` this flow generates confidential identities first to use them in the transaction instead of well known
legal identities. Notice that issuer is always well known.

For testing delivery versus payment it would be great to have some money tokens issued onto the ledger as well. Issuing
fungible `GBP` tokens is straightforward:

```kotlin
    // kotlin

    // Let's print some money!
    val otherParty: Party = ...
    subFlow(IssueTokens(listOf(1_000_00.GBP issuedBy issuerParty heldBy otherParty))) // Initiating version of IssueFlow
```
```java
    // java

    // Let's print some money!
    Party otherParty = ...;
    subFlow(new IssueTokens(ImmutableList.of(TokenUtilitiesKt.heldBy(AmountUtilitiesKt.issuedBy(UtilitiesKt.GBP(100000), issuerParty), otherParty));
```

**Note** There are different versions of `IssueFlow` inlined (that require you to pass in flow sessions) and initiating (callable via RPC),
 it's worth checking API documentation.

## Write initiator flow

After the initial setup phase let's write a simple flow that involves two parties that want to swap a house for some `GBP`.

```kotlin
// kotlin

    @StartableByRPC
    @InitiatingFlow
    class SellHouseFlow(val house: House, val newHolder: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            TODO("Implement delivery versus payment logic.")
        }
    }
```
```java
// java

    @StartableByRPC
    @InitiatingFlow
    public class SellHouseFlow extends FlowLogic<SignedTransaction> {
        private final House house;
        private final Party newHolder;
    
        public SellHouseFlow(House house, Party newHolder) {
            this.house = house;
            this.newHolder = newHolder;
        }
        
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // TODO("Implement delivery verses payment logic.")
            return null;
        }
    }
```

Flow takes house to sell and new owner party. Let's define price notification to be sent to the new owner:

```kotlin
// kotlin

    @CordaSerializable
    data class PriceNotification(val amount: Amount<TokenType>)
```
```java
// java

    @CordaSerializable
    class PriceNotification {
        private final Amount<TokenType> amount;
        public PriceNotification(Amount<TokenType> amount) { this.amount = amount; }
        public Amount<TokenType> getAmount() { return amount; }
    }   
```

Then construct transaction builder with house move to the new holder:

```kotlin
// kotlin

    @Suspendable
    override fun call(): SignedTransaction {
        val housePtr = house.toPointer<House>()
        // We can specify preferred notary in cordapp config file, otherwise the first one from network parameters is chosen.
        val txBuilder = TransactionBuilder(notary = getPreferredNotary(serviceHub))
        addMoveNonFungibleTokens(txBuilder, serviceHub, housePtr, newHolder)
        ...
    }
```
```java
// java

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        TokenPointer<House> housePtr = house.toPointer(House.class);
        // We can specify preferred notary in cordapp config file, otherwise the first one from network parameters is chosen.
        TransactionBuilder txBuilder = new TransactionBuilder(NotaryUtilitiesKt.getPreferredNotary(getServiceHub(), NotaryUtilitiesKt.firstNotary()));
        MoveTokensUtilitiesKt.addMoveNonFungibleTokens(txBuilder, getServiceHub(), housePtr, newHolder);
        ...
    }
```

Time to contact the counterparty to collect `GBP` states in exchange for house:

```kotlin
// kotlin

        ...
        // Initiate new flow session. If this flow is supposed to be called as inline flow, then session should have been already passed.
        val session = initiateFlow(newHolder)
        // Ask for input stateAndRefs - send notification with the amount to exchange.
        session.send(PriceNotification(house.valuation))
        // Receive GBP states back.
        val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken>(session))
        // Receive outputs.
        val outputs = session.receive<List<FungibleToken>>().unwrap { it }
        // For the future we could add some checks for inputs and outputs - that they sum up to house valuation,
        // usually we would like to implement it as part of the contract
        ...
```
```java
// java
    
        ...
        // Initiate new flow session. If this flow is supposed to be called as inline flow, then session should have been already passed.
        FlowSession session = initiateFlow(newHolder);
        // Ask for input stateAndRefs - send notification with the amount to exchange.
        session.send(new PriceNotification(house.getValuation()));
        // Receive GBP states back.
        List<StateAndRef<FungibleToken>> inputs = subFlow(new ReceiveStateAndRefFlow<FungibleToken>(session));
        // Receive outputs.
        List<FungibleToken> outputs = session.receive(List.class).unwrap(it -> it);
        // For the future we could add some checks for inputs and outputs - that they sum up to house valuation,
        // usually we would like to implement it as part of the contract
        ...
```

Add move of `GBP` tokens to the transaction builder:

```kotlin
// kotlin

        ...
        addMoveTokens(txBuilder, inputs, outputs)
        ...
```
```java
// java

        ...
        MoveTokensUtilitiesKt.addMoveTokens(txBuilder, inputs, outputs);
        ...
```

It can happen that input states to the transaction have confidential identities as participants, we should synchronise any
identities before the final phase:

```kotlin
// kotlin

        ...
        subFlow(SyncKeyMappingFlow(session, txBuilder.toWireTransaction(serviceHub)))
        ...
```
```java
// java

        ...
        subFlow(new SyncKeyMappingFlow(session, txBuilder.toWireTransaction(getServiceHub())));
        ...
```

## Signing and finalising transaction

The last step is signing the transaction by all parties involved:

```kotlin
// kotlin

        ...
        // Because states on the transaction can have confidential identities on them, we need to sign them with corresponding keys.
        val ourSigningKeys = txBuilder.toLedgerTransaction(serviceHub).ourSigningKeys(serviceHub)
        val initialStx = serviceHub.signInitialTransaction(txBuilder, signingPubKeys = ourSigningKeys)
        // Collect signatures from the new house owner.
        val stx = subFlow(CollectSignaturesFlow(initialStx, listOf(session), ourSigningKeys))
        ...
```
```java
// java

        ...
        // Because states on the transaction can have confidential identities on them, we need to sign them with corresponding keys.
        List<PublicKey> ourSigningKeys = FlowUtilitiesKt.ourSigningKeys(txBuilder.toLedgerTransaction(getServiceHub()), getServiceHub());
        SignedTransaction initialStx = getServiceHub().signInitialTransaction(txBuilder, ourSigningKeys);
        SignedTransaction stx = subFlow(new CollectSignaturesFlow(initialStx, ImmutableList.of(session), ourSigningKeys));
        ...
```

Evolvable tokens are special, because they can change over time. To keep all the parties interested notified about the update
distribution lists are used. This is a tactical solution that will be changed in the future for more robust design.
Distribution list is a list of identities that should receive updates, it's usually kept on the issuer node (and other maintainers nodes if specified).
For this mechanism to behave correctly we need to add special `UpdateDistributionListFlow` subflow:

```kotlin
// kotlin
        ...
        // Update distribution list.
        subFlow(UpdateDistributionListFlow(stx))
        // Finalise transaction! If you want to have observers notified, you can pass optional observers sessions.
        return subFlow(ObserverAwareFinalityFlow(stx, listOf(session)))
    }
```
```java
// java

        ...
        // Update the distribution list.
        subFlow(new UpdateDistributionListFlow(stx));
        // Finalise transaction! If you want to have observers notified, you can pass optional observers sessions.
        return subFlow(new ObserverAwareFinalityFlow(stx, ImmutableList.of(session)));
        ...
```

## Write responder flow

The responder flow is pretty straightforward to write calling corresponding flow handlers in order:

```kotlin
// kotlin

    @InitiatedBy(SellHouseFlow::class)
    class SellHouseFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // Receive notification with house price.
            val priceNotification = otherSession.receive<PriceNotification>().unwrap { it }
            // Generate fresh key, possible change outputs will belong to this key.
            val changeHolder = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false).party.anonymise()
            // Chose state and refs to send back.
            val (inputs, outputs) = DatabaseTokenSelection(serviceHub).generateMove(
                lockId = runId.uuid,
                partiesAndAmounts = listOf(Pair(otherSession.counterparty, priceNotification.amount)),
                changeHolder = changeHolder
            )
            subFlow(SendStateAndRefFlow(otherSession, inputs))
            otherSession.send(outputs)
            subFlow(SyncKeyMappingFlowHandler(otherSession))
            subFlow(object : SignTransactionFlow(otherSession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    // We should perform some basic sanity checks before signing the transaction. This step was omitted for simplicity.
                }
            })
            subFlow(ObserverAwareFinalityFlowHandler(otherSession))
        }
    }
```
```java
// java

@InitiatedBy(SellHouseFlow)
public class SellHouseFlowHandler extends FlowLogic<Void> {
    private final FlowSession otherSession;

    public SellHouseFlowHandler(FlowSession otherSession) {
        this.otherSession = otherSession;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        // Receive notification with house price.
        TestFlow.PriceNotification priceNotification = otherSession.receive(TestFlow.PriceNotification.class).unwrap(it -> it);
        // Generate fresh key, possible change outputs will belong to this key.
        AnonymousParty changeHolder = getServiceHub().getKeyManagementService().freshKeyAndCert(getOurIdentityAndCert(), false).getParty().anonymise();
        // Choose state and refs to send back.
        DatabaseTokenSelection tokenSelection = (new DatabaseSelectionConfig().toSelector(getServiceHub()));
        Pair<List<StateAndRef<FungibleToken>>, List<FungibleToken>> selectedTokens = tokenSelection.generateMove(
                Collections.singletonList(new Pair<AbstractParty, Amount<TokenType>>(otherSession.getCounterparty(), priceNotification.getAmount())),
                changeHolder,
                new TokenQueryBy(null, it -> {
                    return true;
                }, null),
                getRunId().getUuid()
        );
        List<StateAndRef<FungibleToken>> inputs = selectedTokens.getFirst();
        List<FungibleToken> outputs = selectedTokens.getSecond();
        subFlow(new SendStateAndRefFlow(otherSession, inputs));
        otherSession.send(outputs);
        subFlow(new SyncKeyMappingFlowHandler(otherSession));
        subFlow(new SignTransactionFlow(otherSession) {
            @Override
            protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                // We should perform some basic sanity checks before signing the transaction. This step was omitted for simplicity.
            }
        });
        subFlow(new ObserverAwareFinalityFlowHandler(otherSession));
        
        return null;
    }
}
```

Notice the ```DatabaseTokenSelection(serviceHub).generateMove(...)``` used for chosing tokens that cover the required amount.

## Extra advanced topic
### Updating evolvable token

So why bother with the `EvolvableTokenType` if we don't use it (apart from obscure `UpdateDistributionListFlow` usage)?
Well, let's say because of unclear political situation prices of houses in London change. We could record this event by
updating the already issued house valuation and all interested parties will get notified of that change.

It's sufficient for the issuer (or house state maintainer) to update that token by simply running: 

```kotlin
// kotlin

    // Update that evolvable state on issuer node.
    val oldHouse: StateAndRef<House> = ... // Query for the state.
    val newHouse: House = oldHouse.state.data.copy(valuation = 800_000L.GBP)
    subFlow(UpdateEvolvableToken(oldStateAndRef = oldHouse, newState = newHouse))
```
```java
// java
    
    // Update that evolvable state on issuer node.
    StateAndRef<House> oldHouse = ...; // Query for the state.
    House newHouse = new House(
            oldHouse.getState().getData().getAddress(),
            UtilitiesKt.GBP(800000),
            oldHouse.getState().getData().getMaintainers(),
            oldHouse.getState().getData().getFractionDigits(),
            oldHouse.getState().getData().getLinearId()
    );
    subFlow(new UpdateEvolvableToken(oldHouse, newHouse));

```


It will send the transaction with new updated house state to all parties on the issuer's distribution list. They will record it locally
in their vaults. Next time `housePtr.pointer.resolve(serviceHub)` is called it will contain updated state.

## Next steps

[Most common tasks](IWantTo.md)