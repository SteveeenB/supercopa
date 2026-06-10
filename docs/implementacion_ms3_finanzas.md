# Plan · MS3 Finanzas + Inscripciones + Premios (Iter 2 ampliado)

> **Estado:** propuesto v3 — incorpora decisiones finales sobre elegibilidad granular por multa, premios como proyección desde MS2 a MS4, pago de arbitraje como excepción nueva.
> **Origen:** iter 2 del [plan_configurar_torneo.md](../../Frontend-CodeCup/codecup/docs/plan_configurar_torneo.md) quedó abierto porque tocaba MS3.
> **Alcance temporal:** demo de flujo completo, sin deadline duro tras el ajuste reciente.
> **Arquitectura base:** [arquitectura_modular_ms2_ms3.md](arquitectura_modular_ms2_ms3.md). Premios viven en MS2 (`supercopa.premios_torneo`); MS4 los expone públicamente como **proyección de lectura** vía el patrón documentado en §5.5 del doc de arquitectura.

---

## 1 · Contexto y objetivos

Hoy el sistema cubre la parte deportiva (formato, fixture, eventos, clasificación, auto-fill bracket), pero la capa económica está mockeada:

- El **delegado** se "auto-inscribe" llenando un formulario que no representa el flujo real.
- **No hay configuración de multas**: las tarjetas se registran pero no generan deuda económica ni suspensiones automáticas.
- El **monto de inscripción** está hardcodeado en `150.000` y no hay cuenta destino para transferir.
- Los comprobantes de inscripción no se almacenan realmente: el botón "Pagar" solo cambia un estado en BD sin archivo asociado.
- **No existe el flujo de "Registrar Pago" del árbitro** — el sistema no sabe cuándo una multa fue pagada en cancha.

El plan resuelve estos huecos respetando que MS3 ya tiene skeleton (entidades `Comprobante`, `Multa`, controllers básicos) y que MS2 + MS3 conviven en el mismo binario.

### 1.1 Decisiones acordadas

| # | Decisión | Implicación |
|---|---|---|
| 1 | **Supabase Storage real** para comprobantes de inscripción, en el **mismo proyecto Supabase #2** que aloja `supercopa.*` y `finanzas.*` | Bucket dedicado + URL firmadas; archivos NO en BD; sin proyecto Supabase nuevo |
| 2 | **Amarillas/azules generan multa económica** pero NO suspenden deportivamente | El bloqueo deportivo es exclusivo de la roja |
| 3 | **Bolsa de multas se trackea** con desglose por partido y quien registró el pago. Destino del dinero offline | Trazabilidad fina; admin distribuye recaudado a medallas/trofeos |
| 4 | **Suspensión por roja es automática** | Backend marca jugador no apto N fechas; admin/árbitro puede "habilitar excepción" |
| 5 | **Multa pendiente bloquea jugar** hasta que árbitro/admin haga click en "REGISTRAR PAGO" en Alineación | Mecanismo central de este plan |
| 6 | **"REGISTRAR PAGO" es por-multa** (granularidad fina en backend). La UI muestra estado agregado por jugador y un click paga **TODAS las multas pendientes** del jugador en una transacción | Si el jugador tiene 2 amarillas pendientes a $5k c/u → un click paga las dos = $10k total. El BD guarda 2 filas marcadas como PAGADA con trazabilidad individual |
| 7 | **Premios viven en MS2** (`supercopa.premios_torneo`). MS4 los expone públicamente vía proyección de lectura | Patrón §5.5 del doc de arquitectura. Admin configura desde MS2 admin UI; el frontend público pega a MS4 |
| 8 | **HU26 (notificación al delegado por roja)** fuera de scope hasta que MS5 exista. Se deja `LoggingNotificacionPublisher` como placeholder en MS3 | Cuando MS5 entre en producción se sustituye sin cambiar lógica de MS3 |
| 9 | **HU27 (comprobante de pago de multa)** fuera de scope inicial, documentada como futuro | Nice-to-have; mismo patrón que HU25 |
| 10 | **Portero menos vencido** se calcula automático usando `Jugador.posicion='PORTERO'` (campo ya existe) al cerrar torneo | Sin migración; cálculo lo hace MS4 al recibir webhook `torneo-cerrado` |
| 11 | **NUEVA: Pago de arbitraje como excepción** (50.000 entre ambos equipos antes de iniciar). Si no se paga → partido queda 0-0 con 0 puntos para ambos. El dinero NO se trackea (es de los árbitros, no del torneo) | Caso nuevo en `ExcepcionesTab` con su propio `tipoCierre` |
| 12 | **Admin Y árbitros** pueden hacer "Registrar Pago" y "Habilitar excepción" deportiva | Única diferencia entre roles: admin puede reabrir partido cerrado |

### 1.2 Cambios respecto a la versión v2 del plan

- 🔄 **Revertido**: premios vuelven a MS2 (`supercopa.premios_torneo`). MS4 los proyecta como lectura.
- 🔄 **Granularidad ajustada**: "Registrar Pago" es por-multa en backend pero la UI agrega todas las pendientes del jugador en un click (mockup confirmado por usuario).
- ✨ **Nuevo**: pago de arbitraje como excepción que cierra el partido en 0-0 con 0 puntos.
- ✨ **Nuevo**: `LoggingNotificacionPublisher` placeholder en MS3 para HU26 (notificación al delegado por roja) cuando MS5 exista.
- ✨ **Confirmado**: admin Y árbitros pueden "Registrar Pago" y "Habilitar excepción".
- ✨ **Confirmado**: `Jugador.posicion` ya existe; cálculo automático del portero en MS4.

---

## 2 · Modelo de datos

### 2.1 MS2 (schema `supercopa.*`)

**Modificación: entidad `Torneo`** — agregar campos de inscripción:

```sql
ALTER TABLE supercopa.torneos
    ADD COLUMN monto_inscripcion NUMERIC(12,2),
    ADD COLUMN cuenta_destino VARCHAR(120),     -- ej: "Nequi 300 555 1234"
    ADD COLUMN banco_destino VARCHAR(80),       -- opcional: "Bancolombia"
    ADD COLUMN titular_cuenta VARCHAR(150);     -- "Tesorero Code Cup"
```

**Nueva tabla `supercopa.multa_config`** (1:1 con torneo):

```sql
CREATE TABLE supercopa.multa_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    torneo_id UUID NOT NULL UNIQUE REFERENCES supercopa.torneos(id) ON DELETE CASCADE,
    monto_amarilla NUMERIC(12,2) NOT NULL DEFAULT 0,
    monto_azul NUMERIC(12,2) NOT NULL DEFAULT 0,
    monto_roja NUMERIC(12,2) NOT NULL DEFAULT 0,
    fechas_suspension_roja INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**Premios: REUSAR las 3 tablas que ya existen** en `supercopa.*` (no crear nueva tabla):

```sql
-- Estas tablas YA EXISTEN en el schema actual. Solo hay que poblarlas y usarlas:

-- 1. Catálogo global de tipos de premio (semilla inicial, no cambia por torneo)
--    supercopa.premio_catalogo (id, codigo, nombre, descripcion)
--    Codigos esperados: 'CAMPEON', 'SUBCAMPEON', 'TERCERO', 'GOLEADOR',
--                       'PORTERO_MENOS_VENCIDO', 'MVP', 'OTRO'
--    Si OTRO no funciona como código único cuando hay múltiples, usar 'OTRO_<n>'
--    o aprovechar el campo 'titulo' en torneo_premio para diferenciar.

-- 2. Instancia del premio para un torneo específico (admin lo configura aquí)
--    supercopa.torneo_premio (id, torneo_id, premio_id, titulo, descripcion, monto, created_at)
--    El admin crea filas aquí en el wizard de Configurar Torneo.
--    'titulo' permite override del nombre catalogado (útil para premios OTRO con nombre libre).
--    'monto' es INT (la columna es integer en el SQL real, no NUMERIC).

-- 3. Ganador asignado del premio (poblado al cerrar torneo, manual o auto)
--    supercopa.premio_asignado (id, torneo_id, premio_id, cedula, equipo_torneo_id, fecha_asignacion)
--    cedula → para premios individuales (Goleador, Portero, MVP)
--    equipo_torneo_id → para premios colectivos (Campeón, Subcampeón, 3°)
--    Una de las dos columnas puede ser NULL según categoría.
```

**Seed inicial necesario** en `premio_catalogo` (idempotente, una sola vez):

```sql
INSERT INTO supercopa.premio_catalogo (codigo, nombre, descripcion) VALUES
  ('CAMPEON',                'Campeón',                'Equipo ganador del torneo'),
  ('SUBCAMPEON',             'Subcampeón',             'Segundo puesto'),
  ('TERCERO',                'Tercer puesto',          'Tercer puesto deportivo'),
  ('GOLEADOR',               'Goleador',               'Máximo anotador del torneo'),
  ('PORTERO_MENOS_VENCIDO',  'Portero menos vencido',  'Portero del equipo con menor promedio GC'),
  ('MVP',                    'MVP',                    'Jugador más valioso (asignación manual)'),
  ('OTRO',                   'Otro',                   'Premio custom; usar campo titulo para diferenciar')
