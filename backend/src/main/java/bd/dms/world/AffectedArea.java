package bd.dms.world;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A geographic area a disaster affects, stored as a GeoJSON polygon the frontend hands
 * straight to Leaflet. A disaster may have several (e.g. the Jamuna flood spans two
 * river-char belts).
 */
@Entity
@Table(name = "affected_areas")
public class AffectedArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "disaster_id", nullable = false)
    private Long disasterId;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_bn", nullable = false)
    private String nameBn;

    /** GeoJSON geometry object as text. */
    @Column(nullable = false)
    private String geometry;

    protected AffectedArea() {
        // for JPA
    }

    public Long getId() {
        return id;
    }

    public Long getDisasterId() {
        return disasterId;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getNameBn() {
        return nameBn;
    }

    public String getGeometry() {
        return geometry;
    }
}
