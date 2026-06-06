package terminus.co.edu.ufps.competicion.ms3finanzas.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.competicion.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.competicion.ms3finanzas.dto.GenerarMultaRequest;
import terminus.co.edu.ufps.competicion.ms3finanzas.dto.MultaDTO;
import terminus.co.edu.ufps.competicion.ms3finanzas.model.EstadoMulta;
import terminus.co.edu.ufps.competicion.ms3finanzas.model.Multa;
import terminus.co.edu.ufps.competicion.ms3finanzas.model.TipoSancion;
import terminus.co.edu.ufps.competicion.ms3finanzas.repository.MultaRepository;

/**
 * Gestión de multas (HU29 generación automática, HU30 habilitación).
 *
 * Estado actual: skeleton.
 * Falta implementar:
 *  - Leer la tabla de sanciones configurada por torneo (HU09 MS2) para
 *    determinar montos según TipoSancion.
 *  - Lógica de acumulación de amarillas → generar suspensión automática
 *    cuando un jugador alcance el umbral configurado.
 *  - Endpoint interno consumido por MS2 al cerrar un partido (HU29).
 *  - Validar W.O.: si el partido es W.O., NO generar multas (regla de negocio MS3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultaService {

    private final MultaRepository multaRepo;

    /**
     * Endpoint interno: MS2 invoca esto al cerrar un partido con tarjetas.
     * TODO: aplicar tabla de sanciones del torneo y calcular monto real.
     */
    @Transactional
    public MultaDTO generar(GenerarMultaRequest req) {
        TipoSancion tipo;
        try {
            tipo = TipoSancion.valueOf(req.getTipoSancion());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Tipo de sancion invalido: " + req.getTipoSancion());
        }

        // TODO HU29: leer tabla de sanciones del torneo para el monto real.
        // TODO HU29: calcular partidos_suspension según tabla (roja directa = N, etc.).
        var multa = Multa.builder()
                .cedula(req.getCedulaJugador())
                .partidoId(req.getPartidoId())
                .equipoTorneoId(req.getEquipoTorneoId())
                .torneoId(req.getTorneoId())
                .tipoSancion(tipo)
                .monto(BigDecimal.ZERO)     // placeholder
                .partidosSuspension(0)      // placeholder
                .build();
        multaRepo.save(multa);

        // TODO MS5: emitir evento FINE_GENERATED para notificación al jugador.
        log.info("[FINANZAS] Multa {} generada para cedula={} tipo={}",
                multa.getId(), multa.getCedula(), tipo);
        return toDTO(multa);
    }

    @Transactional(readOnly = true)
    public List<MultaDTO> misMultas(String cedula) {
        return multaRepo.findByCedula(cedula).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<MultaDTO> multasActivas(UUID torneoId) {
        return multaRepo.findByTorneoIdAndEstado(torneoId, EstadoMulta.PENDIENTE)
                .stream().map(this::toDTO).toList();
    }

    /**
     * HU19: ¿el jugador tiene multas pendientes que lo suspendan?
     * Usado por MS2 antes de cada partido para validar elegibilidad.
     */
    @Transactional(readOnly = true)
    public boolean jugadorTieneDeudas(String cedula) {
        return multaRepo.countByCedulaAndEstado(cedula, EstadoMulta.PENDIENTE) > 0
                || multaRepo.countByCedulaAndEstado(cedula, EstadoMulta.EN_REVISION) > 0;
    }

    /**
     * HU30: habilita explícitamente al jugador para el próximo partido.
     * Solo aplica si la multa está PAGADA (admin ya aprobó comprobante).
     */
    @Transactional
    public MultaDTO habilitar(UUID multaId, String cedulaAdmin) {
        var multa = multaRepo.findById(multaId)
                .orElseThrow(() -> new ResourceNotFoundException("Multa no encontrada."));
        if (multa.getEstado() != EstadoMulta.PAGADA) {
            throw new RuntimeException(
                    "Solo se puede habilitar un jugador cuya multa este PAGADA. Estado actual: "
                            + multa.getEstado());
        }
        multa.setPartidosSuspension(0);
        multaRepo.save(multa);
        // TODO MS2: notificar elegibilidad del jugador.
        log.info("[FINANZAS] Jugador {} habilitado por {} (multa {})",
                multa.getCedula(), cedulaAdmin, multa.getId());
        return toDTO(multa);
    }

    private MultaDTO toDTO(Multa m) {
        return MultaDTO.builder()
                .id(m.getId())
                .cedula(m.getCedula())
                .partidoId(m.getPartidoId())
                .equipoTorneoId(m.getEquipoTorneoId())
                .torneoId(m.getTorneoId())
                .tipoSancion(m.getTipoSancion().name())
                .monto(m.getMonto())
                .estado(m.getEstado().name())
                .partidosSuspension(m.getPartidosSuspension())
                .fechaGeneracion(m.getFechaGeneracion())
                .fechaPago(m.getFechaPago())
                .build();
    }
}
