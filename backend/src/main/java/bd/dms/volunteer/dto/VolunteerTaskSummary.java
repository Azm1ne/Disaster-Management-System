package bd.dms.volunteer.dto;

import bd.dms.volunteer.AssignmentMethod;
import bd.dms.volunteer.Skill;
import bd.dms.volunteer.VolunteerTaskStatus;
import java.time.Instant;

public record VolunteerTaskSummary(
        long id,
        long alertId,
        long campId,
        Skill requiredSkill,
        String description,
        VolunteerTaskStatus status,
        Long assignedVolunteerId,
        String assignedVolunteerNameEn,
        String assignedVolunteerNameBn,
        AssignmentMethod assignmentMethod,
        double urgencyScore,
        long generatedAtTick,
        Long assignedAtTick,
        boolean canAssign,
        boolean canAccept,
        Instant createdAt,
        Instant updatedAt) {}
