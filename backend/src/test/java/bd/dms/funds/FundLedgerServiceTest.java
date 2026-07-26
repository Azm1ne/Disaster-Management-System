package bd.dms.funds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bd.dms.anomaly.DonationPatternDetector;
import bd.dms.funds.dto.DonationView;
import bd.dms.user.AppUser;
import bd.dms.user.Role;
import bd.dms.world.CampRepository;
import bd.dms.world.Disaster;
import bd.dms.world.DisasterRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit-level coverage of {@code donate} in isolation from the ledger-invariant integration test
 * ({@code FundLedgerServiceIntegrationTest}): does it still return the right view, and does it
 * invoke the anomaly detector on every successful donation.
 */
@ExtendWith(MockitoExtension.class)
class FundLedgerServiceTest {

    @Mock
    private DonationRepository donations;

    @Mock
    private ProcurementRepository procurements;

    @Mock
    private DisasterRepository disasters;

    @Mock
    private CampRepository camps;

    @Mock
    private DonationPatternDetector donationDetector;

    @Mock
    private AppUser donor;

    private FundLedgerService ledger;

    @BeforeEach
    void setUp() {
        ledger = new FundLedgerService(donations, procurements, disasters, camps, donationDetector);
    }

    @Test
    void donateReturnsTheSavedDonationAsAViewAndInvokesTheDetector() {
        when(donor.getRole()).thenReturn(Role.DONOR);
        when(donor.getId()).thenReturn(9L);
        Disaster disaster = newDisaster();
        setField(disaster, "id", 1L);
        setField(disaster, "nameEn", "Jamuna Flood");
        setField(disaster, "nameBn", "যমুনা বন্যা");
        when(disasters.findById(1L)).thenReturn(java.util.Optional.of(disaster));
        Donation saved = new Donation(9L, 1L, BigDecimal.valueOf(500));
        setId(saved, 42L);
        when(donations.save(any(Donation.class))).thenReturn(saved);

        DonationView view = ledger.donate(donor, 1L, BigDecimal.valueOf(500));

        assertThat(view.id()).isEqualTo(42L);
        assertThat(view.amount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        verify(donationDetector).scanDonor(saved);
    }

    private Disaster newDisaster() {
        try {
            var ctor = Disaster.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Disaster disaster, String fieldName, Object value) {
        try {
            var field = Disaster.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(disaster, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void setId(Donation donation, Long id) {
        try {
            var field = Donation.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(donation, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
