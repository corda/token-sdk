In 1.1 version of `token-sdk` we introduced in memory token selection and unified it with the database one.
However, the work isn't fully finished yet, because at unification step we face the problem of difference in query API.
It could be easily solved, but to do so we have to break the API (which I recommend doing in 2.0 release, we shouldn't do that in minor releases).
Why? In initial days pre 1.0 we introduced vault `queryCriteria` parameter on all token-sdk flows (feature requested by one of our customers).
In hindsight, it was an omission on API, as after splitting `Selector` interface and database and in memory token selection
(see `selection` module) only point that those two don't align is querying. We considered some of the alternative solutions,
there are 3 major ways of solving the problem:
1. Restricting the query for tokens to certain operations like querying by issuer, external id, public keys, notaries and
then have filtering predicate (this is for now default internal implementation for selection, although, to avoid API break
we left `queryCriteria` on `TokenQueryBy`, which should be removed when 2.0 is released). Using that restricted set of queries
it is possible to translate easily between token selection modes. Moreover it is sufficient for token usage.
2. Having sealed class for criteria and subclasses that correspond to vault and in memory selections. This solution has some
trade-off too, there is a danger of reinventing the vault query criteria api (when trying to be the most generic with logical operations).
3. Having translation between VaultQueryCriteria and in memory selection, but this is infeasible, because you have to support arbitrary queries,
which is excessive considering our usage, there is lots of place for errors and in general it would be rather experimental.

We went with simple solution number 1. selection is for now internal and not part of the API. It is possible to easily switch
choice between the modes in flows (based on cordapp config options) - it's just single line of code, but it requires changes to 
query API in flows.

We need that unification anyway, because the plan is to split selection from token-sdk and make it token agnostic, so it 
can be used easily from other cordapps - something for the future releases. Fundamental work is laid down already.

So here is TODO list for what should be done to finish this properly:

1. Break the API on move, redeem flow family and on accompaning utilities functions in 2.0 release, old versions could be
deprecated as they always fallback to database selection.
2. Introduce generic query parameter 1 or 2, if `TokenQueryBy` approach is kept then, remove query criteria, add translation
to query criteria using functions from `QueryUtilities`, they already cover most common use cases.
3. Switch on the choice between selection modes in move and redeem utilities - here you have choice of passing selection method,
or using config mechanism.
4. In future, split our selection into separate jar, unify the types so it's not dependent on `token-sdk`, tricky bit: states indexing
in in memory token selection. `VaultWatcherService` is corda service, which means it takes `appServiceHub` only on the construction,
the rest has to be passed by config which is already implemented, apart from state type bit. 
See: `VaultWatcherService.getObservableFromAppServiceHub` that constructs `ownerProvider` and observable that listens for
certain `contractStateType`s. 
