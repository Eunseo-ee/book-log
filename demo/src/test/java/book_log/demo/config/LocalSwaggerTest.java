package book_log.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// prod가 아닌 프로파일(test)에서는 Swagger가 기존처럼 인증 없이 동작해야 한다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalSwaggerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationContext context;
    @Autowired
    private WebEndpointsSupplier webEndpoints;

    @Test
    @DisplayName("비 prod: springdoc 빈이 있고 /v3/api-docs가 인증 없이 200이다")
    void apiDocsWorks() throws Exception {
        assertThat(context.getBeanNamesForType(org.springdoc.core.service.OpenAPIService.class)).isNotEmpty();

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths").exists());
    }

    @Test
    @DisplayName("비 prod: Swagger UI 진입 경로가 인증에 막히지 않고 UI 페이지가 200이다")
    void swaggerUiWorks() throws Exception {
        int entry = mockMvc.perform(get("/swagger-ui.html")).andReturn().getResponse().getStatus();
        assertThat(entry).isIn(200, 302);

        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("비 prod에서도 actuator 노출은 health 하나로 고정이다")
    void onlyHealthIsExposed() {
        List<String> ids = webEndpoints.getEndpoints().stream().map(e -> e.getEndpointId().toString()).toList();
        assertThat(ids).containsExactly("health");
    }
}
