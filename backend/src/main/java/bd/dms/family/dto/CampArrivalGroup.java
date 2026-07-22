package bd.dms.family.dto;

import java.util.List;

/** One group awaiting or having completed arrival at a camp manager's camp, with its roster for staff to act on. */
public record CampArrivalGroup(
        Long id,
        String groupName,
        int memberCount,
        boolean representativeArrived,
        boolean managerConfirmedArrived,
        ArrivalStatus status,
        List<MemberView> members) {}
