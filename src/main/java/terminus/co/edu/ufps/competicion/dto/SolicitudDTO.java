package terminus.co.edu.ufps.competicion.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudDTO {
    private UUID id;
    private String cedula;
    private String nombre;
    private String correo;
    private UUID equipoId;
    private String equipoNombre;
    private UUID campeonatoId;
    private String campeonatoNombre;
    private String estado;
    private LocalDateTime fechaSolicitud;
    private LocalDateTime fechaResolucion;
    private String motivoRechazo;
}
