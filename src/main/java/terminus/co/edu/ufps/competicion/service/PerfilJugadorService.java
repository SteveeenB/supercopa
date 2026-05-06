package terminus.co.edu.ufps.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.competicion.dto.*;
import terminus.co.edu.ufps.competicion.model.Partido;
import terminus.co.edu.ufps.competicion.model.PartidoJugador;
import terminus.co.edu.ufps.competicion.model.TipoTarjeta;
import terminus.co.edu.ufps.competicion.repository.*;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerfilJugadorService {

    private final JugadorRepository jugadorRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final PartidoJugadorRepository partidoJugadorRepo;
    private final TarjetaRepository tarjetaRepo;
    private final TituloRepository tituloRepo;

    @Transactional(readOnly = true)
    public PerfilJugadorDTO obtenerPerfil(String cedula) {
        var jugadorOpt = jugadorRepo.findById(cedula);
        
        // Si el jugador no existe localmente, devolvemos un perfil vacío con ceros
        if (jugadorOpt.isEmpty()) {
            return PerfilJugadorDTO.builder()
                    .cedula(cedula)
                    .nombre("Usuario no registrado")
                    .resumen(ResumenEstadisticasDTO.builder()
                            .partidosJugados(0)
                            .goles(0)
                            .tarjetas(new TarjetasResumenDTO(0, 0, 0))
                            .titulos(0)
                            .build())
                    .equipos(List.of())
                    .titulos(List.of())
                    .partidos(List.of())
                    .build();
        }

        var jugador = jugadorOpt.get();

        // 1. Historial de equipos
        var equipos = jugadorEquipoRepo.findByCedula(cedula).stream()
                .map(je -> HistorialEquipoDTO.builder()
                        .id(je.getEquipo().getId())
                        .nombre(je.getEquipo().getNombre())
                        .desde(je.getFechaInicio())
                        .hasta(je.getFechaFin())
                        .build())
                .toList();

        // 2. Resumen de estadísticas
        long partidosJugados = partidoJugadorRepo.findByJugadorCedulaAndJugoTrue(cedula).size();
        int goles = partidoJugadorRepo.sumGolesByCedula(cedula);
        
        var tarjetasResumen = TarjetasResumenDTO.builder()
                .amarillas(tarjetaRepo.countByJugadorCedulaAndTipo(cedula, TipoTarjeta.AMARILLA))
                .azules(tarjetaRepo.countByJugadorCedulaAndTipo(cedula, TipoTarjeta.AZUL))
                .rojas(tarjetaRepo.countByJugadorCedulaAndTipo(cedula, TipoTarjeta.ROJA))
                .build();

        var titulosList = tituloRepo.findByJugadorCedula(cedula);
        
        var resumen = ResumenEstadisticasDTO.builder()
                .partidosJugados(partidosJugados)
                .goles(goles)
                .tarjetas(tarjetasResumen)
                .titulos(titulosList.size())
                .build();

        // 3. Detalle de títulos
        var titulosDTOs = titulosList.stream()
                .map(t -> TituloDTO.builder()
                        .torneo(t.getTorneo().getNombre())
                        .equipo(t.getEquipo().getNombre())
                        .puesto(t.getPuesto().name())
                        .fecha(t.getFecha())
                        .build())
                .toList();

        // 4. Historial de partidos recientes
        var partidosDTOs = partidoJugadorRepo.findByJugadorCedula(cedula).stream()
                .map(pj -> {
                    Partido p = pj.getPartido();
                    String rival = p.getEquipoLocal().getId().equals(pj.getEquipo().getId()) 
                            ? p.getEquipoVisitante().getNombre() 
                            : p.getEquipoLocal().getNombre();
                    
                    List<String> tarjetasPartido = tarjetaRepo.findByPartidoIdAndJugadorCedula(p.getId(), cedula)
                            .stream()
                            .map(t -> t.getTipo().name())
                            .toList();

                    return PartidoResumenDTO.builder()
                            .id(p.getId())
                            .fecha(p.getFecha())
                            .equipo(pj.getEquipo().getNombre())
                            .rival(rival)
                            .goles(pj.getGoles())
                            .tarjetas(tarjetasPartido)
                            .build();
                })
                .collect(Collectors.toList());

        return PerfilJugadorDTO.builder()
                .cedula(jugador.getCedula())
                .nombre(jugador.getNombre())
                .equipos(equipos)
                .resumen(resumen)
                .titulos(titulosDTOs)
                .partidos(partidosDTOs)
                .build();
    }
}
