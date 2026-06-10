package terminus.co.edu.ufps.competicion.ms2supercopa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.PremioCatalogo;

import java.util.Optional;
import java.util.UUID;

public interface PremioCatalogoRepository extends JpaRepository<PremioCatalogo, UUID> {
    Optional<PremioCatalogo> findByCodigo(String codigo);
}
