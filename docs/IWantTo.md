# How do I?
// TODO this goes to tutorials
// Rename
// Restructure
## Create and issue my first token

### Defining a `TokenType`

Two `TokenType`s already exist in the token SDK, `FiatCurrency` and
`DigitalCurrency`. There are easy to use helpers for both, for example:

    val pounds: FiatCurrency = GBP
    val euros: FiatCurrency = EUR
    val bitcoin: DigitalCurrency = BTC

Creating your own is easy; just sub-class the `TokenType` interface. You 
will need to specify a `tokenIdentifier` property and how many `fractionDigits` 
amounts of this token can have. E.g.

* "0" for zero fraction digits where there can only exist whole numbers
  of your token type, and
* "2" for two decimal places like GBP, USD and EUR

You can also add a `toString` override, if you like.

    class MyTokenType(override val tokenIdentifier: String, override val fractionDigits: Int = 0) : TokenType

The `tokenIdentifier` is used along with the `tokenClass` property (defined
in `TokenType` when serializing token types. Two properties are required,
as with `FiatCurrency` and `DigitalCurrency`, there can be many different
instances of one `tokenClass`, each with their own `tokenIdentifier`.

The above defined token type, allows CorDapp developers to create multiple
instances of the token type with different identifiers, for example:

* `MyTokenType("ABC") -> tokenClass: MyTokenType, tokenIdentifier: ABC`
* `MyTokenType("XYZ") -> tokenClass: MyTokenType, tokenIdentifier: XYZ`

This is particularly useful for things like currencies, where there can be
many different instances of the same type of thing. Indeed, this is how
the `FiatCurrency` and `DigitalCurrency` classes work. However, this isn't
always required. For cases where you'll only ever need a single instance
of a token type you can create token types like so:

    object PTK : TokenType {
        override val tokenIdentifier: String = "PTK"
        override val fractionDigits: Int = 12
    }

### Creating an instance of your new `TokenType`

Create an instance of your new token type like you would a regular object.

    val myTokenType = MyTokenType("TEST", 2)

This creates a token of

    tokenClass: MyTokenType
    tokenIdentifier: TEST

### Creating an instance of an `IssuedTokenType`

Create an `IssuedTokenType` as follows

    // With your own token type.
    val issuer: Party = ...
    val myTokenType = MyTokenType("TEST", 2)
    val issuedTokenType: IssuedTokenType<MyTokenType> = myTokenType issuedBy issuer

    // Or with the built in types.
    val issuedGbp: IssuedTokenType<FiatCurrency> = GBP issuedBy issuer
    val issuedGbp: IssuedTokenType<DigitalCurrency>  = BTC issuedBy issuer

The issuing party must be a `Party` as opposed to an `AbstractParty` or
`AnonymousParty`, this is because the issuer must be well known.

The `issuedBy` syntax uses a kotlin infix extension function.

### Creating an amount of some `IssuedTokenType`

Once you have an `IssuedTokenType` you can optionally create some amount
of it using the `of` syntax. For example:

    val issuer: Party = ...
    val myTokenType = MyTokenType("TEST", 2)
    val myIssuedTokenType: IssuedTokenType<MyTokenType> = myTokenType issuedBy issuer
    val tenOfMyIssuedTokenType = 10 of myIssuedTokenType

Or:

    val tenPounds: Amount<IssuedTokenType<FiatCurrency>> = 10 of GBP issuedBy issuer

Or:

    val tenPounds = 10.GBP issuedBy issuer

If you do not need to create amounts of your token type because it is always
intended to be issued as a `NonFungibleToken` then you don't have to create
amounts of it.

### Creating instances of `FungibleToken`s and `NonFungibleToken`s

To get from a token type or some amount of a token type to a non-fungible
token or fungible token, we need to specify which party the proposed holder
is. This can be done using the `heldBy` syntax:

    val issuer: Party = ...
    val holder: Party = ...

    val myTokenType = MyTokenType("TEST", 2)
    val myIssuedTokenType: IssuedTokenType<MyTokenType> = myTokenType issuedBy issuer
    val tenOfMyIssuedTokenType = 10 of myIssuedTokenType

    // Adding a holder to an amount of a token type, creates a fungible token.
    val fungibleToken: FungibleToken<MyTokenType> = tenOfMyIssuedTokenType heldBy holder
    // Adding a holder to a token type, creates a non-fungible token.
    val nonFungibleToken: NonFungibleToken<MyTokenType> = myIssuedTokenType heldBy holder

Once you have a `FungibleToken` or a `NonFungibleToken`, you can then go
and issue that token on ledger.

#TODO
### Issuing fungible tokens
#### Confidential version
### Issuing non-fungible tokens
#### Confidential version

### Moving tokens fungible tokens
#### Confidential version
### Moving non-fungible tokens
#### Confidential version

### Redeeming tokens