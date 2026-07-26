package bd.dms.volunteer.dto;

/** One ranked candidate for a task, best-fit first (see {@code VolunteerTaskService.candidatesFor}). */
public record VolunteerCandidateView(
        long volunteerId,
        String nameEn,
        String nameBn,
        boolean hasSkill,
        double distanceKm,
        double skillScore,
        double distanceScore,
        double urgencyScore,
        double score) {}
