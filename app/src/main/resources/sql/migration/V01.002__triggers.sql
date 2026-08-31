SET search_path TO investory, public;

CREATE TRIGGER trg_asset_price_history_bind_source_mapping
BEFORE INSERT OR UPDATE OF asset_id, source, source_symbol, source_mapping_id
ON investory.asset_price_history
FOR EACH ROW
EXECUTE FUNCTION investory.bind_asset_price_history_source_mapping();

CREATE TRIGGER trg_validate_daily_history_start
BEFORE INSERT OR UPDATE OF config_value ON investory.fx_configuration
FOR EACH ROW EXECUTE FUNCTION investory.validate_daily_history_start();
