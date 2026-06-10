package terminus.co.edu.ufps.competicion.ms2supercopa.controller;

import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin.AsignarPremioRequest;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin.PremioDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.service.PremioService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supercopa/internal")
@RequiredArgsConstructor
public class InternalController {

    private final PremioService premioService;

    @GetMapping("/torneos/{torneoId}/premios")
    public ResponseEntity<List<PremioDTO>> listarPremios(@PathVariable UUID torneoId) {
        return ResponseEntity.ok(premioService.listarPremiosPublicos(torneoId));
    }

    @PostMapping("/torneos/{torneoId}/premios/{torneoPremioId}/asignar")
    public ResponseEntity<?> asignarPremio(
            @PathVariable UUID torneoId,
            @PathVariable UUID torneoPremioId,
            @RequestBody AsignarPremioRequest req) {
        return ResponseEntity.ok(premioService.asignarPremio(torneoId, torneoPremioId, req));
    }
}
