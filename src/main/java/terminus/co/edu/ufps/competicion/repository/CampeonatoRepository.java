package terminus.co.edu.ufps.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.competicion.model.Campeonato;
import java.util.Optional;
import java.util.UUID;

public interface CampeonatoRepository extends JpaRepository<Campeonato, UUID> {
    Optional<Campeonato> findByActivoTrue();
}
