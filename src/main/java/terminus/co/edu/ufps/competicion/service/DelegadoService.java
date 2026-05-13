package terminus.co.edu.ufps.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.competicion.dto.EquipoDTO;
import terminus.co.edu.ufps.competicion.dto.admin.InscripcionDTO;
import terminus.co.edu.ufps.competicion.dto.delegado.ActualizarCamisetaRequest;
import terminus.co.edu.ufps.competicion.dto.delegado.AgregarMiembroRequest;
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
        // Asegura que el delegado este registrado como Jugador (necesario para auto-inscripcion despues).
        jugadorRepo.findById(delegadoCedula)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El delegado no esta registrado como jugador. Solicita primero el rol jugador o pide al admin que lo cargue al padron."));

        // Un delegado solo puede tener un equipo base.
        var existente = equipoRepo.findByDelegadoCedula(delegadoCedula);
        if (existente.isPresent()) {
            throw new RuntimeException("Ya tienes un equipo creado: \""
                    + existente.get().getNombre() + "\". No puedes crear otro.");
        }

        var equipo = Equipo.builder()
                .nombre(req.getNombre().trim())
                .delegadoCedula(delegadoCedula)
                .build();
        equipoRepo.save(equipo);
        return EquipoDTO.builder().id(equipo.getId()).nombre(equipo.getNombre()).build();
    }

    @Transactional(readOnly = true)
    public EquipoDTO miEquipo(String delegadoCedula) {
        var equipo = equipoRepo.findByDelegadoCedula(delegadoCedula)
                .orElseThrow(() -> new ResourceNotFoundException("Aun no has creado tu equipo."));
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

    // ─────────────────────────────────────────────────────────
    //  HU44 — Delegado gestiona plantel directamente
    // ─────────────────────────────────────────────────────────

    @Transactional
    public MiembroEquipoDTO agregarMiembro(String delegadoCedula, UUID equipoTorneoId,
                                            AgregarMiembroRequest req) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoCedula().equals(delegadoCedula)) {
            throw new RuntimeException("No eres el delegado de esta inscripcion.");
        }

        var jugador = jugadorRepo.findById(req.getCedula())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Jugador no encontrado. Solicita al administrador que lo cargue en el padron oficial."));

        if (jugadorEquipoRepo.existsByCedulaAndTorneoIdAndEstado(
                req.getCedula(), et.getTorneo().getId(), EstadoMembresia.ACTIVO)) {
            throw new RuntimeException("El jugador ya pertenece a un equipo en este torneo.");
        }

        if (req.getNumeroCamiseta() != null) {
            if (jugadorEquipoRepo.existsByEquipoTorneoIdAndNumeroCamisetaAndEstado(
                    equipoTorneoId, req.getNumeroCamiseta(), EstadoMembresia.ACTIVO)) {
                throw new RuntimeException(
                        "El número " + req.getNumeroCamiseta() + " ya está asignado en este equipo.");
            }
        }

        var existente = jugadorEquipoRepo.findByCedulaAndTorneoId(
                req.getCedula(), et.getTorneo().getId());

        if (existente.isPresent()) {
            var je = existente.get();
            je.setEquipoTorneo(et);
            je.setEstado(EstadoMembresia.ACTIVO);
            je.setFechaInicio(LocalDate.now());
            je.setFechaFin(null);
            if (req.getNumeroCamiseta() != null) {
                je.setNumeroCamiseta(req.getNumeroCamiseta());
            }
            jugadorEquipoRepo.save(je);
            return toMiembroDTO(je, jugador, et);
        }

        var je = JugadorEquipo.builder()
                .cedula(jugador.getCedula())
                .torneo(et.getTorneo())
                .equipoTorneo(et)
                .fechaInicio(LocalDate.now())
                .estado(EstadoMembresia.ACTIVO)
                .numeroCamiseta(req.getNumeroCamiseta())
                .build();
        jugadorEquipoRepo.save(je);

        return toMiembroDTO(je, jugador, et);
    }

    @Transactional
    public MiembroEquipoDTO removerMiembro(String delegadoCedula, UUID equipoTorneoId, String cedula) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoCedula().equals(delegadoCedula)) {
            throw new RuntimeException("No eres el delegado de esta inscripcion.");
        }

        var je = jugadorEquipoRepo.findByCedulaAndEquipoTorneoId(cedula, equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado en el plantel."));

        if (je.getCedula().equals(et.getDelegadoCedula())) {
            throw new RuntimeException("No puedes eliminar al delegado del plantel.");
        }

        if (je.getEstado() == EstadoMembresia.RETIRADO) {
            var jugador = jugadorRepo.findById(cedula).orElse(null);
            return toMiembroDTO(je, jugador, et);
        }

        je.setEstado(EstadoMembresia.RETIRADO);
        je.setFechaFin(LocalDate.now());
        jugadorEquipoRepo.save(je);

        var jugador = jugadorRepo.findById(cedula).orElse(null);
        return toMiembroDTO(je, jugador, et);
    }

    @Transactional
    public MiembroEquipoDTO actualizarCamiseta(String delegadoCedula, UUID equipoTorneoId,
                                                String cedula, Integer numeroCamiseta) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getDelegadoCedula().equals(delegadoCedula)) {
            throw new RuntimeException("No eres el delegado de esta inscripcion.");
        }

        var je = jugadorEquipoRepo.findByCedulaAndEquipoTorneoId(cedula, equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado en el plantel."));

        if (numeroCamiseta != null) {
            var ocupado = jugadorEquipoRepo
                    .findByEquipoTorneoIdOrderByFechaInicioAsc(equipoTorneoId)
                    .stream()
                    .filter(j -> j.getEstado() == EstadoMembresia.ACTIVO
                            && j.getNumeroCamiseta() != null
                            && j.getNumeroCamiseta().equals(numeroCamiseta)
                            && !j.getCedula().equals(cedula))
                    .findAny();
            if (ocupado.isPresent()) {
                throw new RuntimeException(
                        "El número " + numeroCamiseta + " ya está asignado a otro jugador activo.");
            }
        }

        je.setNumeroCamiseta(numeroCamiseta);
        jugadorEquipoRepo.save(je);

        var jugador = jugadorRepo.findById(cedula).orElse(null);
        return toMiembroDTO(je, jugador, et);
    }

    private MiembroEquipoDTO toMiembroDTO(JugadorEquipo je, Jugador jugador, EquipoTorneo et) {
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
                .torneoEstado(et.getTorneo().getEstado().name())
                .build();
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
                .map(je -> toMiembroDTO(je, jugadorRepo.findById(je.getCedula()).orElse(null), et))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MiembroEquipoDTO> miembrosAdmin(UUID equipoTorneoId) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        return jugadorEquipoRepo.findByEquipoTorneoIdOrderByFechaInicioAsc(equipoTorneoId)
                .stream()
                .map(je -> toMiembroDTO(je, jugadorRepo.findById(je.getCedula()).orElse(null), et))
                .toList();
    }
}
