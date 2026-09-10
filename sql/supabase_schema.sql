
drop table if exists orders;
drop table if exists inventory;

create table inventory (
    product_id text primary key,
    name        text not null,
    stock       integer not null check (stock >= 0)
);

create table orders (
    order_id    bigserial primary key,
    product_id  text not null references inventory(product_id),
    quantity    integer not null check (quantity > 0),
    status      text not null check (status in ('CONFIRMED', 'REJECTED')),
    reason      text,
    created_at  timestamptz not null default now()
);

insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0);
