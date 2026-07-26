package bd.dms.volunteer.dto;

import bd.dms.volunteer.Skill;

/** How many OPEN tasks need {@code skill} versus how many roster volunteers with that skill are
 * currently free to take one; {@code gap} is the shortfall (never negative) and {@code unmet} is
 * {@code gap > 0} — what the coordinator's skill-coverage panel highlights. */
public record SkillCoverage(Skill skill, long openTaskCount, long availableVolunteerCount, long gap, boolean unmet) {}
