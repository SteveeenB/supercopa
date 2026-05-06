package terminus.co.edu.ufps.competicion.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.competicion.model.Equipo;
import terminus.co.edu.ufps.competicion.repository.EquipoRepository;

import java.util.List;

@RestController
@RequestMapping("/api/supercopa/equipos")
@RequiredArgsConstructor
public class EquipoController {

    private final EquipoRepository equipoRepo;

    /**
     * GET /api/supercopa/equipos
     * Retorna la lista de todos los equipos registrados (los 13 seeded).
     */
    @GetMapping
    public ResponseEntity<List<Equipo>> listarEquipos() {
        return ResponseEntity.ok(equipoRepo.findAll());
    }
}
