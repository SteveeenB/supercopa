-- ============================================================
--  CODECUP MS2 - Ajustes SQL Iteracion 2
-- ============================================================
-- Ejecutar bloque por bloque en el SQL Editor de Supabase.
-- Asume que ya se aplicaron iteraciones previas.
-- ============================================================


-- 1) jugadores.rol_jugador deja de ser NOT NULL.
--    MS2 NO conoce el rol oficial de un jugador (ese dato vive en
--    el padron de MS1). Hasta hoy se insertaba 'GRADUADO' como
--    default falso para cumplir el NOT NULL: eso ensuciaba la
--    tabla con datos incorrectos. Mejor NULL = "aun no proyectado
--    desde el padron".
--
--    Las filas existentes con 'GRADUADO' no se modifican: pueden
--    ser graduados reales y no hay forma confiable de distinguir.
--    Cuando se implemente la proyeccion desde MS1 (ver
--    docs/refinamiento-proyeccion-jugadores.md), se sobrescribiran
--    al primer refresh.
ALTER TABLE public.jugadores
    ALTER COLUMN rol_jugador DROP NOT NULL;

-- 2) Ajustar el CHECK para aceptar NULL explicitamente.
ALTER TABLE public.jugadores
    DROP CONSTRAINT IF EXISTS jugadores_rol_jugador_check;

ALTER TABLE public.jugadores
    ADD CONSTRAINT jugadores_rol_jugador_check
    CHECK (rol_jugador IS NULL
           OR rol_jugador IN ('ESTUDIANTE', 'GRADUADO', 'PROFESOR', 'ADMINISTRATIVO'));
