package terminus.co.edu.ufps.competicion.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialEquipoDTO {
    private UUID id;
    private String nombre;
    private LocalDate desde;
    private LocalDate hasta;
}
