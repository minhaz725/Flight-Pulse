-- deal table — stores normalised flight deals across their full lifecycle
create table deal (
    id                  uuid primary key default gen_random_uuid(),
    origin              varchar(3)      not null,
    destination         varchar(3)      not null,
    airline             varchar(100),
    price               numeric(10, 2)  not null,
    currency            varchar(3)      not null,
    departure_date      date            not null,
    return_date         date,
    discount_percentage numeric(5, 2),
    score               integer,
    status              varchar(20)     not null,
    source_adapter      varchar(100)    not null,
    external_id         varchar(255),
    created_at          timestamptz     not null default now(),
    updated_at          timestamptz     not null default now()
);

create index idx_deal_status          on deal (status);
create index idx_deal_origin_dest     on deal (origin, destination);
create index idx_deal_source_external on deal (source_adapter, external_id);

-- user_subscription table — a user's saved watch; matched against published deals
create table user_subscription (
    id                uuid primary key default gen_random_uuid(),
    user_id           varchar(255)    not null,
    origin            varchar(3),
    destination       varchar(3),
    max_price         numeric(10, 2),
    min_score         integer,
    preferred_channel varchar(20)     not null default 'LOG',
    active            boolean         not null default true,
    created_at        timestamptz     not null default now()
);

create index idx_subscription_user_id on user_subscription (user_id);
create index idx_subscription_active  on user_subscription (active);
