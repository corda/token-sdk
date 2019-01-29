Misc notes:

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

The [Issuer] type is nice because it allows you to strip the issuer and
then group same the same currencies as fungible, if you'd like to view
them that way. A step up from this would be the ability to specify fungibility
predicates. E.g. "I'll treat all these issuers on my whitelist the same, the
other ones I won't accept" - we won't do this for now as it's quite a bit
more complex and no-one has yet asked for it.

Dealing with the the same token type issued by multiple issuers is probably
going to be a common theme going forward. The fact that there exists multiple
USD stable coins on Ethereum today proves this. We need a mechanism for users
to handle this and the Issued type seems to fit quite well, although we
will have to re-define it for the token SDK and deprecate Issued in the
core repo (as it uses PartyAndReference, which went used as advertised
makes all tokens non-fungible, as they mostly will have different references!)

Not all tokens have issuers. Bitcoin doesn't... But maybe it does...? A
null bytes key as we don't care about the issuer OR the PoW notary public
key can be used as the issuer! Also, if some asset doesn't have an issuer
then we are saying that it is not a contract but pretty much all things on
Corda have an issuer, so this probably doesn't hold.

Other areas which need more thought:

 1. Need to develop a nice API for using Token pointers. Currently the API is a bit clunky.
 2. Where does reference data sit? On ledger? In library? In attachments? Where exactly?
 3. What token types will be used as pointers, what tokens will be inlined? Equities probably pointers, money will
    be inlined.
 4. Depth of modelling, do we want to model commercial bank money as a liability of a commerical bank to deliver a
    liability of a central bank?? Would be nice if we could but is this overly complex?
 5. Are non fungible things just tokens?
 6. How does all this interact with obligations?

Token selection:

* Need to discriminate on issuer, notary, token type
* Can we do coin selection with a mixture of fixed and evolvable token types?