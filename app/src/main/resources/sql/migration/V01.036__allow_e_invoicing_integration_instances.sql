-- KSeF is managed as an e-invoicing integration instance.
ALTER TABLE investory.integration_instances
    DROP CONSTRAINT IF EXISTS chk_integration_instances_type;

ALTER TABLE investory.integration_instances
    ADD CONSTRAINT chk_integration_instances_type
        CHECK (plugin_type IN (
            'BROKER_IMPORT',
            'MARKET_DATA',
            'FX_DATA',
            'NOTIFICATION',
            'AI',
            'EXPORT',
            'E_INVOICING'
        ));
