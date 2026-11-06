-- CFP schema. Runs on every start-up (spring.sql.init.mode=always), so everything must be IF NOT EXISTS.
-- Don't edit old statements, add new ones at the bottom.

-- 2019-03 initial version
CREATE TABLE IF NOT EXISTS speaker (
    id         SERIAL PRIMARY KEY,
    name       TEXT NOT NULL,
    email      TEXT NOT NULL,
    bio        TEXT,
    created    TEXT          -- dd.MM.yyyy, the old admin UI wrote it like that
);

-- 2019-04 talks
CREATE TABLE IF NOT EXISTS talk (
    id           SERIAL PRIMARY KEY,
    title        TEXT NOT NULL,
    abstract     TEXT,                              -- never exposed, the web form was dropped
    format       TEXT NOT NULL,
    duration     INTEGER,                           -- derived from format, the reporting tool reads it
    level        TEXT,
    status       TEXT NOT NULL DEFAULT 'SUBMITTED',
    speaker_id   INTEGER NOT NULL,
    submitted_at BIGINT                             -- epoch millis (UTC)
);

-- 2019-10 voting, replaced by rating in 2020. Kept for the 2019 statistics.
CREATE TABLE IF NOT EXISTS vote (
    talk_id INTEGER,
    voter   TEXT,
    stars   INTEGER
);

-- 2020-02 ratings
CREATE TABLE IF NOT EXISTS rating (
    talk_id     INTEGER NOT NULL,
    reviewer_id INTEGER NOT NULL,
    score       INTEGER NOT NULL,
    rated_at    TEXT,                               -- yyyy-MM-dd HH:mm:ss (UTC)
    PRIMARY KEY (talk_id, reviewer_id)
);

-- 2020-06 programme planning (never finished)
-- ALTER TABLE talk ADD COLUMN room TEXT;
-- ALTER TABLE talk ADD COLUMN slot TEXT;

-- 2021-09 after the CSV import created duplicate speakers
CREATE UNIQUE INDEX IF NOT EXISTS ux_speaker_email ON speaker (lower(email));

-- 2022-01 GET /speakers/{id}/talks was slow
CREATE INDEX IF NOT EXISTS ix_talk_speaker ON talk (speaker_id);

-- 2026-11 co-speakers: up to two per talk, in the order they were added (by id)
CREATE TABLE IF NOT EXISTS talk_co_speaker (
    id         SERIAL,
    talk_id    INTEGER NOT NULL,
    speaker_id INTEGER NOT NULL,
    PRIMARY KEY (talk_id, speaker_id)
);

-- 2026-11 workshop registrations: at most 20 per workshop, one per email (any case), in order of id
CREATE TABLE IF NOT EXISTS workshop_registration (
    id            SERIAL PRIMARY KEY,
    talk_id       INTEGER NOT NULL,
    name          TEXT NOT NULL,
    email         TEXT NOT NULL,                    -- as the attendee typed it
    registered_at BIGINT NOT NULL                   -- epoch millis (UTC)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_workshop_registration_email ON workshop_registration (talk_id, lower(email));

-- 2026-11 programme: fixed rooms R1-R3 and slots S1-S5. One talk per room and slot, each talk at most once.
-- programme_slot exists for SELECT ... FOR UPDATE: changes to one slot run one at a time.
CREATE TABLE IF NOT EXISTS programme_slot (
    slot TEXT PRIMARY KEY
);
INSERT INTO programme_slot (slot) VALUES ('S1'), ('S2'), ('S3'), ('S4'), ('S5') ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS programme_entry (
    room    TEXT NOT NULL,
    slot    TEXT NOT NULL,
    talk_id INTEGER NOT NULL UNIQUE,
    PRIMARY KEY (room, slot)
);
