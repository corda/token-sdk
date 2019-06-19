<p align="center">
    <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Corda Token SDK

## Reminder

This project is open source under an Apache 2.0 licence. That means you
can submit PRs to fix bugs and add new features if they are not currently
available.

## What is the token SDK?

The tokens SDK exists to make it easy for CorDapp developers to create
CorDapps which use tokens. Functionality is provided to create token types,
then issue, move and redeem tokens of a particular type.

The tokens SDK comprises three CorDapp JARs:

1. Contracts which contains the base types, states and contracts
2. Workflows which contains flows for issuing, moving and redeeming tokens
   as well as utilities for the above operations.
3. Money which contains token type definitions for various currencies

The token SDK is intended to replace the "finance module" from the core
Corda repository.

For more details behind the token SDK's design, see
[here](design/design.md).

## How to use the SDK?

### Using the tokens template.

By far the easiest way to get started with the tokens SDK is to use the
`tokens-template` which is a branch on the kotlin version of the "CorDapp
template". You can obtain it with the following commands:

    git clone http://github.com/corda/cordapp-template-kotlin
    cd cordapp-template-kotlin
    git checkout token-template

Once you have cloned the repository, you should open it with IntelliJ. This
will give you a template repo with the token SDK dependencies already
included and some example code which should illustrate you how to use  token SDK.
You can `deployNodes` to create three nodes:

    ./gradlew clean deployNodes
    ./build/nodes/runnodes

You can issue some currency tokens from `PartyA` to `PartyB` from Party A's
shell with the following command:

    start ExampleFlowWithFixedToken currency: GBP, amount: 100, recipient: PartyB

