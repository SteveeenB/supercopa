-- WARNING: This script DROPS all Supercopa tables and their data.
-- Run only if you want a clean reset.

begin;

-- Drop in dependency-safe order

drop table if exists premio_asignado cascade;
drop table if exists torneo_premio cascade;
drop table if exists premio_catalogo cascade;

drop table if exists eventos_partido cascade;
drop table if exists partido_jugador cascade;
drop table if exists partidos cascade;

drop table if exists solicitudes_equipo cascade;
drop table if exists jugador_equipo cascade;

drop table if exists titulos cascade;

drop table if exists equipo_torneo cascade;
drop table if exists equipos cascade;

drop table if exists jugadores cascade;
drop table if exists torneos cascade;

drop table if exists campeonatos cascade;

commit;

-- After this, run db/supabase_schema.sql to create the new schema.
