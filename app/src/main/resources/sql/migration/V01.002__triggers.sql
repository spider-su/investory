SET search_path TO investory, public;

CREATE TRIGGER investment_trg_asset_price_history_bind_source_mapping
BEFORE INSERT OR UPDATE OF asset_id, source, source_symbol, source_mapping_id
ON investory.asset_price_history
FOR EACH ROW
EXECUTE FUNCTION investory.investment_fn_bind_asset_price_history_source_mapping();
