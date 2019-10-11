CREATE ROLE offender_events LOGIN PASSWORD '${database_password}';
GRANT USAGE ON SCHEMA offender_events TO offender_events;
