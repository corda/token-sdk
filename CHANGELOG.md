## Changelog

### V1.1

#### Workflows

* Rebased the `workflows` to use the new `confidential-identities` module.

### V1

#### Contracts

* The type of `TokenType.tokenClass` has been changed from `String`
  to `Class<*>`.
* The contracts module now builds against core-deterministic. The tokens
  SDK is the FIRST project with a contracts JAR compiled against Corda
  `core-deterministic`.
* `TokenType` is now a class instead of an interface. This makes it easier
  and more secure for CorDapp developers to create their own token types
  by simply just creating an instance of `TokenType` and supplying a 
  `tokenIdentifier` string and a number of `fractionDigits`. The trade-off
  with using instances of `TokenType` as opposed to sub-types of `TokenType`
  is that type-safety is lost and you cannot define any custom properties. 
  If type-safety is required or if you need to define custom properties on
  top of the `tokenIdentifier` and `fractionDigits` then it is still
  possible to create your own `TokenType` sub-type by sub-classing `TokenType`.
* The Type parameter `T : TokenType` from `FungibleToken` and `NonFungibleToken`
  has been removed. Here we compromise on type-safety for a more simple 
  implementation of these classes and the flows which use them. As a consequence
  the majority of the tokens flows now do not require the use of a type 
  parameter. Furthermore, when querying tokens from the vault, one can 
  only query of type `FungibleToken` or `NonFungibleToken`. To specify the
  exact type, the `tokenClass` and `tokenIdentifier` must be specified. 
* Moved `IssuedTokenType._heldBy(owner: AbstractParty)` and the sister
  infix function to the `workflows` module as it requires the generation
  of a `UniqueIdentifier` which is not deterministic.
* `NonFungibleTokens` now must be passed a `UniqueIdentifier` upon creation.
* Changed the method signatures for `addIssueTokens` so that they now
  take `TransactionBuilder` as the first argument. Thanks to Ian Lloyd
  for the contribution.
* Added tests for `NonFungibleTokens`.
* Fixed a bug in the contract code whereby the `TokenType` was not "pinned"
  to a `NonFungibleToken` or `FungibleToken` state. The `AbstractTokenContract`
  now checks that an attachment that implements the intended `TokenType`
  is attached to a transaction involving `AbstractToken`s. The `TokenType`
  attachments are now pinned by storing the attachment hash in the `AbstractToken`
  state. The hash is propagated to future versions of the state. The attachment
  cannot change during the life of the token, thus the `TokenType` cannot
  change. However, the token can be redeemed and re-issued, thus allowing
  the `TokenType` to be changed.
* Deterministic compilation of `contracts` has been enalbed for versions 
  of Corda > 4.1 (this is due to a bug in 4.0 and 4.1 where `StatePointer`
  was not annotated `KeepForDJVM`.
 
#### Workflows

* Added in memory token selection implementation. It is not currently the
  default token selection method but in a future release CorDapp developers
  will be able to choose between database or in memory selection.
* Changes to how how change parties are generated and used:
  * The issuance flows don't require any change outputs
  * The non-confidential move and redeem token flows now default
    the change party to be the calling node's identity.
  * The confidential redeem flow requires that the issuer request
    a new key from the redeeming party.
  * The confidential move token flow requires that a change party
    is generated prior and passed into the confidential move
    tokens flow.
* The issue tokens flows now resolve the attachments implementing the
  specified `TokenType` and ensure it is added to the issue transaction.
* The `EvolvableTokenType` flows have been refactored to use the same
  design as the other tokens flows in that there is a set of inline
  flows as well as initiating and `StartableByRPC` flows.  

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
  `issuedTokenType heldBy party`
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
