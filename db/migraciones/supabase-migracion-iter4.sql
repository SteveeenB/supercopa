-- ============================================================
--  CODECUP MS2 - Ajustes SQL Iteracion 4 (HU16 parte 2)
-- ============================================================
--  Continuacion de iter3. Cuando un equipo es DESCALIFICADO
--  (estado EXPULSADO), sus partidos pendientes pasan al nuevo
--  estado DESCANSO: no se juegan, no suman puntos al rival,
--  no se registran goles. Los partidos ya FINALIZADO conservan
--  intactos su marcador, eventos y stats individuales.
--
--  Ejecutar bloque por bloque en el SQL Editor de Supabase.
--  Asume schema 'supercopa'.
-- ============================================================


-- 1) Permitir el nuevo estado DESCANSO en el CHECK de partidos.
ALTER TABLE supercopa.partidos
    DROP CONSTRAINT IF EXISTS partidos_estado_check;

ALTER TABLE supercopa.partidos
    ADD CONSTRAINT partidos_estado_check
    CHECK (estado::text = ANY (ARRAY[
        'PROGRAMADO'::character varying,
        'EN_CURSO'::character varying,
        'FINALIZADO'::character varying,
        'APLAZADO'::character varying,
        'WO'::character varying,
        'DESCANSO'::character varying
    ]::text[]));
