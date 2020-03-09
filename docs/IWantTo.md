# How do I?
## Create fungible and non-fungible tokens
### Creating an instance of the `TokenType`

Two `TokenType` helpers already exist in the token SDK, `FiatCurrency` and
`DigitalCurrency`. There are easy to use utilities for both, for example:

```kotlin
// kotlin

    val pounds: TokenType = GBP
    val euros: TokenType = EUR
    val bitcoin: TokenType = BTC
```
```java
// java

    TokenType pounds = FiatCurrency.Companion.getInstance("GBP");
    TokenType euros = FiatCurrency.Companion.getInstance("EUR");
    TokenType bitcoin = FiatCurrency.Companion.getInstance("BTC");
```

Creating your own tokens is easy; just create an instance of `TokenType` class. You 
will need to specify a `tokenIdentifier` property and how many `fractionDigits` 
amounts of this token can have. E.g.

* "0" for zero fraction digits where there can only exist whole numbers
  of your token type, and
* "2" for two decimal places like GBP, USD and EUR

You can also add a `toString` override, if you like.

```kotlin
// kotlin

    val myTokenType: TokenType = TokenType(tokenIdentifier = "TEST", fractionDigits = 2)
```
```java
// java

    TokenType myTokenType = new TokenType("TEST", 2);
```

The `tokenIdentifier` is used along with the `tokenClass` property (defined
in `TokenType`) when serializing token types. Two properties are required,
as with `FiatCurrency` and `DigitalCurrency`, there can be many different
instances of one `tokenClass`, each with their own `tokenIdentifier`.

### Creating an instance of an `IssuedTokenType`

Create an `IssuedTokenType` as follows

```kotlin
// kotlin

    // With your own instance of token type.
    val issuer: Party = ...
    val myTokenType = TokenType("MyToken", 2)
    val issuedTokenType: IssuedTokenType = myTokenType issuedBy issuer

    // Or with the built in tokens.
    val issuedGbp: IssuedTokenType = GBP issuedBy issuer
    val issuedBtc: IssuedTokenType  = BTC issuedBy issuer
```
```java
// java

    // With your own instance of token type.
    Party issuer = ...;
    TokenType myTokenType = new TokenType("MyToken", 2);
    IssuedTokenType issuedTokenType = AmountUtilitiesKt.issuedBy(myTokenType, issuer);
    
    // Or with the built in tokens.
    IssuedTokenType issuedGbp = AmountUtilitiesKt.issuedBy(FiatCurrency.Companion.getInstance("GBP"), issuer);
    IssuedTokenType issuedBtc = AmountUtilitiesKt.issuedBy(FiatCurrency.Companion.getInstance("BTC"), issuer);

```

The issuing party must be a `Party` as opposed to an `AbstractParty` or
`AnonymousParty`, this is because the issuer must be well known.

The `issuedBy` syntax uses a kotlin infix extension function.

### Creating an amount of some `IssuedTokenType`

Once you have an `IssuedTokenType` you can optionally create some amount
of it using the `of` syntax. For example:

```kotlin
// kotlin

    val issuer: Party = ...
    val myTokenType = TokenType("MyToken", 2)
    val myIssuedTokenType: IssuedTokenType = myTokenType issuedBy issuer
    val tenOfMyIssuedTokenType = 10 of myIssuedTokenType
```
```java
// java

    Party issuer = ...;
    TokenType myTokenType = new TokenType("MyToken", 2);
    IssuedTokenType myIssuedTokenType = AmountUtilitiesKt.issuedBy(myTokenType, issuer);
    Amount<IssuedTokenType> tenOfMyIssuedTokenType = AmountUtilitiesKt.amount(10, myIssuedTokenType);
```

Or:

```kotlin
// kotlin

    val tenPounds: Amount<IssuedTokenType> = 10 of GBP issuedBy issuer
```
```java
// java

    Amount<IssuedTokenType> tenPounds = AmountUtilitiesKt.amount(10, AmountUtilitiesKt.issuedBy(FiatCurrency.Companion.getInstance("GBP"), issuer));
```

Or:

