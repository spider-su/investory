# Asset identity and monetary semantics

## Asset identity

`assets.id` is the only application identity for an instrument. `positions.asset_id`,
`cash_operations.asset_id`, price history, and source-symbol mappings all reference this numeric
key. `assets.symbol` is a canonical display and provider-routing value. It is not a foreign key.

`source_asset_symbol` stores the symbol exactly as supplied by an imported row. `broker_symbol`
stores a broker contract or symbol identifier when one exists. Importers must resolve either value
to exactly one existing `assets.id`. Missing or ambiguous mappings reject the row; importers do not
create an identity from an unknown symbol.

## Currency roles

`accounts.currency` is only the denomination of account cash. It says nothing about an
instrument's quote, acquisition, proceeds, profit, or fee currency.

`assets.currency` is the market-price/listing currency. Price-history rows also carry their own
`price_currency`, because alternate sources can use another listing or scale.

Every position has explicit currency roles:

- `price_currency`: `open_price` and `close_price`.
- `cost_currency`: `purchase_value`, `sale_value`, `base_value`, and `margin`.
- `profit_currency`: `profit` and `swap`.
- `commission_currency`: `commission`.

`cash_operations.currency` is the currency of `amount`. Importers must read it from broker/source
data. Missing currency is a validation failure. Account currency is never a fallback.

Values are converted once, from their declared field currency into the required reporting
currency. Code must not relabel a number with another currency or convert a value already stored in
the target currency.

## Signed position quantity

`positions.volume` stores a non-negative absolute quantity. Direction comes from `operation`.
Canonical signed quantity is:

```text
BUY  => +abs(volume)
SELL => -abs(volume)
```

Java uses `PositionQuantities.signed(...)`. SQL uses
`investory.signed_position_quantity(operation, volume)`. Valuation logic must use these shared
definitions for direction-sensitive quantities and values.
