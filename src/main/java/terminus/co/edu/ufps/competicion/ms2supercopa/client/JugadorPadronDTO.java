package terminus.co.edu.ufps.competicion.ms2supercopa.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vista local del jugador del padrón MS1.
 * Mapea la respuesta JSON de GET /api/jugadores/{cedula} de MS1.
 * Solo campos relevantes para MS2 (snapshots académicos + display).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JugadorPadronDTO {

    private String cedula;
    private String nombre;
    private String correo;
    private String rolJugador;
    private String codigoUniversitario;
    private Integer semestre;
    private Boolean activo;
}
