package terminus.co.edu.ufps.competicion.dto;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudRequestDTO {
    private UUID equipoId;
    private UUID torneoId;
}
