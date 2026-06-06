package terminus.co.edu.ufps.competicion.ms2supercopa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.competicion.ms2supercopa.model.Jugador;

public interface JugadorRepository extends JpaRepository<Jugador, String> {
}
