# Arquitectura: Monolito Modular MS2 + MS3 (Super-Copa + Finanzas)

> **Propósito de este documento:** Explicar a fondo por qué MS2 y MS3 conviven en un **único binario Spring Boot** dentro del repo `supercopa`, qué patrón arquitectónico estamos aplicando, cómo se valida desde los endpoints reales del proyecto, y bajo qué condiciones tendría sentido separarlos en dos microservicios independientes en el futuro.
>
> **Audiencia:** desarrolladores del repo, revisores del proyecto y cualquiera que se pregunte: *"si el proyecto se llama microservicios, ¿por qué hay dos microservicios en el mismo binario?"*.

---

## 1. Resumen ejecutivo (TL;DR)

MS2 (Super-Copa) y MS3 (Finanzas) están implementados como **dos módulos lógicos dentro del mismo binario Spring Boot**, bajo el patrón conocido como **Monolito Modular** (también llamado *"modular monolith"*, *"package-per-microservice"* o *"componentes Newman tipo 2"*).

- Cada módulo tiene su propio paquete Java (`ms2supercopa.*`, `ms3finanzas.*`), su propio prefix de URL (`/api/supercopa/**`, `/api/finanzas/**`), su propio schema de Postgres (`supercopa.*`, `finanzas.*`) y su propio set de DTOs/services/controllers.
- Comparten el mismo `application.properties`, el mismo `pom.xml`, el mismo proceso JVM y la misma conexión a Supabase #2.
- Para el resto del sistema (MS1, MS4, MS5, Frontend), son **dos APIs distintas servidas por una sola URL en DigitalOcean App Platform**.

Esta decisión **NO contradice** el espíritu del proyecto de microservicios: cumple los criterios de aislamiento de dominio, separación de despliegue lógico, y la posibilidad de extracción posterior. Lo que sacrifica es el aislamiento de proceso, que para este dominio NO es un requisito crítico.

---

## 2. ¿Por qué "monolito modular" y no "microservicios reales"?

### 2.1 El acoplamiento natural entre MS2 y MS3

MS3 es un **consumidor reactivo de eventos generados por MS2**. Concretamente:

- Cuando MS2 cierra un partido con tarjetas rojas/azules/amarillas → MS3 debe generar las multas correspondientes (HU29).
- Cuando MS2 confirma una inscripción al torneo → MS3 espera un comprobante de pago (HU25).
- Antes de que MS2 inicie un partido → debe consultar a MS3 si los jugadores están al día con sus deudas (HU19).
- Cuando MS3 aprueba un comprobante de inscripción → debe disparar un cambio en `EquipoTorneo.estadoInscripcion` (campo dueño de MS2) (HU28).

Esta es una relación **alta en frecuencia, baja en latencia tolerada y crítica para integridad transaccional**. Cada cierre de partido en producción dispara escrituras casi inmediatas a multas. Cada aprobación de comprobante modifica estado de inscripción de equipo en otro dominio.

Hay dos formas de implementar este acoplamiento:

| Enfoque | Cost | Beneficio |
|---|---|---|
| **HTTP entre MS2 y MS3** | Serializar a JSON + token de service-to-service + manejar retries + latencia de red (~50ms) + fallar parcialmente + transacciones distribuidas (Saga) | Aislamiento de procesos: MS3 puede caer sin tumbar MS2 |
| **Llamada de método in-process** | Acoplar paquetes; mismo deploy | Sin overhead de red; transacción Spring única; debugging sencillo |

Para el caso de este proyecto (estudiantes, torneo universitario, sin SLA público estricto), la **complejidad añadida por HTTP no se justifica** porque:

1. La probabilidad de que MS3 esté caído pero MS2 funcione es cercana a cero (corren en el mismo proceso).
2. La frecuencia de evento (1 cierre de partido cada ~90 min en pico) NO requiere desacople asíncrono.
3. El equipo de desarrollo es uno solo — no hay múltiples teams que necesiten ciclos de release independientes.

### 2.2 La definición de "microservicio" según Newman

Sam Newman (autor de *Building Microservices* y *Monolith to Microservices*) define explícitamente que un microservicio no es necesariamente igual a un binario. Lo que define un microservicio es:

> "Un servicio independientemente desplegable, alineado a una capacidad de negocio, dueño de su propio dato y comunicable a través de un contrato bien definido."

Bajo esta definición:

