package bd.dms.world;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A relief camp inside a disaster. Its point location plus headline state (capacity,
 * population, open/closed) is what a marker shows on the map. Capacity and population are
 * operational facts and never leave the authenticated world API — the public locator sees
 * only name, location, and status.
 */
@Entity
@Table(name = "camps")
public class Camp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "disaster_id", nullable = false)
    private Long disasterId;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_bn", nullable = false)
    private String nameBn;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int population;

    @Column(nullable = false)
    private String status;

    protected Camp() {
        // for JPA
    }

    public Long getId() {
        return id;
    }

    public Long getDisasterId() {
        return disasterId;
    }

    public String getCode() {
        return code;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getNameBn() {
        return nameBn;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getPopulation() {
        return population;
    }

    public String getStatus() {
        return status;
    }
}
