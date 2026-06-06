# MS2 + MS3 · Evolución y Decisiones Técnicas

> **Propósito:** Registro consolidado de los cambios estructurales aplicados al repo `supercopa` durante la alineación con el modelo multi-microservicio: separación de identidad académica vs perfil deportivo, snapshots para reportes históricos, integración con MS1, reorganización de paquetes Java, y creación de la estructura inicial de MS3 (Finanzas).
>
> Este documento NO reemplaza al `contexto_ms2.md` ni al `contexto_ms3.md` (que describen QUÉ hace cada uno); aquí queda el POR QUÉ de las decisiones para que futuros mantenedores no reviertan cambios sin entender el contexto.

---

## 1. Reorganización de paquetes Java (estructura por microservicio)

### Antes

Todo el código de MS2 vivía suelto en sub-paquetes de `terminus.co.edu.ufps.competicion.*` (controller, dto, model, repository, service, etc.) sin separación clara cuando MS3 se sumó al mismo binario.

### Ahora

```
terminus/co/edu/ufps/competicion/
├── exception/                    ← compartido entre MS2 y MS3
│   ├── GlobalExceptionHandler.java
│   └── ResourceNotFoundException.java
├── ms2supercopa/                 ← TODO lo de MS2 vive aquí
│   ├── client/                   ← Ms1JugadoresClient + DTO
│   ├── config/, controller/, dto/, model/, repository/, service/
├── ms3finanzas/                  ← TODO lo de MS3 vive aquí
│   ├── controller/, dto/, model/, repository/, service/
└── CompeticionCodeCupApplication.java
```

### Por qué `ms2supercopa` y `ms3finanzas` (sin guión)

Java prohíbe el guión (`-`) en nombres de paquete (sí permite guión bajo, pero no es idiomático). Se eligió pegar palabras (`ms2supercopa` en vez de `ms2_supercopa`) siguiendo el espíritu del patrón de `AuthCodeCup` que usa `ms1` simple, pero agregando descriptividad porque aquí conviven 2 MS y la distinción visual ayuda.

### Por qué `exception/` se queda fuera de `ms2supercopa/` y `ms3finanzas/`

`GlobalExceptionHandler` es `@ControllerAdvice` global; aplica a TODOS los controllers sin importar el paquete. `ResourceNotFoundException` la usan tanto MS2 como MS3. Moverla bajo un MS implica que el otro tenga que importar de un namespace ajeno → acoplamiento innecesario. Mejor mantenerla compartida en la raíz del dominio.

### Cómo no romper Spring al refactorizar

`CompeticionCodeCupApplication` está en `terminus.co.edu.ufps.competicion` (raíz). Sin `@ComponentScan` explícito, Spring escanea recursivamente desde ahí — todos los sub-paquetes (`ms2supercopa.*`, `ms3finanzas.*`, `exception.*`) entran automáticamente. **No se necesitó tocar configuración de Spring para que la reorganización funcionara.**

---

## 2. Separación: identidad académica vive en MS1, perfil deportivo en MS2

### Problema detectado

La tabla `jugadores` de MS2 duplicaba datos del padrón oficial de MS1: `nombre`, `correo`, `rol_jugador`, `codigo_universitario`, `semestre`. Lo mismo ocurría en `solicitudes_equipo` (duplicaba `nombre` y `correo`).

Consecuencias:
- Dos fuentes de verdad para los mismos datos → divergencia silenciosa.
- Si un usuario manipula los campos en el formulario, MS2 guardaba lo que él dijera (no lo oficial).
- Cambios futuros en el padrón (CSV admin) no se reflejan en datos viejos de MS2.

### Decisión arquitectónica

Cada dato del jugador pertenece a uno de tres dominios:

| Dominio | Dueño | Ejemplos | Editable por el jugador |
|---|---|---|---|
| **Identidad académica oficial** | MS1 padrón | cedula, nombre legal, correo institucional, rol_jugador, código, semestre | NO |
| **Identidad cosmética** | MS2 | apodo, foto_url | SÍ (opt-in) |
| **Atributos deportivos** | MS2 | altura_cm, pierna_habil, posicion | SÍ |

### Cambios en entidades JPA

**`Jugador.java` (MS2):**
- **Quitados**: `nombre`, `correo`, `rolJugador`, `codigoUniversitario`, `semestre`.
- **Agregados**: `apodo`, `fotoUrl`.

