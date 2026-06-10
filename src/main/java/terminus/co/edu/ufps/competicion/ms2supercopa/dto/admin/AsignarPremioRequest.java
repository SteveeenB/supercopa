package terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin;

import lombok.Data;

import java.util.UUID;

@Data
public class AsignarPremioRequest {
    private String cedula;
    private UUID equipoTorneoId;
}
