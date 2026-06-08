package terminus.co.edu.ufps.competicion.ms2supercopa.dto.admin;

import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddAllRequest {
    private UUID equipoTorneoId;
}
