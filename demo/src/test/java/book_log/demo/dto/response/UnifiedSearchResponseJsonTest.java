package book_log.demo.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import book_log.demo.domain.Category;

/**
 * 프론트는 검색 응답의 "isSaved", "externalId" 키를 그대로 읽는다.
 * Boolean 래퍼 + Lombok getter(getIsSaved) 조합이 "saved"로 바뀌지 않는지 서버 없이 확인한다.
 */
class UnifiedSearchResponseJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("직렬화 시 키 이름이 isSaved, externalId로 나간다 (saved가 아님)")
    void serializesIsSavedAndExternalIdKeys() throws Exception {
        UnifiedSearchResponse response = UnifiedSearchResponse.builder()
                .externalId("9788996094012")
                .title("테스트 책")
                .category(Category.BOOK)
                .isSaved(true)
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.has("isSaved")).isTrue();
        assertThat(json.get("isSaved").asBoolean()).isTrue();
        assertThat(json.has("saved")).isFalse();
        assertThat(json.get("externalId").asText()).isEqualTo("9788996094012");
    }
}
