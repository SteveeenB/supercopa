package terminus.co.edu.ufps.competicion.ms2supercopa.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import terminus.co.edu.ufps.competicion.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.competicion.ms2supercopa.client.JugadorPadronDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.client.Ms1JugadoresClient;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.SolicitudDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.*;
import terminus.co.edu.ufps.competicion.ms2supercopa.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SolicitudService {

    private final SolicitudEquipoRepository solicitudRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final JugadorRepository jugadorRepo;
    private final Ms1JugadoresClient ms1Client;

    // ──────────────────────────────────────────────
    //  JUGADOR: crear solicitud de ingreso (con datos deportivos)
    // ──────────────────────────────────────────────

    @Transactional
    public SolicitudDTO crear(String cedula, UUID equipoTorneoId) {
        if (equipoTorneoId == null) {
            throw new RuntimeException("equipoTorneoId es obligatorio.");
        }

        var equipoTorneo = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo del torneo no encontrado."));
        if (equipoTorneo.getEstadoInscripcion() == EstadoInscripcion.RECHAZADO) {
            throw new RuntimeException("Ese equipo fue rechazado en el torneo; no puedes unirte.");
        }
        var torneo = equipoTorneo.getTorneo();
        if (torneo.getEstado() != EstadoTorneo.PUBLICADO && torneo.getEstado() != EstadoTorneo.EN_CURSO) {
            throw new RuntimeException("El torneo no esta aceptando jugadores.");
        }

        // El perfil deportivo (altura/pierna/posicion) lo captura el modal
        // bloqueante de bienvenida ANTES de llegar aqui. Aqui solo se valida
        // que ya este completo (defensa en profundidad si el frontend falla).
        var jugador = jugadorRepo.findById(cedula).orElseGet(() -> {
            var padron = ms1Client.getJugadorPorCedula(cedula)
                    .orElseThrow(() -> new RuntimeException(
                            "La cedula " + cedula + " no esta en el padron oficial MS1. "
                                    + "Pide al admin que la cargue antes de unirte a un equipo."));
            return jugadorRepo.save(Jugador.builder()
                    .cedula(cedula)
                    .nombre(padron.getNombre())
                    .correo(padron.getCorreo())
                    .build());
        });
        if (jugador.getAlturaCm() == null
                || jugador.getPiernaHabil() == null || jugador.getPiernaHabil().isBlank()
                || jugador.getPosicion() == null || jugador.getPosicion().isBlank()) {
            throw new RuntimeException("Completa tu perfil deportivo antes de solicitar ingreso.");
        }

        // Validaciones de negocio
        if (jugadorEquipoRepo.existsByCedulaAndTorneoId(cedula, torneo.getId())) {
            throw new RuntimeException("Ya perteneces a un equipo en este torneo.");
        }
        if (solicitudRepo.existsByCedulaAndTorneoIdAndEstadoIn(
                cedula, torneo.getId(), List.of(EstadoSolicitud.PENDIENTE, EstadoSolicitud.APROBADA))) {
            throw new RuntimeException("Ya tienes una solicitud activa para este torneo.");
        }

        // nombre y correo son NOT NULL en la tabla; se snapshotean del Jugador
        // local (que ya los tomo del padron MS1 al crearlo arriba).
        var solicitud = SolicitudEquipo.builder()
                .cedula(cedula)
                .nombre(jugador.getNombre())
                .correo(jugador.getCorreo())
                .torneo(torneo)
                .equipoTorneo(equipoTorneo)
                .build();
        solicitudRepo.save(solicitud);
        return toDTO(solicitud);
    }

    @Transactional(readOnly = true)
    public List<SolicitudDTO> listarSolicitudesJugador(String cedula) {
        return solicitudRepo.findByCedula(cedula).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<SolicitudDTO> listarSolicitudesDelegado(String cedulaDelegado) {
        var inscripciones = equipoTorneoRepo.findByDelegadoCedulaOrderByFechaInscripcionDesc(cedulaDelegado);
        if (inscripciones.isEmpty()) return List.of();
        var ids = inscripciones.stream().map(EquipoTorneo::getId).toList();
        return solicitudRepo.findByEquipoTorneoIdIn(ids).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<SolicitudDTO> listarPendientesDelegado(String cedulaDelegado) {
        var inscripciones = equipoTorneoRepo.findByDelegadoCedulaOrderByFechaInscripcionDesc(cedulaDelegado);
        if (inscripciones.isEmpty()) return List.of();
        var ids = inscripciones.stream().map(EquipoTorneo::getId).toList();
        return solicitudRepo.findByEquipoTorneoIdInAndEstado(ids, EstadoSolicitud.PENDIENTE)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public SolicitudDTO aprobar(UUID solicitudId, String cedulaDelegado) {
        var solicitud = solicitudRepo.findById(solicitudId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada."));
        validarDelegadoEsDueno(solicitud, cedulaDelegado);
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new RuntimeException("La solicitud ya fue resuelta: " + solicitud.getEstado());
        }
        if (jugadorEquipoRepo.existsByCedulaAndTorneoId(
                solicitud.getCedula(), solicitud.getTorneo().getId())) {
            throw new RuntimeException("El jugador ya pertenece a un equipo en este torneo.");
        }

        // Snapshot academico desde MS1 + snapshot deportivo del Jugador local.
        var je = JugadorEquipo.builder()
                .cedula(solicitud.getCedula())
                .torneo(solicitud.getTorneo())
                .equipoTorneo(solicitud.getEquipoTorneo())
                .fechaInicio(LocalDate.now())
                .estado(EstadoMembresia.ACTIVO)
                .build();
        poblarSnapshotsDesdeMs1(je, solicitud.getCedula());
        poblarSnapshotDeportivo(je, solicitud.getCedula());
        jugadorEquipoRepo.save(je);

        solicitud.setEstado(EstadoSolicitud.APROBADA);
        solicitud.setFechaResolucion(LocalDateTime.now());
        solicitudRepo.save(solicitud);
        return toDTO(solicitud);
    }

    @Transactional
    public SolicitudDTO rechazar(UUID solicitudId, String motivo, String cedulaDelegado) {
        var solicitud = solicitudRepo.findById(solicitudId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada."));
        validarDelegadoEsDueno(solicitud, cedulaDelegado);
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new RuntimeException("La solicitud ya fue resuelta: " + solicitud.getEstado());
        }
        solicitud.setEstado(EstadoSolicitud.RECHAZADA);
        solicitud.setMotivoRechazo(motivo);
        solicitud.setFechaResolucion(LocalDateTime.now());
        solicitudRepo.save(solicitud);
        return toDTO(solicitud);
    }

    /**
     * Poblar snapshots academicos desde MS1. Si MS1 no responde o la cedula
     * no esta en el padron, lanzamos error: nunca queremos un JugadorEquipo
     * con snapshot null porque luego se muestra la cedula en eventos/plantel
     * en lugar del nombre real.
     */
    /**
     * Snapshot deportivo desde el Jugador local. Si el perfil esta incompleto
     * en este punto es un bug del caller (el modal bloqueante deberia haber
     * impedido llegar hasta aqui); aun asi guardamos null sin romper.
     */
    private void poblarSnapshotDeportivo(JugadorEquipo je, String cedula) {
        jugadorRepo.findById(cedula).ifPresent(j -> {
            je.setAlturaCmSnapshot(j.getAlturaCm());
            je.setPiernaHabilSnapshot(j.getPiernaHabil());
            je.setPosicionSnapshot(j.getPosicion());
        });
    }

    private void poblarSnapshotsDesdeMs1(JugadorEquipo je, String cedula) {
        var p = ms1Client.getJugadorPorCedula(cedula)
                .orElseThrow(() -> new RuntimeException(
                        "La cedula " + cedula + " no esta en el padron oficial MS1. "
                                + "Pide al admin que la cargue antes de aprobar la solicitud."));
        je.setNombreSnapshot(p.getNombre());
        je.setSemestreSnapshot(p.getSemestre());
        if (p.getRolJugador() != null) {
            try {
                je.setRolJugadorSnapshot(RolJugador.valueOf(p.getRolJugador()));
            } catch (IllegalArgumentException ignored) { }
        }
        je.setSnapshotAt(LocalDateTime.now());
    }

    private void validarDelegadoEsDueno(SolicitudEquipo solicitud, String cedulaDelegado) {
        var et = solicitud.getEquipoTorneo();
        if (et.getDelegadoCedula() == null || !et.getDelegadoCedula().equals(cedulaDelegado)) {
            throw new RuntimeException("No eres el delegado de este equipo.");
        }
    }

    private SolicitudDTO toDTO(SolicitudEquipo s) {
        var et = s.getEquipoTorneo();
        var jugador = jugadorRepo.findById(s.getCedula()).orElse(null);
        var padron = ms1Client.getJugadorPorCedula(s.getCedula()).orElse(null);
        return SolicitudDTO.builder()
                .id(s.getId())
                .cedula(s.getCedula())
                .nombre(nombreDe(padron, jugador))
                .correo(padron != null ? padron.getCorreo() : null)
                .alturaCm(jugador != null ? jugador.getAlturaCm() : null)
                .piernaHabil(jugador != null ? jugador.getPiernaHabil() : null)
                .posicion(jugador != null ? jugador.getPosicion() : null)
                .equipoTorneoId(et.getId())
                .equipoId(et.getEquipo().getId())
                .equipoNombre(et.getEquipo().getNombre())
                .torneoId(s.getTorneo().getId())
                .torneoNombre(s.getTorneo().getNombre())
                .estado(s.getEstado().name())
                .fechaSolicitud(s.getFechaSolicitud())
                .fechaResolucion(s.getFechaResolucion())
                .motivoRechazo(s.getMotivoRechazo())
                .build();
    }

    /** Prioridad: apodo cosmetico del MS2 > nombre legal del padron MS1. */
    private String nombreDe(JugadorPadronDTO padron, Jugador jugador) {
        if (jugador != null && jugador.getApodo() != null && !jugador.getApodo().isBlank()) {
            return jugador.getApodo();
        }
        return padron != null ? padron.getNombre() : null;
    }
}
