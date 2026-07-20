package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the Flyway baseline actually ran against the (H2) test database rather than the
 * context merely starting. If migrations did not apply, app_info would not exist and this
 * query errors — which is exactly the "fail fast if migration fails" guarantee, exercised.
 */
@SpringBootTest
class FlywayBaselineTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void baselineMigrationApplied() {
        String name = jdbc.queryForObject(
                "SELECT name FROM app_info WHERE id = 1", String.class);

        assertThat(name).isEqualTo("disaster-management-system");
    }
}
