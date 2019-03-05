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

For more details behind the token SDK's design, see
[here](design/design.md).

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

### Installing the token SDK binaries

The token SDK is in a pre-release state, so currently, there are no
binaries available. To use the SDK one must built it from source and
publish binaries to your local maven repository like so:

    git clone http://github.com/corda/token-sdk
    cd token-sdk
    ./gradlew clean install

### Building your CorDapp

With the binaries installed to your local maven repository, you can add
the token SDK as a dependency to your CorDapp. Add the following lines
to the `build.gradle` files for your CorDapp. In your contract
`build.gradle`, add:

    cordaCompile "com.r3.corda.sdk.token:contract:0.1"

In your workflow `build.gradle` add:

    cordaCompile "com.r3.corda.sdk.token:workflow:0.1"

For `FiatCurrency` and `DigitalCurrency` definitions add:

    cordaCompile "com.r3.corda.sdk.token.modules:money:0.1"

Alternatively, you can use the following bootstrapped token SDK template:

    git clone http://github.com/corda/cordapp-template-kotlin
    cd cordapp-template-kotlin
    git checkout token-template

**Don't** building your CorDapp inside the token-sdk repository, instead
use the supplied template, above.

### When building transactions

When building your flows, you need to make sure that you add the token
SDK contract JAR to your transaction builders (and the money JAR if you
require the money definitions). If you don't then you will likely
encounter `NoClassDefFound` errors. This is because at this point in time
there is no support for handling CorDapp dependencies in Corda 4.

The solution is to manually add the contract JAR and the money JAR (if
you need it), to your transaction builders. This can be done with the
following code:

    val builder = TransactionBuilder()
    val contractJarHash = SecureHash.parse("frewfrgregregre")
    builder.addAttachment(contractJarHash)

When support is added for handling CorDapp dependencies in Corda, then
you will not need the above lines of code.

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

* @roger3cev have some suggestion on TokenSelection class, 1. Make it open, so it would be possible to extend 2. For "generateMove" have possibility to give it acceptableCoins list as parameter or as I mentioned by sublcassing the class have the possibility to control that, as we most likely need api to limit acceptableCoins to externalIDs, so that we can choose only subset of coins
* Start writing the standards docs - research how best to do this and
  look at ERC20 as one example.
* Start thinking about a Java API or a shim over the kotlin API for
  java users. For example, the infix functions for using tokens are
  nasty for Java developers.
* Add the tokens branch to the cordapp template kotlin
* Talk to dev ops about getting CI set up for this repo
* Talk to Gavin on release process steps
* Talk to clinton on artifactory / bintray use
* Add schemas for tokens.
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

Add these!

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
* Database agnostic token selection via the database XXX
* Move tokens by selecting the appropriate subset of tokens XXX
* Redeem tokens XXX
* Schemas for tokens and owned tokens which allow custom querying XXX
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

* Add accounts - note there is support in Corda 4, for this
* Optimised in memory token selection for parallel flows
* Publish standards

### April

Data distribution groups

### May

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
  which allows issuers to restrict ownership of their issued assets to
  only those parties which have been added to a whitelist. The whitelist
  is manifested as a list of signed public keys. For a party to receive
  a “restricted” token, they must prove their their public key has been
  signed by the issuer. This can be done within the restricted token
  contract.
* Begin to start defining abstract types for commonly used evolvable
  tokens, e.g. equities and bonds

### June

Start adding support for "wallets"

* Beginning of support for keys generated out of process
* Add more flows now that typical abstractions will have been identified
  E.g. atomic swaps, repos, lending...

### July

* Hopefully, start making some changing to the identity model to
  support beneficial owners

### August

Add more support and tooling for issuers.

* Integrate the cash issuer repository with the token SDK. The token SDK
  will be split into the base SDK and the issuer SDK.
* The issuer SDK will contain utilities for issuers such as keeping
  track of issued tokens and managing off-ledger records.

## September

Start thinking about other topics:

* Transaction fees - plenty of approach to take: Single spend tokens,
  demurrage, etc.
* How notaries effect token selection - we want all tokens to be on
  the same notary
* Zero knowledge proofs for amounts and potentially public keys

## October

* Integrate the ISDA CDM to the token SDK



// John Smith @ Barclays
// JS as CA can only create certs in his name

Some notes from Antony:

What is the Token SDK and what is its history
The finance module was originally built by Mike Hearn as the first example corDapp.  This contained a number of flows and states that represented Cash transactions.  It didn’t model other financial instruments nor did it provide common flows (issue, redeem etc).  It also uses old and incorrect terminology and needs to be deprecated.
The Token SDK provides a more comprehensive set of states, flows and functions to model any financial instrument / token. Its design reduces the code required to issue a fungible or non-fungible token and defines standard that will aid integration in the future (think ERC20).
This SDK is similar to what Cordite team have been working on, but provides additional functionality and may soon form part of the core Corda platform once no longer evolving (not in the short term)

Why use it?
Intention is for this to become the standard for Corda Tokens so they will be commonly understood.
R3 has done the complex thinking so other don’t have to
It will also help with marketing - Corda can (it already does) do tokens!
R3 will continue to maintain this, with input from the open source community.
The SDK contains a bunch of examples

SDK Next Steps:
The Token SDK is available at  https://github.com/corda/token-sdk , but has not yet been launched. It requires Corda 4.
The first version and associated documentation should be completed by the end of the month.
The current roadmap includes items such as issuer whitelisting, confidential tx helpers and some ZKP features.  The roadmap will be defined over the coming weeks.