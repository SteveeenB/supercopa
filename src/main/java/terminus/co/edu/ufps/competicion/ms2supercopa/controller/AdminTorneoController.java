package terminus.co.edu.ufps.competicion.ms2supercopa.controller;

import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.RechazoRequestDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.TorneoDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin.CrearTorneoRequest;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin.InscripcionDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin.PartidoAdminDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.Partido;
import terminus.co.edu.ufps.competicion.ms2supercopa.service.FixtureService;
import terminus.co.edu.ufps.competicion.ms2supercopa.service.PartidoAdminService;
import terminus.co.edu.ufps.competicion.ms2supercopa.service.TorneoAdminService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supercopa/admin/torneos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdminTorneoController {

    private final TorneoAdminService torneoAdminService;
    private final FixtureService fixtureService;
    private final PartidoAdminService partidoAdminService;

    @PostMapping
    public ResponseEntity<TorneoDTO> crear(@RequestBody CrearTorneoRequest req) {
        return ResponseEntity.ok(torneoAdminService.crear(req));
    }

    @GetMapping
    public ResponseEntity<List<TorneoDTO>> listar() {
        return ResponseEntity.ok(torneoAdminService.listar());
    }

    @PostMapping("/{torneoId}/publicar")
    public ResponseEntity<TorneoDTO> publicar(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.publicar(torneoId));
    }

    @PostMapping("/{torneoId}/iniciar")
    public ResponseEntity<TorneoDTO> iniciar(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.iniciar(torneoId));
    }

    @GetMapping("/{torneoId}/inscripciones")
    public ResponseEntity<List<InscripcionDTO>> inscripciones(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.listarInscripciones(torneoId));
    }

    @PostMapping("/{torneoId}/inscripciones/{equipoTorneoId}/aprobar")
    public ResponseEntity<InscripcionDTO> aprobarInscripcion(
            @PathVariable UUID torneoId,
            @PathVariable UUID equipoTorneoId,
            @AuthenticationPrincipal Jwt jwt) {
        String admin = jwt.getClaimAsString("cedula");
        return ResponseEntity.ok(torneoAdminService.aprobarInscripcion(torneoId, equipoTorneoId, admin));
    }

    @PostMapping("/{torneoId}/inscripciones/{equipoTorneoId}/rechazar")
    public ResponseEntity<InscripcionDTO> rechazarInscripcion(
            @PathVariable UUID torneoId,
            @PathVariable UUID equipoTorneoId,
            @RequestBody RechazoRequestDTO req) {
        return ResponseEntity.ok(torneoAdminService.rechazarInscripcion(torneoId, equipoTorneoId, req.getMotivo()));
    }

    @PostMapping("/{torneoId}/fixture")
    public ResponseEntity<List<PartidoAdminDTO>> generarFixture(@PathVariable UUID torneoId) {
        List<Partido> creados = fixtureService.generar(torneoId);
        return ResponseEntity.ok(creados.stream().map(partidoAdminService::toPartidoDTO).toList());
    }

    @GetMapping("/{torneoId}/partidos")
    public ResponseEntity<List<PartidoAdminDTO>> partidos(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(partidoAdminService.listarPorTorneo(torneoId));
    }
}
