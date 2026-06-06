package terminus.co.edu.ufps.competicion.ms3finanzas.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.competicion.ms3finanzas.dto.ComprobanteDTO;
import terminus.co.edu.ufps.competicion.ms3finanzas.dto.RechazoComprobanteRequest;
import terminus.co.edu.ufps.competicion.ms3finanzas.dto.SubirComprobanteRequest;
import terminus.co.edu.ufps.competicion.ms3finanzas.service.ComprobanteService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * HU25 (subir inscripción), HU27 (subir pago de multa), HU28 (aprobar/rechazar).
 */
@RestController
@RequestMapping("/api/finanzas/comprobantes")
@RequiredArgsConstructor
public class ComprobanteController {

    private final ComprobanteService comprobanteService;

    @PostMapping
    @PreAuthorize("hasRole('DELEGADO')")
    public ResponseEntity<ComprobanteDTO> subir(
            @Valid @RequestBody SubirComprobanteRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(comprobanteService.subir(req, jwt.getClaimAsString("cedula")));
    }

    @GetMapping("/mis-comprobantes")
    @PreAuthorize("hasRole('DELEGADO')")
    public ResponseEntity<List<ComprobanteDTO>> misComprobantes(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(comprobanteService.misComprobantes(jwt.getClaimAsString("cedula")));
    }

    @GetMapping("/pendientes")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<ComprobanteDTO>> listarPendientes() {
        return ResponseEntity.ok(comprobanteService.listarPendientes());
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ComprobanteDTO> aprobar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(comprobanteService.aprobar(id, jwt.getClaimAsString("cedula")));
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ComprobanteDTO> rechazar(
            @PathVariable UUID id,
            @Valid @RequestBody RechazoComprobanteRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(comprobanteService.rechazar(id, req.getMotivo(), jwt.getClaimAsString("cedula")));
    }
}
