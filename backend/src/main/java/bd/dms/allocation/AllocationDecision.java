package bd.dms.allocation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One suggested cross-camp redistribution: move {@code recommendedQuantity} of
 * {@code resourceType} from {@code sourceCampId} (has surplus) to {@code targetCampId} (has a
 * shortage), ranked by {@code priorityScore} — the weighted sum of the four stored sub-scores, so
 * the UI can show the exact per-factor breakdown without recomputing anything.
 * {@code decidedQuantity} is null until a coordinator acts on it via {@code applyDecision}; it
 * never changes {@code camp_resources} — see {@code AllocationService}'s class doc for why.
 */
@Entity
@Table(name = "allocation_decisions")
public class AllocationDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "source_camp_id", nullable = false)
    private Long sourceCampId;

    @Column(name = "target_camp_id", nullable = false)
    private Long targetCampId;

    @Column(name = "recommended_quantity", nullable = false)
    private BigDecimal recommendedQuantity;

    @Column(name = "decided_quantity")
    private BigDecimal decidedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AllocationStatus status;

    @Column(name = "severity_score", nullable = false)
    private double severityScore;

    @Column(name = "shortage_score", nullable = false)
    private double shortageScore;

    @Column(name = "population_score", nullable = false)
    private double populationScore;

    @Column(name = "fairness_score", nullable = false)
    private double fairnessScore;

    @Column(name = "priority_score", nullable = false)
    private double priorityScore;

    @Column(name = "generated_at_tick", nullable = false)
    private long generatedAtTick;

    @Column(name = "decided_at_tick")
    private Long decidedAtTick;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AllocationDecision() {
        // for JPA
    }

    public AllocationDecision(
            String resourceType,
            Long sourceCampId,
            Long targetCampId,
            BigDecimal recommendedQuantity,
            double severityScore,
            double shortageScore,
            double populationScore,
            double fairnessScore,
            double priorityScore,
            long generatedAtTick) {
        this.resourceType = resourceType;
        this.sourceCampId = sourceCampId;
        this.targetCampId = targetCampId;
        this.recommendedQuantity = recommendedQuantity;
        this.status = AllocationStatus.RECOMMENDED;
        this.severityScore = severityScore;
        this.shortageScore = shortageScore;
        this.populationScore = populationScore;
        this.fairnessScore = fairnessScore;
        this.priorityScore = priorityScore;
        this.generatedAtTick = generatedAtTick;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Long getSourceCampId() {
        return sourceCampId;
    }

    public Long getTargetCampId() {
        return targetCampId;
    }

    public BigDecimal getRecommendedQuantity() {
        return recommendedQuantity;
    }

    public BigDecimal getDecidedQuantity() {
        return decidedQuantity;
    }

    public AllocationStatus getStatus() {
        return status;
    }

    public double getSeverityScore() {
        return severityScore;
    }

    public double getShortageScore() {
        return shortageScore;
    }

    public double getPopulationScore() {
        return populationScore;
    }

    public double getFairnessScore() {
        return fairnessScore;
    }

    public double getPriorityScore() {
        return priorityScore;
    }

    public long getGeneratedAtTick() {
        return generatedAtTick;
    }

    public Long getDecidedAtTick() {
        return decidedAtTick;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** The quantity that actually counts against the source camp's surplus: the coordinator's
     * decided quantity once one exists, else the still-open recommendation's quantity. */
    public BigDecimal effectiveQuantity() {
        return decidedQuantity != null ? decidedQuantity : recommendedQuantity;
    }

    /** Called only by {@code AllocationGenerationService} on a still-{@code RECOMMENDED} row it
     * looked up itself, to refresh a recommendation in place instead of creating a duplicate. */
    public void refreshRecommendation(
            BigDecimal recommendedQuantity,
            double severityScore,
            double shortageScore,
            double populationScore,
            double fairnessScore,
            double priorityScore,
            long generatedAtTick) {
        if (status != AllocationStatus.RECOMMENDED) {
            throw new IllegalStateException("Cannot refresh a decided allocation");
        }
        this.recommendedQuantity = recommendedQuantity;
        this.severityScore = severityScore;
        this.shortageScore = shortageScore;
        this.populationScore = populationScore;
        this.fairnessScore = fairnessScore;
        this.priorityScore = priorityScore;
        this.generatedAtTick = generatedAtTick;
        this.updatedAt = Instant.now();
    }

    /** Called only by {@code AllocationService} — the sole writer of decided state. */
    public void applyDecision(AllocationStatus toStatus, BigDecimal decidedQuantity, long atTick) {
        this.status = toStatus;
        this.decidedQuantity = decidedQuantity;
        this.decidedAtTick = atTick;
        this.updatedAt = Instant.now();
    }
}
