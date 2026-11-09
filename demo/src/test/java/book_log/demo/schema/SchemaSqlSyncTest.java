package book_log.demo.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 25-f-1: 운영 DB 대조용 SQL(91-check-all-tables.sql)의 "기대 스키마" 목록이 엔티티와 어긋나지 않게 지킨다.
 *
 * 임베디드 H2(test 프로파일, ddl-auto=create-drop)가 엔티티로 만든 테이블/컬럼 이름을 SQL 파일의 VALUES 목록과 비교한다.
 * 이름만 비교한다(타입은 H2와 PostgreSQL이 달라 SQL 파일 머리말의 문서로 관리). 실제 DB와 Testcontainers는 쓰지 않는다.
 * 엔티티를 바꾸고 SQL을 안 고치면(또는 반대로) 이 테스트가 실패한다.
 */
@DataJpaTest
@ActiveProfiles("test")
class SchemaSqlSyncTest {

    // Gradle 테스트의 작업 디렉터리는 demo/ 이다
    private static final Path SQL_FILE = Path.of("..", ".claude", "tasks", "22-perf", "91-check-all-tables.sql");

    private static final Pattern COLUMN_ROW = Pattern.compile("\\('([a-z_]+)',\\s*'([a-z_]+)',\\s*'([a-z ]+)'\\)");
    private static final Pattern INDEX_ROW = Pattern.compile("\\('([a-z_]+)',\\s*'([a-z_]+)',\\s*(true|false),\\s*'[^']*'\\)");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("SQL의 EXPECTED_COLUMNS(테이블.컬럼)가 엔티티로 만든 H2 스키마와 정확히 일치한다")
    void expectedColumnsMatchEntities() throws IOException {
        Set<String> inSql = new TreeSet<>();
        for (Matcher m = COLUMN_ROW.matcher(block("EXPECTED_COLUMNS")); m.find();) {
            inSql.add(m.group(1) + "." + m.group(2));
        }
        Set<String> inEntities = new TreeSet<>(jdbc.queryForList(
                "SELECT lower(table_name) || '.' || lower(column_name) FROM information_schema.columns "
                        + "WHERE upper(table_schema) = 'PUBLIC'", String.class));

        assertThat(inSql).as("SQL 파일에서 파싱한 기대 컬럼이 비어 있으면 안 된다").isNotEmpty();
        assertThat(inEntities).as("엔티티로 만든 H2 스키마가 비어 있으면 테스트가 헛돈 것").isNotEmpty();

        Set<String> onlyInEntities = new TreeSet<>(inEntities);
        onlyInEntities.removeAll(inSql);
        Set<String> onlyInSql = new TreeSet<>(inSql);
        onlyInSql.removeAll(inEntities);
        assertThat(onlyInEntities).as("엔티티에는 있는데 91-check-all-tables.sql EXPECTED_COLUMNS에 없는 컬럼").isEmpty();
        assertThat(onlyInSql).as("EXPECTED_COLUMNS에는 있는데 엔티티에 없는 컬럼").isEmpty();
    }

    @Test
    @DisplayName("SQL의 EXPECTED_INDEXES(코드가 의존하는 이름 있는 제약/인덱스)가 엔티티 스키마에 같은 이름으로 존재한다")
    void expectedIndexesExistInEntitySchema() throws IOException {
        Set<String> names = new TreeSet<>();
        for (Matcher m = INDEX_ROW.matcher(block("EXPECTED_INDEXES")); m.find();) {
            names.add(m.group(1) + "@" + m.group(2));
        }
        assertThat(names).isNotEmpty();

        Set<String> inSchema = new TreeSet<>(jdbc.queryForList(
                "SELECT lower(index_name) || '@' || lower(table_name) FROM information_schema.indexes "
                        + "WHERE upper(table_schema) = 'PUBLIC'", String.class));
        inSchema.addAll(jdbc.queryForList(
                "SELECT lower(constraint_name) || '@' || lower(table_name) FROM information_schema.table_constraints "
                        + "WHERE upper(table_schema) = 'PUBLIC'", String.class));

        assertThat(inSchema).as("엔티티(@Table)로 만든 H2 스키마의 제약/인덱스").containsAll(names);
    }

    @Test
    @DisplayName("SQL 파일은 SELECT만 담는다 (DDL/DML 키워드 없음)")
    void sqlFileIsReadOnly() throws IOException {
        List<String> code = Files.readAllLines(SQL_FILE).stream()
                .map(l -> l.contains("--") ? l.substring(0, l.indexOf("--")) : l)
                .map(l -> l.replaceAll("'[^']*'", "''")) // 문자열 리터럴 안의 단어(예: 'CREATE UNIQUE INDEX%')는 제외
                .map(String::toLowerCase)
                .toList();
        for (String forbidden : List.of("insert ", "update ", "delete ", "drop ", "alter ", "create ", "truncate ", "grant ")) {
            assertThat(code).as("금지된 키워드: " + forbidden).noneMatch(l -> l.contains(forbidden));
        }
    }

    private static String block(String name) throws IOException {
        String all = Files.readString(SQL_FILE);
        int start = all.indexOf("-- BEGIN " + name);
        int end = all.indexOf("-- END " + name);
        assertThat(start).as(name + " 시작 마커").isGreaterThanOrEqualTo(0);
        assertThat(end).as(name + " 끝 마커").isGreaterThan(start);
        return all.substring(start, end);
    }
}
