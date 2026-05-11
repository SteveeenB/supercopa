package terminus.co.edu.ufps.competicion.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jugador_equipo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cedula", "torneo_id"}))
public class JugadorEquipo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String cedula;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id", nullable = false)
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_torneo_id", nullable = false)
    private EquipoTorneo equipoTorneo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMembresia estado = EstadoMembresia.ACTIVO;

    @Column(name = "numero_camiseta")
    private Integer numeroCamiseta;
}
