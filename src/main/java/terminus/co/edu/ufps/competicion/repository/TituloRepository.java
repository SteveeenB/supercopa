package terminus.co.edu.ufps.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import terminus.co.edu.ufps.competicion.model.Titulo;
import java.util.List;
import java.util.UUID;

public interface TituloRepository extends JpaRepository<Titulo, UUID> {
    
    /**
     * Busca los títulos de un jugador basado en su historial de equipos.
     * Un jugador tiene un título si pertenecía al equipo (JugadorEquipo) 
     * en el campeonato del título.
     */
    @Query("SELECT t FROM Titulo t " +
           "JOIN JugadorEquipo je ON t.equipo.id = je.equipo.id AND t.torneo.id = je.campeonato.id " +
           "WHERE je.cedula = :cedula")
    List<Titulo> findByJugadorCedula(String cedula);
}
