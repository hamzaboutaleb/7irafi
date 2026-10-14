SELECT 'CREATE DATABASE auth_db'
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = 'auth_db'
)\gexec

SELECT 'CREATE DATABASE user_db'
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = 'user_db'
)\gexec

SELECT 'CREATE DATABASE job_db'
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = 'job_db'
)\gexec

SELECT 'CREATE DATABASE application_db'
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = 'application_db'
)\gexec

SELECT 'CREATE DATABASE chat_db'
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = 'chat_db'
)\gexec

SELECT 'CREATE DATABASE notification_db'
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = 'notification_db'
)\gexec

SELECT 'CREATE DATABASE rating_db'
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = 'rating_db'
)\gexec