```kotlin
// kotlin

    val tenPounds = 10.GBP issuedBy issuer
```
```java
// java

    Amount<IssuedTokenType> tenPounds = AmountUtilitiesKt.issuedBy(UtilitiesKt.GBP(10), issuer);
```

If you do not need to create amounts of your token because it is always
intended to be issued as a `NonFungibleToken` then you don't have to create
amounts of it.

### Creating instances of `FungibleToken`s and `NonFungibleToken`s

To get from a token type or some amount of a token type to a non-fungible
token or fungible token, we need to specify which party the proposed holder
is. This can be done using the `heldBy` syntax:

```kotlin
// kotlin

    val issuer: Party = ...
    val holder: Party = ...

    val myTokenType = TokenType("MyToken", 2)
    val myIssuedTokenType: IssuedTokenType = myTokenType issuedBy issuer
    val tenOfMyIssuedTokenType = 10 of myIssuedTokenType

    // Adding a holder to an amount of a token type, creates a fungible token.
    val fungibleToken: FungibleToken = tenOfMyIssuedTokenType heldBy holder
    // Adding a holder to a token type, creates a non-fungible token.
    val nonFungibleToken: NonFungibleToken = myIssuedTokenType heldBy holder
```
```java
// java

    Party issuer = ...;
    Party holder = ...;
    
    TokenType myTokenType = new TokenType("MyToken", 2);
    // Note: in java, Amounts and TokenTypes can also be instatiated with their constructors in addition to the provided utilities
    IssuedTokenType myIssuedTokenType = new IssuedTokenType(issuer, myTokenType);
    Amount<IssuedTokenType> tenOfMyIssuedTokenType = new Amount<>(10, myIssuedTokenType);
    
    // Adding a holder to an amount of a token type, creates a fungible token
    FungibleToken fungibleToken = TokenUtilitiesKt.heldBy(tenOfMyIssuedTokenType, holder);
    NonFungibleToken nonFungibleToken = TokenUtilities.heldBy(myIssuedTokenType, holder);
```

Once you have a `FungibleToken` or a `NonFungibleToken`, you can then go
and issue that token on ledger.

## Use flows from token-sdk? Issue, move and redeem tokens.

All the common flows that come with `token-sdk` have different versions. We tried to
predict most common use cases for issue, move and redeem. So all of the flows below come in fungible and non-fungible,
confidential, inlined and initiating versions (only redeem has slightly different form, it doesn't have confidential non-fungible flow
as there are never any change outputs to handle).

Let's take a look how these behaviours are different. While fungible and non-fungible versions are straightforward we need some more
explanation about inline and initiating flows.
When do we use them? General rule is that when a flow is a part of more complicated business logic that involves opening many
sessions before it's called, then we could reuse already opened sessions, you should use inline versions of that flow.
If you use wrong initiating version of the flow, you will get an error message indicating that there was mismatch in expected
`send`/`receive` order. Initiating flows start sessions with counterparties after passing the `Party` objects as parameters.

Additionally, all flows take observer sessions (or observer parties in initiating versions). Observers become important in
transaction finalisation step. Transaction will be broadcasted to the participants as well as to the observers to be recorded
in the vault (recording strategy for the observers is `StatesToRecord.ALL_VISIBLE`).

Confidential versions of flows generate and swap new keys for the parties involved in a transaction. These new identities will
be used in output states.

If more control over what is in the transaction is needed `token-sdk` provides useful utilities that take `TransactionBuilder`
and `addMoveTokens`, `addIssueTokens`, `addRedeemTokens` can be called depending what kind of transaction developer wants to construct.
Developer is responsible for generating confidential identities, finalisation and possible updating of distribution lists 
(see section below on that).

It is possible to specify a preferred notary from the list in network parameters. On default it is taken from cordapp configuration
file (include `notary` string with X500 name of the notary you wish to use). If no preferred notary is specified then
the first one will be used.

### Issuing tokens

**Usage**

