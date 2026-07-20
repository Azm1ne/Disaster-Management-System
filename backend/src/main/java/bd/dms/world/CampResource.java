package bd.dms.world;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * One line of a camp's resource summary — a quantity of a given supply type. The type is an
 * enum-like code the frontend translates via i18n, so no per-row bilingual label is stored.
 */
@Entity
@Table(name = "camp_resources")
public class CampResource {

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
    private String unit;

    protected CampResource() {
        // for JPA
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

    public String getUnit() {
        return unit;
    }
}
