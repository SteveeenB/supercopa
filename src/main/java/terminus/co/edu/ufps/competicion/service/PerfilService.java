package terminus.co.edu.ufps.competicion.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.competicion.dto.PerfilEquipoDTO;
import terminus.co.edu.ufps.competicion.dto.PerfilPartidoDTO;
import terminus.co.edu.ufps.competicion.dto.PerfilResumenDTO;
import terminus.co.edu.ufps.competicion.dto.PerfilResponseDTO;
import terminus.co.edu.ufps.competicion.dto.PerfilTarjetasDTO;
import terminus.co.edu.ufps.competicion.dto.PerfilTituloDTO;
import terminus.co.edu.ufps.competicion.model.EventoPartido;
import terminus.co.edu.ufps.competicion.model.Jugador;
import terminus.co.edu.ufps.competicion.model.PartidoJugador;
import terminus.co.edu.ufps.competicion.model.RolJugador;
import terminus.co.edu.ufps.competicion.model.TipoEvento;
import terminus.co.edu.ufps.competicion.repository.EventoPartidoRepository;
import terminus.co.edu.ufps.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.competicion.repository.JugadorRepository;
import terminus.co.edu.ufps.competicion.repository.PartidoJugadorRepository;
import terminus.co.edu.ufps.competicion.repository.TituloRepository;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final JugadorRepository jugadorRepository;
    private final JugadorEquipoRepository jugadorEquipoRepository;
    private final PartidoJugadorRepository partidoJugadorRepository;
    private final EventoPartidoRepository eventoPartidoRepository;
    private final TituloRepository tituloRepository;

    @Transactional
    public PerfilResponseDTO obtenerPerfil(String cedula, String nombre, String email) {
        var jugador = jugadorRepository.findById(cedula)
                .orElseGet(() -> jugadorRepository.save(
                        Jugador.builder()
                                .cedula(cedula)
                                .nombre(nombre != null ? nombre : "")
                                .correo(email)
                                .rolJugador(RolJugador.GRADUADO)
                                .build()));

        var equipos = jugadorEquipoRepository.findByCedulaOrderByFechaInicioDesc(cedula)
                .stream()
                .map(je -> PerfilEquipoDTO.builder()
                        .id(je.getEquipoTorneo().getEquipo().getId())
                        .nombre(je.getEquipoTorneo().getEquipo().getNombre())
                        .torneo(je.getTorneo() != null ? je.getTorneo().getNombre() : null)
                        .desde(je.getFechaInicio())
                        .hasta(je.getFechaFin())
                        .build())
                .toList();

        var participaciones = partidoJugadorRepository.findByJugadorCedulaAndJugoTrue(cedula);
        int goles = participaciones.stream()
                .map(PartidoJugador::getGoles)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        var eventos = eventoPartidoRepository.findByCedula(cedula);
        var tarjetasPorPartido = new HashMap<UUID, List<String>>();
        int amarillas = 0;
        int azules = 0;
        int rojas = 0;
        for (EventoPartido ev : eventos) {
            TipoEvento tipo = ev.getTipoEvento();
            if (tipo == TipoEvento.GOL) continue;
            if (tipo == TipoEvento.AMARILLA) amarillas++;
            else if (tipo == TipoEvento.AZUL) azules++;
            else if (tipo == TipoEvento.ROJA) rojas++;
            tarjetasPorPartido
                    .computeIfAbsent(ev.getPartido().getId(), k -> new ArrayList<>())
                    .add(tipo.name());
        }

        var titulos = tituloRepository.findByJugadorCedula(cedula)
                .stream()
                .map(t -> PerfilTituloDTO.builder()
                        .torneo(t.getTorneo().getNombre())
                        .equipo(t.getEquipoTorneo().getEquipo().getNombre())
                        .puesto(t.getPuesto().name())
                        .build())
                .toList();

        var partidos = participaciones.stream()
                .map(pj -> toPartidoDTO(pj, tarjetasPorPartido))
                .toList();

        var resumen = PerfilResumenDTO.builder()
                .partidosJugados(participaciones.size())
                .goles(goles)
                .tarjetas(PerfilTarjetasDTO.builder()
                        .amarillas(amarillas)
                        .azules(azules)
                        .rojas(rojas)
                        .build())
                .titulos(titulos.size())
                .build();

        return PerfilResponseDTO.builder()
                .cedula(jugador.getCedula())
                .nombre(jugador.getNombre())
                .equipos(equipos)
                .resumen(resumen)
                .titulos(titulos)
                .partidos(partidos)
                .build();
    }

    private PerfilPartidoDTO toPartidoDTO(PartidoJugador pj, Map<UUID, List<String>> tarjetasPorPartido) {
        var partido = pj.getPartido();
        var equipoTorneo = pj.getEquipoTorneo();
        var rival = Objects.equals(partido.getEquipoLocalTorneo().getId(), equipoTorneo.getId())
                ? partido.getEquipoVisitanteTorneo()
                : partido.getEquipoLocalTorneo();
        return PerfilPartidoDTO.builder()
                .id(partido.getId())
                .fecha(partido.getFecha())
                .equipo(equipoTorneo.getEquipo().getNombre())
                .rival(rival.getEquipo().getNombre())
                .goles(pj.getGoles())
                .tarjetas(tarjetasPorPartido.getOrDefault(partido.getId(), List.of()))
                .build();
    }
}