ON CONFLICT (codigo) DO NOTHING;
```

> **Sobre la tabla `supercopa.titulos`** (ya existe): registra puesto CAMPEON/SUBCAMPEON/TERCERO con `fecha`. Sigue siendo útil como registro deportivo histórico paralelo a `premio_asignado` (que puede tener monto). Estrategia: al cerrar torneo, **ambas tablas** se pueblan: `titulos` para el récord deportivo, `premio_asignado` para el ganador del premio económico correspondiente.

**Modificación: entidad `Partido`** — agregar `tipoCierre` para la excepción de pago de arbitraje:

```sql
ALTER TABLE supercopa.partidos
    ADD COLUMN tipo_cierre VARCHAR(30) CHECK (tipo_cierre IS NULL OR tipo_cierre IN ('WO', 'SIN_PAGO_ARBITRAJE'));
```

Cuando `tipo_cierre = 'SIN_PAGO_ARBITRAJE'`:
- `estado = FINALIZADO`, **sin ningún `eventos_partido` asociado** (los goles se computan de eventos; sin eventos = 0-0 natural).
- En `ClasificacionService`: NO suma puntos a ninguno de los dos equipos (ni 1 punto de empate normal).
- No se generan multas (no hubo partido).
- Ambos equipos lo cuentan en PJ; GF/GC suman 0 cada uno (no hay eventos GOL).
- `tipo_cierre = 'WO'` se llena retroactivamente en partidos cerrados por W.O. para mantener consistencia (opcional; el estado=WO actual ya los distingue).

### 2.2 MS3 (schema `finanzas.*`)

**Tabla `finanzas.multas` YA EXISTE** con la mayoría de campos que necesitamos:
- `id`, `cedula`, `torneo_id`, `partido_id`, `equipo_torneo_id` ✓
- `tipo_sancion` (AMARILLA | AZUL | ROJA | ACUMULACION_AMARILLAS | ROJA_DIRECTA) — *los últimos dos NO los usamos por decisión #2; quedan en el check para futuro*
- `monto NUMERIC` ✓ — *NO necesita `monto_snapshot` aparte; este valor es el snapshot implícito al momento de generar la multa, no cambia retroactivamente si se edita `multa_config` después*
- `estado` (PENDIENTE | EN_REVISION | PAGADA | CONDONADA) ✓
- `fecha_generacion`, `fecha_pago` ✓
- `partidos_suspension INT NOT NULL` ✓ — *esta es la columna que la decisión #4 (suspensión automática) ya tenía implícita en el schema*

**Sobre la "suspensión": NO se crea tabla `finanzas.suspensiones` aparte.** El schema actual ya encoda la duración de la suspensión en `multas.partidos_suspension`. Para tracking del cumplimiento + habilitación manual, **extendemos `finanzas.multas`**:

```sql
ALTER TABLE finanzas.multas
    ADD COLUMN partidos_suspension_restantes INT,           -- decrementa al cerrar cada partido del equipo;
                                                            -- inicialmente igual a partidos_suspension
    ADD COLUMN pagado_por_cedula VARCHAR(20),               -- árbitro/admin que registró pago (HU30)
    ADD COLUMN partido_pago_id UUID REFERENCES supercopa.partidos(id),  -- en qué partido se cobró
    ADD COLUMN habilitado_manual BOOLEAN NOT NULL DEFAULT FALSE,        -- override deportivo
    ADD COLUMN habilitado_por_cedula VARCHAR(20),
    ADD COLUMN habilitado_en TIMESTAMP,
    ADD COLUMN motivo_habilitacion VARCHAR(200);

CREATE INDEX idx_multas_cedula_torneo_estado
    ON finanzas.multas(cedula, torneo_id, estado);
```

**Semántica de los nuevos campos:**

- `partidos_suspension_restantes`: para ROJA, inicialmente = `partidos_suspension`. Decrementa cuando cerramos un partido del equipo del jugador (FINALIZADO, no DESCANSO ni SIN_PAGO_ARBITRAJE). Llega a 0 = la suspensión deportiva se cumplió. Para AMARILLA/AZUL queda en 0 desde el inicio (no suspenden).
- `pagado_por_cedula` + `partido_pago_id`: trazabilidad de quién y cuándo cobró. Se llena al hacer "Registrar Pago" en alineación.
- `habilitado_manual`/`habilitado_por_cedula`/`habilitado_en`/`motivo_habilitacion`: para override deportivo (admin/árbitro decide habilitar antes de cumplir suspensión por algún acuerdo en cancha).

**Tabla `finanzas.comprobantes` YA EXISTE** y soporta INSCRIPCION y MULTA:
- `id`, `cedula_uploader`, `estado` (PENDIENTE_REVISION | APROBADO | RECHAZADO), `fecha_subida`, `fecha_resolucion`, `motivo_rechazo`, `aprobado_por` ✓
- `tipo` (INSCRIPCION | MULTA) ✓ — soporta HU27 cuando llegue
- `referencia_id UUID NOT NULL` — **polimorfico**: si tipo=INSCRIPCION → apunta a `equipo_torneo.id`; si tipo=MULTA → apunta a `multas.id`
- `url_archivo` ✓ — guarda el path/URL del archivo en Supabase Storage

**No se agregan campos `monto_declarado`, `fecha_pago`, `metodo_pago`, `referencia_transaccion`** que mi plan original proponía. Por simplicidad: el delegado solo sube la foto. El admin revisa visualmente. Si en HU27 se requieren esos metadatos se agregan con ALTER en su iter futuro.

**Bolsa de multas (consulta on-demand, no es tabla)**:

```sql
-- Total recaudado en el torneo
SELECT SUM(monto) FROM finanzas.multas
WHERE torneo_id = ? AND estado = 'PAGADA';

-- Desglose por partido en que se cobró
SELECT partido_pago_id, SUM(monto), COUNT(*)
FROM finanzas.multas
WHERE torneo_id = ? AND estado = 'PAGADA' AND partido_pago_id IS NOT NULL
GROUP BY partido_pago_id;

-- Desglose por árbitro que registró pago
SELECT pagado_por_cedula, SUM(monto), COUNT(*)
FROM finanzas.multas
WHERE torneo_id = ? AND estado = 'PAGADA' AND pagado_por_cedula IS NOT NULL
GROUP BY pagado_por_cedula;
```

### 2.3 Migración

Archivo nuevo: `supercopa/db/supabase-migracion-iter7.sql` con:
- Las 4 columnas nuevas en `supercopa.torneos` (inscripción config).
- Tabla `supercopa.multa_config`.
- Columna `supercopa.partidos.tipo_cierre`.
- 7 columnas nuevas en `finanzas.multas` (suspensión_restantes, pagado_por_cedula, partido_pago_id, habilitado_*).
- Índice `idx_multas_cedula_torneo_estado`.
- Seed en `supercopa.premio_catalogo` (7 codigos estándar).

**NO crear:**
- ~~`supercopa.premios_torneo`~~ — usar las 3 existentes (`premio_catalogo`, `torneo_premio`, `premio_asignado`).
- ~~`finanzas.suspensiones`~~ — usar `multas.partidos_suspension_restantes`.
- ~~`finanzas.multas.monto_snapshot`~~ — `monto` ya es snapshot implícito.

Bucket Supabase Storage: se crea via Dashboard manual en **Supabase #2**, no requiere SQL.

---

## 3 · Storage Supabase para comprobantes de inscripción

### 3.1 Bucket

Crear desde Supabase Dashboard del **proyecto Supabase #2** (el mismo donde están los schemas `supercopa.*` y `finanzas.*`):

- Nombre: `comprobantes-inscripcion`
- Privado (no público)
- Tamaño máximo por archivo: 5MB
- Tipos permitidos: `image/jpeg, image/png, image/jpg, application/pdf`
- Path layout: `{torneoId}/{equipoTorneoId}/{uuid}.{ext}`

> **Nota:** este bucket es solo para comprobantes de inscripción (HU25). Cuando HU27 se implemente en el futuro, decidiremos si reutilizar este bucket con subpath `multas/...` o crear `comprobantes-multas` aparte.

### 3.2 Flujo de upload (signed URL)

1. Frontend pide URL firmada: `POST /api/finanzas/comprobantes/upload-url` con `{ tipo: INSCRIPCION, equipoTorneoId }`.
2. Backend genera URL firmada (válida 5 min) y devuelve `{ uploadUrl, fileKey }`.
3. Frontend hace `PUT` directo al storage con el archivo.
4. Frontend confirma: `POST /api/finanzas/comprobantes` con `{ fileKey, tipo: 'INSCRIPCION', referenciaId: equipoTorneoId }`.
5. Backend valida que el archivo exista (HEAD request) y crea el `Comprobante` con:
   - `url_archivo = fileKey`
   - `tipo = 'INSCRIPCION'`
   - `referencia_id = equipoTorneoId` (polimorfico — cuando llegue HU27 con `tipo=MULTA`, `referencia_id` apunta a `multas.id`)
   - `cedula_uploader = jwt.cedula`
   - `estado = 'PENDIENTE_REVISION'`
   - `fecha_subida = NOW()`

> El schema real **no tiene** `monto_declarado`, `fecha_pago`, `metodo_pago`, `referencia_transaccion`. El comprobante es un thin record "archivo + estado". Si HU27 (o futuras versiones) requieren esos metadatos, se agregan con `ALTER` en su iter.

### 3.3 Lectura

Admin que quiere ver el comprobante:
- `GET /api/finanzas/comprobantes/{id}/download-url` → URL firmada de descarga (60s) → frontend abre en nueva pestaña.

### 3.4 Config

`application.properties`:

```properties
supabase.url=${SUPABASE_URL}
supabase.storage.key=${SUPABASE_STORAGE_KEY}     # service_role key
supabase.storage.bucket=comprobantes-inscripcion
```

Cliente: `RestClient` de Spring contra `/storage/v1/object/...`. No hay SDK oficial Java de Supabase Storage.

---

## 4 · Endpoints

### 4.1 MS2 (`/api/supercopa/admin`)

```
GET    /torneos/{id}/finanzas-config        — devuelve { inscripcion, multas, premios }
PUT    /torneos/{id}/inscripcion-config     — monto, cuenta, banco, titular
PUT    /torneos/{id}/multa-config           — montos amarilla/azul/roja, fechasSuspensionRoja

