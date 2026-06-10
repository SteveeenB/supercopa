package terminus.co.edu.ufps.competicion.ms3finanzas.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class SolicitudUploadUrlRequest {
    @NotNull
    private UUID equipoTorneoId;
}
