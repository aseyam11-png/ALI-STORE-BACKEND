-- V5: remove database-only legacy modules outside ALI STORE's single-merchant model.
-- ALI STORE does not operate a B2B/RFQ/quotation marketplace and does not own warehouse
-- inventory. External suppliers remain infrastructure for sourcing and automated fulfillment.

DROP TABLE IF EXISTS quotation_items CASCADE;
DROP TABLE IF EXISTS quotations CASCADE;
DROP TABLE IF EXISTS rfq_items CASCADE;
DROP TABLE IF EXISTS rfqs CASCADE;
DROP TABLE IF EXISTS sellers CASCADE;
DROP TABLE IF EXISTS warehouses CASCADE;
