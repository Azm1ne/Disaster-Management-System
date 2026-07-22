package bd.dms.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * The scenario carries its own baseline constants, and the V4/V5/V6 migrations seed the same
 * world in SQL. This test pins the two together: {@link Scenario#stateAt(long) stateAt(0)} must
 * equal the seeded database exactly, so the map shows the same world before and after the engine
 * first ticks. A drift in either the Java baseline or the seed SQL fails here.
 *
 * <p>Runs against its own fresh in-memory database so no engine mutation from another test can
 * disturb the seed being checked.
 */
@SpringBootTest
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:seedcheck;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class ScenarioSeedConsistencyTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seededCampsEqualScenarioBaseline() {
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT code, population, status FROM camps");
        ScenarioState baseline = Scenario.stateAt(0);

        assertThat(rows).isNotEmpty();
        for (Map<String, Object> row : rows) {
            String code = (String) row.get("code");
            CampState expected = baseline.camp(code);
            assertThat(expected).as("scenario knows seeded camp %s", code).isNotNull();
            assertThat(((Number) row.get("population")).intValue())
                    .as("population of %s", code)
                    .isEqualTo(expected.population());
            assertThat(row.get("status")).as("status of %s", code).isEqualTo(expected.status());
        }
    }

    @Test
    void seededResourceQuantitiesEqualScenarioBaseline() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT c.code AS code, r.resource_type AS type, r.quantity AS quantity "
                        + "FROM camp_resources r JOIN camps c ON c.id = r.camp_id");
        ScenarioState baseline = Scenario.stateAt(0);

        assertThat(rows).isNotEmpty();
        for (Map<String, Object> row : rows) {
            String code = (String) row.get("code");
            String type = (String) row.get("type");
            BigDecimal seeded = (BigDecimal) row.get("quantity");
            BigDecimal expected = baseline.camp(code).resources().get(type);
            assertThat(expected).as("scenario knows resource %s for %s", type, code).isNotNull();
            assertThat(seeded)
                    .as("quantity of %s at %s", type, code)
                    .isEqualByComparingTo(expected);
        }
    }
}
