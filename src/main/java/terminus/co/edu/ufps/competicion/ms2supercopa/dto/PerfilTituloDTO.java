package terminus.co.edu.ufps.competicion.ms2supercopa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfilTituloDTO {
    private String torneo;
    private String equipo;
    private String puesto;
}
