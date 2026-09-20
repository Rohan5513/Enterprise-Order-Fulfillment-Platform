-- Local development only. One database and one user per service (see docs/03-database-design.md).
-- CONNECT is revoked from PUBLIC so a service's credentials cannot open another service's database.

CREATE USER customer_svc WITH PASSWORD 'customer_dev';
CREATE DATABASE customer_db OWNER customer_svc;
REVOKE ALL ON DATABASE customer_db FROM PUBLIC;

CREATE USER product_svc WITH PASSWORD 'product_dev';
CREATE DATABASE product_db OWNER product_svc;
REVOKE ALL ON DATABASE product_db FROM PUBLIC;

CREATE USER order_svc WITH PASSWORD 'order_dev';
CREATE DATABASE order_db OWNER order_svc;
REVOKE ALL ON DATABASE order_db FROM PUBLIC;

CREATE USER inventory_svc WITH PASSWORD 'inventory_dev';
CREATE DATABASE inventory_db OWNER inventory_svc;
REVOKE ALL ON DATABASE inventory_db FROM PUBLIC;

CREATE USER payment_svc WITH PASSWORD 'payment_dev';
CREATE DATABASE payment_db OWNER payment_svc;
REVOKE ALL ON DATABASE payment_db FROM PUBLIC;

CREATE USER notification_svc WITH PASSWORD 'notification_dev';
CREATE DATABASE notification_db OWNER notification_svc;
REVOKE ALL ON DATABASE notification_db FROM PUBLIC;

