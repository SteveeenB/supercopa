package terminus.co.edu.ufps.competicion.ms3finanzas.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HabilitarExcepcionRequest {
    @NotBlank
    private String motivo;
}
