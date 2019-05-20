<p align="center">
    <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Corda Token SDK

### !!! REMEMBER: THIS PROJECT IS OPEN SOURCE - THAT MEANS YOU CAN SUBMIT PULL REQUESTS TO ADD THE FUNCTIONALITY YOU NEED IF IT IS NOT CURRENTLY AVAILABLE. !!!

## Release Candidate 01

Release candidate 1 is available. Please see branch `release/1.0-RC01`.

Known issues:

1. Redeem flows still require refactoring.
2. Some tests are ignored while we investigate why they are failing
3. Docs and examples are still to come.

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

The most up-to-date version of the token SDK will be on the `master`
branch. The first release version will be 0.1.

### Building your CorDapp

With the binaries installed to your local maven repository, you can add
the token SDK as a dependency to your CorDapp. Add the following lines
to the `build.gradle` files for your CorDapp. In your contract
`build.gradle`, add:

    cordaCompile "com.r3.tokens-sdk:contract:1.0-SNAPSHOT"
    
In your workflow `build.gradle` add:

    cordaCompile "com.r3.tokens-sdk:workflow:1.0-SNAPSHOT"

For `FiatCurrency` and `DigitalCurrency` definitions add:

    cordaCompile "com.r3.tokens-sdk:money:1.0-SNAPSHOT"

If you want to use the `deployNodes` task, you will need to add the following dependencies to your root `build.gradle`
file:

    cordapp "com.r3.tokens-sdk:contract:1.0-SNAPSHOT"
    cordapp "com.r3.tokens-sdk:workflow:1.0-SNAPSHOT"
    cordapp "com.r3.tokens-sdk:money:1.0-SNAPSHOT"

These should also be added to the `deployNodes` task with the following syntax:

    cordapp("com.r3.tokens-sdk:contract:1.0-SNAPSHOT")
    cordapp("com.r3.tokens-sdk:workflow:1.0-SNAPSHOT")
    cordapp("com.r3.tokens-sdk:money:1.0-SNAPSHOT")

See the [kotlin token-template](https://github.com/corda/cordapp-template-kotlin/blob/token-template/build.gradle)
for an example.

Alternatively, you can use the following bootstrapped token SDK template:

    git clone http://github.com/corda/cordapp-template-kotlin
    cd cordapp-template-kotlin
    git checkout token-template

**Don't** build your CorDapp inside the token-sdk repository, instead
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
ledger would be where the crypto-currency is issued directly onto a
Corda ledger.
