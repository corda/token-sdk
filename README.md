<p align="center">
    <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Corda Token SDK

How to use the SDK:

Assets:

* All custom tokens should be created as sub-classes of `Token`.
* Tokens that are intended to be split and merged and that do not evolve
   should be created as `FixedDefinition`s.
* Tokens that are intended to be split and merged and that do evolve
  should be created as `EvolvableDefinition`s.
* Non fungible tokens should sub-class `Evolvable Token` and be composed
  within `OwnedToken`.

Agreements:

* Anything that is not an asset should be a linear state.

Keeping mind that:

* Issued tokens are liabilities to issuers.
* Issued tokens are assets to owners.
* Issuers / owners should not be hard-coded into tokens. They should be added
  via the `Issued` class and `OwnedToken` / `OwnedTokenAmount` classes.
  This means we can use the same token definition across multiple issuers.

TODO:

* Add JAR publishing stuff to gradle files (Ask Clinton about this)

 TODO

 Think further about the differences in token types across issuer. The [Issuer] type is nice because it allows you to strip
 the issuer and then group same the same currencies as fungible, if you'd like to view them that way. A step up from this
 would be the ability to specify fungibility predicates. E.g. "I'll treat all these issuers on my whitelist the same, the
 other ones I won't accept"

 Reason being is that dealing with the the same token type issued by multiple issuers is probably going to be a common
 theme going forward. The fact that there exists multiple USD stable coins on Ethereum today proves this. We need a
 mechanism for users to handle this. How does it map to the current token types?

 Not all tokens have issuers. Bitcoin doesn't... But maybe it does...? A null bytes key as we don't care about the issuer.
 If something doesn't have an issuer then we are saying that it is not a contract but pretty much all things on Corda
 have an issuer, so this probably doesn't hold.

 Other areas which need more thought:

 1. The toke type interfaces. Ledger native vs depository receipt etc. Issued, redeemable, etc.
 2. Need to develop a nice API for using Token pointers. Currently the API is a bit clunky.
 3. Where does reference data sit? On ledger? In library? In attachments? Where exactly?
 4. What token types will be used as pointers, what tokens will be inlined? Equities probably pointers, money will
    be inlined.
 5. Depth of modelling, do we want to model commercial bank money as a liability of a commerical bank to deliver a
    liability of a central bank?? Would be nice if we could but is this overly complex?
 6. Are non fungible things just token types?
 7. Do we need to inline token symbol and description for fungible token pointers? How does this affect coin selection?
 8. How does all this interact with obligations?

Coin selection woes

* Need to discriminate on issuer, notary\

Comment from Alex

* Resolving token pointer to evolvable definition to just get the maintainer could vet tedious.
  If we are constantly doing it then why not embed the maintainer in the pointer or the issued type.

TODO:

* Introduce some nice kotlin DSL syntax for creating amounts of some evolvable token type. how would one do this?
* Write some flows for creating token types and updating token types
* Write some flows for creating `OwnedToken`s and `OwnedTokenAmount`s
* Write a small persistence app for tracking tokens which have been issued. Can only remove a token from the ledger if nothing is issued and owned of it
* Draw diagrams to explain how issued and owns works by wrapping the tokens
* Explain how to issue a token from an evolvable token type. perhaps we can use some kind of DSL?
    * First we need to know the token type (maybe by ID, or through some search)
* Implement issuer whitelisting
* Implement an example of a non issued/owned token, such as an agreement
    * With issued owned tokens, the token is ALWAYS a liability of the issuer and always an asset of the owner
    * With agreements, the token might not be of some financial thing and if it is, either side could hold an asset/liability and it may change (derivatives)
* Start implementing coin selection for all token types

