package terminus.co.edu.ufps.competicion.ms2supercopa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.PremioAsignado;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PremioAsignadoRepository extends JpaRepository<PremioAsignado, UUID> {
    Optional<PremioAsignado> findByTorneoIdAndPremioId(UUID torneoId, UUID premioId);
    List<PremioAsignado> findByTorneoId(UUID torneoId);
    boolean existsByPremioId(UUID torneoPremioId);
}
