package terminus.co.edu.ufps.competicion.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TarjetasResumenDTO {
    private long amarillas;
    private long azules;
    private long rojas;
}