For issue tokens we don't differentiate between fungible and non-fungible flows. Issue flows take `AbstractToken` as parameter.
What it does internally is pretty simple, all the flows below construct an issuance transaction (no inputs, only outputs and `IssueTokenCommand`)
and finalise it with participants and possible observers. Distribution lists get updated after finalisation.
Call these flows for one instance of `TokenType` at a time. If you need to do multiple instances of token type in one transaction then create a new
flow, calling `addIssueTokens` for each token.
Confidential versions additionally request that the recipients generate new keys for the output holders.

**Initiating**

```kotlin
// kotlin

// As in previous examples
val fungibleToken: FungibleToken = ...
val nonFungibleToken: NonFungibleToken = ...
// Start flows via RPC or as a subFlow (it starts a new session with a holder of the token!)
// All of the below flows can take a list of observer parties.

// Fungible
IssueTokens(listOf(fungibleToken))
IssueTokens(listOf(10 of myTokenType issuedBy issuer heldBy holder))
IssueTokens(listOf(10 of myIssuedTokenType heldBy holder))

// Nonfungible
IssueTokens(listOf(nonFungibleToken))
IssueTokens(listOf(myTokenType issuedBy issuer heldBy holder))
IssueTokens(listOf(myIssuedTokenType heldBy holder))
```
```java
// java

// As in previous examples
FungibleToken fungibleToken = ...;
NonFungibleToken nonFungibleToken = ...;
// Start flows via RPC or as a subFlow (it starts a new session with a holder of the token!)
// All of the below flows can take a list of observer parties.


// Fungible
new IssueTokens(Collections.singletonList(fungibleToken));
new IssueTokens(Collections.singletonList(
        TokenUtilitiesKt.heldBy(
                AmountUtilitiesKt.issuedBy(
                        AmountUtilitiesKt.amount(10, myTokenType),
                        issuer
                ),
                holder
        )
));
new IssueTokens(Collections.singletonList(
        TokenUtilitiesKt.heldBy(
                AmountUtilitiesKt.amount(10, myIssuedTokenType),
                holder
        )
));

// Nonfungible
new IssueTokens(Collections.singletonList(nonFungibleToken));
new IssueTokens(Collections.singletonList(
        TokenUtilities.heldBy(
                AmountUtilitiesKt.issuedBy(
                        myTokenType,
                        issuer
                ),
                holder
        )
));
new IssueTokens(Collections.singletonList(
        TokenUtilities.heldBy(
                myIssuedTokenType,
                holder
        )
));
```

There are many other constructor overloads for initiating `IssueTokens` it's worth investigating the class itself.

_Responder flow:_ `IssueTokensHandler`

_Conidential version:_ `ConfidentialIssueTokens`, _responder_: `ConfidentialIssueTokensHandler`

**Inline**

```kotlin
// kotlin

// We need to pass in counterparties sessions.
val holderSession = initateFlow(holder)
...
// All of the below flows can take a list of observer sessions.
// Fungible
subFlow(IssueTokensFlow(listOf(fungibleToken), listOf(holderSession)))
// NonFungible
subFlow(IssueTokensFlow(listOf(nonFungibleToken), listOf(holderSession)))
``` 
```java

// We need to pass in counterparties sessions.
FlowSession holderSession = initiateFlow(holder);
...
// All of the below flows can take a list of observer sessions.
// Fungible
subFlow(new IssueTokensFlow(Collections.singletonList(fungibleToken), ImmutableList.of(holderSession)));
// NonFungible
subFlow(new IssueTokensFlow(Collections.singletonList(nonFungibleToken), ImmutableList.of(holderSession)));
```

There are other constructor overloads worth investigating.

_Responder flow:_ `IssueTokensFlowHandler`

**Confidential inline**

```kotlin
// kotlin

// We need to pass in counterparties sessions.
val holderSession = initateFlow(holder)
...
// All of the below flows can take a list of observer sessions.
// Fungible
subFlow(ConfidentialIssueTokensFlow(listOf(fungibleToken), listOf(holderSession)))
// NonFungible
subFlow(ConfidentialIssueTokensFlow(listOf(nonFungibleToken), listOf(holderSession)))
```
```java
// java

// We need to pass in counterparties sessions.
FlowSession holderSession = initiateFlow(holder);
...
// All of the below flows can take a list of observer sessions.
// Fungible
subFlow(new ConfidentialIssueTokensFlow(Collections.singletonList(fungibleToken), ImmutableList.of(holderSession)));
// NonFungible
subFlow(new ConfidentialIssueTokensFlow(Collections.singletonList(nonFungibleToken), ImmutableList.of(holderSession)));

```

