package terminus.co.edu.ufps.competicion.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import terminus.co.edu.ufps.competicion.dto.RechazoRequestDTO;
import terminus.co.edu.ufps.competicion.dto.SolicitudDTO;
import terminus.co.edu.ufps.competicion.dto.SolicitudRequestDTO;
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
     * Jugador solicita ingreso a un equipo para un torneo.
     */
    @PostMapping
    @PreAuthorize("hasRole('JUGADOR')")
    public ResponseEntity<SolicitudDTO> crear(
            @RequestBody SolicitudRequestDTO req,
            @AuthenticationPrincipal Jwt jwt) {
        String cedula = jwt.getClaimAsString("cedula");
        String nombre = jwt.getClaimAsString("nombre");
        String correo = jwt.getClaimAsString("email");
        return ResponseEntity.ok(solicitudService.crear(cedula, nombre, correo, req.getEquipoId(), req.getTorneoId()));
    }

    /**
     * GET /api/supercopa/solicitudes/mis-solicitudes
     * Jugador ve sus propias solicitudes.
     */
    @GetMapping("/mis-solicitudes")
    @PreAuthorize("hasRole('JUGADOR')")
    public ResponseEntity<List<SolicitudDTO>> misSolicitudes(
            @AuthenticationPrincipal Jwt jwt) {
        String cedula = jwt.getClaimAsString("cedula");
        return ResponseEntity.ok(solicitudService.listarSolicitudesJugador(cedula));
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
    *   - Crea la membresia jugador_equipo para ese torneo
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
