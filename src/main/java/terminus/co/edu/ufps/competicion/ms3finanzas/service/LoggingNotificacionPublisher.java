package terminus.co.edu.ufps.competicion.ms3finanzas.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class LoggingNotificacionPublisher implements NotificacionPublisher {

    @Override
    public void notificarRojaSuspendida(String cedula, UUID partidoId, int fechasSuspension) {
        log.info("[NOTIF] roja: cedula={} partido={} partidos={}", cedula, partidoId, fechasSuspension);
    }
}
