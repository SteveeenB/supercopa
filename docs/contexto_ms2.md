# MS2 · Super-Copa — Contexto del Microservicio

> **Proyecto:** CODE-CUP · UFPS · Facultad de Ingeniería de Sistemas
> **Stack:** Spring Boot · PostgreSQL
> **Sprint actual:** Sprint 2 (29 Abr – 20 May 2026) — **este MS es el núcleo del sprint activo**
> **Estado del MS:** 🔄 EN PROGRESO — el microservicio más grande del proyecto, implementación parcial

---

## ¿Qué hace este microservicio?

MS2 es el corazón del sistema. Gestiona todo el ciclo de vida de un torneo de fútbol: desde la creación de equipos permanentes y la configuración del torneo, pasando por la generación del fixture y la asignación de horarios, hasta el registro en tiempo real de los eventos de cada partido.

También cubre los flujos de excepción: aplazamientos, W.O., tanda de penales, descalificaciones y corrección de resultados por comité disciplinario.

> **Nota de arquitectura:** La funcionalidad de "Partido en Tiempo Real" fue absorbida por este MS en la v3.0. No existe un MS separado para partidos.

---

## Historias de Usuario

### ✅ Completadas

| HU | Descripción | Actor | Pts | Horas |
|----|-------------|-------|-----|-------|
| HU12 | Generar fixture automático por grupos | Admin | 8 | 16 h |
| HU15 | Visualizar y filtrar equipos inscritos | Admin | 2 | 4 h |
| HU20 `[MOD]` | Registrar eventos del partido con trazabilidad cronológica | Árbitro/Admin | 8 | 16 h |
| HU21 `[MOD]` | Registrar resultado final del partido | Árbitro/Admin | 3 | 6 h |
| HU42 `[NUEVA]` | Jugador solicita unirse a un equipo | Jugador | 3 | 6 h |
| HU43 `[NUEVA]` | Delegado gestiona solicitudes entrantes de jugadores | Delegado | 3 | 6 h |
| HU46 `[NUEVA]` | Admin registra partido completo en ausencia del árbitro | Admin | 3 | 6 h |

### 🔄 Parciales (iniciadas, requieren completarse)

| HU | Descripción | Actor | Pts | Pendiente |
|----|-------------|-------|-----|-----------|
| HU05 | Registrar equipo permanente | Delegado | 3 | Falta integrar flujo completo de pseudónimo (~2 h) |
| HU44 `[NUEVA]` | Delegado gestiona plantel directamente | Delegado | 5 | Falta: asignar número de camiseta y eliminar jugador del plantel (~3 h) |

### ⚠️ Arrastre de Sprint 1 (prioridad ALTA — deben cerrarse en Sprint 2)

| HU | Descripción | Actor | Pts | Horas |
|----|-------------|-------|-----|-------|

| HU07 | Asignar pseudónimo de torneo a un equipo | Delegado | 3 | 6 h |
| HU08 | Configurar parámetros generales del torneo | Admin | 5 | 10 h |
| HU09 `[MOD]` | Configurar montos de multas y premios del torneo | Admin | 3 | 6 h |

### ⬜ Pendientes Sprint 2 (deben cerrarse antes del 20 de mayo)

| HU | Descripción | Actor | Pts | Horas |
|----|-------------|-------|-----|-------|
| HU10 | Seleccionar plantilla de formato del torneo | Admin | 5 | 10 h |
| HU11 | Personalizar formato del torneo (grupos, clasificados, bracket) | Admin | 8 | 16 h |
| HU13 | Asignar y editar horarios del fixture | Admin | 5 | 10 h |
| HU14 | Publicar cronograma de partidos | Admin | 3 | 5 h |
| HU16 | Registrar retiro o descalificación de equipo | Admin | 5 | 8 h |
| HU19 `[MOD]` | Verificar elegibilidad de jugadores antes del partido | Árbitro/Admin | 5 | 10 h |
| HU45 `[NUEVA]` | Jugador solicita salir de un equipo | Jugador | 3 | 6 h |

### ⬜ Pendientes Sprint 3 (20 May – 10 Jun 2026)

| HU | Descripción | Actor | Pts | Horas |
|----|-------------|-------|-----|-------|
| HU17 | Registrar aplazamiento y reasignar horario | Admin | 5 | 8 h |
| HU18 | Generar PDF de partidos por fecha | Admin | 5 | 8 h |
| HU22 `[MOD]` | Registrar W.O. con observación del árbitro | Árbitro/Admin | 3 | 6 h |
| HU23 | Registrar tanda de penales desde móvil | Árbitro | 5 | 8 h |
| HU24 | Consultar cronograma público de partidos (sin login) | Visitante | 2 | 4 h |
| HU47 `[NUEVA]` | Admin corrige resultado de partido cerrado (comité disciplinario) | Admin | 5 | 10 h |

---

### ⚠️ Historias deprecadas de Sprint 2
HU06 se marca como obsoleta. Su escenario de agregar jugadores directamente quedó cubierto por HU44, y su escenario de solicitudes por HU43. No se implementará

| HU06 | Gestionar plantel de jugadores (agregar / validar / remover) | Delegado | 5 | 10 h |

## Flujos principales

