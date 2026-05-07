package terminus.co.edu.ufps.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.competicion.dto.SolicitudDTO;
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
    private final EquipoRepository equipoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final TorneoRepository torneoRepo;

    // ──────────────────────────────────────────────
    //  JUGADOR: crear solicitud de ingreso
    // ──────────────────────────────────────────────

    @Transactional
    public SolicitudDTO crear(String cedula, String nombre, String correo, UUID equipoId, UUID torneoId) {
        var equipo = equipoRepo.findById(equipoId)
                .orElseThrow(() -> new RuntimeException("Equipo no encontrado."));
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new RuntimeException("Torneo no encontrado."));

        if (jugadorEquipoRepo.existsByCedulaAndTorneoId(cedula, torneoId)) {
            throw new RuntimeException("Ya perteneces a un equipo en este torneo.");
        }

        if (solicitudRepo.existsByCedulaAndTorneoIdAndEstadoIn(
                cedula, torneoId, List.of(EstadoSolicitud.PENDIENTE, EstadoSolicitud.APROBADA))) {
            throw new RuntimeException("Ya tienes una solicitud activa para este torneo.");
        }

        var solicitud = SolicitudEquipo.builder()
                .cedula(cedula)
                .nombre(nombre != null ? nombre : "")
                .correo(correo)
                .equipo(equipo)
                .torneo(torneo)
                .build();
        solicitudRepo.save(solicitud);
        return toDTO(solicitud);
    }

    // ──────────────────────────────────────────────
    //  JUGADOR: listar sus propias solicitudes
    // ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SolicitudDTO> listarSolicitudesJugador(String cedula) {
        return solicitudRepo.findByCedula(cedula)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // ──────────────────────────────────────────────
    //  DELEGADO: listar solicitudes de sus equipos
    // ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SolicitudDTO> listarSolicitudesDelegado(String cedulaDelegado) {
        var equipos = equipoRepo.findByDelegadoCedula(cedulaDelegado);
        if (equipos.isEmpty()) {
            return List.of();
        }
        var equipoIds = equipos.stream().map(Equipo::getId).toList();
        return solicitudRepo.findByEquipoIdIn(equipoIds)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SolicitudDTO> listarPendientesDelegado(String cedulaDelegado) {
        var equipos = equipoRepo.findByDelegadoCedula(cedulaDelegado);
        if (equipos.isEmpty()) {
            return List.of();
        }
        var equipoIds = equipos.stream().map(Equipo::getId).toList();
        return solicitudRepo.findByEquipoIdInAndEstado(equipoIds, EstadoSolicitud.PENDIENTE)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // ──────────────────────────────────────────────
    //  DELEGADO: aprobar solicitud
    // ──────────────────────────────────────────────

    @Transactional
    public SolicitudDTO aprobar(UUID solicitudId, String cedulaDelegado) {
        var solicitud = solicitudRepo.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada."));

        validarDelegadoEsDueno(solicitud, cedulaDelegado);

        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new RuntimeException("La solicitud ya fue resuelta: " + solicitud.getEstado());
        }

        // Verificar regla: 1 equipo por torneo
        if (jugadorEquipoRepo.existsByCedulaAndTorneoId(
                solicitud.getCedula(), solicitud.getTorneo().getId())) {
            throw new RuntimeException("El jugador ya pertenece a un equipo en este torneo.");
        }

        // Crear la membresía jugador ↔ equipo
        var membresia = JugadorEquipo.builder()
                .cedula(solicitud.getCedula())
                .equipo(solicitud.getEquipo())
                .torneo(solicitud.getTorneo())
                .fechaInicio(LocalDate.now())
                .build();
        jugadorEquipoRepo.save(membresia);

        // Marcar solicitud como aprobada
        solicitud.setEstado(EstadoSolicitud.APROBADA);
        solicitud.setFechaResolucion(LocalDateTime.now());
        solicitudRepo.save(solicitud);

        return toDTO(solicitud);
    }

    // ──────────────────────────────────────────────
    //  DELEGADO: rechazar solicitud
    // ──────────────────────────────────────────────

    @Transactional
    public SolicitudDTO rechazar(UUID solicitudId, String motivo, String cedulaDelegado) {
        var solicitud = solicitudRepo.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada."));

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

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private void validarDelegadoEsDueno(SolicitudEquipo solicitud, String cedulaDelegado) {
        var equipo = solicitud.getEquipo();
        if (equipo.getDelegadoCedula() == null
                || !equipo.getDelegadoCedula().equals(cedulaDelegado)) {
            throw new RuntimeException("No eres el delegado de este equipo.");
        }
    }

    private SolicitudDTO toDTO(SolicitudEquipo s) {
        return SolicitudDTO.builder()
                .id(s.getId())
                .cedula(s.getCedula())
                .nombre(s.getNombre())
                .correo(s.getCorreo())
                .equipoId(s.getEquipo().getId())
                .equipoNombre(s.getEquipo().getNombre())
                .torneoId(s.getTorneo().getId())
                .torneoNombre(s.getTorneo().getNombre())
                .estado(s.getEstado().name())
                .fechaSolicitud(s.getFechaSolicitud())
                .fechaResolucion(s.getFechaResolucion())
                .motivoRechazo(s.getMotivoRechazo())
                .build();
    }
}
