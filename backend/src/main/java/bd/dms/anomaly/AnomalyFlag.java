package bd.dms.anomaly;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A single "this evidence looks off, a human should look" flag raised by one of the three
 * detectors (see the {@code bd.dms.anomaly} package's detector classes). Detectors never mutate
 * the tables they read — they only ever create rows here — so an {@code AnomalyFlag} is always
 * additive, reviewed by a Coordinator/Admin via {@link #dispose}, and never regenerated in place.
 * {@code innocentExplanation} is deliberately carried on every flag (not just shown in the UI from
 * a lookup table) so the reviewer sees the counter-argument right next to the evidence, matching
 * this ticket's "flag for review, don't accuse" stance. {@code subjectIds} are the evidence row
 * ids; what they point at depends on {@code detectorType} (allocation-decision ids / family-group
 * ids / donation ids) — deliberately untyped since a single flag never mixes subject kinds.
 */
@Entity
@Table(name = "anomaly_flags")
public class AnomalyFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "detector_type", nullable = false)
    private AnomalyDetectorType detectorType;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private String summary;

    @Column(name = "innocent_explanation", nullable = false)
    private String innocentExplanation;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "anomaly_flag_subjects", joinColumns = @JoinColumn(name = "anomaly_flag_id"))
    @Column(name = "subject_id")
    private List<Long> subjectIds = new ArrayList<>();

    @Column(name = "detected_at_tick")
    private Long detectedAtTick;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnomalyFlagStatus status;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AnomalyFlag() {
        // for JPA
    }

    public AnomalyFlag(
            AnomalyDetectorType detectorType,
            double score,
            String summary,
            String innocentExplanation,
            List<Long> subjectIds,
            Long detectedAtTick) {
        this.detectorType = detectorType;
        this.score = score;
        this.summary = summary;
        this.innocentExplanation = innocentExplanation;
        this.subjectIds = new ArrayList<>(subjectIds);
        this.detectedAtTick = detectedAtTick;
        this.status = AnomalyFlagStatus.OPEN;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public AnomalyDetectorType getDetectorType() {
        return detectorType;
    }

    public double getScore() {
        return score;
    }

    public String getSummary() {
        return summary;
    }

    public String getInnocentExplanation() {
        return innocentExplanation;
    }

    public List<Long> getSubjectIds() {
        return subjectIds;
    }

    public Long getDetectedAtTick() {
        return detectedAtTick;
    }

    public AnomalyFlagStatus getStatus() {
        return status;
    }

    public Long getReviewedByUserId() {
        return reviewedByUserId;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Called only by {@code AnomalyReviewService} — a flag is reviewed exactly once, so this
     * throws if it has already left {@code OPEN} or if {@code toStatus} isn't a terminal state. */
    public void dispose(AnomalyFlagStatus toStatus, Long reviewerUserId, String note) {
        if (toStatus != AnomalyFlagStatus.CONFIRMED && toStatus != AnomalyFlagStatus.DISMISSED) {
            throw new IllegalStateException("Cannot dispose an anomaly flag to " + toStatus);
        }
        if (status != AnomalyFlagStatus.OPEN) {
            throw new IllegalStateException("Anomaly flag " + id + " has already been reviewed");
        }
        this.status = toStatus;
        this.reviewedByUserId = reviewerUserId;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
