package bd.dms.sim;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Every other test runs with the scheduler switched off so no background tick races an
 * assertion — which means nothing else ever builds this bean, and a wiring fault here would only
 * surface when the real application started. This boots it the way production does.
 *
 * <p>It exists because that exact fault happened: two constructors and no {@code @Autowired} left
 * Spring unable to choose, and the app failed at startup while the whole suite stayed green.
 */
@SpringBootTest
@TestPropertySource(properties = "dms.simulation.auto-tick=true")
class SimulationSchedulerBeanTest {

    @Autowired(required = false)
    private SimulationScheduler scheduler;

    @Test
    void theSchedulerIsWiredWhenAutoTickIsEnabled() {
        assertThat(scheduler).as("the wall-clock driver must exist when auto-tick is on").isNotNull();
    }
}
