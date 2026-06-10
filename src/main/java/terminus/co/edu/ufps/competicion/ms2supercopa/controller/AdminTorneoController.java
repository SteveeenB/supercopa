package terminus.co.edu.ufps.competicion.ms2supercopa.controller;

import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.RechazoRequestDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.TorneoDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin.CrearTorneoRequest;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin.GuardarConfigRequest;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin.InscripcionDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin.PartidoAdminDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin.PosicionGrupoDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin.TorneoConfigDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.Partido;
import terminus.co.edu.ufps.competicion.ms2supercopa.service.ClasificacionService;
import terminus.co.edu.ufps.competicion.ms2supercopa.service.FixtureService;
import terminus.co.edu.ufps.competicion.ms2supercopa.service.PartidoAdminService;
import terminus.co.edu.ufps.competicion.ms2supercopa.service.TorneoAdminService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/supercopa/admin/torneos")
@RequiredArgsConstructor
public class AdminTorneoController {

    private final TorneoAdminService torneoAdminService;
    private final FixtureService fixtureService;
    private final PartidoAdminService partidoAdminService;
    private final ClasificacionService clasificacionService;

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TorneoDTO> crear(@RequestBody CrearTorneoRequest req) {
        return ResponseEntity.ok(torneoAdminService.crear(req));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<List<TorneoDTO>> listar() {
        return ResponseEntity.ok(torneoAdminService.listar());
    }

    @PostMapping("/{torneoId}/publicar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TorneoDTO> publicar(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.publicar(torneoId));
    }

    @PostMapping("/{torneoId}/iniciar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TorneoDTO> iniciar(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.iniciar(torneoId));
    }

    // ── Configuracion de formato ──────────────────────────────────

    @GetMapping("/{torneoId}/configuracion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TorneoConfigDTO> obtenerConfiguracion(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.obtenerConfiguracion(torneoId));
    }

    @PutMapping("/{torneoId}/configuracion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TorneoConfigDTO> guardarConfiguracion(
            @PathVariable UUID torneoId,
            @RequestBody GuardarConfigRequest req) {
        return ResponseEntity.ok(torneoAdminService.guardarConfiguracion(torneoId, req));
    }

    // ── Inscripciones ─────────────────────────────────────────────

    @GetMapping("/{torneoId}/inscripciones")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<InscripcionDTO>> inscripciones(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(torneoAdminService.listarInscripciones(torneoId));
    }

    @PostMapping("/{torneoId}/inscripciones/{equipoTorneoId}/aprobar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<InscripcionDTO> aprobarInscripcion(
            @PathVariable UUID torneoId,
            @PathVariable UUID equipoTorneoId,
            @AuthenticationPrincipal Jwt jwt) {
        String admin = jwt.getClaimAsString("cedula");
        return ResponseEntity.ok(torneoAdminService.aprobarInscripcion(torneoId, equipoTorneoId, admin));
    }

    @PostMapping("/{torneoId}/inscripciones/{equipoTorneoId}/rechazar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<InscripcionDTO> rechazarInscripcion(
            @PathVariable UUID torneoId,
            @PathVariable UUID equipoTorneoId,
            @RequestBody RechazoRequestDTO req) {
        return ResponseEntity.ok(torneoAdminService.rechazarInscripcion(torneoId, equipoTorneoId, req.getMotivo()));
    }

    @PostMapping("/{torneoId}/inscripciones/{equipoTorneoId}/habilitar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<InscripcionDTO> habilitarInscripcion(
            @PathVariable UUID torneoId,
            @PathVariable UUID equipoTorneoId) {
        return ResponseEntity.ok(torneoAdminService.habilitarInscripcion(torneoId, equipoTorneoId));
    }

    @PostMapping("/{torneoId}/inscripciones/{equipoTorneoId}/expulsar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<InscripcionDTO> expulsarEquipo(
            @PathVariable UUID torneoId,
            @PathVariable UUID equipoTorneoId,
            @RequestBody RechazoRequestDTO req,
            @AuthenticationPrincipal Jwt jwt) {
        String admin = jwt.getClaimAsString("cedula");
        return ResponseEntity.ok(torneoAdminService.expulsarEquipo(torneoId, equipoTorneoId, req.getMotivo(), admin));
    }

    // ── Fixture ──────────────────────────────────────────────────

    @PostMapping("/{torneoId}/fixture")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<PartidoAdminDTO>> generarFixture(@PathVariable UUID torneoId) {
        List<Partido> creados = fixtureService.generar(torneoId);
        return ResponseEntity.ok(creados.stream().map(partidoAdminService::toPartidoDTO).toList());
    }

    @DeleteMapping("/{torneoId}/fixture")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> borrarFixture(@PathVariable UUID torneoId) {
        torneoAdminService.borrarFixture(torneoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{torneoId}/partidos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<List<PartidoAdminDTO>> partidos(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(partidoAdminService.listarPorTorneo(torneoId));
    }

    @GetMapping("/{torneoId}/clasificacion")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','ARBITRO')")
    public ResponseEntity<Map<String, List<PosicionGrupoDTO>>> clasificacion(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(clasificacionService.calcularPorGrupo(torneoId));
    }
}
