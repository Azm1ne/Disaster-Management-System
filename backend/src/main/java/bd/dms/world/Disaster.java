package bd.dms.world;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A disaster the system is responding to. The demo world holds two at once — the active
 * Jamuna flood and the stable Patuakhali cyclone. {@code type} and {@code status} are the
 * headline facts the map and every later feature key off; both names are carried because
 * bilingual coverage is full.
 */
@Entity
@Table(name = "disasters")
public class Disaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String status;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_bn", nullable = false)
    private String nameBn;

    /** GeoJSON geometry object as text, the admin-drawn boundary polygon. Nullable: the two
     * seeded demo disasters (ticket 3) predate manual registration (ticket 13) and have none. */
    @Column
    private String geometry;

    protected Disaster() {
        // for JPA
    }

    /** Creates a new disaster for manual registration (see {@code DisasterAdminService}). */
    public Disaster(String code, String type, String status, String nameEn, String nameBn, String geometry) {
        this.code = code;
        this.type = type;
        this.status = status;
        this.nameEn = nameEn;
        this.nameBn = nameBn;
        this.geometry = geometry;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
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

    public void setGeometry(String geometry) {
        this.geometry = geometry;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public void setNameBn(String nameBn) {
        this.nameBn = nameBn;
    }
}