See the token template code [here](https://github.com/corda/cordapp-template-kotlin/blob/token-template/)
for more information.

### Adding token SDK dependencies to an existing CorDapp

First, add a variable for the tokens SDK version you wish to use:

    buildscript {
        ext {
            tokens_release_version = '1.0-RC03'
            tokens_release_group = 'com.r3.corda.lib.tokens'
        }
    }

Second, you must add the tokens development artifactory repository to the
list of repositories for your project:

    repositories {
        maven { url 'https://ci-artifactory.corda.r3cev.com/artifactory/corda-tokens-dev' }
    }

Now, you can add the tokens SDK dependencies to the `dependencies` block
in each module of your CorDapp. For contract modules add:

    cordaCompile "tokens_release_group:tokens-contracts:$tokens_release_version"

In your workflow `build.gradle` add:

    cordaCompile "$tokens_release_group:tokens-workflows:$tokens_release_version"

For `FiatCurrency` and `DigitalCurrency` definitions add:

    cordaCompile "$tokens_release_group:tokens-money:$tokens_release_version"

If you want to use the `deployNodes` task, you will need to add the
following dependencies to your root `build.gradle` file:

    cordapp "$tokens_release_group:tokens-contracts:$tokens_release_version"
    cordapp "$tokens_release_group:tokens-workflows:$tokens_release_version"
    cordapp "$tokens_release_group:tokens-money:$tokens_release_version"

These should also be added to the `deployNodes` task with the following syntax:

    nodeDefaults {
        projectCordapp {
            deploy = false
        }
        cordapp("$tokens_release_group:tokens-contracts:$tokens_release_version")
        cordapp("$tokens_release_group:tokens-workflows:$tokens_release_version")
        cordapp("$tokens_release_group:tokens-money:$tokens_release_version")
    }

### Installing the token SDK binaries

If you wish to build the token SDK from source then do the following to
publish binaries to your local maven repository:

    git clone http://github.com/corda/token-sdk
    cd token-sdk
    ./gradlew clean install

## Why should I use the token SDK?

If you want to build a CorDapp which involves a transfer of value, we've
done some heavy lifting for you by building the tokens SDK.

## What are tokens?

A token represents an agreement between an issuer and a holder. The token
is a liability from the issuer's perspective and an asset from the holder's
perspective.

There are two types of tokens which are supported by the tokens SDK:

1. Depository receipts (or asset backed tokens) which are claims held by
   the owner against the issuer to redeem an amount of some underlying
   thing/asset/security. In this case, the value exists off-ledger. However,
   the Corda ledger is authoritative regarding the question of which party
   has a valid claim over said off-ledger value.
2. Ledger native assets which are issued directly on to the ledger by parties
   participating on the ledger. In this case, we can say that the token
   represents the actual instrument as opposed to a claim over an issuer
   to deliver some underlying instrument.

Ledger native crypto-currencies are the exceptional case where tokens do
not represent agreements. This is because the miners which mint units of
the crypto-currency are pseudo-anonymous and therefore cannot be identified.
Clearly, it is impossible to enter into a legal agreement with an unknown
party, therefore it is reasonable to say that ledger native crypto-currencies
are not agreements when represented on Corda.

Do not be confused when a token is used to represent an amount of some
existing crypto-currency on Corda; such a token would be classed as a depository
receipt because the underlying value exists off-ledger. Indeed, the only
case where a token would not be an agreement on a Corda ledger would be
where the crypto-currency is issued directly onto a Corda ledger.

*Fungible and non-fungible*

Furthermore, tokens come in fungible and non-fungible varieties. Fungible
tokens are represented by the `FungibleToken` class and can be split and merged.
They are for representing things such as money, stocks and bonds. Non-fungible
tokens are represented by the `NonFungibleToken`s state and cannot be split
and merged. They are for representing unique things like title deeds and
loans.

## What are token types?

The token SDK allows CorDapp developers to create their own token types.
These token types can be used to issue tokens of a specified type on the
ledger. Token types come in two flavours:

1. Fixed token types, which do not change over time, or are not expected
   to change over time. Currency is a good example of a fixed token type.
   They are represented as a class which implements `TokenType`.
2. Evolvable token types, which are expected to evolve over time. They are
   represented by the `EvolvableTokenType` interface, which is a `LinearState`.
   CorDapps developers can design their own logic that governs how the
   evolvable token types are updated over time. Evolvable token types
   introduce some additional complexity compared to fixed token types. The
   reason being is that it doesn't make sense to in-line a `LinearState`
   into a token state, so instead we include a pointer in the token state
   which points to the LinearState that contains the token type information.
   We call this pointer a `TokenPointer`.

The token SDK comes with some token types already defined; `FiatCurrency` and
`DigitalCurrency` which are both of type `Money` and in turn `TokenType`.
They are defined within the `money` module.

## Issued token types

As tokens are an agreement between an issuer and a holder, we must specify
an issuer for each token type. For example, there might exist a token type
representing an amount of GBP, that token type can be issued by multiple issuers

    GBP issued by Alice
    GBP issued by Bob

and we need a way to represent this in the token SDK. This is what the
`IssuedTokenType` is for. The class is a tuple of `TokenType` and `Party`
(which represents the issuer).

It is worth noting that the same token types issued by different issuers
are not considered interchangeable. By that we mean that fungible tokens
of the same token type which different issuers cannot be merged.

This makes sense in the case of a depository receipt because whilst the
underlying instrument might be the same thing (GBP), each issuer presents
different credit and operational risks meaning the the tokens they issue
are not equivalent.

In the case of ledger native tokens, things are slightly different. As ledger
participants can issue financial instruments directly onto the ledger then
the `TokenType` for their token implies which participant the issuer is.
However, tokens using this `TokenType` can still be re-issued by another
ledger participant as a depository receipt.

## Creating and issuing your first token

### Defining a `TokenType`

Two `TokenType`s already exist in the token SDK, `FiatCurrency` and
`DigitalCurrency`. There are easy to use helpers for both, for example:

    val pounds: FiatCurrency = GBP
    val euros: FiatCurrency = EUR
    val bitcoin: DigitalCurrency = BTC

Creating your own is easy; just sub-class the `TokenType` interface. You 
will need to specify a `tokenIdentifier` property and how many `fractionDigits` 
amounts of this token can have. E.g.

* "0" for zero fraction digits where there can only exist whole numbers
  of your token type, and
* "2" for two decimal places like GBP, USD and EUR

You can also add a `toString` override, if you like.

    class MyTokenType(override val tokenIdentifier: String, override val fractionDigits: Int = 0) : TokenType

The `tokenIdentifier` is used along with the `tokenClass` property (defined
in `TokenType` when serializing token types. Two properties are required,
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

    object PTK : TokenType {
        override val tokenIdentifier: String = "PTK"
        override val fractionDigits: Int = 12
    }

### Creating an instance of your new `TokenType`

Create an instance of your new token type like you would a regular object.

    val myTokenType = MyTokenType("TEST", 2)

This creates a token of

    tokenClass: MyTokenType
    tokenIdentifier: TEST

### Creating an instance of an `IssuedTokenType`

Create an `IssuedTokenType` as follows

    // With your own token type.
    val issuer: Party = ...
    val myTokenType = MyTokenType("TEST", 2)
    val issuedTokenType: IssuedTokenType<MyTokenType> = myTokenType issuedBy issuer

    // Or with the built in types.
    val issuedGbp: IssuedTokenType<FiatCurrency> = GBP issuedBy issuer
    val issuedGbp: IssuedTokenType<DigitalCurrency>  = BTC issuedBy issuer

The issuing party must be a `Party` as opposed to an `AbstractParty` or
`AnonymousParty`, this is because the issuer must be well known.

The `issuedBy` syntax uses a kotlin infix extension function.

### Creating an amount of some `IssuedTokenType`

Once you have an `IssuedTokenType` you can optionally create some amount
of it using the `of` syntax. For example:

    val issuer: Party = ...
    val myTokenType = MyTokenType("TEST", 2)
    val myIssuedTokenType: IssuedTokenType<MyTokenType> = myTokenType issuedBy issuer
    val tenOfMyIssuedTokenType = 10 of myIssuedTokenType

Or:

    val tenPounds: Amount<IssuedTokenType<FiatCurrency>> = 10 of GBP issuedBy issuer

Or:

    val tenPounds = 10.GBP issuedBy issuer

If you do not need to create amounts of your token type because it is always
intended to be issued as a `NonFungibleToken` then you don't have to create
amounts of it.

### Creating instances of `FungibleToken`s and `NonFungibleToken`s

To get from a token type or some amount of a token type to a non-fungible
token or fungible token, we need to specify which party the proposed holder
is. This can be done using the `heldBy` syntax:

    val issuer: Party = ...
    val holder: Party = ...

    val myTokenType = MyTokenType("TEST", 2)
    val myIssuedTokenType: IssuedTokenType<MyTokenType> = myTokenType issuedBy issuer
    val tenOfMyIssuedTokenType = 10 of myIssuedTokenType

    // Adding a holder to an amount of a token type, creates a fungible token.
    val fungibleToken: FungibleToken<MyTokenType> = tenOfMyIssuedTokenType heldBy holder
    // Adding a holder to a token type, creates a non-fungible token.
    val nonFungibleToken: NonFungibleToken<MyTokenType> = myIssuedTokenType heldBy holder

Once you have a `FungibleToken` or a `NonFungibleToken`, you can then go
and issue that token on ledger.

## Changelog

### V1

#### Contracts

* The type of `TokenType.tokenClass` has been changed from `String`
  to `Class<*>`.
* The contracts module now builds against core-deterministic. The tokens
  SDK is the FIRST project with a contracts JAR compiled against Corda
  `core-deterministic`.
* Moved `IssuedTokenType<T>._heldBy(owner: AbstractParty)` and the sister
  infix function to the `workflows` module as it requires the generation
  of a `UniqueIdentifier` which is not deterministic.
* `NonFungibleTokens` now must be passed a `UniqueIdentifier` upon creation.
* Changed the method signatures for `addIssueTokens` so that they now
  take `TransactionBuilder` as the first argument. Thanks to Ian Lloyd
  for the contribution.
* Added tests for `NonFungibleTokens`.

#### Workflows

* Added in memory token selection implementation. It is not currently the
  default token selection method but in a future release CorDapp developers
  will be able to choose between database or in memory selection.
* Changes to how how change parties are generated and used:

    * RC03:

      * All move and redeem flows specified `changeHolder` as a
        nullable parameter. If it was set to null then the token selection
        mechanism would always generate a new confidential identity for
        the change holder. This behaviour is a little counter-intuitive so
        it has been changed in RC-03.

    * V1:

        * The issuance flows don't require any change outputs
        * The non-confidential move and redeem token flows now default
          the change party to be the calling node's identity.
        * The confidential redeem flow requires that the issuer request
          a new key from the redeeming party.
        * The confidential move token flow requires that a change party
          is generated prior and passed into the confidential move
          tokens flow.

### Release candidate 3

#### General

* Builds against Corda `5.0-SNAPSHOT` for this release. The final release
  will be built against Corda `4.1`.
* Changed module names from "contract", "workflow" to "contracts" and
  "workflows", so now inline with other CorDapps.
* Now don't publish an empty "modules" JAR.
* Changed package namespace to "com.r3.corda.lib" from "com.r3.corda.sdk".
* Changed artifact IDs to be prefixed with "tokens-".
* Changed release group to "com.r3.corda.lib" from "com.r3.tokens-sdk".
* Fixed various compiler warnings.

#### Contracts

* The `tokenClass` property which CorDapp developers were required to implement
  has been implemented in `TokenType`, so there is now no need to implement
  it in your token type classes.
* The `defaultFractionDigits` property of type `BigInteger` has been removed
  in favour of a `fractionDigits` property of type `Int`, simplifying the
  way to create a token type. Example:

  * RC02:

            data class MyTokenType(
                override val tokenIdentifier: String,
                val fractionDigits: Int,
                override val tokenClass: String = javaClass.canonicalName
                override val defaultFractionDigits: BigDecimal = BigDecimal.ONE.scaleByPowerOfTen(-fractionDigits)
            ) : FixedTokenType()

  * RC03:

            data class MyTokenType(
                override val tokenIdentifier: String,
                override val fractionDigits: Int = 0
            ) : TokenType
            
* Removed `FixedTokenType` as it was a spurious abstraction. All "fixed"
  tokens can instead implement `TokenType` directly. Removing `FixedTokenType`
  removes a concept and therefore reduces the mental load of developers 
  who are new to Corda and the token SDK.

#### Workflows

* Added an integration test which uses a DvP flow to swap a `House` for
  some `Money`.

### Release Candidate 2

Release candidate 2 is available. Please see branch `release/1.0-RC02`.
This release candidate is almost code complete.

#### Contracts

* `NonFungibleToken`s are now `LinearState`s so they can be queried for
  by `LinearId`.

#### Workflows

* The redeem flows have been re-factored so that they are in-line with the
  design of the issue and move flows.
* Various bug fixes.
* Added progress trackers to more flows.
* Added @Suspendable annotations to more functions.

#### Known issues

1. Increase test coverage still required.
2. In memory token selection to be merged into `master` and the addition
   of a config option which allows CorDapp developers to use in memory
   token selection or database token selection.
3. Docs and examples are still to come.

### Release Candidate 1 (15 May 2019)

#### Contracts

**Token types**

* Addition of the `TokenType` interface which allows developers to define
  their own token types.
* Addition of a `IssuedTokenType` class which couples a token type with
  an issuing `Party`. This type exists because identical token types with
  different issuers cannot usually be considered fungible.
* Addition of two `TokenType` sub-types; `FixedTokenType` for definitions
  which are not expected to evolve over time and `TokenPointer` which points
  to the `linearId` of a `LinearState` which contains the token type data
  that _is_ expected to evolve.
* Addition of an abstract `EvolvableTokenType` which will be pointed to
  by a `TokenPointer`. It allows developers to define their own evolvable
  token types as `LinearState`s. The `EvolvableTokenType` state comes with
  an abstract contract which defines common logic expected to be used in
  all evolvable token types. Evolvable token types can be converted to
  `TokenPointer`s.
* Addition of two command types `Create` and `Update` for performing transactions
  on evolvable token types.

**Tokens**

* Addition of a `FungibleToken` state type which allows parties to hold
  some amount of an `IssuedTokenType`. Amounts of fungible tokens can be
  split and merged, providing the `IssuedTokenType` is the same type.
* Addition of a `NonFungibleToken` state type which allows parties to hold
  a fungible token of an `IssuedTokenType`. Non fungible tokens cannot be
  split and merged. It is expected that only one non fungible token of a
  particular `IssuedTokenType` can be issued on the ledger at any one time,
  although there are currently no controls to prevent multiple non fungible
  tokens being issued. For the time being, these controls will be up to issuers
  to enforce.
* Addition of contracts for `FungibleToken` and `NonFungibleToken` which
  govern how fungible and non fungible tokens can be issued, moved and redeemed.
* Addition of commands for performing transactions on fungible and non fungible
  tokens.

**Schemas**

* Addition of object relational mappers for `NonFungibleToken` and `FungibleToken`
  so that they can be queried from the vault by the following properties:
  issuer, holder, amount (for fungible tokens) and token type.

**Utilities**

* Addition of kotlin utilities for creating amounts of a `TokenType` or
  `IssuedTokenType` using the following syntax: `10.TOKEN_TYPE` or `10 of tokenType`.
* Addition of kotlin utilities to create an `IssuedTokenType` from a `TokenType`
  using the following syntax `tokenType issuedBy issuer`
* Addition of kotlin utilities to sum amounts of `IssuedTokenType` and `TokenType`.
* Addition of kotlin utilities to create a `NonFungibleToken` or `FungibleToken`
  from an `IssuedTokenType` and a `Party` using the following syntax:
  `issuedTokenType ownedBy party`
* Addition of kotlin utilities to assign a notary to a `NonFungibleToken`
  or `FungibleToken` using the following syntax: `tokens withNotary notary`
* Addition of utilities for summing lists of `FungibleToken`s.

#### Money

* Addition of an abstract `Money` class, that sub-classes `FixedTokenType`,
  from which all money-like token types should derive.
* Addition of a `FiatCurrency` class which is a wrapper around `java.util.Currency`.
* Addition of a `DigitalCurrency` class which behaves similarly to `FiatCurrency`,
  it's registry has been populated with the following digital currencies:
  Bitcoin, Ethereum, Ripple and Dogecoin.
* Addition of utilities for creating amounts of `Money` using the following
  syntax `10.GBP`.

#### Workflows

* Creation of an `internal` package to house parts of the API which may
  change.
* Addition of flows to issue, move and redeem fungible tokens, called
  `IssueTokensFlow`, `MoveTokensFlow` and `RedeemTokensFlow`, respectively.
  All flows are intended to be used as in-line sub-flows. `IssueTokenFlow`
  handles the issuance of fungible as well as non-fungible tokens. The
  move and redeem flows have variants for fungible tokens and variants for
  non-fungible tokens.
* Added confidential variants of the above flows, which allow CorDapp developers
  to issue tokens to newly created public keys, move tokens to newly created
  public keys and redeem tokens, with change being assigned to a newly
  created public key.
* Initiating versions of the above flows have been added which can be
  easily invoked from the node shell (this is useful for testing and performing
  demos).
* Addition of flows to create new evolvable token types.
* Addition of a generic flow to update an evolvable token type.
* Addition of a set of flows to add parties to a distribution list for evolvable
  token updates. This flow exists in the absence of data distribution groups.
  When tokens with evolvable token types are newly issued, the recipient
  is added to a distribution list. When tokens with evolvable token types
  are moved, the recipient is added to the issuer's distribution list.
* Addition of query utilities to query fungible and non fungible tokens
  by type and issuer.
* Addition of query utilities to obtain sums by token type.
* Creation of an "observer aware" finality flow which allows transaction
  observers to store a transaction with `StatesToRecord.ALL_VISIBLE`.

#### Token selection

* Addition of a token selection utility which keeps in memory records of
  available tokens for spending. Multiple buckets can be created to cateogrise
  tokens by various criteria. For example, tokens for a particular account,
  tokens of a particular type, tokens with a particular issuer or notary.

#### Known issues

1. Redeem flows still require refactoring to bring in-line with issue and
   move flows.
2. Some tests are ignored while we investigate why they are failing.
3. Docs and examples are still to come.
4. Increased test coverage required.
