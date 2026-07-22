package bd.dms.family.dto;

import java.util.List;

/**
 * The full picture the group's own owner (or the destination camp's staff) may see:
 * {@code memberCount} is computed from {@code members.size()}, never a stored field, so it
 * cannot drift from the roster it is counting.
 */
public record FamilyGroupStatus(
        Long id,
        String groupName,
        Long campId,
        String campNameEn,
        String campNameBn,
        int memberCount,
        boolean representativeArrived,
        boolean managerConfirmedArrived,
        ArrivalStatus status,
        List<MemberView> members) {}
