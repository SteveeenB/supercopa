package terminus.co.edu.ufps.competicion.ms2supercopa.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import terminus.co.edu.ufps.competicion.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.competicion.ms2supercopa.client.JugadorPadronDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.client.Ms1JugadoresClient;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.EquipoDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin.InscripcionDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.delegado.AgregarMiembroRequest;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.delegado.CrearEquipoRequest;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.delegado.MiembroEquipoDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.delegado.TorneoDisponibleDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.*;
import terminus.co.edu.ufps.competicion.ms2supercopa.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DelegadoService {

    private final EquipoRepository equipoRepo;
    private final TorneoRepository torneoRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final JugadorRepository jugadorRepo;
    private final TorneoAdminService torneoAdminService;
    private final Ms1JugadoresClient ms1Client;

    @Transactional
    public EquipoDTO crearEquipo(String delegadoCedula, CrearEquipoRequest req) {
        if (req.getNombre() == null || req.getNombre().isBlank()) {
            throw new RuntimeException("El nombre del equipo es obligatorio.");
        }
        // Garantiza la fila local de jugador (con nombre del padron MS1) ANTES
        // de crear el equipo: la tabla equipos tiene FK a jugadores.
        garantizarJugadorLocal(delegadoCedula);

        // Un delegado solo puede tener un equipo base.
        var existente = equipoRepo.findByDelegadoCedula(delegadoCedula);
        if (existente.isPresent()) {
            throw new RuntimeException("Ya tienes un equipo creado: \""
                    + existente.get().getNombre() + "\". No puedes crear otro.");
        }

        var equipo = Equipo.builder()
                .nombre(req.getNombre().trim())
                .delegadoCedula(delegadoCedula)
                .build();
        equipoRepo.save(equipo);
        return EquipoDTO.builder().id(equipo.getId()).nombre(equipo.getNombre()).build();
    }

    /**
     * Garantiza una fila local en supercopa.jugadores para la cedula dada.
     * Si no existe, la crea copiando nombre y correo del padron MS1. Si MS1
     * no conoce la cedula, lanza error: nunca queremos jugadores locales con
     * nombre vacio (la tabla lo exige NOT NULL y el frontend muestra el nombre
     * en estadisticas/eventos).
     */
    private Jugador garantizarJugadorLocal(String cedula) {
        return jugadorRepo.findById(cedula).orElseGet(() -> {
            var padron = ms1Client.getJugadorPorCedula(cedula)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "La cedula " + cedula + " no esta en el padron oficial MS1. "
                                    + "Pide al admin que la cargue antes de continuar."));
            return jugadorRepo.save(Jugador.builder()
                    .cedula(cedula)
                    .nombre(padron.getNombre())
                    .correo(padron.getCorreo())
                    .build());
        });
    }

    @Transactional(readOnly = true)
    public EquipoDTO miEquipo(String delegadoCedula) {
        var equipo = equipoRepo.findByDelegadoCedula(delegadoCedula)
                .orElseThrow(() -> new ResourceNotFoundException("Aun no has creado tu equipo."));
        return EquipoDTO.builder().id(equipo.getId()).nombre(equipo.getNombre()).build();
    }

    @Transactional(readOnly = true)
    public List<TorneoDisponibleDTO> torneosDisponibles(String delegadoCedula) {
        var publicados = torneoRepo.findByEstadoInOrderByEdicionDesc(
                List.of(EstadoTorneo.PUBLICADO, EstadoTorneo.EN_CURSO));
        return publicados.stream().map(t -> {
            var inscripcion = equipoTorneoRepo.findByTorneoIdAndDelegadoCedula(t.getId(), delegadoCedula);
            return TorneoDisponibleDTO.builder()
                    .id(t.getId())
                    .nombre(t.getNombre())
                    .edicion(t.getEdicion())
                    .estado(t.getEstado().name())
                    .fechaInicio(t.getFechaInicio())
                    .fechaFin(t.getFechaFin())
                    .inscrito(inscripcion.isPresent())
                    .estadoInscripcion(inscripcion.map(et -> et.getEstadoInscripcion().name()).orElse(null))
                    .equipoTorneoId(inscripcion.map(EquipoTorneo::getId).orElse(null))
                    .motivoExpulsion(inscripcion.map(EquipoTorneo::getMotivoExpulsion).orElse(null))
                    .build();
        }).toList();
    }

    @Transactional
    public InscripcionDTO inscribir(String delegadoCedula, UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        if (torneo.getEstado() != EstadoTorneo.PUBLICADO) {
            throw new RuntimeException("Solo se puede inscribir a torneos PUBLICADOS.");
        }

        var equipoDto = miEquipo(delegadoCedula);
        var equipo = equipoRepo.findById(equipoDto.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipo no encontrado."));

        if (equipoTorneoRepo.findByTorneoIdAndDelegadoCedula(torneoId, delegadoCedula).isPresent()) {
            throw new RuntimeException("Ya tienes una inscripcion para este torneo.");
        }
        if (equipoTorneoRepo.findByTorneoIdAndEquipoId(torneoId, equipo.getId()).isPresent()) {
            throw new RuntimeException("El equipo ya esta inscrito en este torneo.");
        }

        var et = EquipoTorneo.builder()
                .torneo(torneo)
                .equipo(equipo)
                .delegadoCedula(delegadoCedula)
                .estadoInscripcion(EstadoInscripcion.PENDIENTE_PAGO)
                .build();
        equipoTorneoRepo.save(et);

        // Auto-inscribir al delegado como jugador con snapshot academico del padron MS1.
        if (!jugadorEquipoRepo.existsByCedulaAndTorneoId(delegadoCedula, torneoId)) {
            var padron = ms1Client.getJugadorPorCedula(delegadoCedula)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Delegado no esta en el padron oficial MS1."));
            // jugador_equipo.cedula tiene FK a jugadores, asi que garantizamos
            // la fila local antes de insertar JugadorEquipo.
            var jugadorLocal = garantizarJugadorLocal(delegadoCedula);
            if (jugadorLocal.getAlturaCm() == null
                    || jugadorLocal.getPiernaHabil() == null || jugadorLocal.getPiernaHabil().isBlank()
                    || jugadorLocal.getPosicion() == null || jugadorLocal.getPosicion().isBlank()) {
                throw new RuntimeException(
                        "Completa tu perfil deportivo (altura, pierna habil, posicion) antes de inscribir el equipo.");
            }
            var je = JugadorEquipo.builder()
                    .cedula(delegadoCedula)
                    .torneo(torneo)
                    .equipoTorneo(et)
                    .fechaInicio(LocalDate.now())
                    .estado(EstadoMembresia.ACTIVO)
                    .alturaCmSnapshot(jugadorLocal.getAlturaCm())
                    .piernaHabilSnapshot(jugadorLocal.getPiernaHabil())
                    .posicionSnapshot(jugadorLocal.getPosicion())
                    .build();
            poblarSnapshots(je, padron);
            jugadorEquipoRepo.save(je);
        }

        return torneoAdminService.toInscripcionDTO(et);
    }

    @Transactional(readOnly = true)
    public List<InscripcionDTO> misInscripciones(String delegadoCedula) {
        return equipoTorneoRepo.findByDelegadoCedulaOrderByFechaInscripcionDesc(delegadoCedula)
                .stream()
                .map(torneoAdminService::toInscripcionDTO)
                .toList();
    }

    @Transactional
    public InscripcionDTO pagar(String delegadoCedula, UUID equipoTorneoId) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoCedula().equals(delegadoCedula)) {
            throw new RuntimeException("No eres el delegado de esta inscripcion.");
        }
        if (et.getEstadoInscripcion() != EstadoInscripcion.PENDIENTE_PAGO) {
            throw new RuntimeException("La inscripcion no esta pendiente de pago (estado actual: "
                    + et.getEstadoInscripcion() + ").");
        }
        et.setEstadoInscripcion(EstadoInscripcion.APROBADO);
        et.setAprobadoPor("MOCK_PAGO");
        equipoTorneoRepo.save(et);
        return torneoAdminService.toInscripcionDTO(et);
    }

    // ─────────────────────────────────────────────────────────
    //  HU44 — Delegado gestiona plantel directamente
    // ─────────────────────────────────────────────────────────

    @Transactional
    public MiembroEquipoDTO agregarMiembro(String delegadoCedula, UUID equipoTorneoId,
                                            AgregarMiembroRequest req) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoCedula().equals(delegadoCedula)) {
            throw new RuntimeException("No eres el delegado de esta inscripcion.");
        }

        // Validar que la cedula este en el padron MS1 (fuente de verdad).
        var padron = ms1Client.getJugadorPorCedula(req.getCedula())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cedula no encontrada en el padron oficial. Pide al admin que la cargue."));

        // Asegurar que exista Jugador local. Tomamos nombre/correo del padron
        // porque la tabla los exige NOT NULL y porque varias FKs apuntan aqui.
        var jugador = jugadorRepo.findById(req.getCedula())
                .orElseGet(() -> jugadorRepo.save(Jugador.builder()
                        .cedula(req.getCedula())
                        .nombre(padron.getNombre())
                        .correo(padron.getCorreo())
                        .build()));

        if (jugadorEquipoRepo.existsByCedulaAndTorneoIdAndEstado(
                req.getCedula(), et.getTorneo().getId(), EstadoMembresia.ACTIVO)) {
            throw new RuntimeException("El jugador ya pertenece a un equipo en este torneo.");
        }

        if (req.getNumeroCamiseta() != null) {
            if (jugadorEquipoRepo.existsByEquipoTorneoIdAndNumeroCamisetaAndEstado(
                    equipoTorneoId, req.getNumeroCamiseta(), EstadoMembresia.ACTIVO)) {
                throw new RuntimeException(
                        "El número " + req.getNumeroCamiseta() + " ya está asignado en este equipo.");
            }
        }

        var existente = jugadorEquipoRepo.findByCedulaAndTorneoId(
                req.getCedula(), et.getTorneo().getId());

        JugadorEquipo je;
        if (existente.isPresent()) {
            je = existente.get();
            je.setEquipoTorneo(et);
            je.setEstado(EstadoMembresia.ACTIVO);
            je.setFechaInicio(LocalDate.now());
            je.setFechaFin(null);
            if (req.getNumeroCamiseta() != null) {
                je.setNumeroCamiseta(req.getNumeroCamiseta());
            }
        } else {
            je = JugadorEquipo.builder()
                    .cedula(jugador.getCedula())
                    .torneo(et.getTorneo())
                    .equipoTorneo(et)
                    .fechaInicio(LocalDate.now())
                    .estado(EstadoMembresia.ACTIVO)
                    .numeroCamiseta(req.getNumeroCamiseta())
                    .build();
        }
        // Snapshot academico (se actualiza siempre que se agregue/reactive el miembro).
        poblarSnapshots(je, padron);
        jugadorEquipoRepo.save(je);

        return toMiembroDTO(je, jugador, et, padron);
    }

    @Transactional
    public MiembroEquipoDTO removerMiembro(String delegadoCedula, UUID equipoTorneoId, String cedula) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoCedula().equals(delegadoCedula)) {
            throw new RuntimeException("No eres el delegado de esta inscripcion.");
        }

        var je = jugadorEquipoRepo.findByCedulaAndEquipoTorneoId(cedula, equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado en el plantel."));

        if (je.getCedula().equals(et.getDelegadoCedula())) {
            throw new RuntimeException("No puedes eliminar al delegado del plantel.");
        }

        if (je.getEstado() == EstadoMembresia.RETIRADO) {
            return toMiembroDTO(je, jugadorRepo.findById(cedula).orElse(null), et, null);
        }

        je.setEstado(EstadoMembresia.RETIRADO);
        je.setFechaFin(LocalDate.now());
        jugadorEquipoRepo.save(je);

        return toMiembroDTO(je, jugadorRepo.findById(cedula).orElse(null), et, null);
    }

    @Transactional
    public MiembroEquipoDTO actualizarCamiseta(String delegadoCedula, UUID equipoTorneoId,
                                                String cedula, Integer numeroCamiseta) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoCedula().equals(delegadoCedula)) {
            throw new RuntimeException("No eres el delegado de esta inscripcion.");
        }

        var je = jugadorEquipoRepo.findByCedulaAndEquipoTorneoId(cedula, equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado en el plantel."));

        if (numeroCamiseta != null) {
            var ocupado = jugadorEquipoRepo
                    .findByEquipoTorneoIdOrderByFechaInicioAsc(equipoTorneoId)
                    .stream()
                    .filter(j -> j.getEstado() == EstadoMembresia.ACTIVO
                            && j.getNumeroCamiseta() != null
                            && j.getNumeroCamiseta().equals(numeroCamiseta)
                            && !j.getCedula().equals(cedula))
                    .findAny();
            if (ocupado.isPresent()) {
                throw new RuntimeException(
                        "El número " + numeroCamiseta + " ya está asignado a otro jugador activo.");
            }
        }

        je.setNumeroCamiseta(numeroCamiseta);
        jugadorEquipoRepo.save(je);

        return toMiembroDTO(je, jugadorRepo.findById(cedula).orElse(null), et, null);
    }

    @Transactional(readOnly = true)
    public List<MiembroEquipoDTO> miembros(String delegadoCedula, UUID equipoTorneoId) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoCedula().equals(delegadoCedula)) {
            throw new RuntimeException("No eres el delegado de esta inscripcion.");
        }
        return jugadorEquipoRepo.findByEquipoTorneoIdOrderByFechaInicioAsc(equipoTorneoId)
                .stream()
                .map(je -> toMiembroDTO(je, jugadorRepo.findById(je.getCedula()).orElse(null), et, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MiembroEquipoDTO> miembrosAdmin(UUID equipoTorneoId) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        return jugadorEquipoRepo.findByEquipoTorneoIdOrderByFechaInicioAsc(equipoTorneoId)
                .stream()
                .map(je -> toMiembroDTO(je, jugadorRepo.findById(je.getCedula()).orElse(null), et, null))
                .toList();
    }

    private void poblarSnapshots(JugadorEquipo je, JugadorPadronDTO padron) {
        je.setNombreSnapshot(padron.getNombre());
        je.setSemestreSnapshot(padron.getSemestre());
        if (padron.getRolJugador() != null) {
            try {
                je.setRolJugadorSnapshot(RolJugador.valueOf(padron.getRolJugador()));
            } catch (IllegalArgumentException ignored) { }
        }
        je.setSnapshotAt(LocalDateTime.now());
    }

    /**
     * Construye el DTO del miembro. Prioridad para el nombre:
     *   1. apodo (cosmetico del MS2, opt-in del jugador)
     *   2. snapshot academico guardado al inscribirse (estable, no requiere HTTP)
     *   3. fresh lookup al padron MS1 (si los dos anteriores estan vacios)
     */
    private MiembroEquipoDTO toMiembroDTO(JugadorEquipo je, Jugador jugador, EquipoTorneo et,
                                          JugadorPadronDTO padronYaConocido) {
        String nombre = null;
        if (jugador != null && jugador.getApodo() != null && !jugador.getApodo().isBlank()) {
            nombre = jugador.getApodo();
        } else if (je.getNombreSnapshot() != null && !je.getNombreSnapshot().isBlank()) {
            nombre = je.getNombreSnapshot();
        } else {
            var padron = padronYaConocido != null
                    ? padronYaConocido
                    : ms1Client.getJugadorPorCedula(je.getCedula()).orElse(null);
            nombre = padron != null ? padron.getNombre() : null;
        }

        return MiembroEquipoDTO.builder()
                .cedula(je.getCedula())
                .nombre(nombre)
                .posicion(jugador != null ? jugador.getPosicion() : null)
                .piernaHabil(jugador != null ? jugador.getPiernaHabil() : null)
                .alturaCm(jugador != null ? jugador.getAlturaCm() : null)
                .numeroCamiseta(je.getNumeroCamiseta())
                .estado(je.getEstado() != null ? je.getEstado().name() : null)
                .desde(je.getFechaInicio())
                .esDelegado(je.getCedula().equals(et.getDelegadoCedula()))
                .torneoEstado(et.getTorneo().getEstado().name())
                .build();
    }
}
