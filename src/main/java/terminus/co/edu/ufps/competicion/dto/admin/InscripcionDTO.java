package terminus.co.edu.ufps.competicion.dto.admin;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InscripcionDTO {
    private UUID id;
    private UUID torneoId;
    private String torneoNombre;
    private UUID equipoId;
    private String equipoNombre;
    private String delegadoCedula;
    private String estadoInscripcion;
    private String aprobadoPor;
    private String motivoRechazo;
    private LocalDateTime fechaInscripcion;
}