_Responder flow:_ `ConfidentialIssueTokensFlowHandler`

### Moving tokens

**Usage**

Move tokens flows fall into one of the two categories: fungible and non-fungible. Both versions differ significantly in
how states are selected for spend. For more details see `TokenSelection`. The common tasks performed are: choosing states
to spend from `Vault`, generating possible change outputs, adding inputs and outputs with new holders to the transaction
with `MoveTokenCommand`, finalisation and distribution list update. Notary is chosen based on the inputs notary. We don't support
notary change for now.
Call these flows for one `TokenType` at a time. If you need to do multiple instances of token type in one transaction then create a new
flow, calling `addMoveTokens` for each token.
As previously confidential versions generate new identities for use in output states.

#### Moving tokens fungible tokens

This family of flows chooses held amount of given token from vault. If you want to provide other criteria (for example tokens that
come only from one issuer) use `queryCriteria`. `QueryUtilities` module provides many useful helpers i.e. `tokenAmountWithIssuerCriteria`.
You can move many tokens to different parties in one transaction, to do so specify map of `partiesAndAmounts` respectively.
As usual you can provide additional observers parties/sessions for finalization with other interested parties on the network.

**Initiating**

```kotlin
// kotlin

val holder: Party = ...
val otherHolder: Party = ...
val issuer: Party = ...

// Move amount of token to the new holder
MoveFungibleTokens(100 of myTokenType, holder)
// Move different amounts of token to multiple holders
MoveFungibleTokens(listOf(PartyAndAmount(holder, 13 of myTokenType), PartyAndAmount(otherHolder, 44 of myTokenType)))
// Move amount of token issued by particular issuer to the new holder - this is an example of using optional queryCriteria
// parameter.
MoveFungibleTokens(
    partyAndAmount = PartyAndAmount(holder, 5 of myTokenType),
    observers = emptyList<Party>(),
    queryCriteria = tokenAmountWithIssuerCriteria(myTokenType, issuer)
)
```
```java
// java

Party holder = ...;
Party otherHolder = ...;
Party issuer = ...;

// Move amount of token to the new holder
new MoveFungibleTokens(
        AmountUtilitiesKt.amount(10, myTokenType),
        holder
);
// Move different amounts of token to multiple holders
Amount<TokenType> amountOne = AmountUtilitiesKt.amount(13, myTokenType);
Amount<TokenType> amountTwo = AmountUtilitiesKt.amount(44, myTokenType);
new MoveFungibleTokens(ImmutableList.of(
        new PartyAndAmount<>(holder, amountOne),
        new PartyAndAmount<>(otherHolder, amountTwo)
));
// Move amount of token issued by particular issuer to the new holder - this is an example of using optional queryCriteria
// parameter.
new MoveFungibleTokens(
        new PartyAndAmount<TokenType>(holder, amountOne),
        Collections.emptyList(),
        SelectionUtilitiesKt.tokenAmountWithIssuerCriteria(myTokenType, issuer)
);
```

_Responder flow:_ `MoveFungibleTokensHandler`

_Confidential version:_ `ConfidentialMoveFungibleTokens`, _responder_: `ConfidentialMoveFungibleTokensHandler`

**Inline**

