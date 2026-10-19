-- =====================================================================
-- V1__create_experiment_tables.sql        (experiment-service, database: experiment_db)
--
-- Flyway runs this file ONE time, the first time experiment-service starts on an
-- empty database. NEVER edit it after it has run (Flyway keeps a "fingerprint" of
-- the file and refuses to start if it changes). To change something later, add a
-- new file: V2__something.sql.
--
-- Lines that start with "--" are comments.
--
-- WHAT THIS SERVICE OWNS (8 tables):
--   experiment                one simulation scenario (the form the user fills in)
--   infeed_resource_detail    resources per infeed, per shift          (child of experiment)
--   zone_resource_detail      resources and chutes per zone, per shift (child of experiment)
--   sort_plan_detail          old sort name -> new sort name           (child of experiment)
--   acceptable_parcel_unit    allowed parcel size and weight limits    (child of experiment)
--   chute_master              the list of chutes (reference data)
--   resource_template         a saved Excel table (infeed, zone or sort plan)
--   resource_template_row     the rows of a saved template
--
-- WHAT IS NOT HERE (it lives in other services):
--   run progress and results  -> simulation-service and kpi-service (later steps)
--   parcels and file uploads  -> file-service (later step)
--   users                     -> auth-service (database auth_db)
--
-- The table and column names are the SAME as in the old monolith database, so the old
-- data can be copied straight in later. The differences are on purpose and are
-- explained next to each place.
-- =====================================================================


-- ---------------------------------------------------------------------
-- The experiment id counter.
-- The old app started experiment ids at 1001 and took the next number from this
-- "sequence" (a counter owned by the database). We keep it the same, so old and
-- new experiment ids never clash.
-- ---------------------------------------------------------------------
CREATE SEQUENCE exp_sequence START WITH 1001 INCREMENT BY 1;


-- ---------------------------------------------------------------------
-- experiment: one row per experiment.
-- ---------------------------------------------------------------------
CREATE TABLE experiment (
    -- The number comes from exp_sequence (above), so this is not an IDENTITY column.
    experiment_id             BIGINT PRIMARY KEY,
    experiment_name           VARCHAR(255),
    terminal                  VARCHAR(255),
    sorting_type              VARCHAR(255),
    use_case_id               INTEGER      NOT NULL,
    selected_date             DATE,

    -- TIMESTAMP(6) = date and time, with up to 6 decimals of a second, and NO time zone.
    -- These are shift times like 23:00 and 06:30 (wall-clock times at the terminal),
    -- not exact moments in the world. Same type as the old table.
    start_time                TIMESTAMP(6),
    end_time                  TIMESTAMP(6),

    parcel_count              INTEGER,
    new_parcel_count          INTEGER,
    parcel_change_value       INTEGER,
    parcel_change_mode        VARCHAR(255),      -- increase / decrease
    max_recirculation_count   INTEGER,
    initial_run_speed         INTEGER,
    flexible_resources        INTEGER,
    phone_boom_chute          INTEGER,
    drop_chute_flexible       INTEGER,
    flexible_unload_resources INTEGER,
    sorter_full_percent       INTEGER,
    minimum_cage_occupancy    INTEGER,
    maximum_cage_occupancy    INTEGER,
    gui_mode                  VARCHAR(30),
    all_sorted_at             VARCHAR(20),

    -- DIFFERENCE 1: who owns the experiment.
    -- The old table had a FOREIGN KEY to app_user. That table now lives in ANOTHER
    -- database (auth_db), and a foreign key cannot cross databases. So user_id is
    -- only a number now. owner_username is a COPY of the name (the old code asked
    -- "experiments of user bob" through a join, which is no longer possible).
    -- Copying a small value like this is normal in microservices.
    user_id                   BIGINT,
    owner_username            VARCHAR(255),

    -- UTC time (the service runs in UTC). Filled by the service.
    created_at                TIMESTAMP(6),

    -- DIFFERENCE 2: the latest run info.
    -- The real run records live in simulation-service. We keep a small COPY of the
    -- latest status here (updated from events later), so the history screen can
    -- filter and page with ONE database query. A new experiment is PENDING.
    -- (The old code showed FAILED for an experiment that never ran: misleading.)
    current_run_status        VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    current_run_stage         VARCHAR(50),
    current_run_message       VARCHAR(300),
    current_run_updated_at    TIMESTAMP(6),
    active_run_id             BIGINT,

    -- CHECK = a rule the database itself enforces. Even a bug in the Java code
    -- cannot save a status that is not in this list.
    CONSTRAINT ck_experiment_run_status
        CHECK (current_run_status IN ('PENDING', 'RUNNING', 'TERMINATED', 'COMPLETED', 'FAILED'))
);

-- Indexes make searches fast, like the index at the back of a book.
-- The history screen sorts by created_at and filters by status.
CREATE INDEX idx_experiment_created_at     ON experiment (created_at);
CREATE INDEX idx_experiment_run_status     ON experiment (current_run_status);
CREATE INDEX idx_experiment_owner_username ON experiment (owner_username);
CREATE INDEX idx_experiment_user_id        ON experiment (user_id);


