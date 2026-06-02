-- Schema baseline for memcyco URL shortener.
-- Owned by Flyway; backend agents must not edit this — write V2__*.sql for changes.

CREATE TABLE short_links (
    id            BIGSERIAL PRIMARY KEY,
    short_code    VARCHAR(32)  NOT NULL,
    original_url  TEXT         NOT NULL,
    strategy      VARCHAR(32)  NOT NULL,
    expires_at    TIMESTAMPTZ  NULL,
    max_clicks    BIGINT       NULL,
    click_count   BIGINT       NOT NULL DEFAULT 0,
    tags          TEXT[]       NOT NULL DEFAULT '{}',
    parameters    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at    TIMESTAMPTZ  NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT short_links_max_clicks_chk CHECK (max_clicks IS NULL OR max_clicks > 0)
);

-- Partial unique index: lets a soft-deleted short_code be reclaimed by a new link.
CREATE UNIQUE INDEX short_links_code_live_uq
    ON short_links (short_code)
    WHERE deleted_at IS NULL;

CREATE INDEX short_links_tags_gin
    ON short_links USING gin (tags);

CREATE INDEX short_links_created_at_idx
    ON short_links (created_at DESC)
    WHERE deleted_at IS NULL;

-- Clicks: append-only event log. All request metadata in JSONB for schema flexibility.
CREATE TABLE clicks (
    id             BIGSERIAL    PRIMARY KEY,
    short_link_id  BIGINT       NOT NULL REFERENCES short_links(id) ON DELETE CASCADE,
    clicked_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    data           JSONB        NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX clicks_short_link_time_idx
    ON clicks (short_link_id, clicked_at DESC);

-- GIN for arbitrary @> queries on the JSONB blob (e.g. data @> '{"country":"US"}').
CREATE INDEX clicks_data_gin
    ON clicks USING gin (data jsonb_path_ops);

-- Expression indexes that support GROUP BY in the analytics breakdowns.
CREATE INDEX clicks_referer_idx
    ON clicks ((data ->> 'referer'));

CREATE INDEX clicks_user_agent_idx
    ON clicks ((data ->> 'userAgent'));

-- updated_at trigger on short_links
CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER short_links_touch_updated_at
    BEFORE UPDATE ON short_links
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
