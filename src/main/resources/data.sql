INSERT INTO jugadores (cedula, nombre, correo)
VALUES
('2000000001', 'Juan Perez', 'juan.perez@ufps.edu.co'),
('2000000002', 'Laura Gomez', 'laura.gomez@ufps.edu.co'),
('2000000003', 'Santiago Rojas', 'santiago.rojas@ufps.edu.co'),
('2000000004', 'Camila Duarte', 'camila.duarte@ufps.edu.co')
ON CONFLICT (cedula) DO UPDATE
SET nombre = EXCLUDED.nombre,
    correo = EXCLUDED.correo;

INSERT INTO equipos (id, nombre, delegado_cedula)
VALUES
('11111111-1111-1111-1111-111111111111', 'Colombia', NULL),
('22222222-2222-2222-2222-222222222222', 'Brasil', '1091969958'),
('e1111111-1111-1111-1111-111111111111', 'Portugal', NULL),
('e2222222-2222-2222-2222-222222222222', 'Inglaterra', NULL),
('e3333333-3333-3333-3333-333333333333', 'Francia', NULL),
('e4444444-4444-4444-4444-444444444444', 'Marruecos', NULL),
('e5555555-5555-5555-5555-555555555555', 'Noruega', NULL),
('e6666666-6666-6666-6666-666666666666', 'Argentina', NULL),
('e7777777-7777-7777-7777-777777777777', U&'B\00E9lgica', NULL),
('e8888888-8888-8888-8888-888888888888', 'Combo de TITI', NULL),
('e9999999-9999-9999-9999-999999999999', U&'Jap\00F3n', NULL),
('eaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Alemania', NULL),
('ebbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', U&'Pa\00EDses Bajos', NULL)
ON CONFLICT (id) DO UPDATE
SET nombre = EXCLUDED.nombre,
    delegado_cedula = EXCLUDED.delegado_cedula;

INSERT INTO torneos (id, nombre)
VALUES
('33333333-3333-3333-3333-333333333333', 'Supercopa 2024'),
('44444444-4444-4444-4444-444444444444', 'Supercopa 2023')
ON CONFLICT (id) DO UPDATE
SET nombre = EXCLUDED.nombre;

INSERT INTO jugador_equipo (id, cedula, equipo_id, torneo_id, fecha_inicio, fecha_fin)
VALUES
('55555555-5555-5555-5555-555555555555', '2000000001', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', '2024-01-10', NULL),
('66666666-6666-6666-6666-666666666666', '2000000001', '22222222-2222-2222-2222-222222222222', '44444444-4444-4444-4444-444444444444', '2023-01-05', '2023-12-20')
ON CONFLICT (id) DO UPDATE
SET cedula = EXCLUDED.cedula,
    equipo_id = EXCLUDED.equipo_id,
    torneo_id = EXCLUDED.torneo_id,
    fecha_inicio = EXCLUDED.fecha_inicio,
    fecha_fin = EXCLUDED.fecha_fin;

INSERT INTO partidos (id, torneo_id, fecha, equipo_local_id, equipo_visitante_id, estado)
VALUES
('77777777-7777-7777-7777-777777777777', '33333333-3333-3333-3333-333333333333', '2024-05-10 15:00:00', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'JUGADO'),
('88888888-8888-8888-8888-888888888888', '33333333-3333-3333-3333-333333333333', '2024-06-01 16:00:00', '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'JUGADO')
ON CONFLICT (id) DO UPDATE
SET torneo_id = EXCLUDED.torneo_id,
    fecha = EXCLUDED.fecha,
    equipo_local_id = EXCLUDED.equipo_local_id,
    equipo_visitante_id = EXCLUDED.equipo_visitante_id,
    estado = EXCLUDED.estado;

INSERT INTO partido_jugador (id, partido_id, cedula, equipo_id, goles, jugo)
VALUES
('99999999-9999-9999-9999-999999999999', '77777777-7777-7777-7777-777777777777', '2000000001', '11111111-1111-1111-1111-111111111111', 1, true),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '88888888-8888-8888-8888-888888888888', '2000000001', '11111111-1111-1111-1111-111111111111', 2, true)
ON CONFLICT (id) DO UPDATE
SET partido_id = EXCLUDED.partido_id,
    cedula = EXCLUDED.cedula,
    equipo_id = EXCLUDED.equipo_id,
    goles = EXCLUDED.goles,
    jugo = EXCLUDED.jugo;

INSERT INTO tarjetas (id, partido_id, cedula, tipo)
VALUES
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '77777777-7777-7777-7777-777777777777', '2000000001', 'AMARILLA'),
('cccccccc-cccc-cccc-cccc-cccccccccccc', '88888888-8888-8888-8888-888888888888', '2000000001', 'AZUL')
ON CONFLICT (id) DO UPDATE
SET partido_id = EXCLUDED.partido_id,
    cedula = EXCLUDED.cedula,
    tipo = EXCLUDED.tipo;

INSERT INTO titulos (id, torneo_id, equipo_id, puesto, fecha)
VALUES
('dddddddd-dddd-dddd-dddd-dddddddddddd', '33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'CAMPEON', '2024-12-10')
ON CONFLICT (id) DO UPDATE
SET torneo_id = EXCLUDED.torneo_id,
    equipo_id = EXCLUDED.equipo_id,
    puesto = EXCLUDED.puesto,
    fecha = EXCLUDED.fecha;