# Premios (CRUD editable hasta que torneo esté FINALIZADO)
# El admin opera sobre supercopa.torneo_premio. premio_id viene del catálogo.
GET    /torneos/{id}/premios
       → [{ id, premioId, codigoCatalogo, titulo, descripcion, monto,
            ganadorCedula?, ganadorEquipoTorneoId?, asignadoEn?, ... }]

POST   /torneos/{id}/premios
       Body: { codigoCatalogo: 'CAMPEON'|'GOLEADOR'|...|'OTRO',
               titulo?, descripcion?, monto }
       → crea fila en torneo_premio resolviendo premio_id desde premio_catalogo.codigo
       → si codigoCatalogo='OTRO', titulo es OBLIGATORIO (para diferenciar múltiples OTROs)

PUT    /torneos/{id}/premios/{torneoPremioId}
       Body: { titulo?, descripcion?, monto? }

DELETE /torneos/{id}/premios/{torneoPremioId}
       → falla si ya hay premio_asignado vinculado (debe quitarse asignación primero)

POST   /torneos/{id}/premios/{torneoPremioId}/asignar
       Body: { cedula? | equipoTorneoId? }
       → upsert en premio_asignado (PK: torneo_id + premio_id).
       → MS4 hace este mismo POST con X-Internal-Secret al cerrar torneo
         para los premios automáticos (Goleador, Portero, Campeón, etc.).

DELETE /torneos/{id}/premios/{torneoPremioId}/asignar
       → quita la asignación (útil si se reabre torneo o se corrige ganador)

# Cierre torneo (dispara MS4 webhook torneo-cerrado)
POST   /torneos/{id}/cerrar                 — valida + estado=FINALIZADO + dispara webhook a MS4
```

> **Adaptación al schema real:** los premios usan las tablas existentes (`premio_catalogo`, `torneo_premio`, `premio_asignado`). El DTO de respuesta a la UI admin junta las 3 (join) para presentar una vista plana. La UI no necesita conocer la normalización; solo edita por `torneoPremioId`.

### 4.1.1 MS2 internal (`/api/supercopa/internal`) para MS4

```
GET /api/supercopa/internal/torneos/{id}/premios
  → proyección de lectura para MS4 (lo expone públicamente)
  Auth: X-Internal-Secret (ver arquitectura §5.6)
```

### 4.2 MS2 (`/api/supercopa/delegado`)

```
GET  /torneos/{id}/inscripcion-info     — monto a pagar, cuenta destino, estado de mi comprobante
                                          (REEMPLAZA al formulario de "auto-inscribirse")
```

### 4.3 MS3 (`/api/finanzas`)

**Existentes** (verificar y extender):
- `POST /comprobantes`, `GET /comprobantes/mis-comprobantes`, `GET /comprobantes/pendientes`
- `POST /comprobantes/{id}/aprobar`, `POST /comprobantes/{id}/rechazar`
- `GET /multas/mias`, `GET /multas/elegibilidad/{cedula}`
- `POST /multas/{id}/habilitar` (legacy — ver §5 sobre reemplazo por flujo nuevo)

**Nuevos**:

```
# Storage (comprobantes de inscripción)
POST /comprobantes/upload-url              — { equipoTorneoId } → { uploadUrl, fileKey }
GET  /comprobantes/{id}/download-url       — URL firmada de descarga (60s)

# Multas (admin)
GET  /multas/torneo/{torneoId}             — todas las multas del torneo (filtros: estado, equipo, partido)
GET  /multas/torneo/{torneoId}/bolsa       — { totalPagado, totalPendiente, breakdown por partido, breakdown por arbitro }

# Multas (registro de pago — el flujo central de HU19/HU30)
GET  /multas/jugador/{cedula}/pendientes?torneoId=        — multas pendientes del jugador en el torneo
POST /multas/{multaId}/registrar-pago                     — pago individual de UNA multa específica.
                                                            Body: { partidoPagoId }. El árbitro/admin que lo
                                                            invoca queda registrado vía JWT.
POST /multas/jugador/{cedula}/registrar-pago-todas        — atajo UI: paga TODAS las pendientes del jugador
                                                            en una transacción. Body: { torneoId, partidoPagoId }.
                                                            Internamente itera y llama al endpoint individual.

# Suspensiones deportivas — son una vista derivada de las multas ROJA con
# partidos_suspension_restantes > 0. NO hay tabla aparte.
GET  /multas/cedula/{cedula}/suspensiones?torneoId=  — multas ROJA con suspensión activa
GET  /multas/partido/{partidoId}/bloqueados          — jugadores bloqueados de cada equipo
                                                       (combina pendientes económicas + suspensión deportiva)
POST /multas/{multaId}/habilitar-excepcion           — admin/árbitro override deportivo con motivo.
                                                       Marca multas.habilitado_manual=true.
                                                       Solo aplica a multas ROJA con suspensión > 0.
```

### 4.4 MS2 — Pago de arbitraje (excepción)

```
POST /api/supercopa/admin/partidos/{id}/cerrar-sin-pago-arbitraje
     — body: { motivo? }
     — Cierra el partido con tipo_cierre='SIN_PAGO_ARBITRAJE', goles 0-0.
     — Disponible para admin Y árbitros.
     — Dispara webhook a MS4 (clasificación reconstruye sin contar puntos para ninguno).
