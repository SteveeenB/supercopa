# MS4 · Analytics y Reportes — Contexto del Microservicio

> **Proyecto:** CODE-CUP · UFPS · Facultad de Ingeniería de Sistemas
> **Stack:** Spring Boot · PostgreSQL
> **Puerto local:** `8084` · Prefix de rutas: `/api/analytics/**`
> **Sprint planificado:** Sprint 3 (20 May – 10 Jun 2026) + Semana de Cierre (10–17 Jun 2026)
> **Estado del MS:** 🔄 EN PROGRESO — HU48 completada; resto pendiente Sprint 3

---

## ¿Qué hace este microservicio?

MS4 es la capa de lectura y presentación del sistema. Agrega datos de competición producidos por MS2 (partidos, eventos, fixture) y los expone como estadísticas públicas, rankings en tiempo real, reportes descargables y perfiles individuales de jugadores.

No escribe datos de negocio propios: actúa como una vista materializada sobre lo que MS2 registra. Su único dato de escritura propio son los premios configurados por el Admin (HU34), que se almacenan en la base de datos de MS4 vinculados a cada edición del torneo.

Es el MS más expuesto al público: la mayor parte de sus endpoints son accesibles sin autenticación.

---

## Historias de Usuario

### ✅ Completadas (adelanto de Sprint 3)

| HU | Descripción | Actor | Pts | Horas |
|----|-------------|-------|-----|-------|
| HU48 `[NUEVA]` | Jugador consulta su perfil de rendimiento histórico | Jugador | 5 | 10 h |

### ⬜ Pendientes Sprint 3 (20 May – 10 Jun 2026)

| HU | Descripción | Actor | Pts | Horas |
|----|-------------|-------|-----|-------|
| HU31 | Ver resultados y detalle de eventos del partido (sin login) | Visitante | 2 | 4 h |
| HU32 | Consultar tabla de posiciones en tiempo real (sin login) | Visitante | 3 | 6 h |
| HU33 | Consultar tabla de goleadores en tiempo real (sin login) | Visitante | 3 | 6 h |
| HU34 | Definir y mostrar premios del torneo | Admin | 2 | 4 h |

### ⬜ Pendientes Semana de Cierre (10–17 Jun 2026)

| HU | Descripción | Actor | Pts | Horas |
|----|-------------|-------|-----|-------|
| HU35 | Generar reporte semestral PDF para la facultad | Admin | 8 | 16 h |
| HU36 | Visualizar Salón de la Fama (sin login) | Visitante | 3 | 6 h |

**Total MS4:** 7 HU · 26 pts · 52 h — **1 completada, 6 pendientes**

---

## Flujos principales

### Vistas públicas del torneo activo (sin login)
```
Visitante accede al cronograma / resultados públicos
  → GET /api/analytics/partidos/{partidoId}/eventos       (HU31)
      devuelve secuencia cronológica de goles y tarjetas del partido
  → GET /api/analytics/torneos/{torneoId}/posiciones      (HU32)
      devuelve tabla por grupo con PJ, PG, PE, PP, GF, GC, DG, Pts
      aplica criterios de desempate del reglamento
  → GET /api/analytics/torneos/{torneoId}/goleadores      (HU33)
      devuelve ranking individual de goles; excluye goles de W.O.
      los datos se actualizan cada vez que MS2 cierra un partido
```

### Configuración de premios (Admin autenticado)
```
Admin configura premios del torneo activo                 (HU34)
  → POST /api/analytics/torneos/{torneoId}/premios
      almacena descripción y monto por categoría (Campeón, Subcampeón, Goleador, Portero…)
  → GET  /api/analytics/torneos/{torneoId}/premios
      devuelve los premios; endpoint público sin autenticación
  Al cerrar el torneo en MS2 → MS4 vincula al ganador estadístico con su premio
```

### Perfil de rendimiento del jugador (Jugador autenticado)
```
Jugador accede a su pestaña Perfil                        (HU48 ✅)
  → GET /api/analytics/jugadores/mi-perfil
      extrae cedula del claim JWT (no usar path param)
      agrega historial completo: todos los torneos, no solo el activo
      devuelve:
        - resumen (partidosJugados, goles, tarjetas, títulos)
        - detalle de partidos (fecha, rival, resultado, goles, tarjetas)
        - historial de equipos (nombre, período de vinculación)
        - títulos obtenidos (torneo, equipo con pseudónimo, puesto)
      excluye goles de W.O. del conteo individual
```

### Reporte semestral PDF (Admin autenticado)
```
Admin genera informe al finalizar el torneo               (HU35)
  → GET /api/analytics/torneos/{torneoId}/reporte/pdf
      requiere rol ADMINISTRADOR
      compila: participación total por rol, distribución por semestre,
               resultados, goleadores, portero menos vencido,
               campeón, subcampeón y premios entregados
      genera PDF con membrete institucional UFPS
      tiempo máximo de generación: 15 segundos para torneos ≤ 20 equipos
```

### Salón de la Fama (sin login)
```
Visitante accede al historial de ediciones anteriores     (HU36)
  → GET /api/analytics/salon-de-la-fama
      lista ediciones cerradas ordenadas del más reciente al más antiguo
      muestra por edición: nombre, temática, campeón (nombre permanente + pseudónimo),
                           subcampeón, goleador, portero menos vencido, premios
      el torneo activo aparece como "En curso" solo después de ser cerrado
      oficialmente en MS2
```

---

## Reglas de negocio críticas

