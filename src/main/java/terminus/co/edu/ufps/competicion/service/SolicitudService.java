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
    private final CampeonatoRepository campeonatoRepo;

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

        // Verificar regla: 1 equipo por campeonato
        if (jugadorEquipoRepo.existsByCedulaAndCampeonatoId(
                solicitud.getCedula(), solicitud.getCampeonato().getId())) {
            throw new RuntimeException("El jugador ya pertenece a un equipo en este campeonato.");
        }

        // Crear la membresía jugador ↔ equipo
        var membresia = JugadorEquipo.builder()
                .cedula(solicitud.getCedula())
                .equipo(solicitud.getEquipo())
                .campeonato(solicitud.getCampeonato())
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
                .campeonatoId(s.getCampeonato().getId())
                .campeonatoNombre(s.getCampeonato().getNombre())
                .estado(s.getEstado().name())
                .fechaSolicitud(s.getFechaSolicitud())
                .fechaResolucion(s.getFechaResolucion())
                .motivoRechazo(s.getMotivoRechazo())
                .build();
    }
}
