package terminus.co.edu.ufps.competicion.ms3finanzas.service;

import java.util.UUID;

public interface NotificacionPublisher {

    void notificarRojaSuspendida(String cedula, UUID partidoId, int fechasSuspension);
}
