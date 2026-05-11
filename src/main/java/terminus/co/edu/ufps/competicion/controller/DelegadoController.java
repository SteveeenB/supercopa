package terminus.co.edu.ufps.competicion.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import terminus.co.edu.ufps.competicion.dto.EquipoDTO;
import terminus.co.edu.ufps.competicion.dto.admin.InscripcionDTO;
import terminus.co.edu.ufps.competicion.dto.delegado.CrearEquipoRequest;
import terminus.co.edu.ufps.competicion.dto.delegado.MiembroEquipoDTO;
import terminus.co.edu.ufps.competicion.dto.delegado.TorneoDisponibleDTO;
import terminus.co.edu.ufps.competicion.service.DelegadoService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supercopa/delegado")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DELEGADO')")
public class DelegadoController {

    private final DelegadoService delegadoService;

    @PostMapping("/equipos")
    public ResponseEntity<EquipoDTO> crearEquipo(
            @RequestBody CrearEquipoRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.crearEquipo(jwt.getClaimAsString("cedula"), req));
    }

    @GetMapping("/equipos/mi-equipo")
    public ResponseEntity<EquipoDTO> miEquipo(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.miEquipo(jwt.getClaimAsString("cedula")));
    }

    @GetMapping("/torneos/disponibles")
    public ResponseEntity<List<TorneoDisponibleDTO>> torneos(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.torneosDisponibles(jwt.getClaimAsString("cedula")));
    }

    @PostMapping("/torneos/{torneoId}/inscribir")
    public ResponseEntity<InscripcionDTO> inscribir(
            @PathVariable UUID torneoId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.inscribir(jwt.getClaimAsString("cedula"), torneoId));
    }

    @GetMapping("/inscripciones/mias")
    public ResponseEntity<List<InscripcionDTO>> misInscripciones(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.misInscripciones(jwt.getClaimAsString("cedula")));
    }

    @PostMapping("/inscripciones/{equipoTorneoId}/pagar")
    public ResponseEntity<InscripcionDTO> pagar(
            @PathVariable UUID equipoTorneoId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.pagar(jwt.getClaimAsString("cedula"), equipoTorneoId));
    }

    @GetMapping("/equipo-torneo/{equipoTorneoId}/miembros")
    public ResponseEntity<List<MiembroEquipoDTO>> miembros(
            @PathVariable UUID equipoTorneoId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(delegadoService.miembros(jwt.getClaimAsString("cedula"), equipoTorneoId));
    }
}