```

---

## 5 · Lógica reactiva MS3

### 5.1 HU29 — Generar multas al cerrar partido

Modificar [`PartidoAdminService.cerrarPartido`](../src/main/java/terminus/co/edu/ufps/competicion/ms2supercopa/service/PartidoAdminService.java) tras el bloque actual de `bracketAutoFillService`:

```java
if (partido.getEstado() != EstadoPartido.WO) {
    try {
        multaService.generarPorPartido(partido.getId());
    } catch (Exception e) {
        log.error("Multa gen falló para partido {}", partido.getId(), e);
    }
}
```

`MultaService.generarPorPartido(partidoId)`:
1. Cargar partido + eventos AMARILLA/AZUL/ROJA del partido.
2. Cargar `MultaConfig` del torneo. Si no existe → log warning y abortar (no genera nada).
3. Por cada evento, crear UNA fila en `finanzas.multas`:
   ```
   { cedula, torneoId, partidoId (partido en que se generó), equipoTorneoId,
     tipoSancion, monto (de config), estado=PENDIENTE, fechaGeneracion=now(),
     partidosSuspension = (tipo=ROJA ? config.fechasSuspensionRoja : 0),
     partidosSuspensionRestantes = partidosSuspension,
     habilitadoManual = false }
   ```
4. No genera multas de eventos GOL.
5. Si la roja generó una multa nueva → invocar `notificacionPublisher.notificarRojaSuspendida(cedula, partidoId, fechas)`. Hoy es `LoggingNotificacionPublisher` (HU26 placeholder).

**Aclaración:** la "suspensión" no es entidad separada — vive en los campos `partidos_suspension` y `partidos_suspension_restantes` de la fila `multas`. No usamos `tipo_sancion='ACUMULACION_AMARILLAS'` (lo permite el schema pero por decisión #2 las amarillas no acumulan suspensión, solo multa económica).

### 5.2 HU28 — Aprobar/Rechazar comprobante de inscripción (efecto cross-MS)

`ComprobanteService.aprobar(id, adminCedula)`:
1. Cargar `Comprobante`. Validar `estado=PENDIENTE_REVISION`.
2. Cargar `EquipoTorneo` (vía `EquipoTorneoRepository` de MS2 inyectado — patrón documentado en arquitectura §5.1).
3. Si `equipoTorneo.estadoInscripcion == PENDIENTE_PAGO` → setear a `APROBADO`, `aprobadoPor=adminCedula`.
4. Marcar `Comprobante.estado=APROBADO`, `aprobadoPor=adminCedula`, `aprobadoEn=now()`.

`ComprobanteService.rechazar(id, adminCedula, motivo)`:
1. Marcar `Comprobante.estado=RECHAZADO`, `motivoRechazo=motivo`.
2. El `EquipoTorneo` queda en `PENDIENTE_PAGO`. El delegado puede subir un comprobante nuevo (el rechazado no se borra, se mantiene para auditoría).

> **Nota:** la lógica de aprobar/rechazar inscripción YA existe en MS2 vía endpoints administrativos. El plan los reutiliza desde MS3 con la diferencia de que ahora el admin **ve la foto antes de decidir**.

### 5.3 HU19 — Elegibilidad del jugador

La elegibilidad para alinearse en un partido tiene **dos dimensiones independientes**:

**A. Bloqueo deportivo (suspensión por roja):**
- Jugador tiene fila en `finanzas.multas` con `tipo_sancion='ROJA'`, `partidos_suspension_restantes > 0` y `habilitado_manual=false`.
- Bloqueo se levanta solo cuando:
  - `partidos_suspension_restantes` llega a 0 (decremento automático al cerrar cada partido del equipo), o
  - Admin/árbitro hace `POST /multas/{multaId}/habilitar-excepcion` con motivo (HU30 manual override) → `habilitado_manual=true`.
- **El pago de la multa NO levanta el bloqueo deportivo.** Son dos cosas distintas: `estado='PAGADA'` (económico) vs `partidos_suspension_restantes` (deportivo).

**B. Bloqueo económico (multa pendiente):**
- Jugador tiene cualquier multa con `estado='PENDIENTE'` en el torneo.
- Bloqueo se levanta cuando el árbitro hace click en "REGISTRAR PAGO" en la alineación → marca las filas como `estado='PAGADA'` con `pagado_por_cedula` + `partido_pago_id` + `fecha_pago=now()`.

**Estados visibles en la pestaña Alineación del modal de partido:**

| Estado | UI |
|---|---|
| Sin suspensión, sin multa pendiente | Toggle agregar a cancha habilitado |
| Multa pendiente, sin suspensión | Badge rojo "Debe $X · N multas"; toggle deshabilitado. Botón **"Registrar Pago"** disponible. |
| Suspensión activa, multas pagadas | Badge rojo "Suspendido (N fechas pendientes)"; toggle deshabilitado. Botón **"Habilitar excepción"** solo para admin/árbitro. |
| Suspensión activa + multa pendiente | Badge rojo "Suspendido + debe $X"; toggle deshabilitado. Botón "Registrar Pago" saldó la multa pero NO levanta la suspensión. |

**Implementación backend:**

`SuspensionService.consultarElegibilidad(cedula, torneoId)`:
- Devuelve `{ apto: bool, motivos: [] }`.
- `motivos` incluye `SUSPENSION_ACTIVA` y/o `MULTA_PENDIENTE` con detalles.

Modificar `PartidoAdminService.agregarJugador(partidoId, cedula, equipoTorneoId)`:
```java
ElegibilidadDTO el = suspensionService.consultarElegibilidad(cedula, torneoId);
if (!el.isApto()) {
    throw new RuntimeException("Jugador no apto: " + el.formatMotivos());
}
```

### 5.4 Cierre de torneo

Nuevo endpoint `POST /api/supercopa/admin/torneos/{id}/cerrar`:
1. Validar que TODOS los partidos estén FINALIZADO/WO/DESCANSO/SIN_PAGO_ARBITRAJE.
2. Cambiar `Torneo.estado='FINALIZADO'`.
3. Bloquear edición de `inscripcion-config`, `multa-config` y `torneo_premio` (premios).
4. Poblar `supercopa.titulos` (registro deportivo): CAMPEON / SUBCAMPEON / TERCERO con `equipo_torneo_id` desde el bracket.
5. Disparar webhook `POST /api/analytics/webhooks/torneo-cerrado` hacia MS4. MS4:
   - Calcula automáticamente **Goleador** (top de `eventos GOL` excluyendo eventos sin `cedula` — los del W.O. y los de partidos con `tipo_cierre='SIN_PAGO_ARBITRAJE'`, que no existen porque no se generaron eventos).
   - Calcula **Portero menos vencido** (equipo con menor `gc/pj`, portero identificado vía `jugadores.posicion='PORTERO'`).
   - **Hace HTTP de regreso a MS2** llamando `POST /api/supercopa/internal/torneos/{id}/premios/{torneoPremioId}/asignar` con `X-Internal-Secret` para persistir cada ganador automático en `supercopa.premio_asignado`.
6. **MVP y premios OTRO** quedan con `premio_asignado` vacío. La UI admin permite asignación manual desde dropdown de jugadores del torneo.

> **Dueño de la verdad:** MS2 (`supercopa.premio_asignado`). MS4 ayuda con el cálculo agregado pero no persiste premios. Detalles del patrón en [`arquitectura_modular_ms2_ms3.md`](arquitectura_modular_ms2_ms3.md) §5.5.

### 5.5 Registrar Pago de multa (flujo central del árbitro / admin)

Cuando el árbitro o admin abre la pestaña Alineación del modal de partido y ve un jugador con multas pendientes (mockup validado por usuario):

```
┌─────────────────────────────────────────────────────┐
│ 7  Kevin Mendoza                  [REGISTRAR PAGO]  │
│    🟡 AMARILLA PENDIENTE                            │
└─────────────────────────────────────────────────────┘
```

El árbitro pide la plata al delegado/jugador, la recibe físicamente, y hace click en **REGISTRAR PAGO**.

**Granularidad backend (por-multa):**
- El endpoint canónico es `POST /api/finanzas/multas/{multaId}/registrar-pago`.
- Cada multa es una fila independiente con su propio `pagado_en`, `pagado_por_cedula`, `partido_pago_id`, `monto_snapshot`.
- Trazabilidad fina: "¿Qué multa exactamente se cobró? ¿Quién la cobró? ¿En qué partido?".

**Atajo UI (por-jugador):**
- El frontend NO va a hacer N clicks si el jugador tiene N multas pendientes — eso sería terrible UX.
- Un solo click en "Registrar Pago" sobre el jugador llama a `POST /multas/jugador/{cedula}/registrar-pago-todas` con `{ torneoId, partidoPagoId }`.
- Backend itera las pendientes y las marca PAGADAS de a una (cada una con su propia fila actualizada).
- Devuelve `{ totalPagado, multasPagadasIds: [...] }`.
- Frontend muestra toast: `"✓ PAGO REGISTRADO · KEVIN MENDOZA HABILITADO"` (mockup validado).
- Refresca elegibilidad → el badge desaparece, sale botón "+" para agregar a cancha.

**Por qué la dualidad backend-per-multa + UI-per-jugador:**
- Si en el futuro HU27 (comprobante con foto por multa) se implementa, el endpoint per-multa ya está listo.
- Si se necesita auditar "¿qué multas se cobraron en el partido X?", se consulta por `partido_pago_id`.
- Si se necesita "pagar solo la amarilla y no la azul", el endpoint per-multa lo permite (UI lo expone cuando se requiera).

**Quién puede invocar:** árbitros Y admins (JWT requerido con rol ÁRBITRO o ADMINISTRADOR).

**Auditoría:** cada fila `finanzas.multas` queda con `pagado_por_cedula = jwt.cedula` permitiendo trazabilidad por persona.

**Caso edge — race condition:** dos árbitros con el mismo partido abierto en distintos dispositivos hacen click al mismo tiempo. El segundo recibe la multa ya con `estado=PAGADA` → el endpoint devuelve 409 Conflict con mensaje "Esta multa ya fue pagada por X el día Y". UI muestra ese mensaje en lugar de toast de éxito.

### 5.6 Pago de arbitraje — excepción de cierre 0-0

**Contexto:** antes de iniciar el partido, cada equipo le paga $25.000 al árbitro (total $50.000 entre ambos). Ese dinero NO pertenece al torneo — es para los árbitros. Por eso NO se persiste en BD ni se trackea como bolsa.

**Pero el caso operativo importante:** si alguno de los dos equipos no paga, **el partido no se juega**.

Reglas del cierre:
- `estado = FINALIZADO`, `tipo_cierre = 'SIN_PAGO_ARBITRAJE'`, `goles_local = 0`, `goles_visitante = 0`.
- En clasificación: **0 puntos para ambos equipos** (no es un empate 0-0 normal que daría 1 punto a cada uno).
- En GF/GC: cuenta como 0-0 (ambos suman 0 GF y 0 GC).
- En PJ: cuenta como jugado (sube en 1 a ambos equipos).
- **NO genera multas** por tarjetas (no hubo partido).
- **NO afecta suspensiones** existentes (las fechas pendientes no decrementan).
- **NO dispara cierre del bracket** en eliminatorias — el partido KO queda como si no hubiera jugador clasificado a la siguiente ronda; admin decide manualmente (o el partido se reprograma como cancelado fuera de este flujo).

**Endpoint:**

```
POST /api/supercopa/admin/partidos/{id}/cerrar-sin-pago-arbitraje
     Body: { motivo?, equipoNoPagoTorneoId? }
     Roles: ADMINISTRADOR o ARBITRO
