-- baseline migration for flightpulse
-- real domain tables (deal, user_subscription) arrive in F2; this establishes the flyway baseline
-- and enables the pgcrypto extension used later for uuid generation

create extension if not exists pgcrypto;
