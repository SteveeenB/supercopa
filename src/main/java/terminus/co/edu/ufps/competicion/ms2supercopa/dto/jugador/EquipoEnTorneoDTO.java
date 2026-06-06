package terminus.co.edu.ufps.competicion.ms2supercopa.dto.jugador;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquipoEnTorneoDTO {
    private UUID equipoTorneoId;
    private UUID equipoId;
    private String equipoNombre;
    private String delegadoCedula;
    private String estadoInscripcion;
    private Integer miembros;
}
