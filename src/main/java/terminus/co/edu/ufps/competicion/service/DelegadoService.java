package terminus.co.edu.ufps.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.competicion.dto.EquipoDTO;
import terminus.co.edu.ufps.competicion.dto.admin.InscripcionDTO;
import terminus.co.edu.ufps.competicion.dto.delegado.CrearEquipoRequest;
import terminus.co.edu.ufps.competicion.dto.delegado.MiembroEquipoDTO;
import terminus.co.edu.ufps.competicion.dto.delegado.TorneoDisponibleDTO;
import terminus.co.edu.ufps.competicion.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.competicion.model.*;
import terminus.co.edu.ufps.competicion.repository.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DelegadoService {

    private final EquipoRepository equipoRepo;
    private final TorneoRepository torneoRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final JugadorRepository jugadorRepo;
    private final TorneoAdminService torneoAdminService;

    @Transactional
    public EquipoDTO crearEquipo(String delegadoCedula, CrearEquipoRequest req) {
        if (req.getNombre() == null || req.getNombre().isBlank()) {
            throw new RuntimeException("El nombre del equipo es obligatorio.");
        }
        // Asegura que el delegado ya exista como Jugador (se autoinscribira despues al inscribir torneo).
        jugadorRepo.findById(delegadoCedula)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El delegado no esta registrado como jugador. Solicita primero el rol jugador o pide al admin que lo cargue al padron."));

        // Un delegado puede tener un solo equipo base — si ya tiene una inscripcion, devolvemos el equipo base de la primera.
        var inscripcionesPrevias = equipoTorneoRepo.findByDelegadoCedulaOrderByFechaInscripcionDesc(delegadoCedula);
        if (!inscripcionesPrevias.isEmpty()) {
            var existente = inscripcionesPrevias.get(0).getEquipo();
            return EquipoDTO.builder().id(existente.getId()).nombre(existente.getNombre()).build();
        }

        var equipo = Equipo.builder().nombre(req.getNombre().trim()).build();
        equipoRepo.save(equipo);
        return EquipoDTO.builder().id(equipo.getId()).nombre(equipo.getNombre()).build();
    }

    @Transactional(readOnly = true)
    public EquipoDTO miEquipo(String delegadoCedula) {
        var inscripciones = equipoTorneoRepo.findByDelegadoCedulaOrderByFechaInscripcionDesc(delegadoCedula);
        if (inscripciones.isEmpty()) {
            throw new ResourceNotFoundException("Aun no has creado tu equipo.");
        }
        var equipo = inscripciones.get(0).getEquipo();
        return EquipoDTO.builder().id(equipo.getId()).nombre(equipo.getNombre()).build();
    }

    @Transactional(readOnly = true)
    public List<TorneoDisponibleDTO> torneosDisponibles(String delegadoCedula) {
        var publicados = torneoRepo.findByEstadoInOrderByEdicionDesc(
                List.of(EstadoTorneo.PUBLICADO, EstadoTorneo.EN_CURSO));
        return publicados.stream().map(t -> {
            var inscripcion = equipoTorneoRepo.findByTorneoIdAndDelegadoCedula(t.getId(), delegadoCedula);
            return TorneoDisponibleDTO.builder()
                    .id(t.getId())
                    .nombre(t.getNombre())
                    .edicion(t.getEdicion())
                    .estado(t.getEstado().name())
                    .fechaInicio(t.getFechaInicio())
                    .fechaFin(t.getFechaFin())
                    .inscrito(inscripcion.isPresent())
                    .estadoInscripcion(inscripcion.map(et -> et.getEstadoInscripcion().name()).orElse(null))
                    .equipoTorneoId(inscripcion.map(EquipoTorneo::getId).orElse(null))
                    .build();
        }).toList();
    }

    @Transactional
    public InscripcionDTO inscribir(String delegadoCedula, UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        if (torneo.getEstado() != EstadoTorneo.PUBLICADO) {
            throw new RuntimeException("Solo se puede inscribir a torneos PUBLICADOS.");
        }

        var equipoDto = miEquipo(delegadoCedula); // valida que el delegado tenga equipo base
        var equipo = equipoRepo.findById(equipoDto.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipo no encontrado."));

        if (equipoTorneoRepo.findByTorneoIdAndDelegadoCedula(torneoId, delegadoCedula).isPresent()) {
            throw new RuntimeException("Ya tienes una inscripcion para este torneo.");
        }
        if (equipoTorneoRepo.findByTorneoIdAndEquipoId(torneoId, equipo.getId()).isPresent()) {
            throw new RuntimeException("El equipo ya esta inscrito en este torneo.");
        }

        var et = EquipoTorneo.builder()
                .torneo(torneo)
                .equipo(equipo)
                .delegadoCedula(delegadoCedula)
                .estadoInscripcion(EstadoInscripcion.PENDIENTE_PAGO)
                .build();
        equipoTorneoRepo.save(et);

        // Auto-inscribir al delegado como jugador del equipo en ese torneo (HU: el delegado tambien juega).
        if (!jugadorEquipoRepo.existsByCedulaAndTorneoId(delegadoCedula, torneoId)) {
            var jugador = jugadorRepo.findById(delegadoCedula)
                    .orElseThrow(() -> new ResourceNotFoundException("Delegado no esta en el padron como jugador."));
            jugadorEquipoRepo.save(JugadorEquipo.builder()
                    .cedula(jugador.getCedula())
                    .torneo(torneo)
                    .equipoTorneo(et)
                    .fechaInicio(LocalDate.now())
                    .estado(EstadoMembresia.ACTIVO)
                    .build());
        }

        return torneoAdminService.toInscripcionDTO(et);
    }

    @Transactional(readOnly = true)
    public List<InscripcionDTO> misInscripciones(String delegadoCedula) {
        return equipoTorneoRepo.findByDelegadoCedulaOrderByFechaInscripcionDesc(delegadoCedula)
                .stream()
                .map(torneoAdminService::toInscripcionDTO)
                .toList();
    }

    @Transactional
    public InscripcionDTO pagar(String delegadoCedula, UUID equipoTorneoId) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoCedula().equals(delegadoCedula)) {
            throw new RuntimeException("No eres el delegado de esta inscripcion.");
        }
        if (et.getEstadoInscripcion() != EstadoInscripcion.PENDIENTE_PAGO) {
            throw new RuntimeException("La inscripcion no esta pendiente de pago (estado actual: "
                    + et.getEstadoInscripcion() + ").");
        }
        et.setEstadoInscripcion(EstadoInscripcion.APROBADO);
        et.setAprobadoPor("MOCK_PAGO");
        equipoTorneoRepo.save(et);
        return torneoAdminService.toInscripcionDTO(et);
    }

    @Transactional(readOnly = true)
    public List<MiembroEquipoDTO> miembros(String delegadoCedula, UUID equipoTorneoId) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoCedula().equals(delegadoCedula)) {
            throw new RuntimeException("No eres el delegado de esta inscripcion.");
        }
        return jugadorEquipoRepo.findByEquipoTorneoIdOrderByFechaInicioAsc(equipoTorneoId)
                .stream()
                .map(je -> {
                    var jugador = jugadorRepo.findById(je.getCedula()).orElse(null);
                    return MiembroEquipoDTO.builder()
                            .cedula(je.getCedula())
                            .nombre(jugador != null ? jugador.getNombre() : null)
                            .posicion(jugador != null ? jugador.getPosicion() : null)
                            .piernaHabil(jugador != null ? jugador.getPiernaHabil() : null)
                            .alturaCm(jugador != null ? jugador.getAlturaCm() : null)
                            .numeroCamiseta(je.getNumeroCamiseta())
                            .estado(je.getEstado() != null ? je.getEstado().name() : null)
                            .desde(je.getFechaInicio())
                            .esDelegado(je.getCedula().equals(et.getDelegadoCedula()))
                            .build();
                })
                .toList();
    }
}
