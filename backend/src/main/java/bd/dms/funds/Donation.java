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
 * One monetary donation from a donor to a disaster (modeled — no real payment processing). Rows
 * are append-only and never updated: the disaster's fund ledger balance is always just
 * {@code sum(donations.amount) - sum(procurements.amount)} for that disaster, computed fresh by
 * {@link FundLedgerService}, never stored redundantly anywhere that could drift.
 */
@Entity
@Table(name = "donations")
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "donor_user_id", nullable = false)
    private Long donorUserId;

    @Column(name = "disaster_id", nullable = false)
    private Long disasterId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Donation() {
        // for JPA
    }

    public Donation(Long donorUserId, Long disasterId, BigDecimal amount) {
        this.donorUserId = donorUserId;
        this.disasterId = disasterId;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getDonorUserId() {
        return donorUserId;
    }

    public Long getDisasterId() {
        return disasterId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
