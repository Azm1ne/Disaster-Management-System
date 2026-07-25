package bd.dms.volunteer;

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
import java.util.EnumSet;
import java.util.Set;

/**
 * A volunteer's matchable profile: skills plus a home location. Deliberately distinct from
 * {@link bd.dms.user.AppUser} — mirrors {@code FamilyMember} not being a user — so the roster can
 * hold several candidates for scoring even though the demo seeds only one login account with the
 * VOLUNTEER role. {@code userId} is non-null only for the roster entry that IS a real login (the
 * seeded "volunteer" user), which is what lets that account self-accept and see its own shifts.
 */
@Entity
@Table(name = "volunteer_profiles")
public class VolunteerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_bn", nullable = false)
    private String nameBn;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "volunteer_skills", joinColumns = @JoinColumn(name = "volunteer_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "skill")
    private Set<Skill> skills = EnumSet.noneOf(Skill.class);

    protected VolunteerProfile() {
        // for JPA
    }

    public VolunteerProfile(
            String code, Long userId, String nameEn, String nameBn, double lat, double lng, Set<Skill> skills) {
        this.code = code;
        this.userId = userId;
        this.nameEn = nameEn;
        this.nameBn = nameBn;
        this.lat = lat;
        this.lng = lng;
        this.skills = EnumSet.copyOf(skills);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Long getUserId() {
        return userId;
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

    public Set<Skill> getSkills() {
        return skills;
    }
}
