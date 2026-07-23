package bd.dms.allocation;

import bd.dms.family.FamilyMemberRepository;
import bd.dms.forecast.ForecastResult;
import bd.dms.forecast.ForecastService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Pure-ish scoring: derives each camp/resource's reserve, gap (shortage), and surplus straight
 * from {@link ForecastService} — no dependency on {@code Scenario} — plus the three
 * cross-camp-independent priority sub-scores (severity, shortage, and the raw fairness lookup).
 * Population and fairness *normalization* against the candidate set for a given generation pass
 * is {@code AllocationGenerationService}'s job, since only it sees every candidate camp at once;
 * this class only ever answers about one camp/resource at a time.
 */
@Service
public class AllocationScoringService {

    /** The runway (in ticks) a camp should keep in reserve before it's considered to have
     * surplus to give away — symmetric to {@link ForecastService}'s own lookback window. */
    public static final long TARGET_RUNWAY_TICKS = 10;

    /** Worst-case ticks remaining below which shortage urgency saturates to 1.0. Mirrors, but
     * does not import, {@code ForecastAlertListener}'s alerting threshold of the same value. */
    public static final long SHORTAGE_THRESHOLD_TICKS = 6;

    private static final List<AllocationStatus> DECIDED_APPROVED = List.of(
            AllocationStatus.APPROVED, AllocationStatus.MODIFIED);

    /** One camp/resource's reserve/gap/surplus at a tick, derived from a {@link ForecastResult}. */
    public record CampResourceState(
            Long campId,
            String resourceType,
            BigDecimal currentQuantity,
            BigDecimal reserve,
            BigDecimal gap,
            BigDecimal surplus,
            Long ticksRemainingWorstCase) {}

    private final ForecastService forecastService;
    private final FamilyMemberRepository familyMembers;
    private final AllocationDecisionRepository allocations;

    public AllocationScoringService(
            ForecastService forecastService,
            FamilyMemberRepository familyMembers,
            AllocationDecisionRepository allocations) {
        this.forecastService = forecastService;
        this.familyMembers = familyMembers;
        this.allocations = allocations;
    }

    public CampResourceState resourceState(Long campId, String resourceType, long tick) {
        ForecastResult forecast = forecastService.forecast(campId, resourceType, tick);
        BigDecimal reserve = forecast.ratePerTick().multiply(BigDecimal.valueOf(TARGET_RUNWAY_TICKS));
        BigDecimal gap = reserve.subtract(forecast.currentQuantity()).max(BigDecimal.ZERO);
        BigDecimal surplus = forecast.currentQuantity().subtract(reserve).max(BigDecimal.ZERO);
        return new CampResourceState(
                campId, resourceType, forecast.currentQuantity(), reserve, gap, surplus,
                forecast.ticksRemainingWorstCase());
    }

    /** Ratio of registered family members with a staff-set medical flag, at this camp — 0.0 (not
     * NaN) when the camp has no registered members. */
    public double severityScore(Long campId) {
        long total = familyMembers.countByCampId(campId);
        if (total == 0) {
            return 0.0;
        }
        long medical = familyMembers.countMedicalFlagTrueByCampId(campId);
        return (double) medical / total;
    }

    /** 1.0 at zero ticks remaining, 0.0 at (or past) the threshold, and 0.0 when there's no
     * exhaustion horizon at all (nothing depleting). */
    public double shortageScore(Long ticksRemainingWorstCase) {
        if (ticksRemainingWorstCase == null) {
            return 0.0;
        }
        double raw = 1.0 - (double) ticksRemainingWorstCase / SHORTAGE_THRESHOLD_TICKS;
        return clamp01(raw);
    }

    /** Ticks since this camp/resource's most recent APPROVED or MODIFIED allocation, or null if
     * it has never received one — the caller treats null as "never served" (full fairness credit
     * once normalized against the candidate set). */
    public Long ticksSinceLastApproved(Long campId, String resourceType, long currentTick) {
        List<AllocationDecision> decided = allocations.findByTargetCampIdAndResourceTypeAndStatusIn(
                campId, resourceType, DECIDED_APPROVED);
        return decided.stream()
                .map(AllocationDecision::getDecidedAtTick)
                .filter(java.util.Objects::nonNull)
                .max(Long::compareTo)
                .map(lastTick -> currentTick - lastTick)
                .orElse(null);
    }

    public double priorityScore(double severity, double shortage, double population, double fairness) {
        return 0.30 * severity + 0.35 * shortage + 0.15 * population + 0.20 * fairness;
    }

    static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
