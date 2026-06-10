package terminus.co.edu.ufps.competicion.ms2supercopa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.MultaConfig;

import java.util.Optional;
import java.util.UUID;

public interface MultaConfigRepository extends JpaRepository<MultaConfig, UUID> {
    Optional<MultaConfig> findByTorneoId(UUID torneoId);
}
