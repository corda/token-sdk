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
included and some example code which should illustrate you how to use token SDK.
You can `deployNodes` to create three nodes:

    ./gradlew clean deployNodes
    ./build/nodes/runnodes

You can issue some currency tokens from `PartyA` to `PartyB` from Party A's
shell with the following command:

    start ExampleFlowWithFixedToken currency: GBP, amount: 100, recipient: PartyB

See the token template code [here](https://github.com/corda/cordapp-template-kotlin/tree/token-template)
for more information.


### Build Tokens SDK against Corda release branch

Often, in order to use the latest tokens-sdk master you will need to build against a specific Corda release branch until 
the required changes make it into a Corda release. At the team of writing tokens `1.1-SNAPSHOT` requires Corda 
`4.3-SNAPSHOT`. You can build this branch with the following commands:

    git clone https://github.com/corda/corda
    git fetch
    git checkout origin release/os/4.3
   
Then run a `./gradlew clean install` from the root directory.

### Adding token SDK dependencies to an existing CorDapp

First, add a variable for the tokens release group and the version you 
wish to use and set the corda version that should've been installed locally::

    buildscript {
        ext {
            tokens_release_version = '1.0'
            tokens_release_group = 'com.r3.corda.lib.tokens'
        }
    }

Second, you must add the tokens development artifactory repository to the
list of repositories for your project:

    repositories {
        maven { url 'https://ci-artifactory.corda.r3cev.com/artifactory/corda-lib' }
        maven { url 'https://ci-artifactory.corda.r3cev.com/artifactory/corda-lib-dev' }
    }

Now, you can add the tokens SDK dependencies to the `dependencies` block
in each module of your CorDapp. For contract modules add:

    cordaCompile "$tokens_release_group:tokens-contracts:$tokens_release_version"

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

## Where to go next?

[Introduction to token SDK](docs/OVERVIEW.md)

[Most common tasks](docs/IWantTo.md)

[Simple delivery versus payment tutorial](docs/DvPTutorial.md)

## Other useful links

[Token SDK Design](design/design.md)

[Changelog](CHANGELOG.md)

[Contributing](CONTRIBUTING.md)

[Contributors](CONTRIBUTORS.md)

[Roadmap](ROADMAP.md)
