package terminus.co.edu.ufps.competicion.dto;

import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudRequestDTO {
    private UUID equipoTorneoId;
    private Integer alturaCm;
    private String piernaHabil;
    private String posicion;
}
