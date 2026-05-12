# Refinamiento: proyección de datos oficiales de jugador desde MS1 a MS2

## Contexto

`MS2.jugadores` necesita varios campos que en realidad pertenecen al padrón
oficial administrado por MS1: `rol_jugador`, `codigo_universitario`, `semestre`
y, en menor medida, `nombre` y `correo` "oficiales". Hasta la iteración 2,
MS2 inventaba `rol_jugador = 'GRADUADO'` como default cada vez que veía una
cédula nueva, lo cual ensuciaba los datos.

**Estado actual tras iter2:**
- `rol_jugador` ya es nullable (ver `db/supabase-migracion-iter2.sql`).
- Ya no se mete `GRADUADO` falso al crear jugadores.
- Pero los nuevos jugadores quedan con esos campos en `NULL` hasta que algo
  los proyecte desde MS1.

Este documento describe **cómo cerrar ese ciclo** y **cómo permitir que un
delegado registre miembros de su equipo aunque ese miembro nunca se haya
logueado en la plataforma**.

---

## Decisiones de diseño previas (ya tomadas)

1. **Padrón = verdad.** Los campos `cedula`, `nombre` (oficial),
   `rol_jugador`, `codigo_universitario`, `semestre` solo se editan vía
   recarga de CSV o aprobación del admin en MS1. Nunca por el propio usuario
   ni por el delegado directamente.
2. **`MS1.cuentas.nombre` es el alias** que el usuario eligió al registrarse
   (puede diferir del oficial; ej.: padrón "Daniel Esteban" vs alias "el Capi").
3. **Si el delegado intenta registrar una cédula que NO está en el padrón**,
   se crea una **solicitud de inclusión al admin** (no se rechaza, no se
   permite a ciegas). La inscripción al equipo queda en espera hasta la
   aprobación.
4. **Datos deportivos** (`altura_cm`, `pierna_habil`, `posicion`) viven solo
   en MS2 y son opcionales al crear el jugador.

---

## Cómo proyectar el dato oficial: dos caminos posibles

El documento `AuthCodeCup/rules/Auth_Integracion_MS2_MS6.md` define la
doctrina de comunicación entre MS: **JWT enriquecido > llamadas HTTP**.
Existen dos opciones, listadas por preferencia:

### Opción 1 (recomendada) — Enriquecer el JWT desde MS1

MS1 ya conoce el `rol_jugador` cuando emite el token (lo tiene en
`cuenta_roles`). Bastaría con añadir al payload del JWT los claims
`rol_jugador`, `codigo_universitario`, `semestre` cuando el rol que dispara
el token es JUGADOR.

- **Cambios en MS1:** `JwtTokenProvider.generarToken()` agrega los claims
  nuevos. Documentar en `Auth_Integracion_MS2_MS6.md` (sección 1).
- **Cambios en MS2:** `PerfilController` y `SolicitudController` leen los
  nuevos claims y los pasan a sus servicios; los servicios los persisten
  en `MS2.jugadores`. Cero infraestructura nueva.
- **Limitación:** solo cubre jugadores que **se loguean**. No sirve para el
  caso "delegado registra a alguien que nunca entró a la plataforma".

### Opción 2 (fallback) — MS2 llama a MS1 vía endpoint interno

MS1 ya expone `GET /api/jugadores/{cedula}` con autorización
`hasAnyRole('ADMINISTRADOR','DELEGADO')` y usa el patrón `SCOPE_internal`
para llamadas servicio-a-servicio (ver `JugadorController.java:50,58`).

- **Cambios en MS1:** crear `GET /api/jugadores/internal/{cedula}` con
  `hasAuthority('SCOPE_internal')` que devuelva un DTO mínimo
  (`cedula`, `nombre`, `correo`, `rol_jugador`, `codigo_universitario`,
  `semestre`, `activo`). 404 si no existe.
- **Cambios en MS2:** crear paquete `client/ms1/Ms1JugadorClient` con
  `RestClient` + `OAuth2AuthorizedClientManager` (client-credentials,
  scope `internal`). Variable `ms1.base-url` en `application.properties`.
- **Auth servicio-a-servicio:** verificar primero que el Authorization
  Server acepta `client_credentials` con scope `internal`. Si no, hay que
  habilitarlo.

