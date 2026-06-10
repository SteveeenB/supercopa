-- ============================================================
--  CODECUP MS2 — Ajustes SQL Iteracion 1
-- ============================================================
-- Ejecutar bloque por bloque en el SQL Editor de Supabase.
-- Asume que el esquema base de "SQL NUEVA BD MS2.txt" YA fue aplicado
-- (tablas torneos, equipos, equipo_torneo, jugadores, jugador_equipo,
--  solicitudes_equipo, partidos, partido_jugador, eventos_partido,
--  titulos, premio_catalogo, torneo_premio, premio_asignado).
-- ============================================================


-- 1) Eliminar tabla redundante `tarjetas`
--    Las tarjetas se modelan ahora como filas en `eventos_partido`
--    con tipo_evento IN ('AMARILLA','AZUL','ROJA').
DROP TABLE IF EXISTS public.tarjetas;


-- 2) Indices recomendados para rendimiento del Perfil del jugador
--    y consultas frecuentes del admin/delegado.
CREATE INDEX IF NOT EXISTS idx_equipo_torneo_torneo_estado
    ON public.equipo_torneo (torneo_id, estado_inscripcion);

CREATE INDEX IF NOT EXISTS idx_eventos_partido_partido_orden
    ON public.eventos_partido (partido_id, orden);

CREATE INDEX IF NOT EXISTS idx_eventos_partido_cedula
    ON public.eventos_partido (cedula);

CREATE INDEX IF NOT EXISTS idx_jugador_equipo_cedula
    ON public.jugador_equipo (cedula);

CREATE INDEX IF NOT EXISTS idx_partido_jugador_cedula
    ON public.partido_jugador (cedula);

CREATE INDEX IF NOT EXISTS idx_solicitudes_equipo_equipo_torneo_estado
    ON public.solicitudes_equipo (equipo_torneo_id, estado);


-- 3) Indices unicos opcionales (no estan en el script base; los pongo
--    aqui para que cuando demos demo, los datos sean consistentes).

-- 3a) Un jugador no puede aparecer dos veces en el mismo partido_jugador
CREATE UNIQUE INDEX IF NOT EXISTS ux_partido_jugador_partido_cedula
    ON public.partido_jugador (partido_id, cedula);

-- 3b) Un solo equipo por delegado por torneo
CREATE UNIQUE INDEX IF NOT EXISTS ux_equipo_torneo_torneo_delegado
    ON public.equipo_torneo (torneo_id, delegado_cedula);

-- 3c) Un solo equipo distinto por torneo
CREATE UNIQUE INDEX IF NOT EXISTS ux_equipo_torneo_torneo_equipo
    ON public.equipo_torneo (torneo_id, equipo_id);

-- 3d) Un jugador no puede tener dos membresias en el mismo torneo
CREATE UNIQUE INDEX IF NOT EXISTS ux_jugador_equipo_torneo_cedula
    ON public.jugador_equipo (torneo_id, cedula);


-- 4) (Opcional, si quieres limpiar datos viejos del schema antiguo
--    que ya no aplican). DESCOMENTA SI APLICA:
-- TRUNCATE TABLE public.titulos, public.partido_jugador, public.eventos_partido,
--                public.partidos, public.solicitudes_equipo, public.jugador_equipo,
--                public.equipo_torneo, public.equipos, public.torneos, public.jugadores
-- RESTART IDENTITY CASCADE;


-- ============================================================
--  Smoke test (ejecutar despues de levantar el backend MS2):
-- ============================================================
-- SELECT * FROM jugadores LIMIT 5;
-- SELECT estado, COUNT(*) FROM torneos GROUP BY estado;
-- SELECT torneo_id, estado_inscripcion, COUNT(*)
--   FROM equipo_torneo GROUP BY torneo_id, estado_inscripcion;
-- SELECT tipo_evento, COUNT(*) FROM eventos_partido GROUP BY tipo_evento;
