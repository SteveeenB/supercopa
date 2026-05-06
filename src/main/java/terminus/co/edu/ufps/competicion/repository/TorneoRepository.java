package terminus.co.edu.ufps.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.competicion.model.Torneo;
import java.util.UUID;

public interface TorneoRepository extends JpaRepository<Torneo, UUID> {
}
