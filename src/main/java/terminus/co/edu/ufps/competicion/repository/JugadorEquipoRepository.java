package terminus.co.edu.ufps.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.competicion.model.JugadorEquipo;
import java.util.Optional;
import java.util.UUID;

public interface JugadorEquipoRepository extends JpaRepository<JugadorEquipo, UUID> {
    Optional<JugadorEquipo> findByCedulaAndCampeonatoId(String cedula, UUID campeonatoId);
    boolean existsByCedulaAndCampeonatoId(String cedula, UUID campeonatoId);
}
