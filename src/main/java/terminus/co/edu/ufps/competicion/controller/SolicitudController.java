package terminus.co.edu.ufps.competicion.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import terminus.co.edu.ufps.competicion.dto.RechazoRequestDTO;
import terminus.co.edu.ufps.competicion.dto.CrearSolicitudDTO;
import terminus.co.edu.ufps.competicion.dto.SolicitudDTO;
import terminus.co.edu.ufps.competicion.service.SolicitudService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supercopa/solicitudes")
@RequiredArgsConstructor
public class SolicitudController {

    private final SolicitudService solicitudService;

    /**
     * POST /api/supercopa/solicitudes
     * Un jugador solicita unirse a un equipo.
     */
    @PostMapping
    @PreAuthorize("hasRole('JUGADOR')")
    public ResponseEntity<String> crearSolicitud(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CrearSolicitudDTO request) {
        
        String cedula = jwt.getClaimAsString("cedula");
        String nombre = jwt.getClaimAsString("nombre");
        String correo = jwt.getClaimAsString("email");

        solicitudService.crearSolicitud(cedula, nombre, correo, request.getEquipoId(), request.getCampeonatoId());
        
        return ResponseEntity.ok("Solicitud enviada correctamente al delegado del equipo.");
    }

    /**
     * GET /api/supercopa/solicitudes
     * Delegado ve todas las solicitudes de sus equipos.
     */
    @GetMapping
    @PreAuthorize("hasRole('DELEGADO')")
    public ResponseEntity<List<SolicitudDTO>> listarSolicitudes(
            @AuthenticationPrincipal Jwt jwt) {
        String cedulaDelegado = jwt.getClaimAsString("cedula");
        return ResponseEntity.ok(solicitudService.listarSolicitudesDelegado(cedulaDelegado));
    }

    /**
     * GET /api/supercopa/solicitudes/pendientes
     * Delegado ve solo las solicitudes pendientes de sus equipos.
     */
    @GetMapping("/pendientes")
    @PreAuthorize("hasRole('DELEGADO')")
    public ResponseEntity<List<SolicitudDTO>> listarPendientes(
            @AuthenticationPrincipal Jwt jwt) {
        String cedulaDelegado = jwt.getClaimAsString("cedula");
        return ResponseEntity.ok(solicitudService.listarPendientesDelegado(cedulaDelegado));
    }

    /**
     * POST /api/supercopa/solicitudes/{id}/aprobar
     * Delegado aprueba una solicitud:
     *   - Crea la membresía jugador_equipo para ese campeonato
     *   - Marca la solicitud como APROBADA
     */
    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasRole('DELEGADO')")
    public ResponseEntity<SolicitudDTO> aprobar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        String cedulaDelegado = jwt.getClaimAsString("cedula");
        return ResponseEntity.ok(solicitudService.aprobar(id, cedulaDelegado));
    }

    /**
     * POST /api/supercopa/solicitudes/{id}/rechazar
     * Delegado rechaza una solicitud con un motivo.
     */
    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasRole('DELEGADO')")
    public ResponseEntity<SolicitudDTO> rechazar(
            @PathVariable UUID id,
            @RequestBody RechazoRequestDTO req,
            @AuthenticationPrincipal Jwt jwt) {
        String cedulaDelegado = jwt.getClaimAsString("cedula");
        return ResponseEntity.ok(solicitudService.rechazar(id, req.getMotivo(), cedulaDelegado));
    }
}
