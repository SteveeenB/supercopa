package terminus.co.edu.ufps.competicion.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfilJugadorDTO {
    private String cedula;
    private String nombre;
    private List<HistorialEquipoDTO> equipos;
    private ResumenEstadisticasDTO resumen;
    private List<TituloDTO> titulos;
    private List<PartidoResumenDTO> partidos;
}