> **Recomendación operativa:** implementar la **Opción 1** para resolver
> el caso del jugador que se loguea (mayoría). La **Opción 2** solo es
> imprescindible para el flujo del delegado (siguiente sección), y aún
> entonces puede evitarse si se modela como evento.

---

## Bloque A — Cambios en MS1 (`AuthCodeCup`)

### A.1 — Si se elige Opción 1 (JWT enriquecido)

- Modificar `JwtTokenProvider.generarToken()` (en MS1) para incluir los
  claims oficiales cuando el usuario tenga rol `jugador` aprobado.
- Actualizar `Auth_Integracion_MS2_MS6.md` sección 1 con los nuevos claims.
- Bumpear el TTL no es necesario, pero sí re-emitir tokens activos cuando
  cambie el padrón (ya hoy cualquier cambio relevante invalida el token).

### A.2 — Endpoint interno (Opción 2 o complemento de A.3)

- Nuevo `GET /api/jugadores/internal/{cedula}` con `SCOPE_internal`.
- DTO de proyección **mínimo**: sin datos de delegado, sin auditoría,
  sin metadatos del padrón (`fecha_actualizacion`, etc.).

### A.3 — Solicitudes de inclusión al padrón

Tabla nueva `padron_solicitudes` con campos:

- `id`, `cedula`
- `nombre_propuesto`, `correo_propuesto`, `rol_jugador_propuesto`,
  `codigo_universitario_propuesto`, `semestre_propuesto`
- `solicitante_cedula` (cédula del delegado que la creó)
- `estado` (`PENDIENTE` / `APROBADA` / `RECHAZADA`)
- `fecha_solicitud`, `fecha_resolucion`, `motivo_rechazo`
- Índice único parcial sobre `cedula` cuando `estado = PENDIENTE` para
  evitar duplicados simultáneos.

Endpoints:

- `POST /api/jugadores/internal/solicitudes-padron` — `SCOPE_internal`,
  consumido por MS2 cuando el delegado intenta registrar una cédula no
  conocida.
- `GET /api/admin/padron/solicitudes?estado=` — `hasRole('ADMINISTRADOR')`.
- `POST /api/admin/padron/solicitudes/{id}/aprobar` — inserta en
  `jugadores` y marca `APROBADA`. Notifica a MS2 (ver A.4).
- `POST /api/admin/padron/solicitudes/{id}/rechazar` — marca `RECHAZADA`
  con motivo. Notifica a MS2 también para que descarte la inscripción
  pendiente.

### A.4 — Notificación a MS2 sobre la resolución

Dos opciones, **alineadas con la doctrina de eventos enriquecidos**:

- **Síncrona** (más simple): MS2 expone
  `POST /api/supercopa/internal/padron-resuelta` (`SCOPE_internal`),
  MS1 lo invoca tras aprobar/rechazar con
  `{ cedula, estado, datos oficiales del padrón }`.
- **Bus de eventos** (cuando exista): MS1 publica `PADRON_RESUELTO`
  con todos los datos. MS2 (y MS6 para correos) lo consumen.

Empezar por la síncrona; migrar al bus cuando esté disponible.

---

## Bloque B — Cambios en MS2 (`supercopa`)

### B.1 — Refactor de los puntos donde antes se inventaba GRADUADO

- `service/PerfilService.java:42` y `service/SolicitudService.java:52`:
  cuando se cree el `Jugador`, llenar `rolJugador`, `codigoUniversitario`
  y `semestre` desde el JWT (Opción 1) o desde el cliente de MS1
  (Opción 2). Si ninguno aporta, dejar `null` (es lo que ya hace iter2).
- Cuando se reciba una operación sobre un `Jugador` ya existente,
  **refrescar** los campos oficiales si la fuente (JWT/HTTP) trae datos
  más nuevos. Conviene un caché breve (Caffeine, TTL 5–10 min) si se va
  por Opción 2 para no martillar MS1.

### B.2 — Endpoint del delegado para registrar miembros

- `POST /api/supercopa/delegado/equipo-torneo/{equipoTorneoId}/miembros`
  con body `{ cedula, numeroCamiseta?, alturaCm?, piernaHabil?, posicion?,
  /* campos del padrón opcionales para el caso "no existe en MS1" */ }`.
