## Thoughts and questions on persisting token data

 **Need to record token class in the table!**

1. For fixed token definitions we need to include the symbol which can be used to select the correct token from
the token registry. We don't need to include the default fraction digits as they are included within the fixed
token definition. All we need is the type and the symbol to create a new instance of the fixed token. NOTE that
we don't need to define an entity for fixed token definitions as the definition exists in code. The description
and default fraction digits can evolve and this shouldn't have any impact on the persisted fixed token information
as long as the type and symbol doesn't change.

2. For pointers we just need to list the linearID of the token pointer and the type of the underlying evolvable
token. we can use this information to perform a join with the evolvable token table. To do this we will need a
map of evolvable token states to the underlying entity.

TODO: Think about how these queries will be performed. What things will be used to search and why will be searching.

Also, it doesn't make sense to have one entity per evolvable token defintion as there will only ever be one of
them! Perhaps it makes more sense to split out _some_ of the information but which information exactly? Symbol,
default fraction digits, type (stock, blond, etc.)

Question: How do we handle custom shitcoin tokens?

They cannot be added to the fixed token registry, the likes of which i have created for fiat currencies and
common digital currencies. Instead, they'll have to create their own "one-off" token class. Alternatively, they can
use the evolvable token types to create their own tokens.

For OwnedTokens, there should be a 1:1 mapping from token to owned token, this is because the token is deemed to be
non fungible. However, this might not always be the case, due to error perhaps. Should this be checked? You can't
have more than one claim for the same underlying thing.

For owned token amounts, as they can be split and merged, it is expected that there is a one to many relationship
from token to owned token amounts.

Observations
------------

ALL TOKENS NEED A SYMBOL PROPERTY - THE SYMBOL IS USED TO QUERY FOR THE TOKEN
ALL TOKENS NEED A TYPE PROPERTY - THIS CAN BE AN ENUM POTENTIALLY

* It is easy to pick the right fixed token type for a query. They are all in code. We just create an instance of the
  one that we want. However, in the DB this is split out into two columns.
    * Get peoples view on a composite database column
* It is more difficult for the evolvable tokens because we first need to figure out which evolvable tokens are in scope.
  We can only provide the linear ID to the owned token query but of course we get the linear ids from doing some other
  query. Maybe using a symbol and an instrument type?
    * Enums could be useful here?
        * Stock, bond, option, whatever, and the class of option.
    * So maybe all tokens whether they are fxed or evolvable, have a symbol property to identify the token.
    * the queries would have to happen in two parts.
    * We can wrap all of this complexity behind an API. Under the hood, a first query is done to get the linear ID
      then a second query is done to get the actual tokens.


* There are two columns to represent the embeddable token.
    * The string name of the token class instance, whether it's fixed or evolvable.
    * Either the linearID of the evolvable token or the symbol which is used to create an instane of a fixed token
      If the fixed token doesn't behave like a registry, then this column in null.
* tokens in general can be queried by:
    * Issuer
    * Token type
        * Linear Id of pointer or fixd token type.
        * Of course the tokens may have more information embedded in them. So we should be able to query by that stuff
          as well.
        * Given that each evolvable type will have a different schema, how to we query by their custom attributes?
        * To query by issuer, owner, amount or overall token type we dont need to do a join, with any table other than
          the vault states table.
        * Some of the suggested column names are already recorded in tables higher up the hierarchy, e.g. amount.
    * Owner


* Fixed tokens are queried by providing an instance of the fixed token type. Give me all of this token type.
*