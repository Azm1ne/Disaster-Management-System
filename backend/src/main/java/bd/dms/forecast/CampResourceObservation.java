package bd.dms.forecast;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * One reading of a camp's resource quantity at a given tick, appended by
 * {@code SimulationEngine} — the sole writer, same discipline as {@code camp_resources}. A
 * camp+resource with a gap in its tick numbers is "stale"; one with two rows for the same tick
 * that disagree is "conflicting" — {@code ForecastService} reads this history to compute both.
 */
@Entity
@Table(name = "camp_resource_observations")
public class CampResourceObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "camp_id", nullable = false)
    private Long campId;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private long tick;

    protected CampResourceObservation() {
        // for JPA
    }

    public CampResourceObservation(Long campId, String resourceType, BigDecimal quantity, long tick) {
        this.campId = campId;
        this.resourceType = resourceType;
        this.quantity = quantity;
        this.tick = tick;
    }

    public Long getId() {
        return id;
    }

    public Long getCampId() {
        return campId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public long getTick() {
        return tick;
    }
}
