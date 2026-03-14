package com.psh.diet_planner.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtils {

    @Value("${jwt.secret:dietPlannerSecretKey}")
    private String secretKey;

    // 24 小时
    private static final long EXPIRATION_TIME = 86400000L;

    public String generateToken(Long userId, String username, String role) {
        return Jwts.builder()
            .setSubject(username)
            .addClaims(Map.of(
                "uid", userId,
                "role", role
            ))
            .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
            .signWith(SignatureAlgorithm.HS512, secretKey)
            .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
            .setSigningKey(secretKey)
            .parseClaimsJws(token)
            .getBody();
    }

    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}