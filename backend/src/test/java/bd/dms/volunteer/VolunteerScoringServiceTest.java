package bd.dms.volunteer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class VolunteerScoringServiceTest {

    private final VolunteerScoringService scoring = new VolunteerScoringService();

    @Test
    void skillScoreIsOneWithTheSkillAndZeroWithout() {
        VolunteerProfile hasIt = new VolunteerProfile(
                "v1", null, "A", "A", 0, 0, EnumSet.of(Skill.MEDICAL));
        VolunteerProfile lacksIt = new VolunteerProfile(
                "v2", null, "B", "B", 0, 0, EnumSet.of(Skill.LOGISTICS));

        assertThat(scoring.skillScore(hasIt, Skill.MEDICAL)).isEqualTo(1.0);
        assertThat(scoring.skillScore(lacksIt, Skill.MEDICAL)).isEqualTo(0.0);
    }

    @Test
    void distanceScoreIsOneAtZeroDistanceAndDecaysMonotonically() {
        assertThat(scoring.distanceScore(0)).isEqualTo(1.0);
        double near = scoring.distanceScore(5);
        double far = scoring.distanceScore(50);
        assertThat(near).isGreaterThan(far);
        assertThat(far).isGreaterThan(0.0);
    }

    @Test
    void distanceKmIsZeroForSamePointAndPositiveOtherwise() {
        assertThat(scoring.distanceKm(25.8, 89.6, 25.8, 89.6)).isCloseTo(0.0, within(0.001));
        // Kurigram Sadar to Chilmari camp, roughly 30km apart per the seed coordinates.
        double d = scoring.distanceKm(25.806, 89.636, 25.553, 89.683);
        assertThat(d).isBetween(20.0, 40.0);
    }

    @Test
    void scoreIsTheProductOfTheThreeSubScoresAndZeroWithoutTheSkill() {
        assertThat(scoring.score(1.0, 1.0, 1.0)).isEqualTo(1.0);
        assertThat(scoring.score(0.0, 1.0, 1.0)).isEqualTo(0.0);
        assertThat(scoring.score(1.0, 0.5, 0.5)).isCloseTo(0.25, within(0.0001));
    }
}
