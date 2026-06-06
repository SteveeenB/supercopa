package terminus.co.edu.ufps.competicion.ms2supercopa.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.competicion.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.EquipoTorneo;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.EstadoInscripcion;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.EstadoPartido;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.Partido;
import terminus.co.edu.ufps.competicion.ms2supercopa.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.competicion.ms2supercopa.repository.PartidoRepository;
import terminus.co.edu.ufps.competicion.ms2supercopa.repository.TorneoRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FixtureService {

    private final TorneoRepository torneoRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final PartidoRepository partidoRepo;

    /**
     * Genera todos contra todos (round-robin) sobre los equipos APROBADOS del torneo.
     * Cada jornada se programa con una semana de separacion a partir de hoy.
     * Falla si ya existen partidos en el torneo.
     */
    @Transactional
    public List<Partido> generar(UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        if (partidoRepo.existsByTorneoId(torneoId)) {
            throw new RuntimeException("El torneo ya tiene fixture generado.");
        }

        var aprobados = equipoTorneoRepo.findByTorneoIdAndEstadoInscripcionOrderByFechaInscripcionAsc(
                torneoId, EstadoInscripcion.APROBADO);
        if (aprobados.size() < 2) {
            throw new RuntimeException("Se necesitan al menos 2 equipos APROBADOS para generar fixture.");
        }

        var jornadas = roundRobin(aprobados);
        var base = LocalDateTime.now()
                .withMinute(0).withSecond(0).withNano(0)
                .plusDays(1)
                .withHour(15);
        var creados = new ArrayList<Partido>();

        int jornadaIdx = 0;
        for (List<Partido> ronda : jornadas) {
            for (int matchIdx = 0; matchIdx < ronda.size(); matchIdx++) {
                var partido = ronda.get(matchIdx);
                partido.setTorneo(torneo);
                partido.setFecha(base.plusDays(7L * jornadaIdx).plusHours(matchIdx * 2L));
                partido.setEstado(EstadoPartido.PROGRAMADO);
                partidoRepo.save(partido);
                creados.add(partido);
            }
            jornadaIdx++;
        }
        return creados;
    }

    /**
     * Algoritmo Berger (round-robin). Para n impar agrega "bye" (null).
     * Devuelve lista de jornadas; cada jornada es lista de Partidos con los EquipoTorneo asignados (sin persistir).
     */
    private List<List<Partido>> roundRobin(List<EquipoTorneo> equipos) {
        List<EquipoTorneo> work = new ArrayList<>(equipos);
        if (work.size() % 2 != 0) {
            work.add(null); // bye
        }
        int n = work.size();
        int rondas = n - 1;
        int mitad = n / 2;
        List<List<Partido>> jornadas = new ArrayList<>();

        List<EquipoTorneo> rot = new ArrayList<>(work);
        for (int r = 0; r < rondas; r++) {
            List<Partido> ronda = new ArrayList<>();
            for (int i = 0; i < mitad; i++) {
                var local = rot.get(i);
                var visitante = rot.get(n - 1 - i);
                if (local == null || visitante == null) {
                    continue; // bye
                }
                // alternar localia para balancear
                var p = (i == 0 && r % 2 == 1)
                        ? Partido.builder().equipoLocalTorneo(visitante).equipoVisitanteTorneo(local).build()
                        : Partido.builder().equipoLocalTorneo(local).equipoVisitanteTorneo(visitante).build();
                ronda.add(p);
            }
            jornadas.add(ronda);

            // rotar: deja el primero fijo, mueve los demas
            var fijo = rot.get(0);
            var ultimo = rot.remove(rot.size() - 1);
            rot.add(1, ultimo);
            rot.set(0, fijo);
        }
        return jornadas;
    }
}
