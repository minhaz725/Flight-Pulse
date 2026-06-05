-- price_history — append-only log of observed prices per route; used by the scorer's rolling-median baseline
create table price_history (
    id          uuid primary key default gen_random_uuid(),
    origin      varchar(3)      not null,
    destination varchar(3)      not null,
    price       numeric(10, 2)  not null,
    currency    varchar(3)      not null,
    observed_at timestamptz     not null
);

create index idx_price_history_route on price_history (origin, destination, observed_at desc);

-- extend user_subscription with travel window, alert type, lifecycle status, and best-price tracking
alter table user_subscription
    add column travel_date_from      date        not null default '1970-01-01',
    add column travel_date_to        date        not null default '1970-01-01',
    add column alert_type            varchar(20) not null default 'THRESHOLD',
    add column subscription_status   varchar(20) not null default 'ACTIVE',
    add column best_price_seen       numeric(10, 2);

-- migrate the old active boolean into subscription_status, then drop it
update user_subscription set subscription_status = 'EXPIRED' where active = false;
alter table user_subscription drop column active;
