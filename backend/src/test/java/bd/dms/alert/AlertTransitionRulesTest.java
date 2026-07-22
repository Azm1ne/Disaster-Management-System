// backend/src/test/java/bd/dms/alert/AlertTransitionRulesTest.java
package bd.dms.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AlertTransitionRulesTest {

    @Test
    void newMayOnlyMoveToAcknowledged() {
        assertThat(AlertTransitionRules.isLegal(AlertStatus.NEW, AlertStatus.ACKNOWLEDGED)).isTrue();
        assertThat(AlertTransitionRules.isLegal(AlertStatus.NEW, AlertStatus.IN_PROGRESS)).isFalse();
        assertThat(AlertTransitionRules.isLegal(AlertStatus.NEW, AlertStatus.CLOSED)).isFalse();
    }

    @Test
    void inProgressMayResolveOrEscalate() {
        assertThat(AlertTransitionRules.isLegal(AlertStatus.IN_PROGRESS, AlertStatus.RESOLVED)).isTrue();
        assertThat(AlertTransitionRules.isLegal(AlertStatus.IN_PROGRESS, AlertStatus.ESCALATED)).isTrue();
    }

    @Test
    void escalatedMayReworkOrClose() {
        assertThat(AlertTransitionRules.isLegal(AlertStatus.ESCALATED, AlertStatus.IN_PROGRESS)).isTrue();
        assertThat(AlertTransitionRules.isLegal(AlertStatus.ESCALATED, AlertStatus.CLOSED)).isTrue();
        assertThat(AlertTransitionRules.isLegal(AlertStatus.ESCALATED, AlertStatus.RESOLVED)).isFalse();
    }

    @Test
    void closedIsTerminal() {
        for (AlertStatus to : AlertStatus.values()) {
            assertThat(AlertTransitionRules.isLegal(AlertStatus.CLOSED, to)).isFalse();
        }
    }

    @Test
    void medicalAndSecurityHaveAShorterFuseThanShortageAndDamage() {
        assertThat(AlertSla.thresholdTicks(AlertType.MEDICAL_EMERGENCY))
                .isLessThan(AlertSla.thresholdTicks(AlertType.RESOURCE_SHORTAGE));
        assertThat(AlertSla.thresholdTicks(AlertType.SECURITY_INCIDENT))
                .isLessThan(AlertSla.thresholdTicks(AlertType.INFRASTRUCTURE_DAMAGE));
    }
}
