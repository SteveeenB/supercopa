package terminus.co.edu.ufps.competicion.ms2supercopa.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia MS1 (Identidad) para consultar el padrón oficial.
 *
 * Propaga el JWT del usuario actual del SecurityContext, así MS1 valida
 * permisos (DELEGADO o ADMINISTRADOR pueden leer datos del padrón).
 *
 * Cache local en memoria con TTL corto para evitar llamadas repetidas
 * durante el mismo flujo (ej. listar plantel con 12 jugadores = 1 llamada
 * por cédula, sin cache se traduciría a 12 round-trips a MS1).
 */
@Slf4j
@Component
public class Ms1JugadoresClient {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RestClient client = RestClient.create();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Value("${ms1.base-url:http://localhost:8081}")
    private String ms1BaseUrl;

    /**
     * Busca un jugador en el padrón MS1 por cédula.
     * @return Optional vacío si MS1 responde 404 o si la cédula es null/blank.
     */
    public Optional<JugadorPadronDTO> getJugadorPorCedula(String cedula) {
        if (cedula == null || cedula.isBlank()) return Optional.empty();
        String key = cedula.trim();

        var cached = cache.get(key);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return Optional.ofNullable(cached.value);
        }

        String jwt = obtenerJwtActual();
        if (jwt == null) {
            log.warn("[MS1-CLIENT] Sin JWT en contexto, no se puede consultar padrón para cédula={}", key);
            return Optional.empty();
        }

        try {
            JugadorPadronDTO body = client.get()
                    .uri(ms1BaseUrl + "/api/jugadores/" + key)
                    .header("Authorization", "Bearer " + jwt)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        // 404 = no está en padrón. Otros 4xx = falla de auth.
                        if (res.getStatusCode().value() != 404) {
                            log.warn("[MS1-CLIENT] Error {} al consultar cédula={}",
                                    res.getStatusCode().value(), key);
                        }
                    })
                    .body(JugadorPadronDTO.class);

            cache.put(key, new CacheEntry(body, Instant.now().plus(CACHE_TTL)));
            return Optional.ofNullable(body);
        } catch (Exception e) {
            log.warn("[MS1-CLIENT] Falla consultando MS1 para cédula={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Invalida la entrada de cache para una cédula (útil si el padrón se actualizó).
     */
    public void invalidar(String cedula) {
        if (cedula != null) cache.remove(cedula.trim());
    }

    private String obtenerJwtActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken().getTokenValue();
        }
        return null;
    }

    private record CacheEntry(JugadorPadronDTO value, Instant expiresAt) {}
}
