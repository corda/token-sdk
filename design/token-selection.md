# In Memory Token Selection and Locking

## Overview

We are proposing a change to the tokens SDK which will; 
* eliminate the existing database based token selection
* eliminate the existing database based soft-locking of states
* perform token selection on an in-memory copy of the vault
* maintain locks on tokens, in-memory - but in a restart safe fashion. 
* allow a user to group assets by an externalID (aka, accounts), and select across all tokens for a given externalID. 

## Background

The key motivation driving these changes are customer requirements.
In highly threaded workloads, the existing database backed implementation
is a bottleneck, and can lead to token exhaustion, where all tokens are
locked by flows which are not making progress.
We would like the design review board to review our proposed changes detailed
in this document and also review a PoC which has been put together
that implements the changes described in this document.

## Goals

* Provide a **fast** in-memory token selection (in the order of 100's of selections a second)
* Provide a way for users of tokens-sdk to group together states which are owned by the same externalId, but different public keys
* Provide a way for flows to "time out" and release their tokens if they take too long
* Survive a node-restart on a best effort basis. Tokens that were previously locked by a flow should not be available for newly started flows.
* Provide a way to combine states that have been split by a spend.

## Non-goals

* Optionality of in-memory selection. In initial version, all users of tokens-SDK will have token selection performed in-memory
* Complex configurability of ownership. In initial version, only externalId and public key are supported as ownership markers.
* Bullet-proof node restartability. It is possible with current design that if a flow is kicked off before the node has
fully resumed all existing flow checkpoints, a double spend error may occur.
* Support a *distributed* node. Initial design will assume a single node process.

## Timeline

1. DONE Figure out a rough approach.
2. DONE Write a PoC.
3. DONE Formalise a design.
4. TODO Get design reviewed by the DRB.
5. TODO Implement the design using the PoC as a basis.
6. TODO Review and merge the work into tokens-SDK and supporting work into corda 5

## Requirements

1. Tokens must be selectable by “owner”**, as Corda supports multiple owners on each node
2. Tokens must be selectable by TokenType (FiatCurrency/DigitalAsset.. etc)
3. Tokens must be selectable by TokenIdentifier (“GBP” / “BTC” .. etc)
4. Tokens must be lockable to prevent other flows from utilizing the same states
5. Token selection speed must be improved from current performance. Especially for threaded workloads.
6. Tokens must be selectable by issuer
7. Tokens must be selectable by notary

## Design (V1)

For the initial design, we will keep it simple and essentially have a map of maps. SoftLocking will be done by doing an atomic C-A-S (compare-and-swap) on a boolean.

    private val cache: ConcurrentMap<TokenIndex, TokenBucket> = ConcurrentHashMap()

    class TokenIndex(@Kdoc("Any because we support UUID and publickey) val owner: Any, val tokenClazz: Class<*>, val tokenIdentifier: String)

    class TokenBucket() : ConcurrentMap<StateAndRef<FungibleToken<TokenType>>, Boolean>

#### Node Startup

1. On startup, load all states within the vault
2. For each state determine the TokenIndex, and create a TokenBucket if none exists.
3. Add the StateAndRef to the TokenBucket with initial value false
4. Register a vault observer to receive updates.

#### Selection
1. Owner, Amount (contains the type and identifier information) and Predicate are the required parameters used to find the tokens to select from.
2. Find the corresponding   TokenBucket
3. Within the TokenBucket
    1. For each state, check that it satisfies the predicate
    2. Attempt to C-A-S the boolean to true (expecting oldValue=false)
    3. If state is successfully locked, increment the current amount locked
4. If the required amount is reached, terminate and return the list of locked states
5. If the required amount is not reached, roll back the locks, and throw an exception.
6. Register a scheduled callable which will automatically unlock the tokens after a Duration.

#### Updating
1. On each vault update
    1. Remove all consumed states from their corresponding TokenBucket
    2. Add all producted states to their corresponding TokenBucket

Requirements 6 and 7 are satisfied by the optional Predicate. This would be of type: `((StateAndRef<FungibleToken<TokenType>>) -> Boolean)`.
This would allow the calling flow to specify arbitary filtering of tokens, especially if they know the TokenType that will be returned, as
you would be able to cast the token to inspect extra properties, for example, it would be possible to inspect the grading of a Diamond, etc etc.

#### Restartability and Resumability
Selections have to persist across node restarts, as otherwise it is possible for flows to fail due to double spends once they resume after a restart.
In the existing model, this was achieved by creating a soft-lock within the database, but as we are moving to an in-memory model, this would
no longer be possible. Luckily, Corda itself provides a mechanism that we can use to rebuild the state of the token selector on a node restart.
Corda stores the state of flows using a serialized snapshot of the current stack, and it is possible to customise the serialized state by
implementing `SerializeAsToken`. In the context of TokenSelection, we have to serialize the tokens which are locked by the current flow, and
when the node is restarted, ensure that those tokens are locked. We will add a new class for the purposes of this;
`class LocalTokenSelector : TokenSelector, SerializeAsToken`

When the flow is snapshoted, the locked tokens will be serialized as part of the snapshot.
When the flow is resumed, the GlobalTokenSelector (implementing `SingletonSerializeAsToken`) is notified which states were locked.
This allows the node to restart, and repopulate it’s locked token state without having to persist data to an external database or file.

1. User creates an instance of LocalTokenSelector
2. User uses the instance to select some tokens
3. Flow checkpoints with the locked tokens in the snapshot
4. Node restarts
5. Flow is loaded from checkpoint
6. As the LocalTokenSelector is being deserialized from the snapshot, it communicates with the GlobalTokenSelector and notifies which tokens were locked by this flow.
7. Node has rebuilt its locked states cache.


#### Heap Exhaustion
The current design and implementation assumes that the nodes running will be large in comparison to their token vault data.
If heap memory is exhausted, an out of memory exception will be thrown. 

Moving forwards, we intend to introduce a LoadingCache backed approach, where only StateRefs will be held in memory, whilst state 
data will be sourced from the database as required, the size of the cache will be configurable by CorDapp configuration.
This will reduce the speed of token selection, but with the benefit 
of allowing potentially much larger vaults to be selected across, as only a preconfigured number of states will be held in memory. 



## PoC implementation

* github PR: https://github.com/corda/token-sdk/pull/34/
* GlobalTokenSelector: https://github.com/corda/token-sdk/pull/34/files#diff-7cbf91adf23633420d95ab263bbb6617
* LocalTokenSelector: https://github.com/corda/token-sdk/pull/34/files#diff-2f77a171a7bee8f53b08d0f36c8491b1
