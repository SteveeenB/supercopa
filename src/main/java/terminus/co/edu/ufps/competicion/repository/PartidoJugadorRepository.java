package terminus.co.edu.ufps.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.competicion.model.PartidoJugador;
import java.util.List;
import java.util.UUID;

public interface PartidoJugadorRepository extends JpaRepository<PartidoJugador, UUID> {
    List<PartidoJugador> findByJugadorCedulaAndJugoTrue(String cedula);
}