-- ---------------------------------------------------------------------
-- The 4 child tables of an experiment.
-- exp_id is a FOREIGN KEY to experiment (allowed: same database).
-- ON DELETE CASCADE = when an experiment is deleted, its child rows are deleted
-- by the database too. The old tables did not have this, so the old Java code had
-- to delete every child by hand. NOT NULL = a child row must belong to an experiment.
-- ---------------------------------------------------------------------
CREATE TABLE infeed_resource_detail (
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    shift_id          INTEGER,
    tc                VARCHAR(255),
    infeed            VARCHAR(255),
    zone_id           VARCHAR(255),
    no_of_resources   INTEGER,
    active            BOOLEAN,
    exp_id            BIGINT NOT NULL,
    CONSTRAINT fk_infeed_resource_detail_exp
        FOREIGN KEY (exp_id) REFERENCES experiment (experiment_id) ON DELETE CASCADE
);
CREATE INDEX idx_infeed_resource_detail_exp_id ON infeed_resource_detail (exp_id);

CREATE TABLE zone_resource_detail (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    shift_id     INTEGER,
    zone_id      VARCHAR(255),
    resource_id  INTEGER,
    chutes       VARCHAR(255),
    exp_id       BIGINT NOT NULL,
    CONSTRAINT fk_zone_resource_detail_exp
        FOREIGN KEY (exp_id) REFERENCES experiment (experiment_id) ON DELETE CASCADE
);
CREATE INDEX idx_zone_resource_detail_exp_id ON zone_resource_detail (exp_id);

CREATE TABLE sort_plan_detail (
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    old_sort_name  VARCHAR(255),
    new_sort_name  VARCHAR(255),
    exp_id         BIGINT NOT NULL,
    CONSTRAINT fk_sort_plan_detail_exp
        FOREIGN KEY (exp_id) REFERENCES experiment (experiment_id) ON DELETE CASCADE
);
CREATE INDEX idx_sort_plan_detail_exp_id ON sort_plan_detail (exp_id);

-- Allowed parcel size and weight limits.
-- (The id column has a special name in the old table: acceptable_parcel_unit_id.)
-- DOUBLE PRECISION = a number with decimals.
CREATE TABLE acceptable_parcel_unit (
    acceptable_parcel_unit_id  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    min_height  DOUBLE PRECISION,
    max_height  DOUBLE PRECISION,
    min_length  DOUBLE PRECISION,
    max_length  DOUBLE PRECISION,
    min_width   DOUBLE PRECISION,
    max_width   DOUBLE PRECISION,
    min_weight  DOUBLE PRECISION,
    max_weight  DOUBLE PRECISION,
    exp_id      BIGINT NOT NULL,
    CONSTRAINT fk_acceptable_parcel_unit_exp
        FOREIGN KEY (exp_id) REFERENCES experiment (experiment_id) ON DELETE CASCADE
);
CREATE INDEX idx_acceptable_parcel_unit_exp_id ON acceptable_parcel_unit (exp_id);


-- ---------------------------------------------------------------------
-- chute_master: the list of chutes. Reference data, not linked to experiments.
-- ---------------------------------------------------------------------
CREATE TABLE chute_master (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    chute_id    INTEGER      NOT NULL,
    zone_id     INTEGER      NOT NULL,
    zone_name   VARCHAR(255) NOT NULL,
    chute_type  VARCHAR(255) NOT NULL,
    -- UNIQUE = two rows cannot have the same chute_id.
    CONSTRAINT uk_chute_master_chute_id UNIQUE (chute_id),
    CONSTRAINT ck_chute_master_type CHECK (chute_type IN ('BOOM', 'DROP', 'SPIRAL', 'REJECT'))
);


-- ---------------------------------------------------------------------
-- Saved templates (an uploaded Excel table, saved by a user) and their rows.
-- ---------------------------------------------------------------------
CREATE TABLE resource_template (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    template_type   VARCHAR(255) NOT NULL,
    template_name   VARCHAR(255) NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    row_count       INTEGER      NOT NULL,
    -- Kept only because the old table has it as NOT NULL (old data copies straight in).
    -- The real rows are in resource_template_row. We can drop this column in a later V2.
    payload_json    TEXT         NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    -- Same idea as in experiment: a plain id plus a copy of the username. No foreign key.
    user_id         BIGINT       NOT NULL,
    owner_username  VARCHAR(255),
    CONSTRAINT ck_resource_template_type CHECK (template_type IN ('INFEED', 'ZONE', 'SORT_PLAN'))
);
CREATE INDEX idx_resource_template_owner_username ON resource_template (owner_username);
CREATE INDEX idx_resource_template_user_id        ON resource_template (user_id);

CREATE TABLE resource_template_row (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    template_id  BIGINT  NOT NULL,
    row_index    INTEGER NOT NULL,
    -- JSONB = JSON stored in a fast binary form. One Excel row = one JSON object.
    row_data     JSONB,
    CONSTRAINT fk_resource_template_row_template
        FOREIGN KEY (template_id) REFERENCES resource_template (id) ON DELETE CASCADE,
    -- A template cannot have two rows with the same row number.
    CONSTRAINT uk_resource_template_row_index UNIQUE (template_id, row_index)
);
CREATE INDEX idx_resource_template_row_template_id ON resource_template_row (template_id);