package bd.dms.family.dto;

/** A roster row as seen by the group's own owner or the destination camp's staff — never by reunification search. */
public record MemberView(Long id, String nickname, String ageBand, boolean medicalFlag) {}
