package terminus.co.edu.ufps.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.competicion.model.Equipo;
import java.util.UUID;

public interface EquipoRepository extends JpaRepository<Equipo, UUID> {
}
