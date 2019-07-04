# How do I?
## Create fungible and non-fungible tokens
### Defining a `TokenType`

Two `TokenType`s already exist in the token SDK, `FiatCurrency` and
`DigitalCurrency`. There are easy to use helpers for both, for example:

```kotlin
    val pounds: FiatCurrency = GBP
    val euros: FiatCurrency = EUR
    val bitcoin: DigitalCurrency = BTC
```

Creating your own is easy; just sub-class the `TokenType` interface. You 
will need to specify a `tokenIdentifier` property and how many `fractionDigits` 
amounts of this token can have. E.g.

* "0" for zero fraction digits where there can only exist whole numbers
  of your token type, and
* "2" for two decimal places like GBP, USD and EUR

You can also add a `toString` override, if you like.

```kotlin
    class MyTokenType(override val tokenIdentifier: String, override val fractionDigits: Int = 0) : TokenType
```


The `tokenIdentifier` is used along with the `tokenClass` property (defined
in `TokenType`) when serializing token types. Two properties are required,
as with `FiatCurrency` and `DigitalCurrency`, there can be many different
instances of one `tokenClass`, each with their own `tokenIdentifier`.

The above defined token type, allows CorDapp developers to create multiple
instances of the token type with different identifiers, for example:

* `MyTokenType("ABC") -> tokenClass: MyTokenType, tokenIdentifier: ABC`
* `MyTokenType("XYZ") -> tokenClass: MyTokenType, tokenIdentifier: XYZ`

This is particularly useful for things like currencies, where there can be
many different instances of the same type of thing. Indeed, this is how
the `FiatCurrency` and `DigitalCurrency` classes work. However, this isn't
always required. For cases where you'll only ever need a single instance
of a token type you can create token types like so:

```kotlin
    object PTK : TokenType {
        override val tokenIdentifier: String = "PTK"
        override val fractionDigits: Int = 12
    }
```

### Creating an instance of your new `TokenType`

Create an instance of your new token type like you would a regular object.

```kotlin
    val myTokenType = MyTokenType("TEST", 2)
```

This creates a token of

```kotlin
    val tokenClass: MyTokenType
    val tokenIdentifier: TEST
```

### Creating an instance of an `IssuedTokenType`

Create an `IssuedTokenType` as follows

```kotlin
    // With your own token type.
    val issuer: Party = ...
    val myTokenType = MyTokenType("TEST", 2)
    val issuedTokenType: IssuedTokenType<MyTokenType> = myTokenType issuedBy issuer

    // Or with the built in types.
    val issuedGbp: IssuedTokenType<FiatCurrency> = GBP issuedBy issuer
    val issuedGbp: IssuedTokenType<DigitalCurrency>  = BTC issuedBy issuer
```

The issuing party must be a `Party` as opposed to an `AbstractParty` or
`AnonymousParty`, this is because the issuer must be well known.

The `issuedBy` syntax uses a kotlin infix extension function.

### Creating an amount of some `IssuedTokenType`

Once you have an `IssuedTokenType` you can optionally create some amount
of it using the `of` syntax. For example:

```kotlin
    val issuer: Party = ...
    val myTokenType = MyTokenType("TEST", 2)
    val myIssuedTokenType: IssuedTokenType<MyTokenType> = myTokenType issuedBy issuer
    val tenOfMyIssuedTokenType = 10 of myIssuedTokenType
```

Or:

```kotlin
    val tenPounds: Amount<IssuedTokenType<FiatCurrency>> = 10 of GBP issuedBy issuer
```

Or:

```kotlin
    val tenPounds = 10.GBP issuedBy issuer
```

If you do not need to create amounts of your token type because it is always
intended to be issued as a `NonFungibleToken` then you don't have to create
amounts of it.

### Creating instances of `FungibleToken`s and `NonFungibleToken`s

To get from a token type or some amount of a token type to a non-fungible
token or fungible token, we need to specify which party the proposed holder
is. This can be done using the `heldBy` syntax:

