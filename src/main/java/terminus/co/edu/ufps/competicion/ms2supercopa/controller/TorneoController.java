package terminus.co.edu.ufps.competicion.ms2supercopa.controller;

import lombok.RequiredArgsConstructor;
import terminus.co.edu.ufps.competicion.ms2supercopa.dto.TorneoDTO;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.EstadoTorneo;
import terminus.co.edu.ufps.competicion.ms2supercopa.repository.TorneoRepository;
import terminus.co.edu.ufps.competicion.ms2supercopa.service.TorneoAdminService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/supercopa/torneos")
@RequiredArgsConstructor
public class TorneoController {

    private final TorneoRepository torneoRepository;
    private final TorneoAdminService torneoAdminService;

    @GetMapping
    @PreAuthorize("hasAnyRole('JUGADOR','DELEGADO','ADMINISTRADOR')")
    public ResponseEntity<List<TorneoDTO>> listar() {
        var visibles = torneoRepository.findByEstadoInOrderByEdicionDesc(
                List.of(EstadoTorneo.PUBLICADO, EstadoTorneo.EN_CURSO, EstadoTorneo.FINALIZADO));
        return ResponseEntity.ok(visibles.stream().map(torneoAdminService::toDTO).toList());
    }
}