```kotlin
// kotlin

// We need to pass in counterparties sessions.
val holderSession = initateFlow(holder)
val otherHolderSession = initateFlow(otherHolder)
...
// All of the below flows can take a list of observer sessions.
// Construct many moves in one transaction.
subFlow(MoveFungibleTokensFlow(listOf(PartyAndAmount(holder, 13 of myTokenType), PartyAndAmount(otherHolder, 44 of myTokenType)),
    listOf(holderSession, otherHolderSession))
)
// Move only tokens issued by particular issuer.
subFlow(MoveFungibleTokensFlow(
    partyAndAmount = PartyAndAmount(holder, 5 of myTokenType),
    queryCriteria = tokenAmountWithIssuerCriteria(myTokenType, issuer),
    participantSessions = listOf(holderSession, otherHolderSession),
    observers = emptyList<FlowSession>()    
))
```
```java
// java

// We need to pass in counterparties sessions.
FlowSession holderSession = initiateFlow(holder);
FlowSession otherHolderSession = initiateFlow(otherHolder);
...
// All of the below flows can take a list of observer sessions.
// Construct many moves in one transaction.
Amount<TokenType> amountOne = AmountUtilitiesKt.amount(13, myTokenType);
Amount<TokenType> amountTwo = AmountUtilitiesKt.amount(44, myTokenType);
subFlow(new MoveFungibleTokensFlow(
        ImmutableList.of(
            new PartyAndAmount<TokenType>(holder, amountOne),
            new PartyAndAmount<TokenType>(otherHolder, amountTwo)),
        ImmutableList.of(holderSession, otherHolderSession)
));
// Move only tokens issued by particular issuer.
Amount<TokenType> amount = new Amount<>(5, myTokenType);
subFlow(new MoveFungibleTokensFlow(
        new PartyAndAmount<TokenType>(holder, amount),
        SelectionUtilitiesKt.tokenAmountWithIssuerCriteria(myTokenType, issuer),
        ImmutableList.of(holderSession, otherHolderSession),
        Collections.emptyList()
));
```

_Responder flow:_ `MoveTokensFlowHandler`

_Confidential version:_ `ConfidentialMoveFungibleTokensFlow`, _responder:_ `ConfidentialMoveTokensFlowHandler`

#### Moving non-fungible tokens

**Initiating**

```kotlin
// kotlin

val holder: Party = ...
...
// Move non fungible token to the new holder
MoveNonFungibleTokens(PartyAndToken(holder, myTokenType))
```
```java
// java

Party holder = ...
...
// Move non fungible token to the new holder
new MoveNonFungibleTokens(new PartyAndToken(holder, myTokenType));
```

Similar to previous examples you can provide `queryCriteria` and list of observer parties.

_Responder flow:_ `MoveNonFungibleTokensHandler`

_Confidential version:_ `ConfidentialMoveNonFungibleTokens`, _responder_: `ConfidentialMoveNonFungibleTokensHandler`

**Inline**

```kotlin
// kotlin

val holderSession = initateFlow(holder)
val observerSession = initatieFlow(observer)
subFlow(MoveNonFungibleTokensFlow(PartyAndToken(holder, myTokenType), listOf(holderSession), listOf(observerSession)))
```
```java
// java

FlowSession holderSession = initiateFlow(holder);
FlowSession observerSession = initiateFlow(observer);
subFlow(new ConfidentialMoveNonFungibleTokensFlow(
        new PartyAndToken(holder, myTokenType),
        ImmutableList.of(holderSession),
        ImmutableList.of(observerSession)
));
```

_Responder flow:_ `MoveTokensFlowHandler`

_Confidential version:_ `ConfidentialMoveNonFungibleTokensFlow`, _responder:_ `ConfidentialMoveTokensFlowHandler`

### Redeeming tokens

**Usage**

Similar to `MoveTokens` family of flows, redeem flows are either fungible or non-fungible. Difference is in the selection of
states to redeem from `Vault`. For more details see `TokenSelection`. Transaction constructed has states to redeem as inputs,
possible single change output and `RedeemTokenCommand` with signatures both from holder and issuer.