### Ciclo de vida de un equipo
```
Delegado crea equipo (HU05)
  → asigna pseudónimo de torneo (HU07)
  → gestiona plantel: agrega jugadores validados por cédula (HU06)
  → Jugador puede solicitar unirse (HU42) → Delegado aprueba/rechaza (HU43)
  → Jugador puede solicitar salir (HU45)
  → Delegado puede gestionar número de camiseta y remover jugadores (HU44)
```

### Configuración del torneo
```
Admin configura parámetros generales (HU08): nombre, edición, fechas, límites
  → configura montos económicos (HU09) → multas y premios
  → selecciona plantilla de formato (HU10): grupos+eliminación, todos vs todos, etc.
  → personaliza el formato (HU11): nº grupos, clasificados por grupo, rondas
  → genera fixture automático por grupos (HU12)
  → asigna horarios a los partidos del fixture (HU13)
  → publica el cronograma (HU14) → visible para todos
```

### Registro de un partido (tiempo real)
```
Árbitro verifica elegibilidad del plantel (HU19) → lista de aptos generada
  → inicia el partido en la app móvil
  → registra eventos con timestamp: gol, tarjeta amarilla, tarjeta azul, tarjeta roja,
    sustitución, lesión (HU20)
  → al finalizar, registra el resultado oficial (HU21)
  → el resultado dispara eventos hacia MS3 (multas por tarjetas) y MS4 (estadísticas)
```

### Flujos de excepción
- **W.O. (HU22):** Resultado fijo `3-0`. Los goles **no** se asignan a jugadores ni cuentan en la tabla de goleadores. Si el motivo es lluvia → pasa a Aplazado.
- **Aplazamiento (HU17):** El Admin puede posponer un partido y reasignar fecha/hora. MS5 notifica a los delegados afectados.
- **Penales (HU23):** Se usa en partido único si hay empate al final del tiempo reglamentario. En ida y vuelta, se usa si los puntos acumulados están empatados. **Nunca** se usa diferencia de goles global como criterio previo.
- **Corrección de resultado (HU47):** Solo el Admin puede corregir un resultado ya cerrado. Queda registrado el motivo (comité disciplinario) y se regeneran las estadísticas en MS4.
- **Descalificación (HU16):** El equipo sale del torneo. Sus resultados pasados se mantienen; los partidos futuros se marcan como W.O. a favor del rival.

---

## Reglas de negocio críticas

- Un equipo debe tener **mínimo 8 jugadores elegibles** para que el árbitro pueda iniciar el partido.
- Un jugador está **no elegible** si: tiene deuda de multa pendiente (MS3), está suspendido por acumulación de tarjetas, o no pertenece al plantel oficial registrado antes de la fecha límite.
- El fixture se genera automáticamente con el algoritmo round-robin dentro de cada grupo. No se puede regenerar si ya hay partidos jugados.
- Los horarios son editables hasta 24 horas antes del partido. Pasada esa ventana, requiere el flujo de aplazamiento.
- La interfaz móvil del árbitro (HU19, HU20, HU23) debe funcionar con lag máximo de **10 segundos** en el marcador público. Los botones deben ser grandes y con confirmación en dos pasos.
- Un delegado puede gestionar múltiples equipos, pero solo uno puede estar activo por edición del torneo.

---

## Contratos de API que este MS expone (para otros MS)

```
GET  /api/v1/matches/{matchId}                   → datos del partido (resultado, eventos)
GET  /api/v1/matches/{matchId}/events             → línea de tiempo de eventos
GET  /api/v1/teams/{teamId}/roster                → plantel activo del equipo
GET  /api/v1/tournaments/{tournamentId}/standings → tabla de posiciones (consume MS4)
POST /api/v1/matches/{matchId}/events             → registrar evento (protegido: Árbitro/Admin)
GET  /api/v1/schedule/public                      → cronograma público sin autenticación
```

---

## Dependencias

| Dirección | MS | Motivo |
|-----------|----|--------|
| MS2 → MS1 | Valida identidad y rol de árbitros, delegados y jugadores |
| MS2 → MS3 | Al cerrar un partido, emite eventos de tarjetas para generar multas |
| MS2 → MS4 | Al cerrar un partido, emite eventos para actualizar estadísticas |
| MS2 → MS5 | Emite eventos para notificar cambios de horario y aplazamientos |
| MS3 → MS2 | MS3 consulta el historial de tarjetas para calcular suspensiones |
| MS4 → MS2 | MS4 consulta resultados y eventos para construir estadísticas |

---

## Estado para agentes de IA

> 🔄 Este es el MS más grande y el que tiene más trabajo pendiente.
>
> **Prioridad inmediata (Sprint 2 — antes del 20 mayo):**
> Completar HU05 y HU44 (parciales), implementar HU06–HU09 (arrastre S1),
> e implementar HU10–HU14, HU16, HU19, HU45.
>
> **Sprint 3 (20 may – 10 jun):**
> HU17, HU18, HU22, HU23, HU24, HU47.
>
> Al trabajar en cualquier HU de partidos, tener en cuenta que el resultado final
> dispara eventos hacia MS3 y MS4 — esos eventos deben emitirse correctamente.
> La interfaz del árbitro es móvil: priorizar rendimiento y UX táctil.