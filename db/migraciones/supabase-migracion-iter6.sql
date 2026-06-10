-- ============================================================
--  CODECUP MS2 - Ajustes SQL Iteracion 6
-- ============================================================
--  Permitir cedula NULL en eventos_partido.
--
--  Cuando el admin declara W.O., el backend genera 3 goles dummy
--  para el equipo ganador. Estos goles administrativos NO tienen
--  un jugador específico (no fueron anotados por nadie real), por
--  eso cedula debe ser NULL.
--
--  Sin este cambio, declararWO falla con:
--    "null value in column "cedula" violates not-null constraint"
--
--  Ejecutar en el SQL Editor de Supabase. Schema 'supercopa'.
-- ============================================================

ALTER TABLE supercopa.eventos_partido
    ALTER COLUMN cedula DROP NOT NULL;

-- Verificación
SELECT column_name, is_nullable
FROM information_schema.columns
WHERE table_schema = 'supercopa'
  AND table_name = 'eventos_partido'
  AND column_name = 'cedula';
-- is_nullable debe ser 'YES'.
