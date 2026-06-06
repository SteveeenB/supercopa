package terminus.co.edu.ufps.competicion.ms2supercopa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.competicion.ms2supercopa.model.EstadoSolicitud;
import terminus.co.edu.ufps.competicion.ms2supercopa.model.SolicitudEquipo;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SolicitudEquipoRepository extends JpaRepository<SolicitudEquipo, UUID> {

    List<SolicitudEquipo> findByEquipoTorneoIdInAndEstado(List<UUID> equipoTorneoIds, EstadoSolicitud estado);

    List<SolicitudEquipo> findByEquipoTorneoIdIn(List<UUID> equipoTorneoIds);

    boolean existsByCedulaAndTorneoIdAndEstadoIn(String cedula, UUID torneoId, Collection<EstadoSolicitud> estados);

    List<SolicitudEquipo> findByCedula(String cedula);
}
