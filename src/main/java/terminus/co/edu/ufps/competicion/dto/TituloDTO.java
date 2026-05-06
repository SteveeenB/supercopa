package terminus.co.edu.ufps.competicion.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TituloDTO {
    private String torneo;
    private String equipo;
    private String puesto;
    private LocalDate fecha;
}
