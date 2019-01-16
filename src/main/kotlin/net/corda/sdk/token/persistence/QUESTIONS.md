 ----------------------------------------
 Need to record token class in the table!
 ----------------------------------------

 1. For fixed token definitions we need to include the symbol which can be used to select the correct token from
    the token registry. We don't need to include the default fraction digits as they are included within the fixed
    token definition. All we need is the type and the symbol to create a new instance of the fixed token. NOTE that
    we don't need to define an entity for fixed token definitions as the definition exists in code. The description
    and default fraction digits can evolve and this shouldn't have any impact on the persisted fixed token information
    as long as the type and symbol doesn't change.

 2. For pointers we just need to list the linearID of the token pointer and the type of the underlying evolvable
    token. we can use this information to perform a join with the evolvable token table. To do this we will need a
    map of evolvable token states to the underlying entity.

    TODO: Think bout how these queries will be performed. What things will be used to search and why will be searching.

    Also, it doesn't make sense to have one entity per evolvable token defintion as there will only ever be one of
    them! Perhaps it makes more sense to split out _some_ of the information but which information exactly? Symbol,
    default fraction digits, type (stock, blond, etc.)

 Question: How do we handle custom shitcoin tokens?

 They cannot be added to the fixed token registiries, the likes of which i have created for fiat currencies and
 common digital currencies. Instead, they'll have to create their own "one-off" token class. Alternatively, they can
 use the evolvable token types to create their own tokens.

 For OwnedTokens, there should be a 1:1 mapping from token to owned token, this is because the token is deemed to be
 non fungible. However, this might not always be the case, due to error perhaps. Should this be checked? You can't
 have more than one claim for the same underlying thing.

 For owned token amounts, as they can be split and merged, it is expected that there is a one to many relationship
 from token to owned token amounts.