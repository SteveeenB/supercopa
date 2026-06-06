package terminus.co.edu.ufps.competicion.ms2supercopa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.competicion.ms2supercopa.model.EstadoTorneo;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.Torneo;

import java.util.List;
import java.util.UUID;

public interface TorneoRepository extends JpaRepository<Torneo, UUID> {
    List<Torneo> findAllByOrderByEdicionDesc();
    List<Torneo> findByEstadoInOrderByEdicionDesc(List<EstadoTorneo> estados);
}