- Lógica del servicio:
  1. Validar que el delegado es dueño del `equipo_torneo` y que el torneo
     acepta inscripciones.
  2. Consultar MS1 (vía Opción 2). Si **existe en padrón**:
     crear/actualizar `MS2.jugadores` con datos oficiales y abrir
     `jugador_equipo` en estado `ACTIVO`. Inscripción inmediata.
  3. Si **no existe en padrón**: el body debe traer también los datos
     propuestos (`nombre`, `rol_jugador`, etc.). Llamar a A.3 para crear
     solicitud, registrar la inscripción en una tabla auxiliar
     (ver B.3) y devolver `202 Accepted`.
- Endpoint complementario `DELETE .../miembros/{cedula}` para baja
  (cierra `jugador_equipo` con `fecha_fin`).

### B.3 — Inscripciones pendientes de aprobación de padrón

Nueva tabla `inscripcion_pendiente_padron`:

- `id`, `cedula`, `equipo_torneo_id`, `numero_camiseta`, datos deportivos,
  `fecha_creacion`.
- Sirve de "memoria" mientras el admin aprueba/rechaza la solicitud.

Cuando MS1 notifique resolución (A.4):

- Si fue **aprobada**: leer la inscripción pendiente, crear el `Jugador`
  en MS2 con los datos oficiales recién aprobados + crear `jugador_equipo`
  en `ACTIVO`. Borrar la fila pendiente.
- Si fue **rechazada**: borrar la inscripción pendiente. Idealmente,
  notificar al delegado (vía MS6).

### B.4 — Endpoint helper para validar cédula desde el formulario

Útil para que el frontend del delegado sepa, antes de submit, si la
cédula está en padrón:

- `GET /api/supercopa/delegado/cedula/{cedula}/validar` — consume MS1 y
  devuelve `{ enPadron: bool, datosPublicos: {...}? }`. No persiste nada.
- Permite al frontend renderizar dinámicamente el formulario corto vs el
  formulario largo (ver C.1).

---

## Bloque C — Cambios en frontend (`Frontend-CodeCup`)

### C.1 — Tab "Mi Equipo" del delegado

Archivo: `src/pages/delegado/MiEquipoTab.jsx`.

- Botón "Agregar miembro" que abre modal con input de cédula.
- Al ingresar la cédula, llamar a B.4. Según respuesta:
  - **En padrón:** mostrar nombre/rol en disabled. Pedir solo número de
    camiseta y datos deportivos opcionales.
  - **No en padrón:** expandir formulario con los campos del padrón
    requeridos. Banner: "Esta persona no está en el padrón. Se enviará
    una solicitud al admin y quedará inscrita cuando se apruebe".
- Lista de miembros: badge "Pendiente de aprobación de padrón" para los
  que vengan de B.3.

### C.2 — Sección admin de solicitudes de padrón

Archivo: `src/pages/AdminDashboard.jsx` (nuevo tab) o página separada.

- Listado paginado, filtro por estado.
- Acción "Aprobar" (un click) y "Rechazar" (con motivo obligatorio).
- Refrescar la lista tras cada acción.

---

## Bloque D — Gestión de baja/retiro de membresía

Bloque **independiente** de A-C: arregla un problema distinto del
modelo `jugador_equipo` y puede implementarse antes, después o en
paralelo.

### D.1 — Diagnóstico del estado actual

La regla de negocio es: *"un jugador solo puede pertenecer a un equipo
por torneo, y queda ligado a ese equipo en cuanto juegue un partido"*.
La regla **se cumple parcialmente** pero el modelo está incompleto:

- **`EstadoMembresia.RETIRADO` está modelado pero muerto.** Existe en el
  enum y en el CHECK del schema (`jugador_equipo.estado`), pero ningún
  servicio lo setea ni lo consulta. Es código zombie.
- **No existe ningún endpoint de retiro.** Ni para el jugador desde su
  perfil, ni para el delegado desde su panel, ni para el admin. Una vez
  aprobada la solicitud, la membresía es de una sola dirección hasta que
  termine el torneo.
- **`existsByCedulaAndTorneoId` no filtra por estado.** Si en el futuro
  se setea `RETIRADO + fecha_fin`, las queries que validan unicidad
  (`SolicitudService.crear:65` y `aprobar:114`) seguirán diciendo "ya
  perteneces a un equipo" aunque la membresía esté cerrada.
