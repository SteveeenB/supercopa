# MS2 · Super-Copa — Contexto del Microservicio

> **Proyecto:** CODE-CUP · UFPS · Facultad de Ingeniería de Sistemas
> **Stack:** Spring Boot · PostgreSQL (Supabase #2, schema `supercopa.*`)
> **Convive con MS3 en el mismo binario** — ver [`arquitectura_modular_ms2_ms3.md`](arquitectura_modular_ms2_ms3.md) para entender el patrón.
> **Documentos relacionados:** [`evolucion_y_decisiones_supercopa.md`](evolucion_y_decisiones_supercopa.md) (decisiones del sprint), [`contexto_ms3.md`](contexto_ms3.md) (módulo Finanzas que comparte binario).

---

## ¿Qué hace este microservicio?

MS2 es el núcleo del sistema. Gestiona todo el ciclo de vida de un torneo de fútbol: desde la creación de equipos permanentes y la configuración del torneo, pasando por la generación del fixture y la asignación de horarios, hasta el registro en tiempo real de los eventos de cada partido.

También cubre los flujos de excepción: aplazamientos, W.O., tanda de penales, descalificaciones y corrección de resultados por comité disciplinario.

> **Nota de arquitectura:** la funcionalidad de "Partido en Tiempo Real" vive dentro de este MS (no es un MS separado). MS3 (Finanzas) **comparte binario** con MS2 pero está aislado bajo el paquete `ms3finanzas/` y el prefix `/api/finanzas/**`. La razón completa de esta decisión está en `arquitectura_modular_ms2_ms3.md`.

---

## Estructura del paquete

```
terminus.co.edu.ufps.competicion.ms2supercopa/
├── client/          ← Ms1JugadoresClient (cliente HTTP hacia MS1)
├── config/
├── controller/      ← 9 controllers, todos bajo /api/supercopa/**
├── dto/
├── model/           ← @Entity con @Table(schema = "supercopa")
├── repository/
└── service/
```

---

## Historias de Usuario

> Estado de cierre observado en el código (no en el sprint plan original). Para el roadmap por sprint ver el board del proyecto.

### Implementadas en el binario actual

| HU | Descripción | Actor |
|----|-------------|-------|
| HU05 | Registrar equipo permanente (delegado crea equipo) | Delegado |
| HU07 | Asignar pseudónimo de torneo a un equipo | Delegado |
| HU08 | Configurar parámetros generales del torneo | Admin |
| HU12 | Generar fixture automático por grupos | Admin |
| HU15 | Visualizar y filtrar equipos inscritos | Admin |
| HU20 | Registrar eventos del partido con trazabilidad cronológica | Árbitro/Admin |
| HU21 | Registrar resultado final del partido | Árbitro/Admin |
| HU42 | Jugador solicita unirse a un equipo | Jugador |
| HU43 | Delegado gestiona solicitudes entrantes de jugadores | Delegado |
| HU44 | Delegado gestiona plantel (agregar/remover, número de camiseta) | Delegado |
| HU46 | Admin registra partido completo en ausencia del árbitro | Admin |
| HU16 | Registrar retiro o descalificación de equipo | Pendiente (spec en [`hu16.md`](hu16.md)) |

### Pendientes / parciales

| HU | Descripción | Estado |
|----|-------------|--------|
| HU09 | Configurar montos de multas y premios del torneo | Falta la entidad de configuración (MS3 la leerá para HU29) |
| HU10 | Seleccionar plantilla de formato del torneo | Pendiente |
| HU11 | Personalizar formato del torneo (grupos, clasificados, bracket) | Pendiente |
| HU13 | Asignar y editar horarios del fixture | Pendiente |
| HU14 | Publicar cronograma de partidos | Pendiente |

| HU17 | Registrar aplazamiento y reasignar horario | Pendiente |
| HU18 | Generar PDF de partidos por fecha | Pendiente |
| HU19 | Verificar elegibilidad de jugadores antes del partido | Pendiente — requiere consumir `MultaService` de MS3 (in-process) |
| HU22 | Registrar W.O. con observación del árbitro | Pendiente |
| HU23 | Registrar tanda de penales desde móvil | Pendiente |
| HU24 | Consultar cronograma público de partidos (sin login) | Pendiente |
| HU41 | Jugador edita perfil deportivo (apodo, foto, atributos) | Backend listo; falta UI (ver [`tickets_hu41_perfil_jugador.md`](tickets_hu41_perfil_jugador.md)) |
| HU45 | Jugador solicita salir de un equipo | Pendiente |
| HU47 | Admin corrige resultado de partido cerrado (comité disciplinario) | Pendiente |

### Deprecadas
- **HU06** — gestión de plantel original. Quedó cubierta por HU43 (solicitudes) + HU44 (gestión directa). No se implementará.

---

## Endpoints reales expuestos (prefix `/api/supercopa/**`)

```
# Público (sin auth)
GET    /api/supercopa/torneos                                 → lista torneos visibles
GET    /api/supercopa/equipos                                 → lista equipos públicos
GET    /api/supercopa/jugador/torneos/{torneoId}/equipos      → equipos del torneo

# Jugador
GET    /api/supercopa/mi-perfil                               → datos de perfil deportivo (HU41)
POST   /api/supercopa/solicitudes                             → solicita unirse a equipo (HU42)
GET    /api/supercopa/solicitudes/mis-solicitudes             → estado de solicitudes propias

# Delegado
POST   /api/supercopa/delegado/equipos                        → registra equipo permanente (HU05)
GET    /api/supercopa/delegado/equipos/mi-equipo              → su equipo activo
GET    /api/supercopa/delegado/torneos/disponibles            → torneos abiertos a inscripción
POST   /api/supercopa/delegado/torneos/{id}/inscribir         → inscribe equipo en torneo (HU07)
GET    /api/supercopa/delegado/inscripciones/mias             → estado de sus inscripciones
POST   /api/supercopa/delegado/inscripciones/{etId}/pagar     → mock pago (será reemplazado por HU28 en MS3)
GET    /api/supercopa/delegado/equipo-torneo/{etId}/miembros  → plantel del equipo en ese torneo
POST   /api/supercopa/delegado/equipo-torneo/{etId}/miembros  → agrega miembro al plantel (HU44)
POST   /api/supercopa/delegado/equipo-torneo/{etId}/miembros/{cedula}/remover  → remueve miembro
GET    /api/supercopa/solicitudes                             → solicitudes que le llegan (HU43)
GET    /api/supercopa/solicitudes/pendientes                  → solicitudes pendientes
POST   /api/supercopa/solicitudes/{id}/aprobar                → aprueba solicitud
POST   /api/supercopa/solicitudes/{id}/rechazar               → rechaza solicitud

# Admin
POST   /api/supercopa/admin/torneos                           → crea torneo (HU08)
GET    /api/supercopa/admin/torneos                           → lista torneos administrables
POST   /api/supercopa/admin/torneos/{id}/publicar             → publica torneo
POST   /api/supercopa/admin/torneos/{id}/iniciar              → inicia torneo
GET    /api/supercopa/admin/torneos/{id}/inscripciones        → ver inscripciones del torneo (HU15)
POST   /api/supercopa/admin/torneos/{id}/inscripciones/{etId}/aprobar  → aprueba inscripción
POST   /api/supercopa/admin/torneos/{id}/inscripciones/{etId}/rechazar → rechaza inscripción
POST   /api/supercopa/admin/torneos/{id}/fixture              → genera fixture (HU12)
GET    /api/supercopa/admin/torneos/{id}/partidos             → lista partidos del torneo
GET    /api/supercopa/admin/partidos/{pid}/eventos            → línea de tiempo de eventos
POST   /api/supercopa/admin/partidos/{pid}/eventos            → registrar evento (HU20, HU46)
DELETE /api/supercopa/admin/partidos/{pid}/eventos/{eid}      → eliminar evento
POST   /api/supercopa/admin/partidos/{pid}/cerrar             → cerrar partido con resultado (HU21)
GET    /api/supercopa/admin/equipo-torneo/{etId}/miembros     → admin ve plantel
```

---

## Flujos principales

### Ciclo de vida de un equipo
```
Delegado crea equipo (HU05)
  → asigna pseudónimo de torneo al inscribirlo en uno (HU07)
  → gestiona plantel: agrega jugadores validados por cédula (HU44)
  → Jugador puede solicitar unirse (HU42) → Delegado aprueba/rechaza (HU43)
  → Jugador puede solicitar salir (HU45 — pendiente)
```

### Configuración del torneo
```
Admin configura parámetros generales (HU08)
  → configura montos económicos (HU09 — pendiente) → multas y premios
  → selecciona plantilla de formato (HU10 — pendiente)
  → personaliza el formato (HU11 — pendiente)
  → genera fixture automático por grupos (HU12)
  → asigna horarios a los partidos del fixture (HU13 — pendiente)
  → publica el cronograma (HU14 — pendiente)
```

### Registro de un partido (tiempo real)
```
Árbitro verifica elegibilidad del plantel (HU19 — pendiente: consume MS3)
  → inicia el partido en la app móvil
  → registra eventos con timestamp (HU20)
  → al finalizar, registra el resultado oficial (HU21)
  → el cierre dispara generación de multas en MS3 (in-process — sin HTTP)
```

### Flujos de excepción
- **W.O. (HU22 — pendiente):** Resultado fijo `3-0`. Los goles **no** se asignan a jugadores ni cuentan para goleadores. Si el motivo es lluvia → pasa a Aplazado.
- **Aplazamiento (HU17 — pendiente):** Admin puede posponer y reasignar fecha/hora. MS5 notifica a delegados afectados.
- **Penales (HU23 — pendiente):** En partido único si hay empate al final del tiempo reglamentario. En ida y vuelta, si los puntos acumulados están empatados.
- **Corrección de resultado (HU47 — pendiente):** Solo el Admin. Queda registrado el motivo (comité disciplinario) y se regeneran estadísticas en MS4.
- **Descalificación (HU16 — pendiente):** El equipo sale del torneo. Sus resultados pasados se mantienen; partidos futuros se marcan como W.O. a favor del rival.

---

## Reglas de negocio críticas

- Un equipo debe tener **mínimo 8 jugadores elegibles** para que el árbitro pueda iniciar el partido.
- Un jugador está **no elegible** si: tiene deuda de multa pendiente (consultar MS3 vía `MultaService.elegibilidad`), está suspendido por acumulación de tarjetas, o no pertenece al plantel oficial registrado antes de la fecha límite.
- El fixture se genera automáticamente con round-robin dentro de cada grupo. No se puede regenerar si ya hay partidos jugados.
- Los horarios son editables hasta 24 horas antes del partido. Pasada esa ventana, requiere el flujo de aplazamiento.
- La interfaz móvil del árbitro (HU19, HU20, HU23) debe funcionar con lag máximo de **10 segundos** en el marcador público. Botones grandes y confirmación en dos pasos.
- Un delegado puede gestionar múltiples equipos, pero solo uno puede estar activo por edición del torneo.

---

## Dependencias

| Dirección | Cómo | Motivo |
|-----------|------|--------|
| MS2 → MS1 | HTTP (`Ms1JugadoresClient`) | Valida identidad y trae datos oficiales del padrón (nombre, código, semestre) |
| MS2 → MS3 | **In-process** (llamada directa al `MultaService`) | Genera multas automáticas al cerrar partido con tarjetas (HU29) — sin HTTP porque comparten binario |
| MS2 → MS3 | **In-process** (consulta `MultaService.elegibilidad`) | Bloquea jugadores con deudas al verificar plantel (HU19) |
| MS2 → MS5 | HTTP `@Async` fire-and-forget (`NotificacionPublisher`) | Notifica cambios de horario, aplazamientos, cierre de partido |
| MS2 → MS4 | (futuro) Lectura vía vistas materializadas en Supabase #3 | MS4 lee tablas `supercopa.*` vía `postgres_fdw` para estadísticas |
| MS3 → MS2 | **In-process** (lectura tabla de sanciones HU09) | MS3 lee configuración de montos cuando HU09 esté lista |

---

## Estado para agentes de IA

> Este MS es el más grande del proyecto y comparte binario con MS3.
>
> **Antes de tocar código:** leer [`arquitectura_modular_ms2_ms3.md`](arquitectura_modular_ms2_ms3.md) para entender por qué los paquetes `ms2supercopa/` y `ms3finanzas/` están separados y qué reglas mantienen el aislamiento (no imports cruzados, schemas Postgres separados, DTOs no se cruzan).
>
> Al implementar nuevas HUs:
> - Si la HU es de torneo/partido → vive en `ms2supercopa/`, expone bajo `/api/supercopa/**`.
> - Si la HU es de comprobantes/multas → vive en `ms3finanzas/`, expone bajo `/api/finanzas/**`.
> - Si MS2 necesita un servicio de MS3, inyecta el bean directamente (es in-process). No usar HTTP entre ellos.
> - Si MS2 necesita datos del padrón, usa `Ms1JugadoresClient` (HTTP).
>
> La interfaz del árbitro es móvil: priorizar rendimiento y UX táctil.
