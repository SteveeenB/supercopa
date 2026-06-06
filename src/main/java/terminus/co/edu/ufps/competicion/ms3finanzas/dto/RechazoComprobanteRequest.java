package terminus.co.edu.ufps.competicion.ms3finanzas.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RechazoComprobanteRequest {

    @NotBlank
    private String motivo;
}
