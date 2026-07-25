package bd.dms.funds;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One spend of ledger funds, notionally converting money into {@code quantity} of
 * {@code resourceType} for one camp. This is a decision-support/ledger record only — it never
 * writes {@code camp_resources}, which stays {@code SimulationEngine}'s sole write path (see that
 * class's doc, and {@code AllocationDecision}'s identical discipline). Rows are append-only.
 */
@Entity
@Table(name = "procurements")
public class Procurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "disaster_id", nullable = false)
    private Long disasterId;

    @Column(name = "camp_id", nullable = false)
    private Long campId;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Procurement() {
        // for JPA
    }

    public Procurement(
            Long disasterId,
            Long campId,
            String resourceType,
            BigDecimal amount,
            BigDecimal unitPrice,
            BigDecimal quantity,
            Long actorUserId) {
        this.disasterId = disasterId;
        this.campId = campId;
        this.resourceType = resourceType;
        this.amount = amount;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.actorUserId = actorUserId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getDisasterId() {
        return disasterId;
    }

    public Long getCampId() {
        return campId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
