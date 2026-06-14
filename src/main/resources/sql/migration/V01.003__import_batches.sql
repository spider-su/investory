create table if not exists import_batch (
    id            bigserial primary key,
    broker        varchar(32) not null,
    source_type   varchar(32) not null,
    source_ref    varchar(255),
    file_name     varchar(255) not null,
    file_sha256   varchar(64) not null,
    started_at    timestamptz not null,
    finished_at   timestamptz,
    status        varchar(32) not null,
    rows_total    integer not null default 0,
    rows_applied  integer not null default 0,
    rows_failed   integer not null default 0,
    error_message text
);
create unique index if not exists ux_import_batch_file_sha256
    on import_batch(file_sha256);
create table if not exists import_row_error (
    id            bigserial primary key,
    batch_id      bigint not null references import_batch(id) on delete cascade,
    sheet_name    varchar(255),
    row_number    integer,
    error_code    varchar(64) not null,
    error_message text not null,
    raw_payload   text
);
