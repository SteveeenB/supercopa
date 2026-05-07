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
import terminus.co.edu.ufps.competicion.model.Jugador;
import terminus.co.edu.ufps.competicion.model.PartidoJugador;
import terminus.co.edu.ufps.competicion.model.Tarjeta;
import terminus.co.edu.ufps.competicion.model.TipoTarjeta;
import terminus.co.edu.ufps.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.competicion.repository.JugadorRepository;
import terminus.co.edu.ufps.competicion.repository.PartidoJugadorRepository;
import terminus.co.edu.ufps.competicion.repository.TarjetaRepository;
import terminus.co.edu.ufps.competicion.repository.TituloRepository;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final JugadorRepository jugadorRepository;
    private final JugadorEquipoRepository jugadorEquipoRepository;
    private final PartidoJugadorRepository partidoJugadorRepository;
    private final TarjetaRepository tarjetaRepository;
    private final TituloRepository tituloRepository;

    @Transactional
    public PerfilResponseDTO obtenerPerfil(String cedula, String nombre, String email) {
        var jugador = jugadorRepository.findById(cedula)
                .orElseGet(() -> jugadorRepository.save(
                        Jugador.builder()
                                .cedula(cedula)
                                .nombre(nombre != null ? nombre : "")
                                .correo(email)
                                .build()));

        var equipos = jugadorEquipoRepository.findByCedulaOrderByFechaInicioDesc(cedula)
                .stream()
                .map(je -> PerfilEquipoDTO.builder()
                        .id(je.getEquipo().getId())
                        .nombre(je.getEquipo().getNombre())
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

        var tarjetas = tarjetaRepository.findByJugadorCedula(cedula);
        var tarjetasPorPartido = new HashMap<UUID, List<String>>();
        int amarillas = 0;
        int azules = 0;
        int rojas = 0;
        for (Tarjeta tarjeta : tarjetas) {
            if (tarjeta.getTipo() == TipoTarjeta.AMARILLA) {
                amarillas++;
            } else if (tarjeta.getTipo() == TipoTarjeta.AZUL) {
                azules++;
            } else if (tarjeta.getTipo() == TipoTarjeta.ROJA) {
                rojas++;
            }
            tarjetasPorPartido
                    .computeIfAbsent(tarjeta.getPartido().getId(), key -> new ArrayList<>())
                    .add(tarjeta.getTipo().name());
        }

        var titulos = tituloRepository.findByJugadorCedula(cedula)
                .stream()
                .map(titulo -> PerfilTituloDTO.builder()
                        .torneo(titulo.getTorneo().getNombre())
                        .equipo(titulo.getEquipo().getNombre())
                        .puesto(titulo.getPuesto().name())
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
        var equipo = pj.getEquipo();
        var rival = Objects.equals(partido.getEquipoLocal().getId(), equipo.getId())
                ? partido.getEquipoVisitante()
                : partido.getEquipoLocal();
        return PerfilPartidoDTO.builder()
                .id(partido.getId())
                .fecha(partido.getFecha())
                .equipo(equipo.getNombre())
                .rival(rival.getNombre())
                .goles(pj.getGoles())
                .tarjetas(tarjetasPorPartido.getOrDefault(partido.getId(), List.of()))
                .build();
    }
}
