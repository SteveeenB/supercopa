package terminus.co.edu.ufps.competicion.dto;

import lombok.*;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TorneoDTO {
    private UUID id;
    private String nombre;
}
