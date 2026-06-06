package terminus.co.edu.ufps.competicion.ms3finanzas.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.competicion.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.competicion.ms3finanzas.dto.ComprobanteDTO;
import terminus.co.edu.ufps.competicion.ms3finanzas.dto.SubirComprobanteRequest;
import terminus.co.edu.ufps.competicion.ms3finanzas.model.Comprobante;
import terminus.co.edu.ufps.competicion.ms3finanzas.model.EstadoComprobante;
import terminus.co.edu.ufps.competicion.ms3finanzas.model.TipoComprobante;
import terminus.co.edu.ufps.competicion.ms3finanzas.repository.ComprobanteRepository;

/**
 * Gestión de comprobantes de pago (HU25, HU27, HU28).
 *
 * Estado actual: skeleton con CRUD y resolución básica.
 * Falta implementar:
 *  - Validar que el delegado es dueño del equipo/multa al subir (HU25/HU27).
 *  - Al aprobar comprobante de INSCRIPCION: notificar a MS2 que la inscripción
 *    pasa a APROBADO (hoy MS2 tiene mock_pago directo).
 *  - Al aprobar comprobante de MULTA: marcar Multa como PAGADA.
 *  - Notificar al delegado vía MS5 (HU26).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComprobanteService {

    private final ComprobanteRepository comprobanteRepo;

    @Transactional
    public ComprobanteDTO subir(SubirComprobanteRequest req, String cedulaUploader) {
        TipoComprobante tipo;
        try {
            tipo = TipoComprobante.valueOf(req.getTipo());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Tipo de comprobante invalido: " + req.getTipo());
        }

        // TODO: validar que el cedulaUploader es delegado del equipo/multa referenciado.

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

        // TODO HU28: si tipo=INSCRIPCION → marcar EquipoTorneo como APROBADO en MS2.
        // TODO HU28: si tipo=MULTA → marcar Multa como PAGADA.
        // TODO HU26: notificar al delegado vía publisher hacia MS5.
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

        // TODO HU26: notificar al delegado.
        log.info("[FINANZAS] Comprobante {} RECHAZADO por {}: {}", c.getId(), cedulaAdmin, motivo);
        return toDTO(c);
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
