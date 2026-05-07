package terminus.co.edu.ufps.competicion.controller;

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.competicion.dto.EquipoDTO;
import terminus.co.edu.ufps.competicion.model.Equipo;
import terminus.co.edu.ufps.competicion.repository.EquipoRepository;

@RestController
@RequestMapping("/api/supercopa/equipos")
@RequiredArgsConstructor
public class EquipoController {

    private final EquipoRepository equipoRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('JUGADOR','DELEGADO')")
    public ResponseEntity<List<EquipoDTO>> listar() {
        var equipos = equipoRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Equipo::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(equipo -> EquipoDTO.builder()
                        .id(equipo.getId())
                        .nombre(equipo.getNombre())
                        .delegadoCedula(equipo.getDelegadoCedula())
                        .build())
                .toList();
        return ResponseEntity.ok(equipos);
    }
}