- ✅ MS2 y MS3 están **alineados a capacidades de negocio distintas** (gestión de torneo vs gestión financiera).
- ✅ Cada uno es **dueño de su propio dato** (schemas separados `supercopa.*` vs `finanzas.*`; ningún controller de MS2 escribe en tablas de MS3 y viceversa).
- ✅ Cada uno expone un **contrato bien definido** (prefixes URL diferenciados, DTOs propios, sin dependencias cruzadas en DTOs).
- ⚠️ **NO son independientemente desplegables HOY** — comparten binario.

Newman llama a esta configuración el **"componente tipo 2"**: módulos lógicamente independientes que cohabitan en un proceso por razones pragmáticas, y que pueden separarse cuando el contexto lo exija. La frase exacta del libro: *"shipping multiple modules in a single deployment is fine, as long as the modules respect their boundaries."*

### 2.3 La trampa del falso microservicio

Una arquitectura mal hecha de "microservicios reales" donde MS2 y MS3 corren en procesos separados pero:

- Comparten la misma BD,
- Se llaman entre sí sincronicamente en cada operación,
- Fallan juntos en cualquier deploy,

…NO es microservicios. Es un **monolito distribuido**, que es peor que un monolito modular: tiene todos los costos de la red sin ninguno de sus beneficios. Newman lo llama explícitamente *"distributed monolith — the worst of both worlds"*.

Optar por monolito modular es preferible a caer en este antipatrón.

---

## 3. Estructura del repo que materializa el patrón

```
supercopa/src/main/java/terminus/co/edu/ufps/competicion/
├── CompeticionCodeCupApplication.java     ← punto de entrada Spring Boot único
├── exception/                              ← compartido entre MS2 y MS3
│   ├── GlobalExceptionHandler.java         ← @ControllerAdvice global
│   ├── ConflictException.java
│   └── ResourceNotFoundException.java
├── ms2supercopa/                           ← MS2: TODO lo de torneo
│   ├── client/                             ← clientes HTTP (Ms1JugadoresClient)
│   ├── config/                             ← config específica de MS2
│   ├── controller/                         ← 11 controllers bajo /api/supercopa/**
│   ├── dto/
│   ├── model/                              ← entidades JPA (schema supercopa.*)
│   ├── repository/
│   └── service/
└── ms3finanzas/                            ← MS3: TODO lo de finanzas
    ├── controller/                         ← 2 controllers bajo /api/finanzas/**
    ├── dto/
    ├── model/                              ← entidades JPA (schema finanzas.*)
    ├── repository/
    └── service/
```

### Reglas que mantienen el aislamiento de módulos

1. **Imports cruzados solo en una dirección**: MS2 puede importar de `ms3finanzas/` (es el caller en HU29, HU19). MS3 **NO** importa de `ms2supercopa/` salvo enums genéricos. Esto se valida con grep:
   ```
   grep -r "ms2supercopa" src/main/java/terminus/co/edu/ufps/competicion/ms3finanzas/
   ```
   Debe devolver vacío (o solo referencias a enums compartidos pre-aprobados).

2. **Schemas Postgres separados**: ningún `@Entity` de MS2 declara `@Table(schema = "finanzas")` ni viceversa. JOIN entre schemas solo se permite **vía service del MS dueño**, nunca vía repository de otro MS.

3. **DTOs no se cruzan**: `MultaDTO` (MS3) nunca se inyecta dentro de `PartidoDTO` (MS2) y viceversa. Si MS2 necesita un dato de MS3, lo recibe vía DTO local construido por el service de MS3.

4. **El paquete `exception/` es compartido** porque `GlobalExceptionHandler` es `@ControllerAdvice` y debe ver excepciones de ambos módulos. Esta es una excepción consciente al patrón.

---

## 4. Endpoints que validan el patrón

Los siguientes endpoints **reales del binario** demuestran que MS2 y MS3 conviven sin colisión de namespace:

### 4.1 Controllers de MS2 (prefix `/api/supercopa/**`)

| Controller | RequestMapping |
|---|---|
| `TorneoController` | `/api/supercopa/torneos` |
| `EquipoController` | `/api/supercopa/equipos` |
| `DelegadoController` | `/api/supercopa/delegado` |
| `SolicitudController` | `/api/supercopa/solicitudes` |
| `JugadorPublicoController` | `/api/supercopa/jugador` |
| `PerfilController` | `/api/supercopa` |
| `AdminEquipoController` | `/api/supercopa/admin` |
| `AdminTorneoController` | `/api/supercopa/admin/torneos` |
| `AdminPartidoController` | `/api/supercopa/admin/partidos` |

