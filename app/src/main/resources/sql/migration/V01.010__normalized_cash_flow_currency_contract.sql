SET search_path TO investory, public;

COMMENT ON VIEW investory.app_v_normalized_cash_operation_flows IS
    'Canonical cash-flow currency contract: amount is in the operation currency; account_flow_amount_in_account_currency is the local account-funding amount; account_flow_amount_in_portfolio_base_currency is the same funding flow converted once to portfolio base on the operation date; portfolio_flow_amount_in_portfolio_base_currency contains external contributions only.';
