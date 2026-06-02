-- Dedicated sequence for SequentialStrategy short-code generation.
-- We read nextval BEFORE insert so the code is known at row-creation time —
-- decoupled from the surrogate short_links.id BIGSERIAL.
CREATE SEQUENCE IF NOT EXISTS seq_short_code_counter
    START WITH 1000
    INCREMENT BY 1
    NO CYCLE;
