package terminus.co.edu.ufps.competicion.ms2supercopa.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfilResponseDTO {
    private String cedula;
    private String nombre;
    private List<PerfilEquipoDTO> equipos;
    private PerfilResumenDTO resumen;
    private List<PerfilTituloDTO> titulos;
    private List<PerfilPartidoDTO> partidos;
}
