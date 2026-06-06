package terminus.co.edu.ufps.competicion.ms2supercopa.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import terminus.co.edu.ufps.competicion.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.TorneoDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin.CrearTorneoRequest;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin.InscripcionDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.EquipoTorneo;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.EstadoInscripcion;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.EstadoTorneo;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.Torneo;
import terminus.co.edu.ufps.competicion.ms2supercopa.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.competicion.ms2supercopa.repository.TorneoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TorneoAdminService {

    private final TorneoRepository torneoRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;

    @Transactional
    public TorneoDTO crear(CrearTorneoRequest req) {
        if (req.getNombre() == null || req.getNombre().isBlank()) {
            throw new RuntimeException("El nombre del torneo es obligatorio.");
        }
        if (req.getEdicion() == null) {
            throw new RuntimeException("La edicion es obligatoria.");
        }
        var torneo = Torneo.builder()
                .nombre(req.getNombre().trim())
                .edicion(req.getEdicion())
                .estado(EstadoTorneo.BORRADOR)
                .fechaInicio(req.getFechaInicio())
                .fechaFin(req.getFechaFin())
                .build();
        torneoRepo.save(torneo);
        return toDTO(torneo);
    }

    @Transactional(readOnly = true)
    public List<TorneoDTO> listar() {
        return torneoRepo.findAllByOrderByEdicionDesc()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public TorneoDTO publicar(UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        if (torneo.getEstado() != EstadoTorneo.BORRADOR) {
            throw new RuntimeException("Solo se puede publicar un torneo en estado BORRADOR.");
        }
        torneo.setEstado(EstadoTorneo.PUBLICADO);
        torneo.setPublicadoEn(LocalDateTime.now());
        torneoRepo.save(torneo);
        return toDTO(torneo);
    }

    @Transactional
    public TorneoDTO iniciar(UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        if (torneo.getEstado() != EstadoTorneo.PUBLICADO) {
            throw new RuntimeException("Solo se puede iniciar un torneo en estado PUBLICADO.");
        }
        torneo.setEstado(EstadoTorneo.EN_CURSO);
        torneoRepo.save(torneo);
        return toDTO(torneo);
    }

    @Transactional(readOnly = true)
    public List<InscripcionDTO> listarInscripciones(UUID torneoId) {
        torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        return equipoTorneoRepo.findByTorneoIdOrderByFechaInscripcionAsc(torneoId)
                .stream()
                .map(this::toInscripcionDTO)
                .toList();
    }

    @Transactional
    public InscripcionDTO aprobarInscripcion(UUID torneoId, UUID equipoTorneoId, String adminCedula) {
        var et = cargarInscripcion(torneoId, equipoTorneoId);
        if (et.getEstadoInscripcion() == EstadoInscripcion.APROBADO) {
            return toInscripcionDTO(et);
        }
        if (et.getEstadoInscripcion() == EstadoInscripcion.RECHAZADO) {
            throw new RuntimeException("La inscripcion fue rechazada y no puede aprobarse.");
        }
        et.setEstadoInscripcion(EstadoInscripcion.APROBADO);
        et.setAprobadoPor(adminCedula != null ? adminCedula : "ADMIN");
        et.setMotivoRechazo(null);
        equipoTorneoRepo.save(et);
        return toInscripcionDTO(et);
    }

    @Transactional
    public InscripcionDTO rechazarInscripcion(UUID torneoId, UUID equipoTorneoId, String motivo) {
        var et = cargarInscripcion(torneoId, equipoTorneoId);
        if (et.getEstadoInscripcion() == EstadoInscripcion.RECHAZADO) {
            return toInscripcionDTO(et);
        }
        if (motivo == null || motivo.isBlank()) {
            throw new RuntimeException("El motivo del rechazo es obligatorio.");
        }
        et.setEstadoInscripcion(EstadoInscripcion.RECHAZADO);
        et.setMotivoRechazo(motivo.trim());
        equipoTorneoRepo.save(et);
        return toInscripcionDTO(et);
    }

    private EquipoTorneo cargarInscripcion(UUID torneoId, UUID equipoTorneoId) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!et.getTorneo().getId().equals(torneoId)) {
            throw new RuntimeException("La inscripcion no pertenece a ese torneo.");
        }
        return et;
    }

    public InscripcionDTO toInscripcionDTO(EquipoTorneo et) {
        return InscripcionDTO.builder()
                .id(et.getId())
                .torneoId(et.getTorneo().getId())
                .torneoNombre(et.getTorneo().getNombre())
                .equipoId(et.getEquipo().getId())
                .equipoNombre(et.getEquipo().getNombre())
                .delegadoCedula(et.getDelegadoCedula())
                .estadoInscripcion(et.getEstadoInscripcion().name())
                .aprobadoPor(et.getAprobadoPor())
                .motivoRechazo(et.getMotivoRechazo())
                .fechaInscripcion(et.getFechaInscripcion())
                .build();
    }

    public TorneoDTO toDTO(Torneo t) {
        return TorneoDTO.builder()
                .id(t.getId())
                .nombre(t.getNombre())
                .edicion(t.getEdicion())
                .estado(t.getEstado() != null ? t.getEstado().name() : null)
                .fechaInicio(t.getFechaInicio())
                .fechaFin(t.getFechaFin())
                .publicadoEn(t.getPublicadoEn())
                .build();
    }
}
