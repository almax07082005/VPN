CREATE TABLE bot_user (
    id            BIGSERIAL PRIMARY KEY,
    tg_user_id    BIGINT       NOT NULL UNIQUE,
    username      VARCHAR(64),
    alias         VARCHAR(128),
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT bot_user_status_check CHECK (status IN ('PENDING','APPROVED','DENIED')),
    CONSTRAINT bot_user_alias_required_when_approved CHECK (status <> 'APPROVED' OR alias IS NOT NULL)
);

CREATE INDEX idx_bot_user_status ON bot_user(status);
