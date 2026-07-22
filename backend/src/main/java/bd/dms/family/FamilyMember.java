package bd.dms.family;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One person in a {@link FamilyGroup} — a lightweight row, not a person record: a nickname and
 * an age band, never a name/ID/roster-grade detail. {@code medicalFlag} is settable only by
 * staff (a camp manager), never by the registering representative, since it is exactly the
 * kind of detail reunification search must never expose.
 */
@Entity
@Table(name = "family_members")
public class FamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_group_id", nullable = false)
    private Long familyGroupId;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "age_band", nullable = false)
    private String ageBand;

    @Column(name = "medical_flag", nullable = false)
    private boolean medicalFlag;

    protected FamilyMember() {
        // for JPA
    }

    public FamilyMember(Long familyGroupId, String nickname, String ageBand) {
        this.familyGroupId = familyGroupId;
        this.nickname = nickname;
        this.ageBand = ageBand;
    }

    public Long getId() {
        return id;
    }

    public Long getFamilyGroupId() {
        return familyGroupId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAgeBand() {
        return ageBand;
    }

    public boolean isMedicalFlag() {
        return medicalFlag;
    }

    public void setMedicalFlag(boolean medicalFlag) {
        this.medicalFlag = medicalFlag;
    }
}