**`SolicitudEquipo.java`:**
- **Quitados**: `nombre`, `correo`.

**`JugadorEquipo.java`:** agregados snapshots (ver §3).

> Los `ALTER TABLE` correspondientes ya están aplicados en Supabase #2 (BD Operacional, compartida por MS2 y MS3).

---

## 3. Snapshots académicos en `jugador_equipo`

### Por qué se necesitan

Los reportes históricos (ej. "Estudiantes por semestre — Super Copa 2024") deben reflejar la verdad **al momento del torneo**, no la verdad actual. Si un estudiante de 10° semestre en 2024 se gradúa antes de 2027, el reporte de 2024 debe seguir diciendo "1 estudiante de 10° en 2024".

El padrón se reemplaza cada año (admin sube CSV nuevo). Sin snapshot, perdemos la verdad histórica.

### Campos agregados

```java
@Column(name = "nombre_snapshot", length = 150)
private String nombreSnapshot;

@Column(name = "semestre_snapshot")
private Integer semestreSnapshot;

@Enumerated(EnumType.STRING)
@Column(name = "rol_jugador_snapshot", length = 20)
private RolJugador rolJugadorSnapshot;

@Column(name = "snapshot_at")
private LocalDateTime snapshotAt;
```

### Decisión: qué se snapshotea y qué no

| Campo | Snapshot | Razón |
|---|---|---|
| `nombre` | ✓ | Performance (evita N round-trips a MS1 al listar planteles) + cambia raramente (matrimonio, rectificación) |
| `semestre` | ✓ | Cambia cada semestre, crítico para reportes |
| `rol_jugador` | ✓ | Cambia con el tiempo (ESTUDIANTE → GRADUADO) |
| `codigo_universitario` | ✗ | Inmutable durante la carrera. Si MS4 lo necesita, consulta MS1 directo |
| `correo` | ✗ | Cosmético, no se usa en reportes históricos |

### Cuándo se puebla el snapshot

En **tres puntos** del código de MS2, siempre justo antes de persistir un `JugadorEquipo`:

1. `SolicitudService.aprobar` — cuando el delegado aprueba la solicitud de un jugador.
2. `DelegadoService.inscribir` — auto-inscripción del delegado al inscribir su equipo en un torneo.
3. `DelegadoService.agregarMiembro` — cuando el delegado agrega un jugador a su plantel.

Patrón: `ms1Client.getJugadorPorCedula(cedula).ifPresent(p -> poblarSnapshots(je, p))`. Si MS1 está caído o la cédula no está en padrón, los snapshots quedan null y MS4 los excluye de las agregaciones.

---

## 4. `Ms1JugadoresClient` — cliente HTTP a MS1

### Ubicación

`terminus.co.edu.ufps.competicion.ms2supercopa.client.Ms1JugadoresClient`

### Cómo se autentica

Propaga el JWT del usuario actual desde `SecurityContextHolder`:

```java
var auth = SecurityContextHolder.getContext().getAuthentication();
if (auth instanceof JwtAuthenticationToken token) {
    return token.getToken().getTokenValue();
}
```

Como el endpoint `/api/jugadores/{cedula}` de MS1 está protegido con `hasAnyRole('ADMINISTRADOR','DELEGADO')`, los flujos donde se llama desde MS2 funcionan porque el caller siempre es delegado o admin. Para llamadas sin contexto de usuario (eventos sistémicos) en el futuro se usará `SCOPE_internal`.

### Cache local

`ConcurrentHashMap` con TTL de 5 minutos. Suficiente para evitar N round-trips al listar un plantel de 12 jugadores. Si crece el tráfico, migrar a Caffeine.

### Configuración

Property `ms1.base-url` en `application.properties`:
```
ms1.base-url=${MS1_BASE_URL:http://localhost:8081}
```

En producción se inyecta vía env var apuntando a la URL pública de DO.

---

## 5. Estrategia de nombre a mostrar

Cuando MS2 tiene que mostrar el nombre del jugador, la **prioridad es:**

```
1. Jugador.apodo (cosmético, MS2)         ← solo si el jugador lo configuró
2. JugadorEquipo.nombreSnapshot           ← cero round-trips, ya en BD
3. Ms1JugadoresClient.getNombre()         ← fresh lookup como fallback
```

**Excepción importante:** en vistas administrativas críticas (eventos de partido, timelines del admin) **NO se usa apodo**. El admin necesita el nombre oficial para auditoría y conciliación. Implementado en `PartidoAdminService.resolverNombreReal(EventoPartido)`: empieza directamente en el paso 2 (snapshot) → 3 (MS1), saltándose el apodo.

