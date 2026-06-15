-- Store the per-account Balance/Equity snapshot taken from the broker export header.
-- equity = total value of all assets on the account as of export time
-- (free cash + market value of open positions). Summed across accounts and
-- converted to the base currency this gives the portfolio "Balance".
create table if not exists account_summaries(
    account     varchar(255) primary key,
    currency    varchar(255) not null, -- CurrencyType (ENUM)
    balance     double precision,
    equity      double precision,
    updated_at  timestamptz
);

