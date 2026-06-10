package terminus.co.edu.ufps.competicion.ms3finanzas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.storage.key}")
    private String storageKey;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    public UploadUrlResult generarUploadUrl(UUID equipoTorneoId) {
        String fileKey = equipoTorneoId + "/" + UUID.randomUUID().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + storageKey);
        headers.set("apikey", storageKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            var response = restTemplate.exchange(
                    supabaseUrl + "/storage/v1/object/upload/sign/" + bucket + "/" + fileKey,
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of(), headers),
                    String.class);

            JsonNode json = objectMapper.readTree(response.getBody());
            String url = json.get("url").asText();
            return new UploadUrlResult(toAbsoluteUrl(url), fileKey);
        } catch (Exception e) {
            log.error("Error generando upload URL para {}: {}", fileKey, e.getMessage());
            throw new RuntimeException("No se pudo generar URL de subida del comprobante: " + e.getMessage(), e);
        }
    }

    public String generarDownloadUrl(String fileKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + storageKey);
        headers.set("apikey", storageKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        int expiresIn = 3600;
        var body = Map.of("expiresIn", expiresIn);

        try {
            var response = restTemplate.exchange(
                    supabaseUrl + "/storage/v1/object/sign/" + bucket + "/" + fileKey,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);

            JsonNode json = objectMapper.readTree(response.getBody());
            String signed = json.has("signedURL")
                    ? json.get("signedURL").asText()
                    : json.get("signedUrl").asText();
            return toAbsoluteUrl(signed);
        } catch (Exception e) {
            log.error("Error generando download URL para {}: {}", fileKey, e.getMessage());
            throw new RuntimeException("No se pudo generar URL de descarga: " + e.getMessage(), e);
        }
    }

    private String toAbsoluteUrl(String url) {
        if (url == null || url.isBlank()) return url;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("/storage/v1")) return supabaseUrl + url;
        if (url.startsWith("/")) return supabaseUrl + "/storage/v1" + url;
        return supabaseUrl + "/storage/v1/" + url;
    }

    public boolean archivoExiste(String fileKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + storageKey);

        try {
            var response = restTemplate.exchange(
                    supabaseUrl + "/storage/v1/object/info/" + bucket + "/" + fileKey,
                    HttpMethod.HEAD,
                    new HttpEntity<>(headers),
                    Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    public record UploadUrlResult(String uploadUrl, String fileKey) {}
}
