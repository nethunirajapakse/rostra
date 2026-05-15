-- Rostra: one database per service (microservices = no shared schema)
-- This file runs automatically on first Postgres startup.

CREATE DATABASE rostra_auth;
CREATE DATABASE rostra_auction;
CREATE DATABASE rostra_bidding;
CREATE DATABASE rostra_notification;

-- Grant the rostra user full access to each
GRANT ALL PRIVILEGES ON DATABASE rostra_auth TO rostra;
GRANT ALL PRIVILEGES ON DATABASE rostra_auction TO rostra;
GRANT ALL PRIVILEGES ON DATABASE rostra_bidding TO rostra;
GRANT ALL PRIVILEGES ON DATABASE rostra_notification TO rostra;
