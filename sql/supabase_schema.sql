-- OrderHub — Supabase (Postgres) schema + seed data (Lab 2)
-- Run this in the Supabase SQL Editor. Recreates the whole schema from
-- scratch, including the Lab 1 tables, so this one script is always the
-- full current picture.

drop table if exists notifications;
drop table if exists order_items;
drop table if exists orders;
drop table if exists inventory;

create table inventory (
    product_id text primary key,
    name        text not null,
    stock       integer not null check (stock >= 0)
);

-- An order is now just a header: who placed what and how it turned out.
-- The actual line items live in order_items, since one order can now
-- cover several products.
create table orders (
    order_id    bigserial primary key,
    status      text not null check (status in ('CONFIRMED', 'REJECTED', 'CANCELLED')),
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

insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0);
