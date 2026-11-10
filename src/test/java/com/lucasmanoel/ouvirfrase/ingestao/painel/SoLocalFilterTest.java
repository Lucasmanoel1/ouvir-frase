package com.lucasmanoel.ouvirfrase.ingestao.painel;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class SoLocalFilterTest {

    private final SoLocalFilter filtro = new SoLocalFilter();

    private MockHttpServletResponse chamar(String host, String origem) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/painel/ingestao");
        request.setServerName(host);
        if (origem != null) {
            request.addHeader("Origin", origem);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void deveAceitarPedidoDoProprioComputador() throws Exception {
        assertThat(chamar("localhost", null).getStatus()).isEqualTo(200);
        assertThat(chamar("127.0.0.1", "http://localhost:8081").getStatus()).isEqualTo(200);
    }

    @Test
    void deveRecusarHostDeFora() throws Exception {
        assertThat(chamar("meu-site.com", null).getStatus()).isEqualTo(403);
    }

    @Test
    void deveRecusarOrigemDeOutroSite() throws Exception {
        assertThat(chamar("localhost", "https://site-malicioso.com").getStatus()).isEqualTo(403);
        assertThat(chamar("localhost", "null").getStatus()).isEqualTo(403);
    }
}
