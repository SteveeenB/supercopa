-- DDL for Supercopa (Supabase Postgres)
-- This is a clean schema aligned to the current workflow.

create extension if not exists "pgcrypto";

-- Torneos (ediciones)
create table if not exists torneos (
  id uuid primary key default gen_random_uuid(),
  nombre varchar not null,
  edicion integer not null,
  estado varchar not null check (estado in ('BORRADOR','PUBLICADO','EN_CURSO','FINALIZADO')),
  fecha_inicio date,
  fecha_fin date,
  publicado_en timestamp,
  created_at timestamp not null default now()
);
create unique index if not exists uq_torneos_edicion on torneos (edicion);

-- Equipos base (permanentes)
create table if not exists equipos (
  id uuid primary key default gen_random_uuid(),
  nombre varchar not null,
  created_at timestamp not null default now()
);

-- Inscripcion de equipos por torneo
create table if not exists equipo_torneo (
  id uuid primary key default gen_random_uuid(),
  torneo_id uuid not null references torneos(id),
  equipo_id uuid not null references equipos(id),
  delegado_cedula varchar not null,
  estado_inscripcion varchar not null check (estado_inscripcion in ('PENDIENTE_PAGO','APROBADO','RECHAZADO')),
  fecha_inscripcion timestamp not null default now(),
  aprobado_por varchar,
  motivo_rechazo varchar
);
create unique index if not exists uq_equipo_torneo on equipo_torneo (torneo_id, equipo_id);
create unique index if not exists uq_delegado_torneo on equipo_torneo (torneo_id, delegado_cedula);

-- Jugadores
create table if not exists jugadores (
  cedula varchar primary key,
  nombre varchar not null,
  correo varchar,
  rol_jugador varchar check (rol_jugador is null or rol_jugador in ('ESTUDIANTE','GRADUADO','PROFESOR','ADMINISTRATIVO')),
  codigo_universitario varchar,
  semestre integer,
  altura_cm integer,
  pierna_habil varchar,
  posicion varchar,
  created_at timestamp not null default now()
);

-- Membresia jugador-equipo por torneo
create table if not exists jugador_equipo (
  id uuid primary key default gen_random_uuid(),
  cedula varchar not null references jugadores(cedula),
  torneo_id uuid not null references torneos(id),
  equipo_torneo_id uuid not null references equipo_torneo(id),
  fecha_inicio date not null default current_date,
  fecha_fin date,
  estado varchar not null check (estado in ('ACTIVO','RETIRADO')),
  numero_camiseta integer,
  constraint chk_jugador_equipo_numero check (numero_camiseta is null or numero_camiseta > 0)
);
create unique index if not exists uq_jugador_torneo on jugador_equipo (torneo_id, cedula);
create unique index if not exists uq_equipo_numero
  on jugador_equipo (equipo_torneo_id, numero_camiseta)
  where numero_camiseta is not null;

-- Solicitudes de ingreso de jugadores
create table if not exists solicitudes_equipo (
  id uuid primary key default gen_random_uuid(),
  torneo_id uuid not null references torneos(id),
  equipo_torneo_id uuid not null references equipo_torneo(id),
  cedula varchar not null references jugadores(cedula),
  nombre varchar not null,
  correo varchar,
  estado varchar not null check (estado in ('PENDIENTE','APROBADA','RECHAZADA')),
  fecha_solicitud timestamp not null default now(),
  fecha_resolucion timestamp,
  motivo_rechazo varchar
);
create unique index if not exists uq_solicitud_activa
  on solicitudes_equipo (torneo_id, cedula)
  where estado in ('PENDIENTE','APROBADA');

-- Partidos
create table if not exists partidos (
  id uuid primary key default gen_random_uuid(),
  torneo_id uuid not null references torneos(id),
  equipo_local_torneo_id uuid not null references equipo_torneo(id),
  equipo_visitante_torneo_id uuid not null references equipo_torneo(id),
  fecha timestamp not null,
  estado varchar not null check (estado in ('PROGRAMADO','EN_CURSO','FINALIZADO','APLAZADO','WO')),
  created_at timestamp not null default now()
);

-- Acta: jugadores que participaron en el partido
create table if not exists partido_jugador (
  id uuid primary key default gen_random_uuid(),
  partido_id uuid not null references partidos(id),
  cedula varchar not null references jugadores(cedula),
  equipo_torneo_id uuid not null references equipo_torneo(id),
  jugo boolean not null default true,
  goles integer not null default 0,
  constraint chk_partido_jugador_goles check (goles >= 0)
);
create unique index if not exists uq_partido_jugador on partido_jugador (partido_id, cedula);

-- Eventos cronologicos del partido (sin minutos)
create table if not exists eventos_partido (
  id uuid primary key default gen_random_uuid(),
  partido_id uuid not null references partidos(id),
  cedula varchar not null references jugadores(cedula),
  equipo_torneo_id uuid not null references equipo_torneo(id),
  tipo_evento varchar not null check (tipo_evento in ('GOL','AMARILLA','AZUL','ROJA')),
  orden integer not null,
  created_at timestamp not null default now(),
  constraint chk_evento_orden check (orden > 0)
);
create unique index if not exists uq_evento_orden on eventos_partido (partido_id, orden);

-- Titulos por edicion
create table if not exists titulos (
  id uuid primary key default gen_random_uuid(),
  torneo_id uuid not null references torneos(id),
  equipo_torneo_id uuid not null references equipo_torneo(id),
  puesto varchar not null check (puesto in ('CAMPEON','SUBCAMPEON','TERCERO')),
  fecha date
);
create unique index if not exists uq_titulo_puesto on titulos (torneo_id, puesto);

-- Premios configurables por torneo
create table if not exists premio_catalogo (
  id uuid primary key default gen_random_uuid(),
  codigo varchar not null,
  nombre varchar not null,
  descripcion varchar
);
create unique index if not exists uq_premio_codigo on premio_catalogo (codigo);

create table if not exists torneo_premio (
  id uuid primary key default gen_random_uuid(),
  torneo_id uuid not null references torneos(id),
  premio_id uuid not null references premio_catalogo(id),
  titulo varchar,
  descripcion varchar,
  monto integer,
  created_at timestamp not null default now()
);
create unique index if not exists uq_torneo_premio on torneo_premio (torneo_id, premio_id);

create table if not exists premio_asignado (
  id uuid primary key default gen_random_uuid(),
  torneo_id uuid not null references torneos(id),
  premio_id uuid not null references premio_catalogo(id),
  cedula varchar references jugadores(cedula),
  equipo_torneo_id uuid references equipo_torneo(id),
  fecha_asignacion timestamp not null default now(),
  constraint chk_premio_destino check (
    cedula is not null or equipo_torneo_id is not null
  )
);
create unique index if not exists uq_premio_asignado
  on premio_asignado (torneo_id, premio_id);
