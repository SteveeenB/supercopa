package terminus.co.edu.ufps.competicion.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartidoResumenDTO {
    private UUID id;
    private LocalDateTime fecha;
    private String equipo;
    private String rival;
    private int goles;
    private List<String> tarjetas;
}
