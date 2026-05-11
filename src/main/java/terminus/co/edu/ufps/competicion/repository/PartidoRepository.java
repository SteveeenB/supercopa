package terminus.co.edu.ufps.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.competicion.model.Partido;
import java.util.List;
import java.util.UUID;

public interface PartidoRepository extends JpaRepository<Partido, UUID> {
    List<Partido> findByTorneoIdOrderByFechaAsc(UUID torneoId);
    boolean existsByTorneoId(UUID torneoId);
}
