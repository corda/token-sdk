<p align="center">
      <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
    </p>

# Corda Token SDK

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

/**
 *
 * Fungible Token types:
 *
 * CurrencyType
 *  Fiat
 *  Crypto
 * Securities
 *  Equities
 *  Fixed income
 * Non-Finance
 * Exchange traded derivatives
 *
 */

