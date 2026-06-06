package terminus.co.edu.ufps.competicion.ms2supercopa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.competicion.ms2supercopa.model.Equipo;

import java.util.Optional;
import java.util.UUID;

public interface EquipoRepository extends JpaRepository<Equipo, UUID> {
    Optional<Equipo> findByDelegadoCedula(String delegadoCedula);
    boolean existsByDelegadoCedula(String delegadoCedula);
}