### 4.2 Controllers de MS3 (prefix `/api/finanzas/**`)

| Controller | RequestMapping |
|---|---|
| `ComprobanteController` | `/api/finanzas/comprobantes` |
| `MultaController` | `/api/finanzas/multas` |

### 4.3 ¿Cómo decide Spring qué controller atiende qué request?

Hay dos niveles de routing claramente separados:

**Nivel 1 — DigitalOcean App Platform** (qué componente recibe el request):

```yaml
- name: ms2-ms3-supercopa
  routes:
    - path: /api/supercopa    # MS2
    - path: /api/finanzas     # MS3
```

DO solo ve que este componente atiende dos prefixes. Cualquier request que empiece por esos paths llega al binario.

**Nivel 2 — Spring Boot** (qué controller dentro del binario):

Spring escanea todos los `@RequestMapping` al arrancar y rutea según el path completo. Como ningún controller de MS3 usa el prefix `/api/supercopa/**` y viceversa, **no hay ambigüedad posible**.

### 4.4 Cómo probar el aislamiento desde Swagger

Para validar el patrón en vivo:

1. Abrir `http://localhost:8080/swagger-ui.html`.
2. Los tags del Swagger UI agrupan endpoints por controller. Verificar que **ningún tag mezcla rutas `/api/supercopa/*` y `/api/finanzas/*`**.
3. Probar `GET /api/finanzas/multas/mias` con un JWT de jugador — debe responder vacío inicialmente.
4. Probar `GET /api/supercopa/torneos` con el mismo JWT — debe listar torneos activos.

Ambas llamadas viajan al mismo proceso JVM, pero son atendidas por controllers en paquetes Java distintos y consultan tablas en schemas Postgres distintos.

---

## 5. Comunicación entre módulos: in-process vs HTTP

### 5.1 MS2 → MS3 (in-process, llamada directa)

Cuando MS2 (en HU29) cierra un partido con tarjetas, el flujo es:

```java
// dentro de PartidoAdminService (MS2)
@Service
public class PartidoAdminService {
    private final MultaService multaService;  // ← bean de MS3 inyectado

    @Transactional
    public void cerrarPartido(Long partidoId, ResultadoDTO resultado) {
        Partido p = partidoRepo.findById(partidoId).orElseThrow();
        p.setEstado(EstadoPartido.FINALIZADO);
        eventos.forEach(e -> {
            if (e.esTarjeta()) {
                multaService.generar(e);   // ← llamada in-process
            }
        });
        // todo dentro de la misma @Transaction
    }
}
```

**Beneficios concretos:**

- **Transacción única**: si la generación de multa falla, el cierre del partido también revierte (atomic). Sin necesidad de Saga ni compensaciones.
- **Cero latencia de red**: la llamada es un push al stack frame, no un round-trip HTTP.
- **Sin manejo de auth interna**: no se necesita un token de service-to-service.
- **Stack trace continuo**: si MS3 falla, el stack trace muestra el origen real en MS2.

### 5.2 MS3 → MS1 (HTTP, vía cliente dedicado)

MS3 necesita el nombre del jugador al construir el DTO de una multa. Como MS1 está en otro binario:

```java
@Service
public class MultaService {
    private final Ms1JugadoresClient ms1Client;  // ← cliente HTTP

    public MultaDTO toDto(Multa m) {
        String nombre = ms1Client.getNombre(m.getCedula())  // round-trip HTTP
                                  .orElse("(desconocido)");
        return new MultaDTO(m.getId(), m.getCedula(), nombre, m.getMonto(), ...);
    }
}
```

**Costo asumido conscientemente:** este SÍ vale la pena porque MS1 es un componente independiente (DO Componente A) que tiene su propia BD, su propio ciclo de release y un team de identidad que podría escalar separadamente.

### 5.3 MS2 → MS5 (HTTP @Async fire-and-forget)

Cuando MS2 cierra un partido, también dispara notificaciones (HU26). Esto se hace de forma asíncrona:

```java
@Service
public class PartidoAdminService {
    private final NotificacionPublisher publisher;  // ← interfaz hacia MS5

    public void cerrarPartido(...) {
        // ... lógica transaccional + multas (in-process) ...
        publisher.notificarCierrePartido(p);  // ← @Async, no bloquea
    }
}
```

Hoy `NotificacionPublisher` está implementado como `LoggingNotificacionPublisher` (solo emite logs). Cuando MS5 esté en producción (DO Componente D), se sustituye por `HttpNotificacionPublisher` sin cambiar PartidoAdminService.