- **Los goles de W.O. no cuentan.** Ni en la tabla de goleadores individuales (HU33) ni en el perfil de rendimiento del jugador (HU48). Se etiquetan como `W.O.` en el detalle de eventos.
- **Criterios de desempate en tabla de posiciones** (HU32), aplicados en este orden: puntos → resultado del enfrentamiento directo → diferencia de goles → goles a favor → goles en contra → expulsiones → sorteo.
- **El perfil de rendimiento es privado** (HU48). Solo el propio jugador puede ver su perfil completo; el endpoint valida que la cédula del claim JWT coincida con el perfil solicitado.
- **El Salón de la Fama solo muestra torneos cerrados** (HU36). Un torneo en curso no aparece en él hasta que el Admin lo cierre formalmente desde MS2.
- **Los premios se escriben en MS4** pero se leen desde el frontend público sin autenticación. El Admin puede modificarlos mientras el torneo no haya cerrado.
- **Al corregir un resultado (HU47, MS2)**, MS4 debe recalcular automáticamente la tabla de posiciones y los goleadores afectados. El recálculo se dispara por evento emitido desde MS2.
- **Los equipos retirados o descalificados** aparecen tachados o separados al pie de la tabla de posiciones; sus resultados ya jugados siguen siendo válidos para el cómputo del resto de equipos.

---

## Contratos de API que este MS expone

Todos los endpoints usan el prefix `/api/analytics/` según el routing del API Gateway.

```
# ── Públicos (sin autenticación) ─────────────────────────────────────────────

GET  /api/analytics/torneos/{torneoId}/posiciones
     → tabla de posiciones por grupo del torneo

GET  /api/analytics/torneos/{torneoId}/goleadores
     → ranking de goleadores del torneo (excluye W.O.)

GET  /api/analytics/partidos/{partidoId}/eventos
     → secuencia cronológica de eventos del partido (goles, tarjetas)

GET  /api/analytics/torneos/{torneoId}/premios
     → premios configurados para la edición del torneo

GET  /api/analytics/salon-de-la-fama
     → historial de todas las ediciones cerradas

# ── Autenticado · JUGADOR ────────────────────────────────────────────────────

GET  /api/analytics/jugadores/mi-perfil
     → perfil de rendimiento histórico del jugador autenticado
     → cedula se extrae del claim JWT; no se admite como parámetro externo

# ── Autenticado · ADMINISTRADOR ──────────────────────────────────────────────

GET  /api/analytics/torneos/{torneoId}/reporte/pdf
     → genera y descarga el reporte semestral en PDF

POST /api/analytics/torneos/{torneoId}/premios
     → crea o actualiza los premios de la edición del torneo
```

---

## Dependencias

| Dirección | MS | Motivo |
|-----------|----|--------|
| MS4 → MS2 | Consume datos de partidos, eventos, tarjetas, tabla y fixture para construir estadísticas |
| MS4 → MS1 | Obtiene nombre, cédula, semestre y rol de los jugadores para perfiles y reportes |
| MS2 → MS4 | Emite evento al cerrar un partido para disparar el recálculo de tabla y goleadores |
| MS2 → MS4 | Emite evento al corregir un resultado (HU47) para forzar recálculo |

> MS4 **no emite eventos** hacia otros microservicios. Solo escucha eventos de MS2 y realiza consultas de lectura a MS1.

---

## Diseño de datos sugerido (MS4)

MS4 puede mantener una copia desnormalizada (cache o vista materializada) de los datos que consume de MS2, actualizándola al recibir eventos, para no hacer consultas síncronas en cada petición pública.

Tablas propias de MS4:

```
premios
  - id
  - torneo_id
  - categoria (CAMPEON, SUBCAMPEON, GOLEADOR, PORTERO, OTRO)
  - descripcion
  - monto_cop

salon_fama
  - id
  - torneo_id
  - nombre_torneo
  - tematica
  - temporada
  - campeon_equipo_id
  - campeon_nombre_permanente
  - campeon_pseudonimo
  - subcampeon_equipo_id
  - goleador_cedula
  - portero_cedula
  - cerrado_en (timestamp)

Nota: las estadísticas de partidos, goles y tarjetas se leen directamente
desde MS2 vía llamada interna, o se cachean localmente al recibir eventos.
La decisión de cache vs. consulta directa se toma al inicio de la implementación.
```

---

## Errores recomendados

- `401` si el endpoint requiere JWT y el token es inválido o expirado.
- `403` si el rol del token no tiene acceso al endpoint solicitado (ej: jugador intenta acceder al PDF del admin).
- `404` si el torneo o el partido no existe, o si la cédula del jugador no tiene perfil registrado.
- `404` en `/mi-perfil` cuando no hay datos históricos: devolver perfil vacío con resumen en ceros, no un error.

---

## Estado para agentes de IA

> 🔄 Este microservicio tiene **HU48 completada** (perfil de rendimiento del jugador).
> El resto de las historias se implementan en Sprint 3 y la Semana de Cierre.
>
> **Prioridad Sprint 3 (desde el 20 de mayo):**
> Empezar por HU31, HU32 y HU33 ya que son las vistas públicas que el torneo
> necesita durante su desarrollo. HU34 (premios) puede implementarse en paralelo
> por ser independiente. Estas cuatro HU son de solo lectura y se apoyan
> principalmente en datos de MS2.
>
> **Prioridad Semana de Cierre:**
> HU35 (reporte PDF) y HU36 (Salón de la Fama) requieren que el torneo haya
> producido datos reales; por eso se dejan para el final.
>
> Al implementar HU32 y HU33, coordinar con el equipo de MS2 el contrato exacto
> de los endpoints o eventos que expone al cerrar un partido, antes de escribir
> código de MS4.
>
> **Nota de routing:** Los controllers deben usar el prefix `/api/analytics/`
> sin versión (`/v1/`). El API Gateway enruta `/api/analytics/**` a este MS
> en el puerto `8084`. No usar prefixes alternativos.