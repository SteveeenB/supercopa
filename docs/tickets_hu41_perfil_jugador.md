# HU41 - Jugador edita su perfil personal

## División en 2 tickets

---

## Ticket 1 - Foto de perfil y nombre visible manual

**Objetivo:** permitir que el jugador tenga foto de perfil y nombre visible editable desde su panel, sin depender todavía de la proyección completa con MS1/MS2.

### Alcance funcional

| Funcionalidad                          | Descripción                                                                 |
|----------------------------------------|-----------------------------------------------------------------------------|
| Subir foto de perfil                   | JPG o PNG, tamaño máximo **2 MB**                                          |
| Cambiar nombre visible                 | El nombre del registro oficial **no** cambia                                |
| Cédula, código universitario, semestre | Solo lectura                                                                |
| Número de camiseta                     | Solo lectura (controlado por el delegado)                                   |
| Reflejo en UI                          | Los cambios se reflejan inmediatamente, sin cerrar sesión                   |

### Criterio mínimo para cerrar este ticket

| Criterio                                                       | Descripción                                                    |
|----------------------------------------------------------------|----------------------------------------------------------------|
| Visualización de foto                                          | El jugador ve su foto actual o un placeholder                  |
| Subir foto válida                                              | Puede subir una nueva foto que cumpla formato y peso           |
| Editar nombre visible                                          | Puede editar su nombre visible                                 |
| Actualización en UI                                             | Al guardar, la interfaz se actualiza en el momento             |
| Validación de foto inválida                                    | Si la foto no cumple formato o peso, se muestra error claro    |
| Reflejo de nombre visible                                      | Si se modifica, se refleja en el panel actual del jugador      |

### Notas de implementación sugerida

- La foto puede almacenarse en **Appwrite Storage**.
- El estado del perfil puede mantenerse inicialmente en frontend o en una capa ligera de API ya existente.
- Este ticket puede resolverse de forma manual o semi-manual al inicio, siempre que el jugador ya tenga foto y nombre visible en su vista.

### Archivos base a revisar

```
JugadorDashboard.jsx
RoleHeaderActions.jsx
appwrite.js
session.js
jugador.css
role-shell.css
```

---

## Ticket 2 - Perfil completo persistido y reutilización en solicitudes

**Objetivo:** completar la integración de perfil para que los datos deportivos y oficiales queden bien proyectados entre MS1 y MS2, y se reutilicen en el flujo de solicitud a equipo.

### Alcance funcional

| Funcionalidad                          | Descripción                                                                 |
|----------------------------------------|-----------------------------------------------------------------------------|
| Editar foto y nombre visible           | El jugador sigue pudiendo editarlos                                        |
| Datos oficiales                        | Solo lectura: cédula, código universitario, semestre                        |
| Datos deportivos del jugador           | Se guardan y reutilizan: altura, pierna hábil, posición                     |
| Precarga en solicitud a equipo         | Al solicitar ingreso, los datos deportivos se precargan si ya existen       |
| Fallback del delegado                  | Si el jugador no completó su perfil, el delegado puede completarlo          |
| Consistencia del perfil                | El perfil se ve consistente entre login, dashboard y solicitud a equipo     |

### Criterio mínimo para cerrar este ticket

| Criterio                                                                 | Descripción                                                           |
|--------------------------------------------------------------------------|-----------------------------------------------------------------------|
| Independencia de datos deportivos                                        | Ya no dependen solo del formulario de solicitud                       |
| Precarga en modal de solicitud                                           | Altura, pierna hábil y posición se precargan automáticamente          |
| Datos oficiales no editables                                             | No se editan desde el panel del jugador                               |
| Flujo del delegado intacto                                               | No rompe el caso del jugador que nunca se registró por sí mismo       |
| Coherencia del sistema                                                   | El perfil visible y el perfil competitivo se mantienen consistentes   |

### Dependencias de este ticket

- Implementar la proyección de datos oficiales desde **MS1** a **MS2**.
- Enriquecer el **JWT** en MS1 con los claims necesarios.
- Ajustar **MS2** para consumir esos claims o refrescar la proyección desde MS1.
- Mantener el número de camiseta como dato controlado por delegado, no editable por el jugador.

### Archivos base a revisar

```
PerfilController.java
PerfilService.java
SolicitudService.java
DelegadoService.java
SolicitudIngresoModal.jsx
MiEquipoTab.jsx
refinamiento-proyeccion-jugadores.md
```

---

## Recomendación de orden

1. **Primero el Ticket 1** — entrega valor visible rápido y habilita la foto de perfil manual.
2. **Después el Ticket 2** — proyección oficial, precarga de datos deportivos y coherencia con el refinamiento de MS1/MS2.
