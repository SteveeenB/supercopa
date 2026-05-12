package terminus.co.edu.ufps.competicion.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 150)
    private String correo;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_jugador", length = 20)
    private RolJugador rolJugador;

    @Column(name = "codigo_universitario", length = 50)
    private String codigoUniversitario;

    @Column
    private Integer semestre;

    @Column(name = "altura_cm")
    private Integer alturaCm;

    @Column(name = "pierna_habil", length = 20)
    private String piernaHabil;

    @Column(length = 30)
    private String posicion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
