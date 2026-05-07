package terminus.co.edu.ufps.competicion.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.competicion.dto.PerfilResponseDTO;
import terminus.co.edu.ufps.competicion.service.PerfilService;

@RestController
@RequestMapping("/api/supercopa")
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilService perfilService;

    @GetMapping("/mi-perfil")
    @PreAuthorize("hasRole('JUGADOR')")
    public ResponseEntity<PerfilResponseDTO> miPerfil(@AuthenticationPrincipal Jwt jwt) {
        String cedula = jwt.getClaimAsString("cedula");
        if (cedula == null || cedula.isBlank()) {
            throw new RuntimeException("Token sin cedula.");
        }
        String nombre = jwt.getClaimAsString("nombre");
        String email = jwt.getClaimAsString("email");
        return ResponseEntity.ok(perfilService.obtenerPerfil(cedula, nombre, email));
    }
}
