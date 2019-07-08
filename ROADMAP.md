# Token SDK Roadmap

## V2

### General

* Java API
* Add better query utilities for tokens and held tokens.

### Issuer Workflows

* Addition of a new JAR which contains flows used by token issuers only.
* Addition of an off-ledger mapping of how many tokens of each type have been issued. The mapping will be updated via the vault observable when tokens are issued and redeemed.
* Addition of a flow which performs "chain-snipping". This flow allows a node operator to select some tokens by issuer and redeem them with the issuer, in return for some fresh tokens with no back-chain.
* Addition of flows to provide issuer-whitelisting. This is useful when an issuer only wants specific parties to hold it's own issued tokens.

### Workflows

* Addition of the "client" side of the "chain-snipping" flow.
* Addition of a service to gather statistics regarding the spending behaviour of the node. This can be used to optimise the size and number of states in the vault. 
* Addition of flows and utilities which allow nodes to whitelist issuers. Tokens issued by issuers which are not in the whitelist will not be accepted.

### Contracts

* Updates to the token contracts to handle issuer-whitelisting.

### Token selection

* Performance updates for in memory token selection.

## vNext

* Publish standards
* Vault grooming - to optimise token sizes for spending, e.g. bucket coins into appropriate denominations. this should be merged into regular spending workflows to reduce the amount of transactions required
* Begin to start defining abstract types for commonly used evolvable tokens, e.g. equities and bonds
* Add support for "wallets"
* Add support for keys generated out of process
* Add flows to handle typical abstractions that have been identified E.g. atomic swaps, repos, lending...
* Add more support and tooling for issuers. Merge in the work done on the cash-issuer into the Issuer workflows module. The issuer module will contain utilities for issuers such as keeping track of issued tokens and managing off-ledger records.
* Zero knowledge proofs for amounts and potentially public keys
* Integrate the ISDA CDM to the token SDK
