package terminus.co.edu.ufps.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import terminus.co.edu.ufps.competicion.model.PartidoJugador;
import java.util.List;
import java.util.UUID;

public interface PartidoJugadorRepository extends JpaRepository<PartidoJugador, UUID> {
    List<PartidoJugador> findByJugadorCedulaAndJugoTrue(String cedula);

    @Query("SELECT COALESCE(SUM(pj.goles), 0) FROM PartidoJugador pj WHERE pj.jugador.cedula = :cedula")
    int sumGolesByCedula(String cedula);

    List<PartidoJugador> findByJugadorCedula(String cedula);
}
