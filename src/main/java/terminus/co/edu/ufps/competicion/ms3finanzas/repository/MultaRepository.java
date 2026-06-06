package terminus.co.edu.ufps.competicion.ms3finanzas.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.competicion.ms3finanzas.model.EstadoMulta;
import terminus.co.edu.ufps.competicion.ms3finanzas.model.Multa;

public interface MultaRepository extends JpaRepository<Multa, UUID> {

    List<Multa> findByCedula(String cedula);

    List<Multa> findByCedulaAndEstado(String cedula, EstadoMulta estado);

    List<Multa> findByTorneoIdAndEstado(UUID torneoId, EstadoMulta estado);

    List<Multa> findByPartidoId(UUID partidoId);

    long countByCedulaAndEstado(String cedula, EstadoMulta estado);
}