---

## 6. Refactor del flujo "jugador solicita unirse a equipo"

### Antes

`SolicitudService.crear` recibía `cedula, nombre, correo, equipoTorneoId, altura, pierna, posicion`. Persistía nombre/correo en `Jugador` y los duplicaba en `SolicitudEquipo`.

### Ahora

`SolicitudService.crear(cedula, equipoTorneoId, alturaCm, piernaHabil, posicion)`:
- Solo persiste datos deportivos en `Jugador`.
- `SolicitudEquipo` queda con solo `cedula` como identificador.
- Para mostrar nombre/correo al delegado, se hace lookup a MS1 vía `Ms1JugadoresClient`.
- Snapshot académico **NO** se toma al crear la solicitud — se toma al **aprobar** (cuando se crea el `JugadorEquipo`).

`SolicitudController` simplificó la signature porque ya no recibe nombre/correo del JWT.

---

## 7. Estructura inicial de MS3 (Finanzas)

Se creó toda la estructura de paquetes y skeletons para MS3 sin implementar las HUs:

```
ms3finanzas/
├── model/
│   ├── Comprobante.java        ← HU25, HU27
│   ├── Multa.java              ← HU29, HU30
│   ├── TipoComprobante.java    ← INSCRIPCION | MULTA
│   ├── EstadoComprobante.java  ← PENDIENTE_REVISION | APROBADO | RECHAZADO
│   ├── TipoSancion.java        ← AMARILLA | AZUL | ROJA | ACUM_AMARILLAS | ROJA_DIRECTA
│   └── EstadoMulta.java        ← PENDIENTE | EN_REVISION | PAGADA | CONDONADA
├── repository/
├── dto/
├── service/                    ← Comprobante y Multa con TODO comments visibles en IDE
└── controller/                 ← /api/finanzas/comprobantes/** y /api/finanzas/multas/**
```

### Endpoints expuestos (skeleton funcional)

| Path | Rol | Estado |
|---|---|---|
| `POST /api/finanzas/comprobantes` | DELEGADO | CRUD OK |
| `GET /api/finanzas/comprobantes/mis-comprobantes` | DELEGADO | OK |
| `GET /api/finanzas/comprobantes/pendientes` | ADMIN | OK |
| `POST /api/finanzas/comprobantes/{id}/aprobar` | ADMIN | **TODO**: aplicar efectos cross-MS |
| `POST /api/finanzas/comprobantes/{id}/rechazar` | ADMIN | OK |
| `GET /api/finanzas/multas/mias` | JUGADOR | OK |
| `GET /api/finanzas/multas/activas` | ADMIN | OK |
| `POST /api/finanzas/multas/generar` | SCOPE_internal (MS2) | **TODO**: tabla de sanciones |
| `GET /api/finanzas/multas/elegibilidad/{cedula}` | ADMIN/DELEGADO/internal | OK |
| `POST /api/finanzas/multas/{id}/habilitar` | ADMIN/ARBITRO | OK |

### Decisión: MS3 vive en el mismo binario que MS2

Razones:
- **Comunicación MS2 → MS3 in-process**: cuando MS2 cierra un partido con tarjetas (HU29), llama a `multaService.generar(...)` como llamada de método directa, sin HTTP. Cero overhead.
- **Acceso compartido a Supabase #2**: ambos MS hacen JOIN sobre las mismas tablas.
- **Cumple "1 binario = 1 JVM"**: pegar MS3 a MS2 agrega ~20 MB extras, NO 250+ MB como una JVM separada. Cabe holgadamente en 512 MB de DO Basic.

### Estructura `analytics/` (futura, Supabase #3 dedicada)

MS4 (Analytics) **NO vive en este repo**. Se desplegará en su propio repo `analytics` como componente DO separado. Tendrá su propia BD Supabase #3 (Analítica), cuyo FDW importa tablas desde Supabase #1 (Identidad) **y** desde Supabase #2 (Operacional). Las vistas materializadas en `analytics.*` JOINean estas tablas remotas sin que MS4 necesite estar en el mismo binario que MS2 (ver `modelo_despliegue.md` §4.4).

---

## 8. Bug arreglado: `PartidoAdminService.toEventoDTO` con `Jugador.getNombre()`

### Síntoma

