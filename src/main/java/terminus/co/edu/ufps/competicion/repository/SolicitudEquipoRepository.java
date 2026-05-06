package terminus.co.edu.ufps.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.competicion.model.EstadoSolicitud;
import terminus.co.edu.ufps.competicion.model.SolicitudEquipo;
import java.util.List;
import java.util.UUID;

public interface SolicitudEquipoRepository extends JpaRepository<SolicitudEquipo, UUID> {

    List<SolicitudEquipo> findByEquipoIdInAndEstado(List<UUID> equipoIds, EstadoSolicitud estado);

    List<SolicitudEquipo> findByEquipoIdIn(List<UUID> equipoIds);

    boolean existsByCedulaAndCampeonatoId(String cedula, UUID campeonatoId);
}
