-- ============================================================
-- MS3 Finanzas + Inscripciones + Premios (Iter 7)
-- ============================================================
-- Fecha: 2026-06-10
-- Descripcion: Migracion que agrega configuracion financiera
--              a torneos, tabla de multas, premios, y nuevos
--              campos a finanzas.multas para suspension y trazabilidad.
-- ============================================================

BEGIN;

-- 1. Columnas de configuracion de inscripcion en supercopa.torneos
ALTER TABLE supercopa.torneos
    ADD COLUMN IF NOT EXISTS monto_inscripcion NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS cuenta_destino VARCHAR(120),
    ADD COLUMN IF NOT EXISTS banco_destino VARCHAR(80),
    ADD COLUMN IF NOT EXISTS titular_cuenta VARCHAR(150);

-- 2. Tabla de configuracion de multas (1:1 con torneo)
CREATE TABLE IF NOT EXISTS supercopa.multa_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    torneo_id UUID NOT NULL UNIQUE REFERENCES supercopa.torneos(id) ON DELETE CASCADE,
    monto_amarilla NUMERIC(12,2) NOT NULL DEFAULT 0,
    monto_azul NUMERIC(12,2) NOT NULL DEFAULT 0,
    monto_roja NUMERIC(12,2) NOT NULL DEFAULT 0,
    fechas_suspension_roja INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 3. Columna tipo_cierre en supercopa.partidos
ALTER TABLE supercopa.partidos
    ADD COLUMN IF NOT EXISTS tipo_cierre VARCHAR(30)
    CHECK (tipo_cierre IS NULL OR tipo_cierre IN ('WO', 'SIN_PAGO_ARBITRAJE'));

-- 4. Nuevas columnas en finanzas.multas
ALTER TABLE finanzas.multas
    ADD COLUMN IF NOT EXISTS partidos_suspension_restantes INT,
    ADD COLUMN IF NOT EXISTS pagado_por_cedula VARCHAR(20),
    ADD COLUMN IF NOT EXISTS partido_pago_id UUID REFERENCES supercopa.partidos(id),
    ADD COLUMN IF NOT EXISTS habilitado_manual BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS habilitado_por_cedula VARCHAR(20),
    ADD COLUMN IF NOT EXISTS habilitado_en TIMESTAMP,
    ADD COLUMN IF NOT EXISTS motivo_habilitacion VARCHAR(200);

-- 5. Indice para consultas de elegibilidad
CREATE INDEX IF NOT EXISTS idx_multas_cedula_torneo_estado
    ON finanzas.multas(cedula, torneo_id, estado);

-- 6. Seed inicial en supercopa.premio_catalogo (idempotente)
INSERT INTO supercopa.premio_catalogo (codigo, nombre, descripcion) VALUES
    ('CAMPEON',               'Campeón',               'Equipo ganador del torneo'),
    ('SUBCAMPEON',            'Subcampeón',             'Segundo puesto'),
    ('TERCERO',               'Tercer puesto',          'Tercer puesto deportivo'),
    ('GOLEADOR',              'Goleador',               'Máximo anotador del torneo'),
    ('PORTERO_MENOS_VENCIDO', 'Portero menos vencido',  'Portero del equipo con menor promedio GC'),
    ('MVP',                   'MVP',                    'Jugador más valioso (asignación manual)'),
    ('OTRO',                  'Otro',                   'Premio custom; usar campo titulo para diferenciar')
ON CONFLICT (codigo) DO NOTHING;

COMMIT;
