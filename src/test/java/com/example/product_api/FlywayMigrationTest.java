package com.example.product_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// 빈 DB(H2)에 Flyway가 V1 → V2를 차례로 적용해, Entity와 맞는 테이블을 만드는지 검증한다
// (Hibernate는 ddl-auto=validate라서 테이블이 맞지 않으면 컨텍스트 로딩 자체가 실패한다)
@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allMigrationsAreApplied() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void descriptionColumnIsNullableVarchar1000() {
        String sql = """
            SELECT CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE UPPER(TABLE_NAME) = 'PRODUCTS' AND UPPER(COLUMN_NAME) = 'DESCRIPTION'
            """;

        jdbcTemplate.query(sql, rs -> {
            assertThat(rs.getInt("CHARACTER_MAXIMUM_LENGTH")).isEqualTo(1000);
            assertThat(rs.getString("IS_NULLABLE")).isEqualTo("YES");
        });
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS"
                + " WHERE UPPER(TABLE_NAME) = 'PRODUCTS' AND UPPER(COLUMN_NAME) = 'DESCRIPTION'",
            Integer.class)).isEqualTo(1);
    }
}
