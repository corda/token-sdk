## Thoughts and questions on persisting token data

### Fixed Tokens

When persisting fixed token definitions we need to include the symbol which can be used to select the correct token from
the token registry. We don't need to include the default fraction digits as they are included within the fixed
token definition. All we need is the type and the symbol to create a new instance of the fixed token. NOTE that
we don't need to define an entity for fixed token definitions as the definition exists in code. The description
and default fraction digits can evolve and this shouldn't have any impact on the persisted fixed token information
as long as the type and symbol doesn't change.

### Evolvable tokens

There can be multiple evolvable token instances per evolvable token type. How do we query for them?

For pointers we need to list the linearID of the token pointer and the type of the underlying evolvable
token. we can use this information to create an instance of a token pointer. We must get the linearID first. The
assumption is that we do not initially know the linear ID, so we must use some other information (the symbol) to
query for it. What if there are symbol dupes?

We need the class and symbol because one evolvable token type can be used to create instances of many tokens. E.g.
`House` can be used to create many houses.

So... The identifier column in the owned token amount entity can contain a fixed token symbol OR a UUID

Performing coin selection must be done in two parts, we need to get the linearID first.

Generic queries don't have to be done in two parts.. We can use JPA to join the evolvable token type's entity to the
owned token amount table.

TODO: Think about how these queries will be performed. What things will be used to search and why will be searching.

### How do we handle new token types?

They cannot be added to the fixed token registry unless they are added in a new release of the SDK. Instead, they'll
have to create their own "one-off" fixed token class. Alternatively, they canuse the evolvable token types to create
their own tokens.

### OwnedToken

For OwnedTokens, there should be a 1:1 mapping from token to owned token, this is because the token is deemed to be
non fungible. However, this might not always be the case, due to error perhaps. Should this be checked? You can't
have more than one claim for the same underlying thing.