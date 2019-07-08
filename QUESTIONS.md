# Thoughts and questions

## Thoughts on persisting token data

### Fixed tokens

When persisting fixed token definitions we need to include the symbol which can be used to select the correct token from
the token registry. We don't need to include the default fraction digits as they are included within the fixed
token definition. All we need is the type and the symbol to create a new instance of the fixed token. NOTE that
we don't need to define an entity for fixed token definitions as the definition exists in code. The description
and default fraction digits can evolve and this shouldn't have any impact on the persisted fixed token information
as long as the type and symbol doesn't change.

### Evolvable tokens

Each `EvolvableTokenType` represents only one type of token. This is unlike regular `TokenType`s which can act as registries. See the `FiatCurrency` class for an example.
For pointers we need to list the linearID of the token pointer and the type of the underlying evolvable token. we can
use this information to create an instance of a token pointer. We must get the linearID first. The assumption is that
we do not initially know the linear ID, so we must use some other information (the symbol) to query for it. If there
are duplicates then you can disambiguate via class name.

### Implications for token selection

Performing coin selection must be done in two parts, we need to get the linearID first. This can be obtained via
querying the vault.

Generic queries don't have to be done in two parts.. We can use JPA to join the evolvable token type's entity to the
held token amount table.

## How do we handle new token types?

They cannot be added to the fixed token registry unless they are added in a new release of the SDK. Instead, they'll
have to create their own "one-off" fixed token class. Alternatively, they can use the evolvable token types to create
their own token types.

## NonFungibleToken

For `NonFungibleToken`s, there should be a 1:1 mapping from `TokenType` to `NonFungibleToken`, this is because the
token is deemed to be non fungible. However, this might not always be the case, due to error perhaps. Should this be
checked as the issuer cannot issue more than one claim for the same underlying thing.

## Agreements

Anything that cannot fit in the `FungibleToken` and `NonFungibleToken` definitions should just be created as
`LinearState`s. Examples are `Obligation` and the ISDA CDM work.

## IssuedTokenType

The `IssuedTokenType` type is nice because it allows you to strip the issuer and
then group same the same tokens as fungible, if you'd like to view
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

## TODO:

 1. Need to develop a nice API for using Token pointers. Currently the API is a bit clunky.
 2. Where does reference data sit? On ledger? In library? In attachments? Where exactly?
 3. What token types will be used as pointers, what tokens will be inlined? Equities probably pointers, money will
    be inlined.
 4. Depth of modelling, do we want to model commercial bank money as a liability of a commerical bank to deliver a
    liability of a central bank?? Would be nice if we could but is this overly complex?
 5. Are non fungible things just tokens?
 6. How does all this interact with obligations?

## Token selection:

* Need to discriminate on issuer, notary, token type
* Can we do coin selection with a mixture of fixed and evolvable token types?
