<p align="center">
    <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Corda Token SDK

## What is the token SDK?

The token SDK is a set of libraries which provide CorDapp developers
functionality to:

* create and manage the reference data which define tokens
* Issue, move and redeem amounts of some token
* Perform operations on tokens such as querying and selecting tokens for
  spending

The token SDK is intended to replace the "finance module" in the core
Corda repository.

## Why did you create the token SDK?

The finance module didn't meet the requirements of the increasing
amount of token projects undertaken on Corda:

* There was little documentation on how to use the finance module
* The finance module did not define any standards for using tokens
* There were only two state types defined: Cash and Obligation.
  Defining new types resulted in significant code duplication.
* Coin selection required database specific implementations and
  didn't parallelise well.
* Etc.

## How to use the SDK?

The token SDK is in a pre-release state, so currently, there are no
binaries available. To use the SDK one must built it from source and
publish binaries to your local maven repository like so:

    git clone http://github.com/corda/token-sdk
    cd token-sdk
    ./gradlew clean install

With the binaries installed to your local maven repository, you can add
the token SDK as a dependency to your CorDapp. Add the following line to
the `build.gradle` file for your CorDapp:

    compile "net.corda.sdk:token:0.1"

Alternatively, you can use the following bootstrapped token SDK template:

    git clone http://github.com/corda/cordapp-template-kotlin
    cd cordapp-template-kotlin
    git checkout token-template

## What is a token?

In the majority of cases, the token SDK treats tokens as agreements
between owners and issuers. As such, tokens either represent:

* **Depository receipts** (or asset backed tokens) which are claims held
  by the owner against the issuer to redeem an amount of some underlying
  thing/asset/security. In this case, the value exists off-ledger.
  However, the ledger remains authoritative regarding the question of
  which party has a valid claim over said off-ledger value.
* **Ledger native assets** which are issued directly on to the ledger by
  parties participating on the ledger. In this case, we can say that the
  token represents the

Ledger native crypto-currencies are the exceptional case where tokens do
not represent agreements. This is because the miners which mint units of
the crypto-currency are pseudo-anonymous and therefore cannot be
identified. Clearly, it is impossible to enter into a legal agreement
with an unknown party, therefore it is reaonsable to say that ledger
native crypto-currencies are not agreements when represented on Corda.

Do not be confused when a token is used to represent an amount of some
existing crypto-currecny on Corda; such a token would be classed as a
*depository receipt* because the underlying value exists off-ledger.
Indeed, the only case where a token would not be an agreement on a Corda
ledger would be where the crypti-currency is issued directly onto a
Corda ledger.

TODO:

* Add JAR publishing stuff to gradle files (Ask Clinton about this)
* The repo needs splitting into multiple parts as the states and contracts
  need to be in separate repositories to the flows and whatnot.
* Add schemas for tokens and owned tokens.
* Add query utilities for tokens and owned tokens.
* Write a small persistence app for tracking tokens which have been issued.
  Can only remove a token from the ledger if nothing is issued and owned
  of it
* Explain how to issue a token from an evolvable token type. perhaps we
can use some kind of DSL?
    * First we need to know the token type (maybe by ID, or through some
      search)
* Implement issuer whitelisting
* Implement an example of a non issued/owned token, such as an agreement
    * With issued owned tokens, the token is ALWAYS a liability of the
      issuer and always an asset of the owner
    * With agreements, the token might not be of some financial thing and
      if it is, either side could hold an asset/liability and it may
      change (derivatives)
* Start implementing coin selection for all token types
* Data distribution groups for managing token reference data
* Accounts app

## Release Notes

*Unreleased*

## Roadmap

### Jan

The aim is to reach parity with the current "finance module". Clearly,
the token SDK is more flexible than the finance module. However, there
is some base functionality which must be implement to make the token SDK
viable.

* Create fixed token types
* Create and update evolvable token types
* Subscribe to updates of evolvable tokens
* Issue amounts of some token to owners
* Database agnostic token selection via the database
* Move tokens by selecting the appropriate subset of tokens
* Redeem tokens
* Schemas for tokens and owned tokens which allow custom querying
* Basic documentation and examples
* Beginnings of some standards
* Token Template for bootstrapping token projects

### Feb

The focus will be on implementing in memory token selection.

* In memory token selection
* Start defining the Obligation SDK. It will be based upon the "Ubin"
  work, the corda settler and the obligation CorDapp. The token SDK
  will be a dependency of the obligation SDK.
* More samples
* Final draft of standards

### March

Focus will be on implementing data distribution groups to syndicate
token reference data around a Corda network.

* Add accounts - note there is support in Corda 4, for this
* Optimised in memory token selection for parallel flows
* Data distribution groups first pass
* Publish standards

### April

Adding commonly requested features.

* Issuer whitelisting - allow users of tokens to specify which issuers
  they trust
* Vault grooming - to optimise token sizes for spending, e.g. bucket
  coins into appropriate denominations. this should be merged into
  regular spending workflows to reduce the amount of transactions
  required
* Chain snipping - cut the chain of provenance for privacy and
  performance reasons
* Token owner whitelisting - often required by regulators. A feature
  which allows issuers to restrict ownnership of their issued assets to
  only those parties which have been added to a whitelist. The whitelist
  is manifested as a list of signed public keys. For a party to receive
  a “restricted” token, they must prove their their public key has been
  signed by the issuer. This can be done within the restricted token
  contract.
* Begin to start defining abstract types for commonly used evolvable
  tokens, e.g. equities and bonds

### May

Start adding support for "wallets"

* Beginnning of support for keys generated out of process
* Add more flow now that typical abstractions will have been identified
  E.g. atomic swaps, repos, lending...

### June

* Hopefully, start making some changing to the identity model to
  support beneficial owners

### July

Add more support and tooling for issuers.

* Integrate the cash issuer repository with the token SDK. The token SDK
  will be split into the base SDK and the issuer SDK.
* The issuer SDK will contain utilities for issuers such as keeping
  track of issued tokens and managing off-ledger records.

## August

Start thinking about other topics:

* Transaction fees - plenty of approach to take: Single spend tokens,
  demurrage, etc.
* How notaries effect token selection - we want all tokens to be on
  the same notary
* Zero knowledge proofs for amounts and potentially public keys

## September

* Integrate the ISDA CDM to the token SDK