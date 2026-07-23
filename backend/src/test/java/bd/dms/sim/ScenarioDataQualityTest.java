package bd.dms.sim;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScenarioDataQualityTest {

    @Test
    void staleProneCombOnlyRecordsEveryThirdTick() {
        assertThat(Scenario.dataQualityCondition("jam-roumari", "WATER"))
                .isEqualTo(Scenario.DataQualityCondition.STALE_PRONE);
        assertThat(Scenario.shouldRecordObservation("jam-roumari", "WATER", 9)).isTrue();
        assertThat(Scenario.shouldRecordObservation("jam-roumari", "WATER", 10)).isFalse();
        assertThat(Scenario.shouldRecordObservation("jam-roumari", "WATER", 11)).isFalse();
    }

    @Test
    void conflictingProneComboIsFlaggedButRecordsEveryTick() {
        assertThat(Scenario.dataQualityCondition("jam-saghata", "FOOD"))
                .isEqualTo(Scenario.DataQualityCondition.CONFLICTING_PRONE);
        assertThat(Scenario.shouldRecordObservation("jam-saghata", "FOOD", 7)).isTrue();
    }

    @Test
    void everyOtherComboIsNormal() {
        assertThat(Scenario.dataQualityCondition("jam-kurigram-sadar", "WATER"))
                .isEqualTo(Scenario.DataQualityCondition.NORMAL);
    }
}
