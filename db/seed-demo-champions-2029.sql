-- ============================================================
--  SEED · DEMO CHAMPIONS 2029 (13 equipos inscritos y APROBADOS)
--  Torneo destino:  5fb0c660-5134-4d1c-8a64-60c8ccc92c8b
--  Cuenta admin que aprueba: 'ADMIN'  (campo libre, no FK)
--  Schema: supercopa
-- ============================================================
--  IMPORTANTE: equipos.delegado_cedula es FK a supercopa.jugadores(cedula).
--  Por eso hay que crear primero 13 filas en supercopa.jugadores con cedulas
--  fake (DEMO901..DEMO913). NO crean cuenta Appwrite — son solo para que el
--  constraint pase y poder probar el flujo del demo.
--
--  Si MS1 nunca responde con esas cedulas el frontend mostrara nombres tipo
--  "(desconocido)" para el delegado en algunos sitios, pero no impide el demo.
--
--  Ejecutar BLOQUE POR BLOQUE en el SQL Editor de Supabase.
-- ============================================================


-- ============================================================
--  0) LIMPIEZA (idempotente — si vuelves a correr el seed)
-- ============================================================
DELETE FROM supercopa.equipo_torneo
WHERE torneo_id = '5fb0c660-5134-4d1c-8a64-60c8ccc92c8b'
  AND delegado_cedula LIKE 'DEMO9%';

DELETE FROM supercopa.equipos
WHERE delegado_cedula LIKE 'DEMO9%';

DELETE FROM supercopa.jugadores
WHERE cedula LIKE 'DEMO9%';


-- ============================================================
--  1) CREAR LOS 13 DELEGADOS (filas en supercopa.jugadores)
--     'cedula' es PK y 'nombre' NOT NULL. El resto es opcional.
-- ============================================================
INSERT INTO supercopa.jugadores (cedula, nombre, correo) VALUES
  ('DEMO901', 'Delegado Colombia',     'delegado.colombia@demo.local'),
  ('DEMO902', 'Delegado Brasil',       'delegado.brasil@demo.local'),
  ('DEMO903', 'Delegado Argentina',    'delegado.argentina@demo.local'),
  ('DEMO904', 'Delegado Francia',      'delegado.francia@demo.local'),
  ('DEMO905', 'Delegado Inglaterra',   'delegado.inglaterra@demo.local'),
  ('DEMO906', 'Delegado Portugal',     'delegado.portugal@demo.local'),
  ('DEMO907', 'Delegado Alemania',     'delegado.alemania@demo.local'),
  ('DEMO908', 'Delegado Belgica',      'delegado.belgica@demo.local'),
  ('DEMO909', 'Delegado Paises Bajos', 'delegado.paisesbajos@demo.local'),
  ('DEMO910', 'Delegado Japon',        'delegado.japon@demo.local'),
  ('DEMO911', 'Delegado Noruega',      'delegado.noruega@demo.local'),
  ('DEMO912', 'Delegado Marruecos',    'delegado.marruecos@demo.local'),
  ('DEMO913', 'Delegado Combo TITI',   'delegado.combotiti@demo.local');


-- ============================================================
--  2) CREAR LOS 13 EQUIPOS
--     equipos.delegado_cedula es UNIQUE (1 equipo por delegado)
--     y FK -> jugadores.cedula (ya creados arriba).
-- ============================================================
INSERT INTO supercopa.equipos (id, nombre, delegado_cedula) VALUES
  (gen_random_uuid(), 'Colombia',     'DEMO901'),
  (gen_random_uuid(), 'Brasil',       'DEMO902'),
  (gen_random_uuid(), 'Argentina',    'DEMO903'),
  (gen_random_uuid(), 'Francia',      'DEMO904'),
  (gen_random_uuid(), 'Inglaterra',   'DEMO905'),
  (gen_random_uuid(), 'Portugal',     'DEMO906'),
  (gen_random_uuid(), 'Alemania',     'DEMO907'),
  (gen_random_uuid(), 'Belgica',      'DEMO908'),
  (gen_random_uuid(), 'Paises Bajos', 'DEMO909'),
  (gen_random_uuid(), 'Japon',        'DEMO910'),
  (gen_random_uuid(), 'Noruega',      'DEMO911'),
  (gen_random_uuid(), 'Marruecos',    'DEMO912'),
  (gen_random_uuid(), 'Combo TITI',   'DEMO913');


-- ============================================================
--  3) INSCRIBIRLOS AL TORNEO COMO APROBADOS
--      torneo_id fijo:  5fb0c660-5134-4d1c-8a64-60c8ccc92c8b
--      estado_inscripcion = 'APROBADO'
--      aprobado_por = 'ADMIN'  (campo libre, no FK)
-- ============================================================
INSERT INTO supercopa.equipo_torneo (
    id, torneo_id, equipo_id, delegado_cedula,
    estado_inscripcion, fecha_inscripcion, aprobado_por
)
SELECT
    gen_random_uuid(),
    '5fb0c660-5134-4d1c-8a64-60c8ccc92c8b',
    e.id,
    e.delegado_cedula,
    'APROBADO',
    NOW(),
    'ADMIN'
FROM supercopa.equipos e
WHERE e.delegado_cedula LIKE 'DEMO9%';


-- ============================================================
--  4) VERIFICACION
-- ============================================================
SELECT count(*) AS total_aprobados
FROM supercopa.equipo_torneo
WHERE torneo_id = '5fb0c660-5134-4d1c-8a64-60c8ccc92c8b'
  AND estado_inscripcion = 'APROBADO';
-- Debe devolver 13.

SELECT e.nombre AS equipo, et.estado_inscripcion, et.delegado_cedula, j.nombre AS delegado
FROM supercopa.equipo_torneo et
JOIN supercopa.equipos   e ON e.id = et.equipo_id
JOIN supercopa.jugadores j ON j.cedula = et.delegado_cedula
WHERE et.torneo_id = '5fb0c660-5134-4d1c-8a64-60c8ccc92c8b'
ORDER BY e.nombre;
