package terminus.co.edu.ufps.competicion.ms2supercopa.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.competicion.ms2supercopa.client.Ms1JugadoresClient;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.PerfilEquipoDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.PerfilPartidoDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.PerfilResponseDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.PerfilResumenDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.PerfilTarjetasDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.PerfilTituloDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.EventoPartido;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.Jugador;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.PartidoJugador;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.TipoEvento;
import terminus.co.edu.ufps.competicion.ms2supercopa.repository.EventoPartidoRepository;
import terminus.co.edu.ufps.competicion.ms2supercopa.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.competicion.ms2supercopa.repository.JugadorRepository;
import terminus.co.edu.ufps.competicion.ms2supercopa.repository.PartidoJugadorRepository;
import terminus.co.edu.ufps.competicion.ms2supercopa.repository.TituloRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final JugadorRepository jugadorRepository;
    private final JugadorEquipoRepository jugadorEquipoRepository;
    private final PartidoJugadorRepository partidoJugadorRepository;
    private final EventoPartidoRepository eventoPartidoRepository;
    private final TituloRepository tituloRepository;
    private final Ms1JugadoresClient ms1Client;

    /**
     * @param nombre fallback si MS1 no responde o no esta en padron (viene del JWT)
     */
    @Transactional
    public PerfilResponseDTO obtenerPerfil(String cedula, String nombre, String email) {
        // El Jugador local solo guarda datos cosmeticos/deportivos. Si no existe, lo creamos vacio.
        var jugador = jugadorRepository.findById(cedula)
                .orElseGet(() -> jugadorRepository.save(Jugador.builder().cedula(cedula).build()));

        // Nombre a mostrar: apodo > nombre oficial del padron MS1 > nombre del JWT (fallback).
        String nombreMostrar;
        if (jugador.getApodo() != null && !jugador.getApodo().isBlank()) {
            nombreMostrar = jugador.getApodo();
        } else {
            nombreMostrar = ms1Client.getJugadorPorCedula(cedula)
                    .map(p -> p.getNombre())
                    .orElse(nombre);
        }

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
                .nombre(nombreMostrar)
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
