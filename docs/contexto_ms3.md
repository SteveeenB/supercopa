# MS3 · Finanzas y Multas — Contexto del Microservicio

> **Proyecto:** CODE-CUP · UFPS · Facultad de Ingeniería de Sistemas
> **Stack:** Spring Boot · PostgreSQL (Supabase #2, schema `finanzas.*`)
> **Convive con MS2 en el mismo binario** — ver [`arquitectura_modular_ms2_ms3.md`](arquitectura_modular_ms2_ms3.md) para entender el patrón.
> **Estado del módulo:** 🟡 SKELETON — endpoints expuestos y CRUD básico; lógica de generación automática (HU29) y efectos cross-MS al aprobar (HU28) pendientes.

---

## ¿Qué hace este microservicio?

MS3 gestiona todo el ciclo económico del torneo: la inscripción de equipos mediante comprobantes de pago, la generación automática de multas por tarjetas, la aprobación de recaudos por parte del Admin, y la habilitación en campo de jugadores sancionados una vez que saldan su deuda.

Es un módulo **reactivo**: la mayor parte de su lógica se dispara por eventos generados desde MS2 (al cerrar un partido con tarjetas), no por acciones directas del usuario. Como vive en el mismo binario que MS2, esos eventos llegan como **llamadas de método in-process**, no como HTTP.

---

## Estructura del paquete

```
terminus.co.edu.ufps.competicion.ms3finanzas/
├── controller/      ← 2 controllers, ambos bajo /api/finanzas/**
│   ├── ComprobanteController.java   → /api/finanzas/comprobantes
│   └── MultaController.java         → /api/finanzas/multas
├── dto/
├── model/           ← @Entity con @Table(schema = "finanzas")
│   ├── Comprobante.java        ← HU25, HU27
│   ├── Multa.java              ← HU29, HU30
│   ├── TipoComprobante.java    ← INSCRIPCION | MULTA
│   ├── EstadoComprobante.java  ← PENDIENTE_REVISION | APROBADO | RECHAZADO
│   ├── TipoSancion.java        ← AMARILLA | AZUL | ROJA | ACUM_AMARILLAS | ROJA_DIRECTA
│   └── EstadoMulta.java        ← PENDIENTE | EN_REVISION | PAGADA | CONDONADA
├── repository/
└── service/
```

---

## Historias de Usuario

| HU | Descripción | Actor | Estado |
|----|-------------|-------|--------|
| HU25 | Cargar comprobante de inscripción del equipo | Delegado | ✅ Skeleton OK (CRUD comprobante) |
| HU26 | Recibir notificación de estado de inscripción | Delegado | ⬜ Pendiente — depende de MS5 |
| HU27 | Cargar comprobante de pago de una multa | Delegado | ✅ Skeleton OK (mismo endpoint que HU25, distinto `tipo`) |
| HU28 | Aprobar/rechazar recaudos | Admin | 🟡 Skeleton OK; **TODO**: efectos cross-MS al aprobar (cambiar `EquipoTorneo.estadoInscripcion` o marcar `Multa` como `PAGADA`) |
| HU29 | Generar multa automática por tarjeta | Sistema | 🟡 Endpoint listo; **TODO**: leer tabla de sanciones (HU09 MS2) y aplicar |
| HU30 | Habilitar jugador sancionado en campo | Árbitro/Admin | ✅ Skeleton OK (endpoint `/multas/{id}/habilitar`) |

---

## Endpoints reales expuestos (prefix `/api/finanzas/**`)

```
# Comprobantes
POST   /api/finanzas/comprobantes                  → Delegado sube comprobante (HU25, HU27)
GET    /api/finanzas/comprobantes/mis-comprobantes → Delegado lista los suyos
GET    /api/finanzas/comprobantes/pendientes       → Admin lista pendientes (HU28)
POST   /api/finanzas/comprobantes/{id}/aprobar     → Admin aprueba (TODO: efectos cross-MS)
POST   /api/finanzas/comprobantes/{id}/rechazar    → Admin rechaza

# Multas
GET    /api/finanzas/multas/mias                   → Jugador lista sus multas
GET    /api/finanzas/multas/activas                → Admin lista multas activas
POST   /api/finanzas/multas/generar                → Llamada interna desde MS2 (in-process en HU29)
GET    /api/finanzas/multas/elegibilidad/{cedula}  → Admin/Delegado/MS2 consulta elegibilidad (HU19)
POST   /api/finanzas/multas/{id}/habilitar         → Admin/Árbitro habilita jugador tras pago (HU30)
```

> **Nota:** aunque `/api/finanzas/multas/generar` está expuesto como HTTP, el camino real desde MS2 (cuando HU29 se complete) será **inyectar `MultaService` y llamarlo directamente**, sin HTTP. El endpoint queda como salida alternativa en caso de que MS3 se extraiga a un binario propio en el futuro.

---

## Flujos principales

### Inscripción de equipo
```
Delegado sube comprobante de pago (HU25): imagen o PDF del recibo
  → queda en estado PENDIENTE_REVISION
  → Admin revisa y aprueba o rechaza con justificación (HU28)
  → al aprobar: marcar EquipoTorneo.estadoInscripcion = APROBADO (TODO)
  → MS5 notifica al Delegado (HU26 — pendiente, depende de MS5)
  → Si rechazado: Delegado puede subir nuevo comprobante corregido
```

### Multas por tarjetas (flujo automático)
```
MS2 cierra partido con tarjetas registradas (HU21)
  → MS2 llama IN-PROCESS a MultaService.generar(eventoTarjeta)
  → MS3 aplica la tabla de sanciones configurada en HU09 (MS2 — pendiente)
  → genera la(s) multa(s) correspondientes (HU29):
      - Tarjeta amarilla: monto fijo
      - Tarjeta azul:    monto fijo (mayor)
      - Tarjeta roja:    monto fijo (mayor) + suspensión automática
      - Acumulación de amarillas: suspensión al llegar al umbral
  → el jugador queda en estado SUSPENDIDO (no elegible) hasta que pague
  → MS2 (en HU19) lo bloquea al verificar elegibilidad antes del partido
```

### Pago de multa y habilitación
```
Delegado sube comprobante de pago de multa (HU27)
  → queda en estado PENDIENTE_REVISION
  → Admin aprueba o rechaza (HU28)
  → Si aprobado: marcar Multa.estado = PAGADA (TODO)
  → Árbitro/Admin habilita al jugador en campo antes del partido (HU30)
  → MS2 detecta el cambio en la próxima verificación de elegibilidad
```

---

## Tabla de sanciones (configurada en MS2 · HU09)

Los montos exactos los define el Admin al configurar el torneo. MS3 los lee como parámetros de configuración. La estructura es:

| Evento | Consecuencia económica | Consecuencia deportiva |
|--------|----------------------|----------------------|
| Tarjeta amarilla | Multa (monto configurable) | Acumulables: N amarillas = 1 partido suspendido |
| Tarjeta azul | Multa (monto configurable) | Sin suspensión directa |
| Tarjeta roja | Multa (monto configurable) | Suspensión mínima: 1 partido (configurable) |
| Roja directa | Multa mayor (configurable) | Suspensión mayor (configurable) |

> El número de amarillas para activar suspensión y la duración de las suspensiones son parámetros del torneo, no están hardcodeados.

---

## Reglas de negocio críticas

- Un equipo con inscripción **no aprobada** no puede participar en el torneo. MS2 bloquea la elegibilidad del plantel completo.
- Un jugador con multa pendiente está **automáticamente suspendido**. MS2 lo excluye de la lista de elegibles (HU19) consultando `MultaService.elegibilidad`.
- La **habilitación en campo** (HU30) es un paso explícito: que el Admin apruebe el pago no habilita automáticamente al jugador. El Árbitro o el Admin deben confirmar la habilitación antes del partido.
- Los comprobantes deben almacenarse (imagen/PDF) y ser auditables. No se eliminan aunque sean rechazados. Storage objetivo: Supabase Storage.
- Si un partido registra W.O. (MS2), **no se generan multas** por ese evento. Las tarjetas de partidos W.O. no existen.
- Si el resultado de un partido es corregido por comité disciplinario (HU47, MS2), MS3 debe recalcular las multas asociadas a ese partido.

---

## Dependencias

| Dirección | Cómo | Motivo |
|-----------|------|--------|
| MS3 → MS1 | HTTP (vía `Ms1JugadoresClient` reutilizado del paquete MS2) | Trae nombre del jugador al construir DTOs de multa |
| MS3 → MS2 | **In-process** (lectura de la entidad de configuración de sanciones de HU09) | Lee la tabla configurada en el torneo para aplicar montos y umbrales |
| MS3 → MS5 | HTTP `@Async` fire-and-forget (futuro `NotificacionPublisher`) | Notifica al delegado el estado del comprobante (HU26) |
| MS2 → MS3 | **In-process** (`MultaService.generar`) | Dispara generación de multas al cerrar partido con tarjetas (HU29) |
| MS2 → MS3 | **In-process** (`MultaService.elegibilidad`) | Consulta elegibilidad del jugador antes del partido (HU19) |

---

## Estado para agentes de IA

> 🟡 Este módulo está implementado a nivel skeleton: endpoints expuestos, modelos definidos, CRUD básico funciona. Lo que falta es **la lógica de negocio cross-MS**:
>
> - **HU29 (prioridad alta):** implementar `MultaService.generar(evento)` leyendo la configuración de sanciones de HU09 en MS2. Como MS3 vive en el mismo binario que MS2, basta con inyectar el repositorio de configuración como bean Spring.
> - **HU28 (cross-MS al aprobar):** al aprobar un comprobante de tipo `INSCRIPCION`, cambiar `EquipoTorneo.estadoInscripcion = APROBADO` (hoy MS2 tiene mock_pago directo). Al aprobar tipo `MULTA`, marcar la `Multa` como `PAGADA`.
> - **Storage de comprobantes:** los archivos (PDF/imagen) van a Supabase Storage. El campo `Comprobante.urlArchivo` ya está preparado; falta integrar el upload desde el frontend.
> - **W.O. y multas:** si un partido se cierra como W.O., MS3 NO debe generar multas. Validar en `MultaService.generar` antes de procesar.
>
> **Antes de tocar código:** leer [`arquitectura_modular_ms2_ms3.md`](arquitectura_modular_ms2_ms3.md). Reglas críticas:
> - Mantener todo bajo el paquete `ms3finanzas/` y schema `finanzas.*`.
> - Las llamadas MS2 → MS3 son in-process (inyección de bean). NO usar HTTP entre ellos.
> - Si en algún momento necesitas que MS3 dependa de algo de `ms2supercopa/`, evalúa primero si ese algo debería moverse al paquete `exception/` compartido o exponerse como un service de MS2 (no como entidad cruzada).
