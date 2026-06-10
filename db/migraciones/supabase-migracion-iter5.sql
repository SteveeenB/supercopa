-- ============================================================
--  CODECUP MS2 - Ajustes SQL Iteracion 5
-- ============================================================
--  Soporte multi-formato de torneo (Iter 1 y 3 del plan
--  docs/plan_configurar_torneo.md):
--
--    - Torneos pueden elegir formato (LIGA, ELIMINACION_DIRECTA,
--      GRUPOS_ELIMINATORIAS, CHAMPIONS) con parametros propios.
--    - Partidos quedan etiquetados con fase, jornada y grupo
--      (antes la jornada se inferia por fecha en el frontend).
--    - Partidos de bracket eliminatorio pueden tener equipos
--      placeholder (NULL) que se rellenan al cerrar el partido
--      previo, gracias a siguiente_partido_id + siguiente_slot.
--
--  Ejecutar bloque por bloque en el SQL Editor de Supabase.
--  Asume schema 'supercopa'.
-- ============================================================


-- 1) Ampliar la tabla torneos con la configuracion de formato.
ALTER TABLE supercopa.torneos
    ADD COLUMN IF NOT EXISTS formato VARCHAR(30),
    ADD COLUMN IF NOT EXISTS num_grupos INT,
    ADD COLUMN IF NOT EXISTS clasifican_por_grupo INT,
    ADD COLUMN IF NOT EXISTS repechaje BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS rondas_playoff VARCHAR(200),
    ADD COLUMN IF NOT EXISTS configurado_en TIMESTAMP;

ALTER TABLE supercopa.torneos
    DROP CONSTRAINT IF EXISTS torneos_formato_check;

ALTER TABLE supercopa.torneos
    ADD CONSTRAINT torneos_formato_check
    CHECK (formato IS NULL OR formato::text = ANY (ARRAY[
        'LIGA'::character varying,
        'ELIMINACION_DIRECTA'::character varying,
        'GRUPOS_ELIMINATORIAS'::character varying,
        'CHAMPIONS'::character varying
    ]::text[]));


-- 2) Anotar fase / jornada / grupo en partidos y permitir
--    placeholders (equipos NULL) para fases eliminatorias.
ALTER TABLE supercopa.partidos
    ALTER COLUMN equipo_local_torneo_id DROP NOT NULL,
    ALTER COLUMN equipo_visitante_torneo_id DROP NOT NULL;

ALTER TABLE supercopa.partidos
    ADD COLUMN IF NOT EXISTS fase VARCHAR(20),
    ADD COLUMN IF NOT EXISTS jornada INT,
    ADD COLUMN IF NOT EXISTS grupo VARCHAR(1),
    ADD COLUMN IF NOT EXISTS siguiente_partido_id UUID,
    ADD COLUMN IF NOT EXISTS siguiente_slot VARCHAR(10);

ALTER TABLE supercopa.partidos
    DROP CONSTRAINT IF EXISTS fk_partidos_siguiente;

ALTER TABLE supercopa.partidos
    ADD CONSTRAINT fk_partidos_siguiente
    FOREIGN KEY (siguiente_partido_id)
    REFERENCES supercopa.partidos(id)
    ON DELETE SET NULL;

ALTER TABLE supercopa.partidos
    DROP CONSTRAINT IF EXISTS partidos_fase_check;

ALTER TABLE supercopa.partidos
    ADD CONSTRAINT partidos_fase_check
    CHECK (fase IS NULL OR fase::text = ANY (ARRAY[
        'GRUPOS'::character varying,
        'REPECHAJE'::character varying,
        'OCTAVOS'::character varying,
        'CUARTOS'::character varying,
        'SEMIS'::character varying,
        'FINAL'::character varying,
        'TERCER_PUESTO'::character varying
    ]::text[]));

ALTER TABLE supercopa.partidos
    DROP CONSTRAINT IF EXISTS partidos_siguiente_slot_check;

ALTER TABLE supercopa.partidos
    ADD CONSTRAINT partidos_siguiente_slot_check
    CHECK (siguiente_slot IS NULL OR siguiente_slot::text = ANY (ARRAY[
        'LOCAL'::character varying,
        'VISITANTE'::character varying
    ]::text[]));


-- 3) Backfill: torneos existentes sin formato se marcan como LIGA
--    (que es el comportamiento que tenian antes de iter5). Sus
--    partidos quedan en fase GRUPOS con jornada nula (la UI ya
--    cae a "agrupar por fecha" cuando jornada es NULL).
UPDATE supercopa.torneos
SET formato = 'LIGA', configurado_en = NOW()
WHERE formato IS NULL;

UPDATE supercopa.partidos
SET fase = 'GRUPOS'
WHERE fase IS NULL;
