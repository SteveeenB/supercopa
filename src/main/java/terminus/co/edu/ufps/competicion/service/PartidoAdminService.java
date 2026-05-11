package terminus.co.edu.ufps.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.competicion.dto.admin.EventoDTO;
import terminus.co.edu.ufps.competicion.dto.admin.EventoRequest;
import terminus.co.edu.ufps.competicion.dto.admin.PartidoAdminDTO;
import terminus.co.edu.ufps.competicion.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.competicion.model.*;
import terminus.co.edu.ufps.competicion.repository.*;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartidoAdminService {

    private final PartidoRepository partidoRepo;
    private final EventoPartidoRepository eventoRepo;
    private final PartidoJugadorRepository partidoJugadorRepo;
    private final JugadorRepository jugadorRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;

    @Transactional(readOnly = true)
    public List<PartidoAdminDTO> listarPorTorneo(UUID torneoId) {
        return partidoRepo.findByTorneoIdOrderByFechaAsc(torneoId)
                .stream()
                .map(this::toPartidoDTO)
                .toList();
    }

    @Transactional
    public EventoDTO crearEvento(UUID partidoId, EventoRequest req) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        if (partido.getEstado() == EstadoPartido.FINALIZADO || partido.getEstado() == EstadoPartido.WO) {
            throw new RuntimeException("No se pueden agregar eventos a un partido cerrado.");
        }
        if (req.getCedula() == null || req.getCedula().isBlank()) {
            throw new RuntimeException("La cedula del jugador es obligatoria.");
        }
        if (req.getEquipoTorneoId() == null) {
            throw new RuntimeException("El equipo (equipoTorneoId) es obligatorio.");
        }
        TipoEvento tipo;
        try {
            tipo = TipoEvento.valueOf(req.getTipoEvento());
        } catch (Exception e) {
            throw new RuntimeException("Tipo de evento invalido. Valores validos: GOL, AMARILLA, AZUL, ROJA.");
        }

        var jugador = jugadorRepo.findById(req.getCedula())
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado."));
        var equipoTorneo = equipoTorneoRepo.findById(req.getEquipoTorneoId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipo del torneo no encontrado."));

        boolean esLocal = partido.getEquipoLocalTorneo().getId().equals(equipoTorneo.getId());
        boolean esVisitante = partido.getEquipoVisitanteTorneo().getId().equals(equipoTorneo.getId());
        if (!esLocal && !esVisitante) {
            throw new RuntimeException("El equipo no participa en este partido.");
        }

        Integer max = eventoRepo.findMaxOrdenByPartidoId(partidoId);
        int orden = (max == null ? 0 : max) + 1;

        var evento = EventoPartido.builder()
                .partido(partido)
                .cedula(req.getCedula())
                .equipoTorneo(equipoTorneo)
                .tipoEvento(tipo)
                .orden(orden)
                .build();
        eventoRepo.save(evento);

        if (tipo == TipoEvento.GOL) {
            var pj = partidoJugadorRepo.findByPartidoIdAndJugadorCedula(partidoId, req.getCedula())
                    .orElseGet(() -> PartidoJugador.builder()
                            .partido(partido)
                            .jugador(jugador)
                            .equipoTorneo(equipoTorneo)
                            .jugo(true)
                            .goles(0)
                            .build());
            pj.setGoles((pj.getGoles() == null ? 0 : pj.getGoles()) + 1);
            partidoJugadorRepo.save(pj);
        }

        if (partido.getEstado() == EstadoPartido.PROGRAMADO) {
            partido.setEstado(EstadoPartido.EN_CURSO);
            partidoRepo.save(partido);
        }

        return toEventoDTO(evento, jugador);
    }

    @Transactional(readOnly = true)
    public List<EventoDTO> listarEventos(UUID partidoId) {
        partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        return eventoRepo.findByPartidoIdOrderByOrdenAsc(partidoId)
                .stream()
                .map(e -> {
                    var jugador = jugadorRepo.findById(e.getCedula()).orElse(null);
                    return toEventoDTO(e, jugador);
                })
                .toList();
    }

    @Transactional
    public void eliminarEvento(UUID partidoId, UUID eventoId) {
        var evento = eventoRepo.findById(eventoId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado."));
        if (!evento.getPartido().getId().equals(partidoId)) {
            throw new RuntimeException("El evento no pertenece al partido indicado.");
        }
        if (evento.getTipoEvento() == TipoEvento.GOL) {
            var pj = partidoJugadorRepo.findByPartidoIdAndJugadorCedula(partidoId, evento.getCedula())
                    .orElse(null);
            if (pj != null && pj.getGoles() != null && pj.getGoles() > 0) {
                pj.setGoles(pj.getGoles() - 1);
                partidoJugadorRepo.save(pj);
            }
        }
        eventoRepo.delete(evento);
    }

    @Transactional
    public PartidoAdminDTO cerrarPartido(UUID partidoId) {
        var partido = partidoRepo.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
        if (partido.getEstado() == EstadoPartido.FINALIZADO) {
            return toPartidoDTO(partido);
        }
        asegurarParticipantes(partido, partido.getEquipoLocalTorneo());
        asegurarParticipantes(partido, partido.getEquipoVisitanteTorneo());
        partido.setEstado(EstadoPartido.FINALIZADO);
        partidoRepo.save(partido);
        return toPartidoDTO(partido);
    }

    private void asegurarParticipantes(Partido partido, EquipoTorneo equipoTorneo) {
        var miembros = jugadorEquipoRepo.findByEquipoTorneoIdOrderByFechaInicioAsc(equipoTorneo.getId());
        for (var je : miembros) {
            if (je.getEstado() != EstadoMembresia.ACTIVO) continue;
            var existente = partidoJugadorRepo.findByPartidoIdAndJugadorCedula(partido.getId(), je.getCedula());
            if (existente.isEmpty()) {
                var jugador = jugadorRepo.findById(je.getCedula()).orElse(null);
                if (jugador == null) continue;
                partidoJugadorRepo.save(PartidoJugador.builder()
                        .partido(partido)
                        .jugador(jugador)
                        .equipoTorneo(equipoTorneo)
                        .jugo(true)
                        .goles(0)
                        .build());
            }
        }
    }

    public PartidoAdminDTO toPartidoDTO(Partido p) {
        var local = p.getEquipoLocalTorneo();
        var visitante = p.getEquipoVisitanteTorneo();
        long golesLocal = eventoRepo.countByPartidoIdAndEquipoTorneoIdAndTipoEvento(
                p.getId(), local.getId(), TipoEvento.GOL);
        long golesVisitante = eventoRepo.countByPartidoIdAndEquipoTorneoIdAndTipoEvento(
                p.getId(), visitante.getId(), TipoEvento.GOL);
        return PartidoAdminDTO.builder()
                .id(p.getId())
                .fecha(p.getFecha())
                .estado(p.getEstado().name())
                .localEquipoTorneoId(local.getId())
                .localNombre(local.getEquipo().getNombre())
                .visitanteEquipoTorneoId(visitante.getId())
                .visitanteNombre(visitante.getEquipo().getNombre())
                .golesLocal((int) golesLocal)
                .golesVisitante((int) golesVisitante)
                .build();
    }

    private EventoDTO toEventoDTO(EventoPartido e, Jugador jugador) {
        return EventoDTO.builder()
                .id(e.getId())
                .orden(e.getOrden())
                .cedula(e.getCedula())
                .jugadorNombre(jugador != null ? jugador.getNombre() : null)
                .equipoTorneoId(e.getEquipoTorneo().getId())
                .equipoNombre(e.getEquipoTorneo().getEquipo().getNombre())
                .tipoEvento(e.getTipoEvento().name())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