- **Ningún punto del código verifica `partido_jugador.jugo = true`.**
  La regla "ya queda ligado al jugar un partido" hoy es solo una
  intención sin enforcement.

### D.2 — Casos que hoy quedan rotos

- **Jugador se equivoca de equipo y nunca jugó:** no puede salirse. Hay
  que ir a Supabase a borrarle la fila a mano. Mala UX.
- **Delegado quiere expulsar a un miembro** (lesión grave, conflicto,
  inactividad): no hay forma desde la app.
- **Equipo es rechazado o expulsado del torneo después** de tener
  jugadores aprobados: esos jugadores quedan ligados a un equipo
  fantasma y no pueden unirse a otro en ese torneo.
- **Migración por error de inscripción:** si un jugador entró por error
  al equipo equivocado del torneo (mismo torneo, otro equipo), el flujo
  de "salir y entrar a otro" no existe.

### D.3 — Endpoint del jugador para retirarse

Acción del propio jugador desde su perfil sobre una membresía propia.

- Ruta sugerida: `POST /api/supercopa/mi-perfil/equipos/{jugadorEquipoId}/retirar`
- Auth: `hasRole('JUGADOR')`. Validar en el servicio que la membresía
  pertenece a la cédula del JWT (no al de otro).
- Reglas:
  1. La membresía debe estar en estado `ACTIVO`.
  2. El jugador **no debe haber jugado ningún partido** en ese
     `equipo_torneo` (consulta a `partido_jugador` con `jugo = true`).
     Si jugó, devolver `409 Conflict` con mensaje claro: "ya estás
     ligado a este equipo, no puedes salirte porque jugaste partidos".
  3. El torneo debe estar en `PUBLICADO` o `EN_CURSO`.
- Efecto: actualizar la fila a `estado = RETIRADO`, `fecha_fin = hoy`.
  **No borrar** — preserva historial.

### D.4 — Endpoint del delegado para dar de baja

Acción del delegado sobre miembros de su propio equipo.

- `DELETE /api/supercopa/delegado/equipo-torneo/{etId}/miembros/{cedula}`
  (encaja con B.2; si B va antes, dejarlo en el mismo controller).
- Auth `hasRole('DELEGADO')`; validar dueño del `equipo_torneo`.
- Reglas idénticas a D.3 (no jugado, torneo activo, membresía activa).
- Recomendado exigir `motivo` en el body para auditoría (texto libre).
- Efecto idéntico a D.3 (`RETIRADO + fecha_fin`).

### D.5 — Override del admin (opcional pero útil)

Acción excepcional del admin cuando **sí jugó** pero hay que desligar
(disciplina grave, suplantación, error grave de inscripción).

- `POST /api/admin/membresias/{jugadorEquipoId}/retirar-forzado`,
  `hasRole('ADMINISTRADOR')`, motivo obligatorio, sin check de "no jugó".
- Históricos intactos: goles, tarjetas y participaciones no se borran;
  solo se cierra la membresía.

### D.6 — Ajuste de la query de unicidad

`JugadorEquipoRepository.existsByCedulaAndTorneoId` mira mera
existencia. Hay que **filtrar por estado activo** para que un jugador
retirado pueda re-inscribirse en otro equipo del mismo torneo.

- Añadir derivación `existsByCedulaAndTorneoIdAndEstado(..., ACTIVO)`
  y reemplazar las dos llamadas en `SolicitudService.crear:65` y
  `aprobar:114`.
- Mergear **junto con D.3/D.4**: aislado no rompe nada pero tampoco
  aporta; si los endpoints de retiro van sin este ajuste, la regla
  queda inconsistente.

### D.7 — Edge cases a considerar en QA

- **Retirar y volver a entrar** en otro equipo del mismo torneo: debe
  funcionar (siempre que no haya jugado).
- **Equipo rechazado a mitad del torneo:** decidir si las membresías se
  marcan auto-`RETIRADO` con motivo `EQUIPO_RECHAZADO` o quedan en limbo.
- **Cierre de torneo (`FINALIZADO`):** no requiere marcar `RETIRADO`; la
  membresía queda como histórico y la unicidad ya no aplica.
- **Auditoría:** registrar quién retiró (jugador/delegado/admin) y motivo.

