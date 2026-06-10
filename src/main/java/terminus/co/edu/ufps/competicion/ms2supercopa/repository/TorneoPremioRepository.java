package terminus.co.edu.ufps.competicion.ms2supercopa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.TorneoPremio;

import java.util.List;
import java.util.UUID;

public interface TorneoPremioRepository extends JpaRepository<TorneoPremio, UUID> {
    List<TorneoPremio> findByTorneoId(UUID torneoId);
    boolean existsByTorneoId(UUID torneoId);
}
