package terminus.co.edu.ufps.competicion.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.competicion.dto.TorneoDTO;
import terminus.co.edu.ufps.competicion.repository.TorneoRepository;

import java.util.List;

@RestController
@RequestMapping("/api/supercopa/torneos")
@RequiredArgsConstructor
public class TorneoController {

    private final TorneoRepository torneoRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('JUGADOR','DELEGADO')")
    public ResponseEntity<List<TorneoDTO>> listar() {
        var torneos = torneoRepository.findAllByOrderByNombreDesc()
                .stream()
                .map(t -> TorneoDTO.builder().id(t.getId()).nombre(t.getNombre()).build())
                .toList();
        return ResponseEntity.ok(torneos);
    }
}
