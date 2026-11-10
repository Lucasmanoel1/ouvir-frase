package com.lucasmanoel.ouvirfrase.ingestao.painel;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * Defesa extra do painel (que já escuta só em 127.0.0.1): recusa pedidos cujo Host ou Origin não seja a
 * própria máquina. Barra sites de fora tentando falar com o painel pelo navegador (CSRF e DNS rebinding).
 */
@Component
@Profile("painel")
public class SoLocalFilter extends OncePerRequestFilter {

    private static final Set<String> LOCAIS = Set.of("localhost", "127.0.0.1", "[::1]");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!LOCAIS.contains(request.getServerName()) || !origemLocal(request.getHeader("Origin"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "O painel só aceita pedidos deste computador.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean origemLocal(String origem) {
        if (origem == null) {
            return true;
        }
        try {
            String host = URI.create(origem).getHost();
            return host != null && LOCAIS.contains(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