---

## 6. Beneficios concretos del monolito modular para este proyecto

### 6.1 Costo de infraestructura mínimo

Con DO App Platform Basic ($5/mes por componente):

| Setup | Componentes | Costo mensual |
|---|---|---|
| MS2 + MS3 en mismo binario | 1 componente | $5 |
| MS2 y MS3 en componentes separados | 2 componentes | $10 |

Para un proyecto académico/sin presupuesto, $5/mes adicionales por separar dos servicios cuya comunicación es interna y no escala diferente es difícil de justificar.

### 6.2 Memoria JVM compartida

Una JVM dedicada a un Spring Boot típico consume ~250 MB sólo de baseline (clases cargadas, metaspace, threads). Si MS3 fuera su propio binario:

- **Hoy (modular)**: 1 JVM × 250 MB baseline + 20 MB extras de MS3 = ~270 MB.
- **Hipotético (separado)**: 2 JVMs × 250 MB = 500 MB.

DO Basic ofrece 512 MB. La opción modular cabe holgadamente; la opción separada ya pega contra el límite.

### 6.3 Sin transacciones distribuidas

El caso del cierre de partido (HU29) es **atómico**:

```
INSERT INTO supercopa.eventos_partido (...)
INSERT INTO supercopa.partidos SET estado='FINALIZADO'
INSERT INTO finanzas.multas (...)   ← otro schema, misma transacción
```

En el monolito modular esto es un único `@Transactional` que Spring traduce a una transacción Postgres. Si cualquier paso falla, todo revierte.

En un setup separado se necesitaría una **Saga** con etapas compensadoras, retries, idempotencia, etc. Cien veces más código y superficie de error para resolver un problema que en realidad no tenemos.

### 6.4 Debug y observabilidad simplificada

- Un solo `application.log` que ya muestra todo el flujo (cierre de partido → generación de multa) en orden cronológico.
- Stack traces continuos a través de módulos.
- Métricas Spring Actuator unificadas (un solo `/actuator/health`).
- Un solo dashboard de DigitalOcean para CPU/memoria.

### 6.5 Onboarding más rápido

Un nuevo desarrollador clona **un repo**, hace `./mvnw spring-boot:run` y tiene todo el sistema funcionando. No necesita orquestar docker-compose con dos servicios + BD + auth interna.

---

## 7. Lo que sacrificamos (y por qué no nos importa, todavía)

| Sacrificio | Mitigación / Justificación |
|---|---|
| **MS3 no puede desplegarse sin redesplegar MS2** | Hoy el equipo es uno solo y las features de MS3 se cierran en sincronía con las de MS2. Si en el futuro hay un team de finanzas separado, este sacrificio empieza a doler — y es la señal para separar. |
| **MS3 no puede escalarse horizontalmente sin escalar MS2** | El tráfico esperado de MS3 (delegado subiendo comprobantes, admin aprobando) es **muy bajo**. Escalar este módulo independientemente es un problema hipotético, no real. |
| **Un bug crítico en MS3 puede tumbar MS2** | Cierto, pero los bugs críticos también tumban un microservicio dedicado si comparten BD. La separación de procesos da aislamiento contra **memory leaks** y **deadlocks de thread pool**, no contra bugs lógicos. |
| **Refactor más caro si crece el equipo** | El refactor está pre-planeado: paquetes `ms3finanzas/` aislados, schema `finanzas.*` separado, comunicación vía service inyectable. Mover MS3 a otro repo es mecánico (ver §9). |

---

## 8. Comparación con la arquitectura general del proyecto

El proyecto CODE-CUP tiene 5 microservicios planeados. Esta es la realidad de su separación:

| MS | Componente DO | Repo | Binario | Razón |
|---|---|---|---|---|
| MS1 (Identidad) | Componente A | `AuthCodeCup` | Propio | Dueño de Appwrite + padrón. Auth crítico, debe ser independientemente reiniciable. |
| MS2 (Super-Copa) | Componente B | `supercopa` | Compartido con MS3 | Núcleo de torneo |
| MS3 (Finanzas) | Componente B | `supercopa` | Compartido con MS2 | Acoplado en eventos a MS2 |
| MS4 (Analytics) | Componente C | `analytics` | Propio | Lecturas pesadas, cero escritura — aislar carga |
| MS5 (Notificaciones) | Componente D (DO) | `notificaciones` | Propio | Reactivo, side-effect only |

