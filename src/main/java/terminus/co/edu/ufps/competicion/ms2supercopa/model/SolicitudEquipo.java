package terminus.co.edu.ufps.competicion.ms2supercopa.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Solicitud de un jugador para ingresar al plantel de un equipo en un torneo.
 *
 * Solo conserva la cédula como identificador del solicitante. Cuando se
 * necesita mostrar nombre o correo en la UI del delegado, MS2 los obtiene
 * vía Ms1JugadoresClient (fuente de verdad = padrón MS1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "solicitudes_equipo", schema = "supercopa")
public class SolicitudEquipo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id", nullable = false)
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_torneo_id", nullable = false)
    private EquipoTorneo equipoTorneo;

    @Column(nullable = false, length = 20)
    private String cedula;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @Builder.Default
    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud = LocalDateTime.now();

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;
}