```kotlin
    val issuer: Party = ...
    val holder: Party = ...

    val myTokenType = MyTokenType("TEST", 2)
    val myIssuedTokenType: IssuedTokenType<MyTokenType> = myTokenType issuedBy issuer
    val tenOfMyIssuedTokenType = 10 of myIssuedTokenType

    // Adding a holder to an amount of a token type, creates a fungible token.
    val fungibleToken: FungibleToken<MyTokenType> = tenOfMyIssuedTokenType heldBy holder
    // Adding a holder to a token type, creates a non-fungible token.
    val nonFungibleToken: NonFungibleToken = myIssuedTokenType heldBy holder
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
Call these flows for one `TokenType` at a time. If you need to do multiple token types in one transaction then create a new
flow, calling `addIssueTokens` for each token type.
Confidential versions additionally request that the recipients generate new keys for the output holders.

**Initiating**

```kotlin
// As in previous examples
val fungibleToken: FungibleToken<MyTokenType> = ...
val nonFungibleToken: NonFungibleToken = ...
// Start flows via RPC or as a subFlow (it starts a new session with a holder of the token!)
// All of the below flows can take a list of observer parties.
// Fungible
IssueTokens(fungibleToken)
IssueTokens(10 of myTokenType, issuer, holder)
IssueTokens(10 of myIssuedTokenType, holder)
// Nonfungible
IssueTokens(nonFungibleToken)
IssueTokens(myTokenType, issuer, holder)
IssueTokens(myIssuedTokenType, holder)
```

There are many other constructor overloads for initiating `IssueTokens` it's worth investigating the class itself.

_Responder flow:_ `IssueTokensHandler`

_Conidential version:_ `ConfidentialIssueTokens`, _responder_: `ConfidentialIssueTokensHandler`

**Inline**

```kotlin
// We need to pass in counterparties sessions.
val holderSession = initateFlow(holder)
...
// All of the below flows can take a list of observer sessions.
// Fungible
subFlow(IssueTokensFlow(fungibleToken, listOf(holderSession)))
// NonFungible
subFlow(IssueTokensFlow(nonFungibleToken, listOf(holderSession)))
``` 

There are other constructor overloads worth investigating.

_Responder flow:_ `IssueTokensFlowHandler`

**Confidential inline**

```kotlin
// We need to pass in counterparties sessions.
val holderSession = initateFlow(holder)
...
// All of the below flows can take a list of observer sessions.
// Fungible
subFlow(ConfidentialIssueTokensFlow(fungibleToken, listOf(holderSession)))
// NonFungible
subFlow(ConfidentialIssueTokensFlow(nonFungibleToken, listOf(holderSession)))
```

_Responder flow:_ `ConfidentialIssueTokensFlowHandler`

### Moving tokens

**Usage**

Move tokens flows fall into one of the two categories: fungible and non-fungible. Both versions differ significantly in
how states are selected for spend. For more details see `TokenSelection`. The common tasks performed are: choosing states
to spend from `Vault`, generating possible change outputs, adding inputs and outputs with new holders to the transaction
with `MoveTokenCommand`, finalisation and distribution list update. Notary is chosen based on the inputs notary. We don't support
notary change for now.
Call these flows for one `TokenType` at a time. If you need to do multiple token types in one transaction then create a new
flow, calling `addMoveTokens` for each token type.
As previously confidential versions generate new identities for use in output states.

#### Moving tokens fungible tokens

This family of flows chooses owned amount of given token from vault. If you want to provide other criteria (for example tokens that
come only from one issuer) use `queryCriteria`. `QueryUtilities` module provides many useful helpers i.e. `tokenAmountWithIssuerCriteria`.
You can move many tokens to different parties in one transaction, to do so specify map of `partiesAndAmounts` respectively.
As usual you can provide additional observers parties/sessions for finalization with other interested parties on the network.

**Initiating**

```kotlin
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

_Responder flow:_ `MoveFungibleTokensHandler`

_Confidential version:_ `ConfidentialMoveFungibleTokens`, _responder_: `ConfidentialMoveFungibleTokensHandler`

**Inline**

