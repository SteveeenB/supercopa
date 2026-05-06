package terminus.co.edu.ufps.competicion.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.competicion.dto.PerfilJugadorDTO;
import terminus.co.edu.ufps.competicion.service.PerfilJugadorService;

@RestController
@RequestMapping("/api/supercopa/mi-perfil")
@RequiredArgsConstructor
public class PerfilJugadorController {

    private final PerfilJugadorService perfilService;

    /**
     * GET /api/supercopa/mi-perfil
     * Retorna el perfil detallado del jugador autenticado.
     * La cédula se extrae automáticamente del JWT (Pattern B).
     */
    @GetMapping
    @PreAuthorize("hasRole('JUGADOR')")
    public ResponseEntity<PerfilJugadorDTO> obtenerMiPerfil(
            @AuthenticationPrincipal Jwt jwt) {
        
        // Extraemos la cédula del claim personalizado del JWT
        String cedula = jwt.getClaimAsString("cedula");
        
        return ResponseEntity.ok(perfilService.obtenerPerfil(cedula));
    }
}
