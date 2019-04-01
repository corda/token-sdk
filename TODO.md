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

TEST TEAM CITY
