# Introduction

## Why should I use the token SDK?

If you want to build a CorDapp which involves a transfer of value, we've
done some heavy lifting for you by building the tokens SDK.

## What are tokens?

A token represents an agreement between an issuer and a holder. The token
is a liability from the issuer's perspective and an asset from the holder's
perspective.

There are two types of tokens which are supported by the tokens SDK:

1. Depository receipts (or asset backed tokens) which are claims held by
   the holder against the issuer to redeem an amount of some underlying
   thing/asset/security. In this case, the value exists off-ledger. However,
   the Corda ledger is authoritative regarding the question of which party
   has a valid claim over said off-ledger value.
2. Ledger native assets which are issued directly on to the ledger by parties
   participating on the ledger. In this case, we can say that the token
   represents the actual instrument as opposed to a claim over an issuer
   to deliver some underlying instrument.

Ledger native crypto-currencies are the exceptional case where tokens do
not represent agreements. This is because the miners which mint units of
the crypto-currency are pseudo-anonymous and therefore cannot be identified.
Clearly, it is impossible to enter into a legal agreement with an unknown
party, therefore it is reasonable to say that ledger native crypto-currencies
are not agreements when represented on Corda.

Do not be confused when a token is used to represent an amount of some
existing crypto-currency on Corda; such a token would be classed as a depository
receipt because the underlying value exists off-ledger. Indeed, the only
case where a token would not be an agreement on a Corda ledger would be
where the crypto-currency is issued directly onto a Corda ledger.

*Fungible and non-fungible*

Furthermore, tokens come in fungible and non-fungible varieties. Fungible
tokens are represented by the `FungibleToken` class and can be split and merged.
They are for representing things such as money, stocks and bonds. Non-fungible
tokens are represented by the `NonFungibleToken`s state and cannot be split
and merged. They are for representing unique things like title deeds and
loans.

## What are token types?

The token SDK allows CorDapp developers to create their own token types.
These token types can be used to issue tokens of a specified type on the
ledger. Token types come in two flavours:

1. Fixed token types, which do not change over time, or are not expected
   to change over time. Currency is a good example of a fixed token type.
   They are represented as an instance of `TokenType` class.
2. Evolvable token types, which are expected to evolve over time. They are
   represented by the `EvolvableTokenType` class, which is a `LinearState`.
   CorDapps developers can design their own logic that governs how the
   evolvable token types are updated over time. Evolvable token types
   introduce some additional complexity compared to fixed token types. The
   reason being is that it doesn't make sense to in-line a `LinearState`
   into a token state, so instead we include a pointer in the token state
   which points to the LinearState that contains the token type information.
   We call this pointer a `TokenPointer`.

The token SDK comes with some token types already defined; `FiatCurrency` and
`DigitalCurrency` contain examples of common currency `TokenType`s.
They are defined within the `money` module.

## Issued token types

As tokens are an agreement between an issuer and a holder, we must specify
an issuer for each token type. For example, there might exist a token type
representing an amount of GBP, that token type can be issued by multiple issuers

    GBP issued by Bank of England
    GBP issued by Royal Bank of Scotland

and we need a way to represent this in the token SDK. This is what the
`IssuedTokenType` is for. The class is a tuple of `TokenType` and `Party`
(which represents the issuer).

It is worth noting that the same token types issued by different issuers
are not considered interchangeable. By that we mean that fungible tokens
of the same token type which different issuers cannot be merged.

This makes sense in the case of a depository receipt because whilst the
underlying instrument might be the same thing (GBP), each issuer presents
different credit and operational risks meaning the the tokens they issue
are not equivalent.

In the case of ledger native tokens, things are slightly different. As ledger
participants can issue financial instruments directly onto the ledger then
the `TokenType` for their token implies which participant the issuer is.
However, tokens using this `TokenType` can still be re-issued by another
ledger participant as a depository receipt.

## Next steps

[Simple delivery versus payment tutorial](DvPTutorial.md)