### D.8 — Frontend asociado

- **Perfil del jugador** (`pages/JugadorDashboard.jsx` o equivalente):
  botón "Retirarme de este equipo" en la tarjeta del equipo activo, con
  modal de confirmación. Si el backend devuelve 409, mostrar mensaje
  claro: "ya jugaste un partido con este equipo, no puedes salirte".
- **Panel del delegado** (`pages/delegado/MiEquipoTab.jsx`): junto a
  cada miembro, opción "Dar de baja" con modal de motivo.
- **Panel del admin** (`pages/AdminDashboard.jsx`): tab o vista
  "Membresías" con buscador por cédula y opción de retiro forzado.

---

## Decisiones que conviene cerrar antes de empezar

1. **¿Opción 1, Opción 2 o las dos?** Opción 1 es suficiente para
   jugadores que se loguean. Opción 2 es necesaria para el flujo del
   delegado. Implementar Opción 1 primero acota mucho el scope.
2. **Auth servicio-a-servicio (solo si va Opción 2):** ¿el Auth Server de
   MS1 acepta `client_credentials` con scope `internal`? Si no, antes
   hay que habilitarlo. Es donde se puede ir más tiempo del esperado.
3. **Caché en MS2 (solo si va Opción 2):** ¿añadir Caffeine o vivir sin
   caché? Para la escala actual probablemente sin caché está bien.
4. **Datos deportivos al inscribir:** ¿el delegado los aporta en el alta
   o se piden al jugador cuando se loguea? Recomendado: opcionales en el
   alta para no bloquear al delegado.
5. **Notificaciones al delegado** cuando su solicitud sea aprobada o
   rechazada: ¿usar MS6 desde ya o dejar como TODO?
6. **Override de admin (D.5):** ¿imprescindible o fuera del primer
   release? Recomendado incluirlo como escape para soporte.
7. **Auditoría de retiros (D.4/D.5):** ¿columna `motivo_retiro` en la
   misma tabla o `auditoria_membresia` aparte? Si poco volumen, columna.

---

## Orden sugerido de implementación

1. **Opción 1 (JWT enriquecido):** A.1 + B.1. Resuelve el caso jugador
   que se loguea sin nueva infraestructura. Desplegable independiente.
2. **Opción 2 (cliente HTTP) + flujo delegado:** A.2 + A.3 + A.4 + B.2 +
   B.3 + B.4 + C.1.
3. **UI admin de padrón:** C.2.
4. **Gestión de baja/retiro:** D.3 + D.4 + D.6 + D.8 (el ajuste de
   query D.6 obligatoriamente con los endpoints, no antes). D.5 y D.7
   pueden ir en una segunda tanda.
5. **Notificaciones (vía MS6):** opcional, cierra UX de A y D.

El bloque D es **independiente** de A/B/C y puede priorizarse antes si
el problema operativo de "no se puede salir del equipo" molesta más que
el problema del rol oficial faltante.

---

## Archivos relevantes hoy (referencia rápida)

- `supercopa/src/main/java/.../service/SolicitudService.java:52,65,114`
- `supercopa/src/main/java/.../service/PerfilService.java:42`
- `supercopa/src/main/java/.../controller/DelegadoController.java`
- `supercopa/src/main/java/.../controller/PerfilController.java`
- `supercopa/src/main/java/.../controller/SolicitudController.java`
- `supercopa/src/main/java/.../repository/JugadorEquipoRepository.java`
  (D.6 — añadir `existsByCedulaAndTorneoIdAndEstado`)
- `supercopa/src/main/java/.../model/EstadoMembresia.java` (enum con
  `RETIRADO` hoy sin uso; lo activan D.3/D.4)
- `supercopa/db/supabase-migracion-iter2.sql`
- `AuthCodeCup/src/main/java/.../controller/JugadorController.java:50,58`
- `AuthCodeCup/src/main/java/.../controller/AdminRolesController.java`
- `AuthCodeCup/rules/Auth_Integracion_MS2_MS6.md` (doctrina de auth)
- `Frontend-CodeCup/codecup/src/pages/delegado/MiEquipoTab.jsx`
- `Frontend-CodeCup/codecup/src/pages/JugadorDashboard.jsx` (D.8)
- `Frontend-CodeCup/codecup/src/pages/AdminDashboard.jsx`