Los **componentes A, B, C, D corren en la misma DO App Platform** bajo routing nativo (`/api/auth/**` → A, `/api/supercopa/**` y `/api/finanzas/**` → B, `/api/analytics/**` → C, MS5 trabaja por eventos/HTTP outbound).

Como ves, **MS2+MS3 son la única excepción al patrón de "1 MS = 1 binario"**. El resto del sistema sí está separado en procesos. La excepción está justificada por el acoplamiento natural de los dominios.

---

## 9. Plan de migración: cuándo y cómo separar MS3 de MS2

El monolito modular **NO es un destino final** — es una decisión que se revisa cuando los costos cambian. Esto es lo que dispararía la separación:

### Señales para separar

1. **El equipo de Finanzas se vuelve un team separado** con su propio backlog y velocidad de release.
2. **MS3 empieza a recibir tráfico significativo independiente de MS2** (ej. portal del jugador para auto-revisar multas → 10× más requests que el flujo MS2).
3. **MS3 requiere recursos diferentes** (ej. procesar PDFs de comprobantes consume CPU intensiva).
4. **Necesidad de SLA diferenciado**: si la zona de pagos requiere uptime 99.9% pero la zona de torneo puede tolerar mantenimientos.
5. **Compliance externo** (ej. auditoría PCI-DSS) que exija aislamiento de procesos para datos financieros.

### Procedimiento de separación (estimación: 1 semana de trabajo)

1. **Crear repo nuevo `finanzas-codecup`**.
2. **Mover el paquete `ms3finanzas/` completo** al nuevo repo. Como ya está aislado en su namespace Java, no hay imports que romper.
3. **Convertir las llamadas in-process MS2 → MS3 en HTTP**:
   - Reemplazar `MultaService.generar(evento)` por `MultaHttpClient.generar(eventoDTO)`.
   - Implementar idempotencia (clave: `partidoId + jugadorId + tipoTarjeta`).
   - Manejar fallo con outbox pattern o cola simple.
4. **Crear nuevo componente DO Componente E** con routing `/api/finanzas/**`.
5. **Cambiar la URL base del frontend**: si Frontend ya usa una sola `VITE_GATEWAY_URL`, no cambia nada porque DO sigue siendo el mismo App Platform.
6. **Aplicar `@SchemaUpdate` o migración** para mover las tablas `finanzas.*` a una BD independiente (Supabase #2 → Supabase #4) si se requiere aislamiento de dato.

Lo importante: **NADA de esto requiere reescribir lógica**. La estructura modular ya respeta los límites; solo cambia la frontera de proceso.

---

## 10. Conclusión

> "El mejor momento para separar microservicios es cuando el costo de NO separarlos supera al costo de hacerlo. Hoy ese costo no se ha cruzado, y forzar la separación temprana es un anti-patrón llamado *premature distribution*."

El patrón de monolito modular para MS2+MS3 en este proyecto:

- ✅ **Respeta los límites de dominio** (MS2 ≠ MS3 en código, BD, contratos, controladores).
- ✅ **Permite extracción futura** sin reescritura.
- ✅ **Minimiza costos operativos** (infra, debug, deploy).
- ✅ **Encaja con el patrón Newman tipo 2** descrito en literatura de microservicios.
- ✅ **Es validable desde endpoints reales** (`/api/supercopa/**` vs `/api/finanzas/**`).

No es una concesión ni un atajo: es una **decisión arquitectónica consciente** alineada con el principio de **YAGNI** (You Aren't Gonna Need It) aplicado a la distribución de procesos.

Cuando el contexto cambie, el camino de migración está pre-trazado. Mientras tanto, el código es más simple, el sistema más confiable y el costo operativo más bajo.

---

## 11. Referencias

- Newman, S. *Monolith to Microservices*. O'Reilly, 2019. Cap. 3: *"Splitting the Monolith"*, sec. *"Component-based decomposition"*.
- Newman, S. *Building Microservices*, 2nd ed. O'Reilly, 2021. Cap. 2: *"Modelling Microservices"*, sec. *"Modular Monoliths as a stepping stone"*.
- Vernon, V. *Implementing Domain-Driven Design*. Addison-Wesley, 2013. Cap. 14: *"Application"*, sobre bounded contexts dentro de un mismo proceso.
- Fowler, M. *MonolithFirst* (artículo, martinfowler.com). 2015.
- Documento interno: `modelo_despliegue.md` §9 — Estrategia de 3 Supabases.
- Documento interno: `evolucion_y_decisiones_supercopa.md` §7, §11 — Decisiones relacionadas en este repo.
