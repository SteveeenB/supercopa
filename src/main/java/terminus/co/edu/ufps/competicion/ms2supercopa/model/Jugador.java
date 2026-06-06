package terminus.co.edu.ufps.competicion.ms2supercopa.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Perfil deportivo + cosmético del jugador en MS2.
 *
 * Los datos académicos oficiales (nombre legal, correo, rol académico,
 * código universitario, semestre) viven en MS1 (padrón). MS2 consulta MS1
 * vía Ms1JugadoresClient cuando los necesita y los snapshotea en
 * {@link JugadorEquipo} al momento de inscripción para reportes históricos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jugadores")
public class Jugador {

    @Id
    @Column(length = 20)
    private String cedula;

    @Column(name = "altura_cm")
    private Integer alturaCm;

    @Column(name = "pierna_habil", length = 20)
    private String piernaHabil;

    @Column(length = 30)
    private String posicion;

    @Column(length = 60)
    private String apodo;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
