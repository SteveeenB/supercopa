package terminus.co.edu.ufps.competicion.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class CrearSolicitudDTO {
    private UUID equipoId;
    private UUID campeonatoId;
}
