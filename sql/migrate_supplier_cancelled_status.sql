-- Apply to an existing Supabase database before deploying status-90 handling.
alter table supplier_orders drop constraint if exists supplier_orders_status_check;
alter table supplier_orders
    add constraint supplier_orders_status_check
    check (status in ('PENDING', 'ACCEPTED', 'PICKING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'FAILED'));
