package terminus.co.edu.ufps.competicion.ms3finanzas.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.competicion.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.EquipoTorneo;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.EstadoInscripcion;
import terminus.co.edu.ufps.competicion.ms2supercopa.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.competicion.ms3finanzas.dto.ComprobanteDTO;
import terminus.co.edu.ufps.competicion.ms3finanzas.dto.SubirComprobanteRequest;
import terminus.co.edu.ufps.competicion.ms3finanzas.model.Comprobante;
import terminus.co.edu.ufps.competicion.ms3finanzas.model.EstadoComprobante;
import terminus.co.edu.ufps.competicion.ms3finanzas.model.TipoComprobante;
import terminus.co.edu.ufps.competicion.ms3finanzas.repository.ComprobanteRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComprobanteService {

    private final ComprobanteRepository comprobanteRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;

    @Transactional
    public ComprobanteDTO subir(SubirComprobanteRequest req, String cedulaUploader) {
        TipoComprobante tipo;
        try {
            tipo = TipoComprobante.valueOf(req.getTipo());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Tipo de comprobante invalido: " + req.getTipo());
        }

        if (tipo == TipoComprobante.INSCRIPCION) {
            var et = equipoTorneoRepo.findById(req.getReferenciaId())
                    .orElseThrow(() -> new ResourceNotFoundException("EquipoTorneo no encontrado."));
            if (!et.getDelegadoCedula().equals(cedulaUploader)) {
                throw new RuntimeException("No eres el delegado de esta inscripcion.");
            }
        }

        var c = Comprobante.builder()
                .tipo(tipo)
                .referenciaId(req.getReferenciaId())
                .urlArchivo(req.getUrlArchivo())
                .cedulaUploader(cedulaUploader)
                .build();
        comprobanteRepo.save(c);
        log.info("[FINANZAS] Comprobante {} subido por {} para referencia {}",
                c.getId(), cedulaUploader, c.getReferenciaId());
        return toDTO(c);
    }

    @Transactional(readOnly = true)
    public List<ComprobanteDTO> listarPendientes() {
        return comprobanteRepo.findByEstado(EstadoComprobante.PENDIENTE_REVISION)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<ComprobanteDTO> misComprobantes(String cedulaUploader) {
        return comprobanteRepo.findByCedulaUploaderOrderByFechaSubidaDesc(cedulaUploader)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public ComprobanteDTO aprobar(UUID comprobanteId, String cedulaAdmin) {
        var c = comprobanteRepo.findById(comprobanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante no encontrado."));
        if (c.getEstado() != EstadoComprobante.PENDIENTE_REVISION) {
            throw new RuntimeException("Comprobante ya resuelto: " + c.getEstado());
        }
        c.setEstado(EstadoComprobante.APROBADO);
        c.setAprobadoPor(cedulaAdmin);
        c.setFechaResolucion(LocalDateTime.now());
        comprobanteRepo.save(c);

        if (c.getTipo() == TipoComprobante.INSCRIPCION) {
            equipoTorneoRepo.findById(c.getReferenciaId()).ifPresent(et -> {
                if (et.getEstadoInscripcion() == EstadoInscripcion.PENDIENTE_PAGO) {
                    et.setEstadoInscripcion(EstadoInscripcion.APROBADO);
                    et.setAprobadoPor(cedulaAdmin);
                    equipoTorneoRepo.save(et);
                    log.info("[FINANZAS] Inscripcion {} APROBADA por comprobante {}", et.getId(), c.getId());
                }
            });
        }

        log.info("[FINANZAS] Comprobante {} APROBADO por {}", c.getId(), cedulaAdmin);
        return toDTO(c);
    }

    @Transactional
    public ComprobanteDTO rechazar(UUID comprobanteId, String motivo, String cedulaAdmin) {
        var c = comprobanteRepo.findById(comprobanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante no encontrado."));
        if (c.getEstado() != EstadoComprobante.PENDIENTE_REVISION) {
            throw new RuntimeException("Comprobante ya resuelto: " + c.getEstado());
        }
        c.setEstado(EstadoComprobante.RECHAZADO);
        c.setMotivoRechazo(motivo);
        c.setAprobadoPor(cedulaAdmin);
        c.setFechaResolucion(LocalDateTime.now());
        comprobanteRepo.save(c);

        log.info("[FINANZAS] Comprobante {} RECHAZADO por {}: {}", c.getId(), cedulaAdmin, motivo);
        return toDTO(c);
    }

    @Transactional(readOnly = true)
    public ComprobanteDTO obtenerComprobante(UUID id) {
        return comprobanteRepo.findById(id)
                .map(this::toDTO)
                .orElse(null);
    }

    private ComprobanteDTO toDTO(Comprobante c) {
        return ComprobanteDTO.builder()
                .id(c.getId())
                .tipo(c.getTipo().name())
                .referenciaId(c.getReferenciaId())
                .urlArchivo(c.getUrlArchivo())
                .cedulaUploader(c.getCedulaUploader())
                .estado(c.getEstado().name())
                .motivoRechazo(c.getMotivoRechazo())
                .aprobadoPor(c.getAprobadoPor())
                .fechaSubida(c.getFechaSubida())
                .fechaResolucion(c.getFechaResolucion())
                .build();
    }
}
