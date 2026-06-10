-- ============================================================
--  CODECUP MS2 - Ajustes SQL Iteracion 3 (HU16 parcial)
-- ============================================================
--  Habilitar re-inscripcion de equipos RECHAZADOS y separar el
--  flujo de EXPULSION del de RECHAZO. Antes ambos compartian el
--  campo motivo_rechazo, lo que hacia que el motivo de expulsion
--  apareciera en la pestana 'Pagos' del delegado.
--
--  Esta iter cubre SOLO el state machine de inscripciones.
--  Los cambios de partidos -> DESCANSO y tabla de posiciones se
--  hacen en una iteracion aparte cuando se aborde HU16 completo.
--
--  Ejecutar bloque por bloque en el SQL Editor de Supabase.
--  Asume schema 'supercopa' (no 'public').
-- ============================================================


-- 1) Permitir el nuevo estado EXPULSADO en el CHECK constraint.
ALTER TABLE supercopa.equipo_torneo
    DROP CONSTRAINT IF EXISTS equipo_torneo_estado_inscripcion_check;

ALTER TABLE supercopa.equipo_torneo
    ADD CONSTRAINT equipo_torneo_estado_inscripcion_check
    CHECK (estado_inscripcion::text = ANY (ARRAY[
        'PENDIENTE_PAGO'::character varying,
        'APROBADO'::character varying,
        'RECHAZADO'::character varying,
        'EXPULSADO'::character varying
    ]::text[]));


-- 2) Agregar columnas dedicadas al flujo de expulsion.
--    Se mantienen separadas de motivo_rechazo / aprobado_por para
--    poder mostrar el motivo de expulsion en 'Torneos' (y a futuro
--    via correo) sin contaminar la vista de 'Pagos'.
ALTER TABLE supercopa.equipo_torneo
    ADD COLUMN IF NOT EXISTS expulsado_por    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS fecha_expulsion  TIMESTAMP,
    ADD COLUMN IF NOT EXISTS motivo_expulsion VARCHAR(500);
