# Token SDK Roadmap

## Release Version 1 (June 2019)

### Contracts

**Token types**

* Addition of the `TokenType` interface which allows developers to define their own token types. 
* Addition of a `IssuedTokenType` class which couples a token type with an issuing `Party`. This type exists because identical type types with different issuers cannot ususally be considered fungible. 
* Addition of two `TokenType` sub-types; `FixedTokenType` for definitions which are not expected to evolve over time and `TokenPointer` which points to the `linearId` of a `LinearState` which contains the token type data that _is_ expected to evolve. 
* Addition of an abstract `EvolvableTokenType` which will be pointed to by a `TokenPointer`. It allows developers to define their own evolvable token types as `LinearState`s. The `EvolvableTokenType` state comes with an abstract contract which defines common logic expected to be used in all evolvable token types. Evolvable token types can be converted to `TokenPointer`s.
* Addition of two command types `Create` and `Update` for performing transactions on evolvable token types. 

**Tokens**

* Addition of a `FungibleToken` state type which allows parties to hold some amount of an `IssuedTokenType`. Amounts of fungible tokens can be split and merged, providing the `IssuedTokenType` is the same type.
* Addition of a `NonFungibleToken` state type which allows parties to hold a fungible token of an `IssuedTokenType`. Non fungible tokens cannot be split and merged. It is expected that only one non fungible token of a particular `IssuedTokenType` can be issued on the ledger at any one time, although there are currently no controls to prevent multiple non fungible tokens being issued. For the time being, these controls will be up to issuers to enforce.
* Addition of contracts for `FungibleToken` and `NonFungibleToken` which govern how fungible and non fungible tokens can be issued, moved and redeemed.
* Addition of commands for performing transactions on fungible and non fungible tokens.

**Schemas**

* Addition of object relational mappers for `NonFungibleToken` and `FungibleToken` so that they can be queried from the vault by the following properties: issuer, holder, amount (for fungible tokens) and token type.

**Utilities**

* Addition of kotlin utilities for creating amounts of a `TokenType` or `IssuedTokenType` using the following syntax: `10.TOKEN_TYPE` or `10 of tokenType`.
* Addition of kotlin utilities to create an `IssuedTokenType` from a `TokenType` using the following syntax `tokenType issuedBy issuer`
* Addition of kotlin utilities to sum amounts of `IssuedTokenType` and `TokenType`.
* Addition of kotlin utilities to create a `NonFungibleToken` or `FungibleToken` from an `IssuedTokenType` and a `Party` using the following syntax: `issuedTokenType ownedBy party`
* Addition of kotlin utilities to assign a notary to a `NonFungibleToken` or `FungibleToken` using the following syntax: `tokens withNotary notary`
* Addition of utilities for summing lists of `FungibleToken`s.

### Money

* Addition of an abstract `Money` class, that sub-classes `FixedTokenType`, from which all money-like token types should derive.
* Addition of a `FiatCurrency` class which is a wrapper around `java.util.Currency`. 
* Addition of a `DigitalCurrency` class which behaves similarly to `FiatCurrency`, it's registry has been populated with the following digital currencies: Bitcoin, Ethereum, Ripple and Dogecoin.
* Addition of utilities for creating amounts of `Money` using the following syntax `10.GBP`.

### Workflows

* Addition of flows to issue, move and redeem fungible tokens.
* Addition of flows to issue, move and redeem non fungible tokens.
* Addition of wrappers for the above flows to issue, move and redeem using confidential identities.
* Addition of flows to create new evolvable token types.
* Addition of a generic flow to update an evolvable token type.
* Addition of a set of flows to add parties to a distribution list for evolvable token updates. This flow exists in the absence of data distribution groups and relies on a one-to-many multicast approach.
* Addition of query utilities to query fungible and non fungible tokens by type and issuer.
* Addition of query utilities to obtain sums by token type.

### Token selection

* Addition of a token selection utility which keeps in memory records of available tokens for spending. Multiple buckets can be created to cateogrise tokens by various criteria. For example, tokens for a particular account, tokens of a particular type, tokens with a particular issuer or notary. 

## V2

### Issuer Workflows

* Addition of a new JAR which contains flows used by token issuers only.
* Addition of an off-ledger mapping of how many tokens of each type have been issued. The mapping will be updated via the vault observable when tokens are issued and redeemed.
* Addition of a flow which performs "chain-snipping". This flow allows a node operator to select some tokens by issuer and redeem them with the issuer, in return for some fresh tokens with no back-chain.
* Addition of flows to provide issuer-whitelisting. This is useful when an issuer only wants specific parties to hold it's own issued tokens.

### Workflows

* Addition of the "client" side of the "chain-snipping" flow.
* Addition of a service to gather statistics regarding the spending behaviour of the node. This can be used to optimise the size and number of states in the vault. 
* Addition of flows and utilities which allow nodes to whitelist issuers. Tokens issued by issuers which are not in the whitelist will not be accepted.

### Contracts

* Updates to the token contracts to handle issuer-whitelisting.

### Token selection

* Performance updates for in memory token selection.

## vNext

* Publish standards
* Vault grooming - to optimise token sizes for spending, e.g. bucket coins into appropriate denominations. this should be merged into regular spending workflows to reduce the amount of transactions required
* Begin to start defining abstract types for commonly used evolvable tokens, e.g. equities and bonds
* Add support for "wallets"
* Add support for keys generated out of process
* Add flows to handle typical abstractions that have been identified E.g. atomic swaps, repos, lending...
* Add more support and tooling for issuers. Merge in the work done on the cash-issuer into the Issuer workflows module. The issuer module will contain utilities for issuers such as keeping track of issued tokens and managing off-ledger records.
* Zero knowledge proofs for amounts and potentially public keys
* Integrate the ISDA CDM to the token SDK