```kotlin
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

_Responder flow:_ `MoveTokensFlowHandler`

_Confidential version:_ `ConfidentialMoveFungibleTokensFlow`, _responder:_ `ConfidentialMoveTokensFlowHandler`

#### Moving non-fungible tokens

**Initiating**

```kotlin
val holder: Party = ...
...
// Move non fungible token to the new holder
MoveNonFungibleTokens(PartyAndToken(holder, myTokenType))
```

Similar to previous examples you can provide `queryCriteria` and list of observer parties.

_Responder flow:_ `MoveNonFungibleTokensHandler`

_Confidential version:_ `ConfidentialMoveNonFungibleTokens`, _responder_: `ConfidentialMoveNonFungibleTokensHandler`

**Inline**

```kotlin
val holderSession = initateFlow(holder)
val observerSession = initatieFlow(observer)
subFlow(MoveNonFungibleTokensFlow(PartyAndToken(holder, myTokenType), listOf(holderSession), listOf(observerSession)))
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

Call these flows for one `TokenType` at a time. If you need to do multiple token types in one transaction then create a new
flow, calling `addTokensToRedeem`, `addNonFungibleTokensToRedeem` or`addFungibleTokensToRedeem` for each token type 
(see `RedeemFlowUtilities.kt` for more documentation). All of the flows take observer parties/sessions as usual.

The main difference to move flows is that only fungible redeem tokens flows have confidential versions. This is due to 
the fact no change is paid in non-fungible case, so there are no output states to generate confidential identities for.

#### Redeeming fungible tokens

Selection is performed using the same utilities used in `MoveFungibleTokens`.

**Initiating**

```kotlin
val amountToRedeem = 10.GBP
val issuerParty: Party = ...
val observerParty: Party = ...

// It is also possible to provide custom query criteria for token selection.
RedeemFungibleTokens(amount = amountToRedeem, issuer = issuerParty, observers = listOf(observerParty))
```

_Responder flow:_ `RedeemFungibleTokensHandler`

_Confidential version:_ `ConfidentialRedeemFungibleTokens`, _responder_: `ConfidentialRedeemFungibleTokensHandler`

**Inline**

```kotlin
val issuerSession = initateFlow(issuer)
val observerSession = initatieFlow(observer)
// It is also possible to provide custom query criteria for token selection.
subFlow(RedeemFungibleTokensFlow(
    amount = 1000.GBP, 
    issuerSession = issuerSession,
    changeOwner = TODO(),
    observerSessions = listOf(observerSession)
))
// TODO sth about query criteria
```

_Responder flow:_ `RedeemTokensFlowHandler`

_Confidential version:_ `ConfidentialRedeemFungibleTokensFlow`,
 
 _responder_: `ConfidentialRedeemFungibleTokensFlowHandler`

#### Redeeming non-fungible tokens

**Initiating**

```kotlin
val myTokenType: MyTokenType = ...
val issuerParty: Party = ...
val observerParty: Party = ...

RedeemNonFungibleTokens(myTokenType, issuerParty, listOf(observerParty))
```

_Responder flow:_ `RedeemNonFungibleTokensHandler`

**Inline**

```kotlin
val myTokenType: MyTokenType = ...
val issuerSession = initateFlow(issuerParty)
val observerSession = initatieFlow(observerParty)
subFlow(RedeemNonFungibleTokensFlow(myTokenType, listOf(issuerSession), listOf(observerSession)))
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
val stx: SignedTransaction = ...
val participantSession: FlowSession = initFlow(participantParty)
val observerSession: FlowSession = initFlow(observerParty)
subFlow(ObserverAwareFinalityFlow(stx, listOf(participantSession, observerSession)))
```

**Keeping distribution lists up-to-date**

This is temporary solution for distributing updates to evolvable tokens. It will be changed in the future for more robust design
using data distribution groups. For now it is important when using pointers to evolvable tokens to call `UpdateDistributionListFlow`
that takes care of adding new parties to the distribution list kept by the token maintainer (usually it's issuer).

Simply call at the end of your flow:

```kotlin
val stx: SignedTransaction = ...
subFlow(UpdateDistributionListFlow(stx))
```
