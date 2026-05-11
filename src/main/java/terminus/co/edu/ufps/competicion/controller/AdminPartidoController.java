package terminus.co.edu.ufps.competicion.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import terminus.co.edu.ufps.competicion.dto.admin.EventoDTO;
import terminus.co.edu.ufps.competicion.dto.admin.EventoRequest;
import terminus.co.edu.ufps.competicion.dto.admin.PartidoAdminDTO;
import terminus.co.edu.ufps.competicion.service.PartidoAdminService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supercopa/admin/partidos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdminPartidoController {

    private final PartidoAdminService partidoAdminService;

    @GetMapping("/{partidoId}/eventos")
    public ResponseEntity<List<EventoDTO>> listar(@PathVariable UUID partidoId) {
        return ResponseEntity.ok(partidoAdminService.listarEventos(partidoId));
    }

    @PostMapping("/{partidoId}/eventos")
    public ResponseEntity<EventoDTO> crear(@PathVariable UUID partidoId, @RequestBody EventoRequest req) {
        return ResponseEntity.ok(partidoAdminService.crearEvento(partidoId, req));
    }

    @DeleteMapping("/{partidoId}/eventos/{eventoId}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID partidoId, @PathVariable UUID eventoId) {
        partidoAdminService.eliminarEvento(partidoId, eventoId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{partidoId}/cerrar")
    public ResponseEntity<PartidoAdminDTO> cerrar(@PathVariable UUID partidoId) {
        return ResponseEntity.ok(partidoAdminService.cerrarPartido(partidoId));
    }
}
