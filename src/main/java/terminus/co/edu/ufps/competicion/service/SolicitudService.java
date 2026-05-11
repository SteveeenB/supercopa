package terminus.co.edu.ufps.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.competicion.dto.SolicitudDTO;
import terminus.co.edu.ufps.competicion.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.competicion.model.*;
import terminus.co.edu.ufps.competicion.repository.*;

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

    // ──────────────────────────────────────────────
    //  JUGADOR: crear solicitud de ingreso (con datos personales)
    // ──────────────────────────────────────────────

    @Transactional
    public SolicitudDTO crear(String cedula,
                              String nombre,
                              String correo,
                              UUID equipoTorneoId,
                              Integer alturaCm,
                              String piernaHabil,
                              String posicion) {
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

        // Carga (o crea) el jugador en MS2; persiste datos personales.
        var jugador = jugadorRepo.findById(cedula).orElseGet(() -> Jugador.builder()
                .cedula(cedula)
                .nombre(nombre != null ? nombre : "")
                .correo(correo)
                .rolJugador(RolJugador.GRADUADO) // default seguro si no esta en padron MS2
                .build());
        if (nombre != null && !nombre.isBlank()) jugador.setNombre(nombre);
        if (correo != null && !correo.isBlank()) jugador.setCorreo(correo);
        if (alturaCm != null) jugador.setAlturaCm(alturaCm);
        if (piernaHabil != null && !piernaHabil.isBlank()) jugador.setPiernaHabil(piernaHabil.trim());
        if (posicion != null && !posicion.isBlank()) jugador.setPosicion(posicion.trim());
        jugadorRepo.save(jugador);

        // Validaciones de negocio
        if (jugadorEquipoRepo.existsByCedulaAndTorneoId(cedula, torneo.getId())) {
            throw new RuntimeException("Ya perteneces a un equipo en este torneo.");
        }
        if (solicitudRepo.existsByCedulaAndTorneoIdAndEstadoIn(
                cedula, torneo.getId(), List.of(EstadoSolicitud.PENDIENTE, EstadoSolicitud.APROBADA))) {
            throw new RuntimeException("Ya tienes una solicitud activa para este torneo.");
        }

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
        jugadorEquipoRepo.save(JugadorEquipo.builder()
                .cedula(solicitud.getCedula())
                .torneo(solicitud.getTorneo())
                .equipoTorneo(solicitud.getEquipoTorneo())
                .fechaInicio(LocalDate.now())
                .estado(EstadoMembresia.ACTIVO)
                .build());

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

    private void validarDelegadoEsDueno(SolicitudEquipo solicitud, String cedulaDelegado) {
        var et = solicitud.getEquipoTorneo();
        if (et.getDelegadoCedula() == null || !et.getDelegadoCedula().equals(cedulaDelegado)) {
            throw new RuntimeException("No eres el delegado de este equipo.");
        }
    }

    private SolicitudDTO toDTO(SolicitudEquipo s) {
        var et = s.getEquipoTorneo();
        return SolicitudDTO.builder()
                .id(s.getId())
                .cedula(s.getCedula())
                .nombre(s.getNombre())
                .correo(s.getCorreo())
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
}
