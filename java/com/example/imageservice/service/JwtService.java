package com.example.imageservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${jwt.jwks-url}")
    private String jwksUrl;
    
    @Value("${jwt.cache-duration:300}")
    private long cacheDurationSeconds;
    
    @Value("${jwt.issuer:}")
    private String expectedIssuer;
    
    @Value("${jwt.audience:}")
    private String expectedAudience;
    
    private static final String JWKS_CACHE_KEY = "jwks:keys";
    private static final String JWT_BLACKLIST_KEY = "jwt:blacklist:";
    
    /**
     * Validate JWT token and return claims
     */
    public Claims validateToken(String token) {
        try {
            // Check if token is blacklisted
            if (isTokenBlacklisted(token)) {
                throw new RuntimeException("Token is blacklisted");
            }
            
            // Decode header to get kid
            String[] chunks = token.split("\\.");
            if (chunks.length != 3) {
                throw new RuntimeException("Invalid JWT format");
            }
            
            String header = new String(Base64.getUrlDecoder().decode(chunks[0]));
            JsonNode headerNode = objectMapper.readTree(header);
            String kid = headerNode.get("kid").asText();
            
            if (kid == null) {
                throw new RuntimeException("Missing kid in JWT header");
            }
            
            // Get public key and verify
            PublicKey publicKey = getPublicKey(kid);
            Claims claims = Jwts.parser()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            // Additional validation
            validateClaims(claims);
            
            return claims;
            
        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            throw new RuntimeException("Invalid JWT token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get public key from JWKS endpoint with caching
     */
    @Cacheable(value = "jwks", key = "#kid", unless = "#result == null")
    public PublicKey getPublicKey(String kid) {
        try {
            // Try Redis cache first
            String cacheKey = JWKS_CACHE_KEY + ":" + kid;
            Object cachedKey = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedKey != null) {
                log.debug("Using cached public key for kid: {}", kid);
                return (PublicKey) cachedKey;
            }
            
            // Fetch JWKS from endpoint
            log.debug("Fetching JWKS from: {}", jwksUrl);
            String jwksResponse = webClient.get()
                    .uri(jwksUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            
            if (jwksResponse == null) {
                throw new RuntimeException("Failed to fetch JWKS");
            }
            
            JsonNode jwks = objectMapper.readTree(jwksResponse);
            JsonNode keys = jwks.get("keys");
            
            if (keys == null || !keys.isArray()) {
                throw new RuntimeException("Invalid JWKS response format");
            }
            
            // Find the key with matching kid
            for (JsonNode key : keys) {
                if (kid.equals(key.get("kid").asText())) {
                    PublicKey publicKey = buildRSAPublicKey(key);
                    
                    // Cache the key
                    redisTemplate.opsForValue().set(cacheKey, publicKey, cacheDurationSeconds, TimeUnit.SECONDS);
                    
                    return publicKey;
                }
            }
            
            throw new RuntimeException("Key not found for kid: " + kid);
            
        } catch (Exception e) {
            log.error("Failed to get public key for kid {}: {}", kid, e.getMessage());
            throw new RuntimeException("Failed to fetch public key", e);
        }
    }
    
    /**
     * Build RSA public key from JWK
     */
    private PublicKey buildRSAPublicKey(JsonNode jwk) throws Exception {
        String kty = jwk.get("kty").asText();
        
        if (!"RSA".equals(kty)) {
            throw new RuntimeException("Unsupported key type: " + kty);
        }
        
        String n = jwk.get("n").asText();
        String e = jwk.get("e").asText();
        
        byte[] nBytes = Base64.getUrlDecoder().decode(n);
        byte[] eBytes = Base64.getUrlDecoder().decode(e);
        
        BigInteger modulus = new BigInteger(1, nBytes);
        BigInteger exponent = new BigInteger(1, eBytes);
        
        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        
        return factory.generatePublic(spec);
    }
    
    /**
     * Validate JWT claims
     */
    private void validateClaims(Claims claims) {
        // Check expiration (already handled by JWT library)
        
        // Check issuer if configured
        if (expectedIssuer != null && !expectedIssuer.isEmpty()) {
            String issuer = claims.getIssuer();
            if (!expectedIssuer.equals(issuer)) {
                throw new RuntimeException("Invalid issuer: " + issuer);
            }
        }
        
        // Check audience if configured
        if (expectedAudience != null && !expectedAudience.isEmpty()) {
            Object audienceObj = claims.getAudience();
            if (audienceObj instanceof String) {
                String audience = (String) audienceObj;
                if (!expectedAudience.equals(audience)) {
                    throw new RuntimeException("Invalid audience: " + audience);
                }
            } else if (audienceObj instanceof java.util.Set) {
                @SuppressWarnings("unchecked")
                java.util.Set<String> audienceSet = (java.util.Set<String>) audienceObj;
                if (!audienceSet.contains(expectedAudience)) {
                    throw new RuntimeException("Invalid audience: " + audienceSet);
                }
            }
        }
    }
    
    /**
     * Extract user ID from claims
     */
    public String getUserId(Claims claims) {
        // Try different claim names based on your JWT structure
        String userId = claims.getSubject();
        if (userId == null || userId.isEmpty()) {
            userId = (String) claims.get("email");
        }
        if (userId == null || userId.isEmpty()) {
            userId = (String) claims.get("user_id");
        }
        if (userId == null || userId.isEmpty()) {
            userId = (String) claims.get("username");
        }
        return userId != null ? userId : "anonymous";
    }
    
    /**
     * Get user email from claims
     */
    public String getUserEmail(Claims claims) {
        String email = (String) claims.get("email");
        if (email == null || email.isEmpty()) {
            email = claims.getSubject();
            if (email != null && email.contains("@")) {
                return email;
            }
        }
        return email;
    }
    
    /**
     * Blacklist a token (for logout functionality)
     */
    public void blacklistToken(String token, long ttlSeconds) {
        String tokenHash = Integer.toString(token.hashCode());
        String key = JWT_BLACKLIST_KEY + tokenHash;
        redisTemplate.opsForValue().set(key, "blacklisted", ttlSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * Check if token is blacklisted
     */
    private boolean isTokenBlacklisted(String token) {
        try {
            String tokenHash = Integer.toString(token.hashCode());
            String key = JWT_BLACKLIST_KEY + tokenHash;
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            log.warn("Failed to check token blacklist: {}", e.getMessage());
            return false;
        }
    }
}