```

**Casos del usuario:**

1. **Ninguno pagó** → `cerrar-sin-pago-arbitraje` sin especificar equipo. 0-0, 0 puntos ambos.
2. **Uno solo pagó** → mismo endpoint, especificar `equipoNoPagoTorneoId` para auditoría. Mismo resultado deportivo (no privilegia al que pagó).
3. **Uno pagó TODO el arbitraje (caso resuelto por flujo existente)** → es un **W.O.** estándar a favor del que pagó (3-0). Se usa el endpoint existente `POST /partidos/{id}/wo` con el equipo que pagó como ganador.

**Clasificación: cómo `ClasificacionService` lo maneja:**
- Modificar el cálculo para excluir partidos con `tipo_cierre='SIN_PAGO_ARBITRAJE'` del cómputo de puntos.
- Sí contarlo en PJ y goles (que serán 0-0).
- La fila del equipo en la tabla muestra estos partidos pero sin afectar puntos.

---

## 6 · Frontend Admin

### 6.1 Configurar torneo: secciones nuevas

En [`ConfigurarTorneoView`](../../Frontend-CodeCup/codecup/src/pages/admin/ConfigurarTorneoView.jsx), agregar al final del wizard (después del bloque "Generar fixture"):

**Sección · Inscripción**
- Input monto (formato pesos colombianos).
- Input cuenta destino (placeholder "Nequi 300 555 1234").
- Input banco (opcional).
- Input titular (opcional).
- Editable mientras `estado != FINALIZADO`.

**Sección · Multas por tarjeta**
- 3 inputs monto: amarilla, azul, roja.
- 1 stepper: `fechasSuspensionRoja` (default 1).
- Texto explicativo: *"Las amarillas/azules generan multa económica. El árbitro registra el pago en cancha vía el botón en la alineación. La roja además suspende N fechas deportivamente."*
- Editable mientras `estado != FINALIZADO`.

**Sección · Premios** (vuelve a MS2)
- Dos sub-cards: **Colectivos** (Campeón / Subcampeón / 3°) y **Individuales** (Goleador, Portero, MVP, +Otros).
- Botón "+ Agregar premio" en cada categoría → fila nueva con `alcance=OTRO` y nombre libre.
- Cada fila: dropdown alcance, input nombre (si OTRO), input monto, botón eliminar.
- Tooltip: *"Goleador y Portero menos vencido se asignan automáticamente al cerrar torneo. MVP y premios OTRO requieren asignación manual."*
- Editable mientras `estado != FINALIZADO`.
- Los datos se persisten en `supercopa.premios_torneo` (MS2). MS4 los proyecta de solo lectura para la vista pública.

### 6.2 Sidebar: tabs nuevos

- **Comprobantes pendientes**: lista de comprobantes en `PENDIENTE_REVISION`. Click → modal con preview de imagen/PDF (vía signed URL) + botones Aprobar/Rechazar (reusa lógica HU28 existente).
- **Multas activas**: lista de todas las multas del torneo. Filtros: estado, equipo, partido en que se generó. Widget superior: "Total recaudado: $X · Total pendiente: $Y · Desglose por partido". Click sobre una multa → detalle de quién la generó y, si está pagada, quién la cobró.
- **Equipos inscritos**: vista existente extendida — botón "Ver comprobante" en cada fila para abrir el preview cuando el delegado lo haya subido.

### 6.3 Modal de partido: elegibilidad visible (mockup validado)

Referencia: [`react_vistas_arbitro_y_delegado/pages/arbitro/AlineacionTab.jsx`](../../react_vistas_arbitro_y_delegado/pages/arbitro/AlineacionTab.jsx) (mockup target). Implementación actual: [`Frontend-CodeCup/codecup/src/pages/arbitro/AlineacionTab.jsx`](../../Frontend-CodeCup/codecup/src/pages/arbitro/AlineacionTab.jsx) (no tiene aún la lógica de suspensión/pago — hay que extenderla siguiendo el mockup).

En la pestaña **Alineación** del [`GestionPartidoModal`](../../Frontend-CodeCup/codecup/src/components/supercopa/GestionPartidoModal.jsx) (compartido entre admin y árbitro):

Para cada jugador del roster, consultar elegibilidad vía `GET /api/finanzas/multas/elegibilidad/{cedula}?torneoId=X&partidoId=Y`. El visual cambia según estado:

| Estado | Badge | Acción disponible |
|---|---|---|
| Sin suspensión ni multa | "Disponible" (gris) | Botón **"+"** verde → agregar a cancha |
| Multa pendiente (amarilla/azul) | 🟡 **"AMARILLA PENDIENTE"** o 🔵 **"AZUL PENDIENTE"** (en mockup, agregado) | Botón **"REGISTRAR PAGO"** amarillo |
| Suspensión por roja activa | 🔴 **"ROJA · NO HABILITADO"** | Botón **X** disabled (gris) |
| Suspensión + multa pendiente | 🔴 "Suspendido + Multa pendiente" | Botón "Registrar Pago" (paga lo económico pero NO levanta suspensión deportiva) |
| Ya en cancha | Sin badge | Botón **"−"** rojo para quitar |

**Flujo "REGISTRAR PAGO"** — click sobre un jugador con multa pendiente:
1. Modal de confirmación: "Cobrar $X a Kevin Mendoza. Saldará N multa(s) pendiente(s)."
2. Confirma → POST `/api/finanzas/multas/jugador/{cedula}/registrar-pago-todas`.
3. Toast verde: `"✓ PAGO REGISTRADO · KEVIN MENDOZA HABILITADO"`.
4. Badge desaparece, aparece botón "+" para agregar a cancha.

**Flujo "Habilitar excepción"** — para suspensión por roja (cuando admin/árbitro acepta el pago en cancha o decide perdonar):
1. Botón "Habilitar excepción" visible solo si hay suspensión activa.
2. Modal pide motivo (text input requerido): "El delegado confirmó pago el día Y", "Comité disciplinario perdonó", etc.
3. Confirma → POST `/api/finanzas/suspensiones/{id}/habilitar-excepcion` con motivo.
4. Disponible para **admin Y árbitros** (decisión usuario).
5. Toast: "✓ Jugador habilitado por excepción".
6. Suspensión queda en BD con `habilitado_manual=true`, `habilitado_por_cedula=jwt.cedula`, `motivo_habilitacion=texto`.

**Botón "Agregar todos"** del equipo (existente):
- Hoy agrega todos los jugadores del roster.
- **Cambio:** ahora salta los jugadores con suspensión activa o multa pendiente. El contador "X/Y" refleja solo elegibles.

### 6.3.1 Tab Excepciones: nueva opción "Sin pago de arbitraje"

Referencia actual: [`Frontend-CodeCup/codecup/src/pages/arbitro/ExcepcionesTab.jsx`](../../Frontend-CodeCup/codecup/src/pages/arbitro/ExcepcionesTab.jsx) ya tiene "W.O." y "Cancelar partido". Hay que **agregar una tercera card** "Sin pago de arbitraje":

```
┌─────────────────────────────────────────────────────┐
│ 🚫 Sin pago de arbitraje                            │
│                                                     │
│ Si ninguno de los equipos paga los $50.000 del      │
│ arbitraje, el partido no se juega. Resultado: 0-0   │
│ con 0 puntos para ambos. Sin multas ni eventos.     │
│                                                     │
│ ¿Quién no pagó? (auditoría)                        │
│   ◯ Ambos    ◯ Local    ◯ Visitante                │
│                                                     │
│ Motivo (opcional): [______________________________] │
│                                                     │
│              [ CONFIRMAR SIN PAGO ]                 │
└─────────────────────────────────────────────────────┘
```

Endpoint: `POST /api/supercopa/admin/partidos/{id}/cerrar-sin-pago-arbitraje` con `{ motivo?, equipoNoPagoTorneoId? }`.

Disponible para **admin y árbitros**.

### 6.4 Cerrar torneo

Botón en `ConfigurarTorneoView` cuando `estado=EN_CURSO` y todos los partidos están en estado terminal (FINALIZADO / WO / DESCANSO / SIN_PAGO_ARBITRAJE).

Modal de confirmación: "Cerrar este torneo congela la configuración financiera, registra los títulos deportivos y dispara el cálculo automático de Goleador, Portero y Campeón en MS4. ¿Continuar?"

Tras el cierre:
- Goleador, Portero, Campeón, Subcampeón, 3° quedan asignados automáticamente en `supercopa.premio_asignado` (MS4 hace el cálculo, MS2 persiste).
- **MVP y premios OTRO** quedan pendientes. La misma `ConfigurarTorneoView` muestra ahora un dropdown "Asignar ganador" por cada uno.
- La vista pública (`TorneoActivo.jsx`) consume `GET /api/analytics/torneos/{id}/premios` para mostrar los premios y sus ganadores. La data viene de MS4 que la proyecta desde MS2.

---

## 7 · Frontend Delegado

### 7.1 Reemplazar `PagosTab` mockeado

Eliminar el formulario manual actual. Dejar:

**Card de Inscripción Pendiente** (si hay alguna en `PENDIENTE_PAGO`):
- Monto a pagar (del torneo).
- Cuenta destino + banco + titular.
- Estado del comprobante: `Sin subir` / `En revisión` / `Aprobado` / `Rechazado` + motivo.
- Botón **"Subir comprobante"** → modal con file input + campos opcionales (referencia, fecha pago, método).
- Upload: pide URL firmada → sube directo a Supabase → POST a `/comprobantes`.
- Si el último comprobante fue rechazado → permite subir uno nuevo (re-intento).

**Historial de inscripciones**: tabla existente, agregar columna "Comprobante" con preview/link.

### 7.2 Inscripción al torneo

El flujo `POST /api/supercopa/delegado/torneos/{id}/inscribir` se mantiene, pero ahora:
- Solo crea `EquipoTorneo` con `estado=PENDIENTE_PAGO`.
- Backend devuelve el monto y cuenta destino para que el frontend muestre "Falta subir comprobante".
- El delegado YA NO confirma pago automáticamente; solo el admin lo hace al aprobar el comprobante.
- El endpoint legacy `POST /inscripciones/{etId}/pagar` (mock) se elimina.

### 7.3 Lo que NO se hace en el frontend del delegado

- Sin tab "Mis multas" — eso es parte de HU27 (futuro, sección 14).
- Sin pago de multas por el delegado — todo se hace cara-a-cara con el árbitro.

---

## 8 · MS4: lo que MS2/MS3 necesitan coordinar

MS2 dueña de la escritura de premios (`supercopa.premios_torneo`). MS4 es **proyección de lectura** pública. Patrón documentado en [`arquitectura_modular_ms2_ms3.md`](arquitectura_modular_ms2_ms3.md) §5.5 y §8.

**Coordinación:**

1. **MS2 expone los premios para MS4** vía endpoint interno:
   ```
   GET /api/supercopa/internal/torneos/{id}/premios
   Headers: X-Internal-Secret: ...
   ```
   Devuelve la lista de premios del torneo. Sin auth de usuario.

2. **MS4 los expone públicamente**:
   ```
   GET /api/analytics/torneos/{id}/premios   ← público, sin auth
   ```
   Internamente MS4 cachea o hace pull on-demand a MS2 (decisión menor de implementación; ver `plan_ms4.md` Sprint 1).

3. **Asignación automática al cerrar torneo:**
   - `POST /api/supercopa/admin/torneos/{id}/cerrar` (MS2) dispara webhook `torneo-cerrado` a MS4.
   - MS4 calcula automáticamente: Goleador (top goles, excluye W.O.), Portero menos vencido (via `Jugador.posicion='PORTERO'` + GC del equipo), Campeón/Subcampeón/3° (desde el bracket).
   - **MS4 hace HTTP de regreso a MS2** llamando `POST /api/supercopa/internal/torneos/{id}/premios/{premioId}/asignar` para persistir los ganadores en `supercopa.premios_torneo`.
   - MS2 sigue siendo el origen de la verdad para premios; MS4 solo los calcula y le devuelve los ganadores.

4. **MVP y premios OTRO:** se asignan manualmente desde la UI admin (MS2), no automático.

**Por qué MS4 hace el cálculo aunque MS2 sea dueño de los datos:**
- El cálculo necesita la clasificación, goleadores y porteros — datos que MS4 ya tiene materializados como snapshots.
- MS2 podría hacerlo también, pero MS4 ya tiene la lógica de agregación. Evita duplicación.
- Es coherente con el patrón general: MS2 escribe la decisión final (quién ganó qué premio); MS4 ayuda con el cálculo.

---

## 9 · Iteraciones de entrega (~5 días estimado)

### Día 1 — Backend MS2 + migración SQL
- Migración `iter7.sql`:
  - ALTER `supercopa.torneos` (4 columnas de inscripción config)
  - CREATE TABLE `supercopa.multa_config`
  - ALTER `supercopa.partidos` (columna `tipo_cierre`)
  - ALTER `finanzas.multas` (7 columnas: `partidos_suspension_restantes`, `pagado_por_cedula`, `partido_pago_id`, `habilitado_manual`, `habilitado_por_cedula`, `habilitado_en`, `motivo_habilitacion`)
  - CREATE INDEX `idx_multas_cedula_torneo_estado`
  - SEED inicial en `supercopa.premio_catalogo` (7 codigos estándar: CAMPEON, SUBCAMPEON, TERCERO, GOLEADOR, PORTERO_MENOS_VENCIDO, MVP, OTRO)
- Entidades JPA mapeando las tablas existentes (`PremioCatalogo`, `TorneoPremio`, `PremioAsignado`) + nuevas (`MultaConfig`, extendido `Multa`).
- `FinanzasConfigService` (MS2) con métodos para inscripción/multas.
- `PremioService` (MS2) CRUD sobre `torneo_premio` + asignación sobre `premio_asignado`.
- Endpoints `/admin/torneos/{id}/finanzas-config` y CRUD de premios.
- Endpoint interno `/api/supercopa/internal/torneos/{id}/premios` para MS4 (lectura) + `/asignar` (callback de MS4 al cerrar torneo).

### Día 2 — Backend MS3 + lógica reactiva
- `MultaService.generarPorPartido` + integración con `cerrarPartido` (escribe en `finanzas.multas`).
- Lógica de suspensión deportiva embedded en `MultaService`:
  - `consultarSuspensionesActivas(cedula, torneoId)`: query `multas WHERE tipo_sancion='ROJA' AND partidos_suspension_restantes > 0 AND habilitado_manual=false`.
  - `decrementarSuspensiones(equipoTorneoId)`: invocado al cerrar partido FINALIZADO del equipo (NO WO/DESCANSO/SIN_PAGO_ARBITRAJE).
  - `habilitarExcepcion(multaId, motivo, cedulaArbitro)`: marca `habilitado_manual=true`.
- `MultaService.registrarPago` (per-multa) y `registrarPagoTodas` (per-jugador, itera).
- `ComprobanteService.aprobar/rechazar` con efectos cross-MS.
- Validación de elegibilidad en `agregarJugador` (consulta multas con suspensión activa Y multas pendientes).
- `LoggingNotificacionPublisher` placeholder + invocación al crear multa ROJA.
- **Endpoint nuevo** `POST /partidos/{id}/cerrar-sin-pago-arbitraje` con la lógica de `tipo_cierre`.
- Modificar `ClasificacionService` para excluir partidos `SIN_PAGO_ARBITRAJE` del cómputo de puntos (cuentan PJ y 0-0 en GF/GC pero NO suman pts).

### Día 3 — Supabase Storage + comprobantes
- Crear bucket en Supabase #2.
- `StorageService` (URLs firmadas upload/download).
- Frontend delegado: integrar upload en `PagosTab` reescrito.
- Frontend admin: preview en "Comprobantes pendientes" y en "Equipos inscritos".

### Día 4 — UI admin completa
- Secciones nuevas en `ConfigurarTorneoView` (Inscripción + Multas + **Premios**).
- Modal de partido: badges de elegibilidad + botones "Registrar Pago" / "Habilitar excepción" (mockup validado).
- Nueva card "Sin pago de arbitraje" en `ExcepcionesTab`.
- Vista "Multas activas" con widget bolsa (filtros por partido, equipo, árbitro que cobró).

### Día 5 — Cierre + integración MS4 + pulido
- Endpoint y UI de "Cerrar torneo".
- Coordinación con MS4: webhook `torneo-cerrado` y callback de MS4 a MS2 con ganadores de premios automáticos.
- UI admin para asignación manual de MVP / premios OTRO.
- Smoke tests del flujo completo.

---

## 10 · Verification (camino del demo)

1. **Configurar finanzas**: como admin, ir a Configurar torneo. Setear inscripción $50k + Nequi destino. Multas $5k/$10k/$30k. **Premios: Campeón $1M, Subcampeón $500k, Goleador $200k, Portero $150k, MVP $300k (custom).**
2. **Validar lectura desde MS4**: hit `GET /api/analytics/torneos/{id}/premios` (público). Devuelve los premios configurados (vía pull a MS2 internal).
3. **Inscripción real del delegado**: inscribir equipo. Ver card de pago pendiente con monto y cuenta. Subir foto.
4. **Admin aprueba**: ir a Comprobantes pendientes. Ver preview. Aprobar → equipo pasa a APROBADO.
5. **Multas auto**: en un partido EN_CURSO, registrar 2 amarillas a un jugador, 1 roja a otro. Cerrar partido. Verificar BD:
   - 3 filas en `finanzas.multas` con `estado='PENDIENTE'` y `monto` poblado desde `multa_config`.
   - La fila ROJA tiene `partidos_suspension = partidos_suspension_restantes = 1` y `tipo_sancion='ROJA'`.
   - Las 2 amarillas tienen `partidos_suspension = 0` (no suspenden).
   - Log: `[NOTIF] roja: cedula=X partidos=1 delegado=Y` (placeholder de HU26).
6. **Elegibilidad bloquea (mockup validado)**: abrir próximo partido. Pestaña Alineación muestra al jugador con badge 🟡 "AMARILLA PENDIENTE" + botón "REGISTRAR PAGO". Botón "+" no aparece. El jugador con roja muestra 🔴 "ROJA · NO HABILITADO" + botón X disabled.
7. **Registrar Pago** (per-multa via UI per-jugador): click "REGISTRAR PAGO" sobre el jugador con 2 amarillas. Confirmar "$10.000". Toast `"✓ PAGO REGISTRADO · X HABILITADO"`. Badge desaparece, sale botón "+". Verificar BD:
   - Las 2 amarillas pasan a `estado='PAGADA'` (cada una con su propia fila).
   - Cada fila tiene `pagado_por_cedula = jwt.cedula del árbitro`, `partido_pago_id = partidoActual.id`, `fecha_pago = now()`.
8. **Suspensión deportiva persiste**: el jugador de la roja sigue bloqueado por `partidos_suspension_restantes=1`. La multa de la roja (monto $30k) puede seguir `PENDIENTE` o pagarse aparte; eso es ortogonal al bloqueo deportivo. Admin/árbitro click "Habilitar excepción" con motivo "Pago confirmado por delegado" → la fila ROJA queda con `habilitado_manual=true`, `habilitado_por_cedula=jwt.cedula`, `motivo_habilitacion='Pago confirmado por delegado'`.
9. **Decremento de suspensión**: cerrar el próximo partido del equipo (estado FINALIZADO, NO SIN_PAGO_ARBITRAJE). Verificar que `partidos_suspension_restantes` bajó de 1 a 0 en la fila ROJA. El jugador queda elegible automáticamente.
10. **Bolsa visible**: en tab "Multas activas", widget muestra "Total recaudado: $10.000 · Desglose por partido: [Partido X → $10k] · Desglose por árbitro: [Árbitro Y → $10k]".
11. **Excepción de pago de arbitraje**: en otro partido, ir a Excepciones → "Sin pago de arbitraje" → confirmar. Verificar BD:
    - `partidos.tipo_cierre = 'SIN_PAGO_ARBITRAJE'`, `estado='FINALIZADO'`.
    - Ningún `eventos_partido` asociado a ese partido (cómputo de goles = 0-0 natural).
    - Ningún equipo suma puntos en clasificación.
    - PJ de ambos sube en 1, GF/GC suman 0 cada uno.
    - No se generan multas (no hubo partido).
    - Suspensiones existentes NO decrementan por este partido.
12. **Cerrar torneo**: tras finalizar todos los partidos, click "Cerrar torneo". MS2 dispara webhook a MS4. MS4 calcula premios automáticos y hace callback a MS2 vía `/internal/.../premios/{id}/asignar`. Verificar:
    - `supercopa.premio_asignado` con `cedula` poblado para Goleador y Portero.
    - `supercopa.premio_asignado` con `equipo_torneo_id` para Campeón / Subcampeón / Tercero.
    - `supercopa.titulos` con 3 filas (CAMPEON, SUBCAMPEON, TERCERO).
    - El premio MVP en `torneo_premio` existe pero sin fila correspondiente en `premio_asignado` (manual pendiente).
13. **Asignar MVP manual**: UI admin selecciona jugador desde dropdown. Verificar BD: nueva fila en `premio_asignado` con `cedula` poblado para el `torneo_premio_id` del MVP.
14. **Vista pública de premios**: hit `GET /api/analytics/torneos/{id}/premios` sin auth. Devuelve premios con ganadores (MS4 hace pull a MS2 internal).

---

## 11 · Out of scope (esta entrega)

- **HU26 — Notificación al delegado por roja**: depende de MS5.
  - MS3 incluye un `NotificacionPublisher` interface con implementación `LoggingNotificacionPublisher` (solo loguea) — mismo patrón que MS2 usa hacia MS5 (ver `arquitectura_modular_ms2_ms3.md` §5.3).
  - Cuando se crea una `Suspension` por roja, `MultaService` invoca `publisher.notificarRojaSuspendida(cedula, partidoId, fechasPendientes)` con `@Async`.
  - Hoy solo emite log estructurado: `[NOTIF] roja: cedula=X partidos=Y delegado=Z`. Cuando MS5 entre en producción, se sustituye por `HttpNotificacionPublisher` sin tocar MS3.
- **HU27 — Comprobantes de pago de multas con foto**: detallado en sección 14 como futuro.
- **Pasarela de pagos real** (Stripe, Wompi, Mercado Pago).
- **HU35 (Reporte PDF)** y **HU36 (Salón de la Fama)** — son del MS4 Sprint 2 / Semana de Cierre.
- **Auditoría detallada** con historial de cambios en configuración (ej. "El admin cambió monto amarilla de $5k a $10k el día X").
- **Multi-temporada / multi-moneda / multi-país.**
- **Asignación manual de partido al árbitro específico** — hoy `pagado_por_cedula` es de quien registra en BD; no hay asignación previa de "este partido es del árbitro X".

---

## 12 · Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Supabase Storage setup tarda | Tener fallback "proxy through backend" listo para el día 3 si la signed URL no funciona. |
| `SUPABASE_STORAGE_KEY` se filtra en git | Solo en `.env` y secrets manager. Verificar `.gitignore`. |
| Árbitro hace click "Registrar Pago" pero no cobra realmente al jugador | Es responsabilidad del árbitro. La trazabilidad `pagado_por_cedula` permite auditoría posterior si surge un reclamo. |
| Suspensión se decrementa erróneamente en partido DESCANSO o WO | Decrementar solo cuando el estado del partido del equipo del jugador es `FINALIZADO`. Saltar WO/DESCANSO. |
| El admin cierra el torneo y luego quiere editar configuración | Estado `FINALIZADO` bloquea. Si necesita corregir, acción admin "Reabrir torneo" con doble confirmación (fuera de scope inicial). |
| Storage llena la cuota free de Supabase | Restricción 5MB por archivo + posible limpieza de comprobantes rechazados antiguos (post-demo). |
| Múltiples árbitros registran el pago de la misma multa al mismo tiempo | El segundo click falla porque la multa ya no está `PENDIENTE`. El frontend muestra "Esta multa fue cobrada por X el día Y". |

---

## 13 · Decisiones tomadas (eran preguntas abiertas)

| Decisión | Resolución |
|---|---|
| Portero menos vencido | Automático vía `Jugador.posicion='PORTERO'` (campo ya existe en MS2). Cálculo lo hace MS4 al recibir webhook `torneo-cerrado`. Resultado se persiste en `supercopa.premios_torneo`. |
| MVP / premios custom (OTRO) | Manuales desde la UI admin de MS2 al cerrar torneo. Admin selecciona desde dropdown de jugadores del torneo. |
| Multas cruzan torneos | NO. Cada torneo tiene su propia `multa_config` y sus propias multas. |
| Delegado paga multa de otro jugador | NO directamente vía la app en esta entrega. Proceso externo: delegado cobra a sus jugadores → llega al partido → árbitro/admin hace "Registrar Pago". |
| Comprobante de inscripción rechazado se puede re-subir | SÍ. El delegado sube uno nuevo; el rechazado se mantiene en BD para auditoría. |
| "Registrar Pago" granularidad | Backend per-multa (`POST /multas/{multaId}/registrar-pago`). UI por-jugador (atajo `POST /multas/jugador/{cedula}/registrar-pago-todas`). Match con el mockup validado por el usuario. |
| Admin vs árbitro: quién puede pagar y habilitar | **Ambos** pueden hacer "Registrar Pago" y "Habilitar excepción". La única diferencia: admin puede reabrir partido cerrado (`POST /partidos/{id}/reabrir` ya existente). |
| Premios: dueño | **MS2** (`supercopa.premios_torneo`). Admin configura desde UI admin de MS2. MS4 los expone públicamente como **proyección de lectura** (patrón doc arquitectura §5.5). |
| Pago de arbitraje ($50k entre equipos) | Excepción nueva en `ExcepcionesTab` ("Sin pago de arbitraje") → partido `tipo_cierre='SIN_PAGO_ARBITRAJE'`, 0-0 con 0 puntos para ambos. Dinero NO trackeado (pertenece a los árbitros, no al torneo). |

---

## 14 · Roadmap: HU27 — Comprobantes de pago de multa (FUTURO)

Cuando llegue el tiempo de implementar HU27, el plan será:

**Modelo:**
- Reutilizar tabla `finanzas.comprobantes` con `tipo='MULTA'` (el campo ya soporta este valor).
- **NO hace falta agregar `multa_id` aparte** — el campo `referencia_id UUID NOT NULL` ya es polimorfico:
  - Para `tipo='INSCRIPCION'` → `referencia_id = equipo_torneo.id`
  - Para `tipo='MULTA'` → `referencia_id = multas.id`
- El backend resuelve la referencia según `tipo`.

**Storage:**
- Reutilizar bucket `comprobantes` de Supabase Storage con subpath `multas/{torneoId}/{multaId}/{uuid}.{ext}`, o crear bucket `comprobantes-multas` aparte (decisión menor).

**Flujo:**
1. Delegado (o jugador) entra a tab "Mis multas".
2. Para cada multa pendiente, botón "Subir comprobante".
3. Sube foto vía signed URL.
4. Backend crea `Comprobante { tipo='MULTA', referencia_id=multaId, url_archivo, estado='PENDIENTE_REVISION', cedula_uploader=jwt.cedula }`.
5. Admin entra a "Comprobantes pendientes", filtra por `tipo='MULTA'`, ve foto, aprueba.
6. `ComprobanteService.aprobar` con `tipo='MULTA'`:
   - Resuelve `multa = multasRepo.findById(comprobante.referencia_id)`.
   - Marca `multa.estado='PAGADA'`, `multa.pagado_por_cedula=admin.cedula`, `multa.fecha_pago=now()`.
   - **NO setea `partido_pago_id`** porque el pago se hizo offline antes del partido. Para distinguir: filas con `pagado_por_cedula IS NOT NULL AND partido_pago_id IS NULL` son las pagadas vía HU27.

**UI delegado:** nueva tab "Mis multas" con lista filtrable por jugador del equipo.

**Coexistencia con "Registrar Pago" del árbitro:**
- Una multa puede pagarse por cualquier flujo, primero gana.
- Si el delegado sube comprobante y el admin no ha aprobado, el árbitro puede hacer "Registrar Pago" igual (acepta efectivo). El segundo flujo falla con 409 porque la multa ya está PAGADA.
- Si el delegado ya pagó y el admin ya aprobó, el árbitro ve la multa como `PAGADA` y NO necesita registrar nada.

**Coordinación esperada:** mockup que el usuario compartirá en próxima iteración para detallar la UI del árbitro registrando pago vs subiendo comprobante.

> Estado: documentado, no implementado. La estructura de datos (`pagado_por_cedula`, `partido_pago_id`, `referencia_id` polimorfico de comprobantes) ya soporta este caso sin cambios al cerrar HU27 más adelante.

---

## 15 · Notas finales

- Este plan **NO modifica** el contrato actual de los endpoints de árbitro/admin para alineación; solo agrega un nuevo check de elegibilidad antes del `agregarJugador`.
- La integración cross-MS (MS3 ↔ MS2 in-process, MS3 ↔ MS4 vía webhook al cerrar torneo) sigue exactamente los patrones del [doc de arquitectura](arquitectura_modular_ms2_ms3.md) §5.1, §5.4 y §5.5.
- El storage va en **Supabase #2** (proyecto compartido con MS2/MS3), confirmado con el usuario. MS4 sigue en Supabase #3.
- Cuando el usuario comparta el mockup del flujo del árbitro "Registrar Pago", se podrá refinar la UI exacta de la sección 6.3 antes de implementar el día 4.

---

## 16 · Reconciliación con el schema SQL real (referencia)

Tras revisar [`SQL_MS2_Schema-Supercopa.sql`](../db/oficiales/SQL_MS2_Schema-Supercopa.sql) y [`SQL_MS3_Schema-finanzas.sql`](../db/oficiales/SQL_MS3_Schema-finanzas.sql), esta sección documenta las decisiones de **reutilizar lo existente** vs **crear nuevo**, para que no se confunda durante la implementación.

### 16.1 Tablas que YA EXISTEN y se reutilizan tal cual

| Tabla | Uso en este plan |
|---|---|
| `supercopa.torneos` | Solo se hace ALTER para agregar 4 columnas (inscripción). El resto ya está. |
| `supercopa.partidos` | Solo se hace ALTER para agregar `tipo_cierre`. Estado check ya soporta WO y DESCANSO. |
| `supercopa.equipos` / `supercopa.equipo_torneo` / `supercopa.jugadores` | No se tocan. |
| `supercopa.jugadores.posicion` | Ya existe — se usa para el cálculo automático de Portero menos vencido. |
| `supercopa.titulos` | Se puebla al cerrar torneo con CAMPEON/SUBCAMPEON/TERCERO. Mantenida en paralelo con `premio_asignado`. |
| `supercopa.premio_catalogo` | Tabla maestra de tipos de premio. Solo se hace SEED inicial (7 codigos). No cambia per-torneo. |
| `supercopa.premio_catalogo` + `supercopa.torneo_premio` + `supercopa.premio_asignado` | Las **3 tablas existentes** cubren todo el flujo de premios. **NO se crea `supercopa.premios_torneo`** (era una propuesta de mi plan original que el schema real ya tenía resuelto de forma más normalizada). |
| `supercopa.eventos_partido.cedula` | Ya es nullable — para soportar goles dummy del W.O. (3-0). |
| `finanzas.comprobantes` | Reusada tal cual. `tipo` ya soporta INSCRIPCION y MULTA. `referencia_id` polimorfico (HU25 → equipo_torneo.id; HU27 → multas.id). |
| `finanzas.multas` | Reusada con 7 ALTER. Los campos base (`cedula`, `torneo_id`, `partido_id`, `tipo_sancion`, `monto`, `estado`, `partidos_suspension`, `fecha_pago`) ya existen. |

### 16.2 Lo que se AGREGA

**MS2 (`supercopa`):**
- `ALTER TABLE torneos`: `monto_inscripcion`, `cuenta_destino`, `banco_destino`, `titular_cuenta`.
- `ALTER TABLE partidos`: `tipo_cierre` con CHECK (NULL | 'WO' | 'SIN_PAGO_ARBITRAJE').
- `CREATE TABLE multa_config` (1:1 con torneos).
- SEED en `premio_catalogo` con 7 codigos estándar.

**MS3 (`finanzas`):**
- `ALTER TABLE multas`: `partidos_suspension_restantes`, `pagado_por_cedula`, `partido_pago_id`, `habilitado_manual`, `habilitado_por_cedula`, `habilitado_en`, `motivo_habilitacion`.
- `CREATE INDEX idx_multas_cedula_torneo_estado`.

### 16.3 Lo que se RECHAZÓ del plan original (no se hace)

| Propuesta original | Por qué se descarta |
|---|---|
| `supercopa.premios_torneo` (tabla nueva flat con `categoria`, `alcance`, `monto`, `ganador_*`) | Las 3 tablas existentes (`premio_catalogo` + `torneo_premio` + `premio_asignado`) son más normalizadas. |
| `finanzas.suspensiones` (tabla nueva) | `multas.partidos_suspension` y `partidos_suspension_restantes` ya carga la duración por multa. Más simple. |
| `finanzas.multas.monto_snapshot` (columna nueva) | El campo `monto` existente ya es snapshot implícito (no cambia retroactivamente cuando se edita `multa_config`). |
| `finanzas.multas.evento_partido_id` (FK opcional) | Se puede recuperar el evento de origen vía la combinación `(partido_id, cedula, tipo_sancion)` cuando haga falta. |
| `finanzas.comprobantes` con `monto_declarado`, `fecha_pago`, `metodo_pago`, `referencia_transaccion` | El schema solo tiene `url_archivo` + estado. Para esta entrega no se necesitan más campos. |
| `finanzas.comprobantes.multa_id` (FK explícito) | El campo polimorfico `referencia_id` ya cubre INSCRIPCION (referencia=equipo_torneo.id) y MULTA (referencia=multas.id). |
| `finanzas.suspensiones.evento_partido_id` (FK opcional) | El concepto desapareció al absorber la suspensión en `multas`. |
| `multa_config.amarillas_para_suspension` | Por decisión #2 (amarillas no acumulan suspensión), este campo es innecesario. |

### 16.4 Lo que se IGNORA del schema (campos que existen pero no usamos)

| Campo | Por qué se ignora |
|---|---|
| `multas.tipo_sancion = 'ACUMULACION_AMARILLAS'` | Decisión #2: amarillas no acumulan. No se generan filas con este tipo. |
| `multas.tipo_sancion = 'ROJA_DIRECTA'` | Por simplicidad usamos solo `'ROJA'`. Si en el futuro se quiere distinguir roja por acumulación vs directa, este tipo está disponible. |
| `multas.estado = 'EN_REVISION'` | El flujo actual va PENDIENTE → PAGADA directo (HU30 "Registrar Pago"). EN_REVISION se usará cuando HU27 (comprobante de multa) se implemente: PENDIENTE → EN_REVISION (delegado sube foto) → PAGADA (admin aprueba). |
| `jugador_equipo.posicion_snapshot` | Es snapshot histórico del momento de inscripción. Para el cálculo de Portero menos vencido usamos la posición LIVE de `jugadores.posicion`. |

### 16.5 Cómo MS3 lee tablas de `supercopa.*` desde su paquete `ms3finanzas/`

Siguiendo el patrón documentado en [`arquitectura_modular_ms2_ms3.md`](arquitectura_modular_ms2_ms3.md) §5.1:

- MS3 inyecta `EquipoTorneoRepository` (de MS2) directamente en sus services. NO usa HTTP.
- MS3 inyecta `MultaConfigRepository` (de MS2) para leer la config al generar multas.
- MS3 inyecta `TorneoRepository` (de MS2) para validar estado del torneo.
- Esto es coherente porque MS2 + MS3 viven en el mismo binario y MS3 puede importar entidades/repos de MS2 (la dirección MS2 → MS3 también está permitida).

> **Regla:** MS3 puede LEER tablas de `supercopa.*` vía repos inyectados. Lo que NO puede hacer es ESCRIBIR directo en `supercopa.*` (ese es el patrón "via service del MS dueño" §3 del doc de arquitectura). Para escribir cambios en `equipo_torneo.estado_inscripcion`, MS3 invoca `equipoTorneoService.aprobarInscripcion(...)` de MS2, no `equipoTorneoRepo.save(...)` directo.
