package terminus.co.edu.ufps.competicion.ms3finanzas.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.competicion.ms3finanzas.dto.GenerarMultaRequest;
import terminus.co.edu.ufps.competicion.ms3finanzas.dto.MultaDTO;
import terminus.co.edu.ufps.competicion.ms3finanzas.service.MultaService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * HU29 (generación), HU30 (habilitación), HU19 (consulta de elegibilidad).
 */
@RestController
@RequestMapping("/api/finanzas/multas")
@RequiredArgsConstructor
public class MultaController {

    private final MultaService multaService;

    @GetMapping("/mias")
    @PreAuthorize("hasRole('JUGADOR')")
    public ResponseEntity<List<MultaDTO>> misMultas(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(multaService.misMultas(jwt.getClaimAsString("cedula")));
    }

    @GetMapping("/activas")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<MultaDTO>> activas(@RequestParam UUID torneoId) {
        return ResponseEntity.ok(multaService.multasActivas(torneoId));
    }

    /**
     * Endpoint interno consumido por MS2 al cerrar un partido con tarjetas.
     * Protegido por SCOPE_internal (token de servicio entre microservicios).
     */
    @PostMapping("/generar")
    @PreAuthorize("hasAuthority('SCOPE_internal')")
    public ResponseEntity<MultaDTO> generar(@Valid @RequestBody GenerarMultaRequest req) {
        return ResponseEntity.ok(multaService.generar(req));
    }

    /**
     * HU19: elegibilidad del jugador (true si NO tiene multas pendientes).
     * También consumido por MS2 antes de programar partidos.
     */
    @GetMapping("/elegibilidad/{cedula}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','DELEGADO') or hasAuthority('SCOPE_internal')")
    public ResponseEntity<Boolean> elegible(@PathVariable String cedula) {
        return ResponseEntity.ok(!multaService.jugadorTieneDeudas(cedula));
    }

    @PostMapping("/{id}/habilitar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<MultaDTO> habilitar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(multaService.habilitar(id, jwt.getClaimAsString("cedula")));
    }
}