The most important thing about usage of these flows
is that they are initiatied by the holder of the tokens. Initiating party sends transaction proposal with states to redeem
and possible change output to the issuer. Issuer is always a well known party, it performs basic checks on the transaction.
There is additional step that synchronises any confidential identities from the states to redeem with the issuer (bear in
mind that issuer usually isn't involved in confidential move of tokens). After that simple collect signatures and finalisation is done.

Call these flows for one `TokenType` at a time. If you need to do multiple instances of token type in one transaction then create a new
flow, calling `addTokensToRedeem`, `addNonFungibleTokensToRedeem` or`addFungibleTokensToRedeem` for each token 
(see `RedeemFlowUtilities.kt` for more documentation). All of the flows take observer parties/sessions as usual.

The main difference to move flows is that only fungible redeem tokens flows have confidential versions. This is due to 
the fact no change is paid in non-fungible case, so there are no output states to generate confidential identities for.

#### Redeeming fungible tokens

Selection is performed using the same utilities used in `MoveFungibleTokens`.

**Initiating**

```kotlin
// kotlin

val amountToRedeem = 10.GBP
val issuerParty: Party = ...
val observerParty: Party = ...

// It is also possible to provide custom query criteria for token selection.
RedeemFungibleTokens(amount = amountToRedeem, issuer = issuerParty, observers = listOf(observerParty))
```
```java
// java

Party issuerParty = ...;
Party observerParty = ...;
Amount<TokenType> amountToRedeem = UtilitiesKt.GBP(10);

// It is also possible to provide custom query criteria for token selection.
new RedeemFungibleTokens(
        amountToRedeem,
        issuerParty,
        ImmutableList.of(observerParty)
);
```

_Responder flow:_ `RedeemFungibleTokensHandler`

_Confidential version:_ `ConfidentialRedeemFungibleTokens`, _responder_: `ConfidentialRedeemFungibleTokensHandler`

**Inline**

```kotlin
// kotlin

val issuerSession = initateFlow(issuer)
val observerSession = initatieFlow(observer)
val changeHolder: AbstractParty = ... // It can be either confidential identity belonging to tokens holder or well known identity of holder
// It is also possible to provide custom query criteria for token selection.
subFlow(RedeemFungibleTokensFlow(
    amount = 1000.GBP, 
    issuerSession = issuerSession,
    changeHolder = changeHolder,
    observerSessions = listOf(observerSession)
))
```
```java
// java

FlowSession issuerSession = initiateFlow(issuer);
FlowSession observerSession = initiateFlow(observer);
AbstractParty changeHolder = ...; // It can be either confidential identity belonging to tokens holder or well known identity of holder
// It is also possible to provide custom query criteria for token selection.

subFlow(new RedeemFungibleTokensFlow(
        UtilitiesKt.GBP(1000),
        issuerSession,
        changeHolder,
        ImmutableList.of(observerSession)
));
```

_Responder flow:_ `RedeemTokensFlowHandler`

_Confidential version:_ `ConfidentialRedeemFungibleTokensFlow`,
 
 _responder_: `ConfidentialRedeemFungibleTokensFlowHandler`

#### Redeeming non-fungible tokens

**Initiating**

```kotlin
// kotlin

val myTokenType: TokenType = ...
val issuerParty: Party = ...
val observerParty: Party = ...

RedeemNonFungibleTokens(myTokenType, issuerParty, listOf(observerParty))
```
```java
// java

TokenType myTokenType = ...;
Party issuerParty = ...;
Party observerParty = ...;

new RedeemNonFungibleTokens(myTokenType, issuerParty, ImmutableList.of(observerParty));
```

_Responder flow:_ `RedeemNonFungibleTokensHandler`

**Inline**

```kotlin
// kotlin

val myTokenType: TokenType = ...
val issuerSession = initateFlow(issuerParty)
val observerSession = initatieFlow(observerParty)
subFlow(RedeemNonFungibleTokensFlow(myTokenType, issuerSession, listOf(observerSession)))
```
```java
// java

TokenType myTokenType = ...;
FlowSession issuerSession = initiateFlow(issuerParty);
FlowSession observerSession = initiateFlow(observerParty);
subFlow(new RedeemNonFungibleTokensFlow(myTokenType, issuerSession, ImmutableList.of(observerSession)));
```

_Responder flow:_ `RedeemTokensFlowHandler`

**No confidential versions** - there is no change paid back.

### Finalising token transactions

If you wish to construct your token transaction using utility functions instead of using ready flows there are some steps that
need to be performed to correctly finalise it. 

**Calling ObserverAwareTokensFlow**

Additionally to normal `FinalityFlow` we introduced `ObserverAwareFinalityFlow` that takes additional flow sessions for
observer nodes. Observer is an identity that is not a participant in a transaction, but should be informed about it.
Observers record states in `StatesToRecord.ALL_VISIBLE` mode. Participants handle the transaction as usual.
You can call it at the finalisation step:

```kotlin
// kotlin

val stx: SignedTransaction = ...
val participantSession: FlowSession = initiateFlow(participantParty)
val observerSession: FlowSession = initiateFlow(observerParty)
subFlow(ObserverAwareFinalityFlow(stx, listOf(participantSession, observerSession)))
```
```java
// java

SignedTransaction stx = ...;
FlowSession participantSession = initiateFlow(participantParty);
FlowSession observerSession = initiateFlow(observerParty);
subFlow(new ObserverAwareFinalityFlow(stx, ImmutableList.of(participantSession, observerSession)));

```

**Keeping distribution lists up-to-date**

This is temporary solution for distributing updates to evolvable tokens. It will be changed in the future for more robust design
using data distribution groups. For now it is important when using pointers to evolvable tokens to call `UpdateDistributionListFlow`
that takes care of adding new parties to the distribution list kept by the token maintainer (usually it's issuer).

Simply call at the end of your flow:

```kotlin
// kotlin

val stx: SignedTransaction = ...
subFlow(UpdateDistributionListFlow(stx))
```
```java
// java

SignedTransaction stx = ...;
subFlow(new UpdateDistributionListFlow(stx));
```

### Creating your own subtypes of TokenType

If type-safety is required or if you need to define custom properties on
top of the `tokenIdentifier` and `fractionDigits` then it is still
possible to create your own `TokenType` sub-type by sub-classing `TokenType`.

```kotlin
// kotlin

class MyTokenType(override val tokenIdentifier: String, override val fractionDigits: Int = 0) : TokenType(tokenIdentifier, fractionDigits)
```
```java

class MyTokenType extends TokenType {
    public MyTokenType(@NotNull String tokenIdentifier) { super(tokenIdentifier, 0); }
    public MyTokenType(@NotNull String tokenIdentifier, int fractionDigits) {
        super(tokenIdentifier, fractionDigits);
    }
}
```

The above defined token type, allows CorDapp developers to create multiple
instances of the token type with different identifiers, for example:

* `MyTokenType("ABC") -> tokenClass: MyTokenType, tokenIdentifier: ABC`
* `MyTokenType("XYZ") -> tokenClass: MyTokenType, tokenIdentifier: XYZ`

#### Creating an instance of your new `TokenType`

Create an instance of your new token type like you would a regular object.

```kotlin
// kotlin

    val myTokenType = MyTokenType("TEST", 2)
```
```java
// java

    MyTokenType myTokenType = new MyTokenType("TEST", 2);
```

This creates a token of

```kotlin
// kotlin

    val tokenClass: MyTokenType
    val tokenIdentifier: TEST
```
```java
// java

    Class tokenClass = MyTokenType.class
    String tokenIdentifier = "TEST" 
```

Similar to the above you can create `IssuedTokenType` using your new token:

```kotlin
// kotlin

val issuer: Party = ...
val issuedTokenType: IssuedTokenType = myTokenType issuedBy issuer
```
```java
// java

Party issuer = ...;
IssuedTokenType issuedTokenType = AmountUtilitiesKt.issuedBy(myTokenType, issuer);        
```

#### Specyfing the notary from the notary list in network parameters

To always use notary of your choice in CorDapp, it needs specifying in 
[CorDapp config](https://docs.corda.net/cordapp-build-systems.html#cordapp-configuration-files). Add notary X500 name 
(one from network parameters):

```text
notary = "O=Notary,L=London,C=GB"
```

All flows from `token-sdk` will use this notary. If you want to use it from your custom flows, you can call:

```kotlin
// kotlin

val notary = getPreferredNotary(serviceHub)
// And pass it to transaction builder
TransactionBuilder(notary = notary)
```
```java
// java

Party notary = NotaryUtilitiesKt.getPreferredNotary(getServiceHub(), NotaryUtilitiesKt.firstNotary());
new TransactionBuilder(notary);
```