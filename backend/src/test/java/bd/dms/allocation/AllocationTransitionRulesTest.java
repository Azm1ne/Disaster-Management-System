package bd.dms.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AllocationTransitionRulesTest {

    @Test
    void recommendedCanMoveToAnyDecidedStatus() {
        assertThat(AllocationTransitionRules.isLegal(AllocationStatus.RECOMMENDED, AllocationStatus.APPROVED))
                .isTrue();
        assertThat(AllocationTransitionRules.isLegal(AllocationStatus.RECOMMENDED, AllocationStatus.MODIFIED))
                .isTrue();
        assertThat(AllocationTransitionRules.isLegal(AllocationStatus.RECOMMENDED, AllocationStatus.REJECTED))
                .isTrue();
    }

    @Test
    void decidedStatusesAreTerminal() {
        assertThat(AllocationTransitionRules.isLegal(AllocationStatus.APPROVED, AllocationStatus.REJECTED))
                .isFalse();
        assertThat(AllocationTransitionRules.isLegal(AllocationStatus.MODIFIED, AllocationStatus.APPROVED))
                .isFalse();
        assertThat(AllocationTransitionRules.isLegal(AllocationStatus.REJECTED, AllocationStatus.APPROVED))
                .isFalse();
    }

    @Test
    void nothingCanMoveBackToRecommended() {
        assertThat(AllocationTransitionRules.isLegal(AllocationStatus.APPROVED, AllocationStatus.RECOMMENDED))
                .isFalse();
    }
}