Tras quitar `nombre` de la entidad `Jugador` en §2, el compilador marcaba error en `PartidoAdminService.java:188`.

### Fix

Nuevo helper `resolverNombreReal(EventoPartido e)`:
1. Busca `JugadorEquipo` por `(cedula, torneoId del evento)` → usa su `nombreSnapshot`.
2. Si no hay snapshot, fresh lookup al padrón MS1 vía cliente HTTP.

Decisión consciente: **no se usa apodo** aquí porque el admin necesita el nombre oficial para auditoría de tarjetas y goles.

---

## 9. Próximos cambios sugeridos

- **HU29 (generación automática de multas):** completar la lógica de `MultaService.generar` leyendo la tabla de sanciones configurada en HU09 del MS2 (montos, partidos de suspensión, umbral de acumulación de amarillas).
- **HU28 (aprobación de comprobantes):** al aprobar un comprobante de tipo `INSCRIPCION`, cambiar `EquipoTorneo.estadoInscripcion` a `APROBADO` (hoy MS2 tiene mock_pago directo). Al aprobar tipo `MULTA`, marcar la `Multa` como `PAGADA`.
- **HU26 (notificación de estado):** emitir evento a MS5 vía `@Async` fire-and-forget cuando se aprueba/rechaza un comprobante. El patrón desacopla el side effect del flujo principal: si MS5 falla, la operación de MS2/MS3 no retrocede (ver `modelo_despliegue.md` §4.7).
- **HU19 (elegibilidad antes de partido):** MS2 debería consultar `GET /api/finanzas/multas/elegibilidad/{cedula}` antes de programar/iniciar un partido para bloquear jugadores suspendidos. Hoy el endpoint existe pero MS2 no lo invoca.
- **Storage de comprobantes:** los archivos (PDF/imagen) van a Supabase Storage. El campo `Comprobante.urlArchivo` ya está preparado; falta integrar el upload desde el frontend del delegado.
- **W.O. y multas:** si un partido se cierra como W.O., MS3 NO debe generar multas. Validar en `MultaService.generar` antes de procesar.

---

## 10. Decisiones que se evaluaron y se descartaron

### Pasar `nombre` por el body al solicitar rol jugador

Se discutió mantener el `nombre` en `SolicitarRolRequestDTO` "por si MS1 está caído". Se descartó: si MS1 está caído, no se puede emitir JWT, así que el flujo entero falla mucho antes. Mejor mantener la fuente única de verdad.

### Snapshot también de `codigoUniversitario`

Se eliminó. Razón: el código universitario es estable durante toda la carrera. Si MS4 lo necesita en un reporte, lookup en vivo a MS1 — siempre devolverá el valor histórico correcto.

### Separar MS3 de MS2 en repos distintos

Se evaluó moverlo a `finanzas-codecup`. Se mantuvo en el mismo repo `supercopa` porque:
- La comunicación MS2 → MS3 es reactiva e intensiva (eventos de partido → multas).
- Mismo Supabase, mismo equipo, mismo ciclo de release.
- Separación lógica suficiente vía paquetes `ms2supercopa/` y `ms3finanzas/`.

---

## 11. Esquemas `supercopa.*` y `finanzas.*` en la BD Operacional

### Contexto

MS2 y MS3 comparten Supabase #2 (BD Operacional) en el mismo binario, lo que podría tentarnos a mezclar sus tablas. Se aplica el **Patrón 2 de Newman (schema privado por servicio)**: cada módulo usa su propio schema de Postgres, y el motor puede hacer JOINs directos dentro del binario sin HTTP.

### Estructura de schemas

```
Supabase #2 (Operacional)
├── supercopa.*     → tablas de MS2: torneos, equipos, jugadores, partidos, eventos_partido, ...
└── finanzas.*      → tablas de MS3: comprobantes, multas, ...
```

### Por qué importa aunque sean el mismo binario

- **Auditoría clara**: un DBA que mire Supabase #2 sabe de inmediato qué tablas pertenecen a cada dominio de negocio.
- **Preparación para separación futura**: si en el futuro MS3 necesita escalar independientemente, los schemas permiten extraerlo a su propia BD sin refactorizar nombres de tabla.
- **Evita el antipatrón "shared database"**: aunque MS2 y MS3 comparten la misma instancia Postgres, sus domains no se mezclan silenciosamente.

Detalles completos del modelo de 3 BDs (Identidad / Operacional / Analítica) en `modelo_despliegue.md` §9.
