-- OrderHub — Supabase (Postgres) schema + seed data (Lab 4)
-- Run this in the Supabase SQL Editor. Recreates the whole schema from
-- scratch, including Lab 1-3 tables, so this one script is always the
-- full current picture.

-- Drop in dependency order: anything with a foreign key first.
drop table if exists channel_order_mappings;
drop table if exists channel_events;
drop table if exists channel_cursor;
drop table if exists supplier_orders;
drop table if exists notifications;
drop table if exists order_items;
drop table if exists orders;
drop table if exists inventory;

create table inventory (
    product_id text primary key,
    name        text not null,
    stock       integer not null check (stock >= 0)
);

-- "BACKORDERED" added in Lab 4, alongside the Lab 2 statuses.
create table orders (
    order_id    bigserial primary key,
    status      text not null check (status in ('CONFIRMED', 'REJECTED', 'CANCELLED', 'BACKORDERED')),
    reason      text,
    created_at  timestamptz not null default now()
);

create table order_items (
    order_item_id bigserial primary key,
    order_id      bigint not null references orders(order_id),
    product_id    text not null references inventory(product_id),
    quantity      integer not null check (quantity > 0)
);

create table notifications (
    notification_id bigserial primary key,
    message          text not null,
    created_at       timestamptz not null default now()
);

create table supplier_orders (
    id          bigserial primary key,
    product_id  text not null references inventory(product_id),
    buyer_ref   text unique,
    request_id  text not null,
    po_number   text,
    cases       integer not null check (cases > 0),
    units       integer not null check (units > 0),
    status      text not null check (status in ('PENDING', 'ACCEPTED', 'PICKING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'FAILED')),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

-- Lab 4: Tiangge channel tables.

-- Dedup ledger: one row per Tiangge feed eventId ever processed.
create table channel_events (
    event_id     text primary key,
    processed_at timestamptz not null default now()
);

-- The ONLY place a Tiangge order ID is stored. UNIQUE on tiangge_order_id
-- is the hard guarantee behind "each Tiangge order becomes exactly one
-- order in your system" — a second insert attempt fails outright.
create table channel_order_mappings (
    id              bigserial primary key,
    tiangge_order_id text not null unique,
    our_order_id    bigint not null references orders(order_id),
    last_decision   text,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

-- Single row (id always 1) holding how far into the feed we've read.
-- Durable on purpose — this is what makes a restart resume instead of
-- re-reading the feed from the start.
create table channel_cursor (
    id       integer primary key,
    last_seq bigint not null
);
insert into channel_cursor (id, last_seq) values (1, 0);

insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0);
