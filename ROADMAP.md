# Token SDK Roadmap

## Release Version 1

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

**Utilities**

* Addition of kotlin utilities for creating amounts of a `TokenType` or `IssuedTokenType` using the following syntax: `10.TOKEN_TYPE` or `10 of tokenType`.
* Addition of kotlin utilities to create an `IssuedTokenType` from a `TokenType` using the following syntax `tokenType issuedBy issuer`
* Addition of kotlin utilities to sum amounts of `IssuedTokenType` and `TokenType`.
* Addition of kotlin utilities to create a `NonFungibleToken` or `FungibleToken` from an `IssuedTokenType` and a `Party` using the following syntax: `issuedTokenType ownedBy party`
* Addition of kotlin utilities to assign a notary to a `NonFungibleToken` or `FungibleToken` using the following syntax: `tokens withNotary notary`
* Addition of utilities for summing lists of `FungibleToken`s.

## vNext

