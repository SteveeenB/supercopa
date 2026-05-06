package terminus.co.edu.ufps.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.competicion.model.Tarjeta;
import terminus.co.edu.ufps.competicion.model.TipoTarjeta;
import java.util.List;
import java.util.UUID;

public interface TarjetaRepository extends JpaRepository<Tarjeta, UUID> {
    List<Tarjeta> findByJugadorCedula(String cedula);
    long countByJugadorCedulaAndTipo(String cedula, TipoTarjeta tipo);
    List<Tarjeta> findByPartidoIdAndJugadorCedula(UUID partidoId, String cedula);
}
