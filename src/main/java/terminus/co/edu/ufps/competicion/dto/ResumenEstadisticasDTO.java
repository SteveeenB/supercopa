package terminus.co.edu.ufps.competicion.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumenEstadisticasDTO {
    private long partidosJugados;
    private int goles;
    private TarjetasResumenDTO tarjetas;
    private int titulos;
}
