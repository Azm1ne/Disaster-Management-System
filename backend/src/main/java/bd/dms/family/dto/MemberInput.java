package bd.dms.family.dto;

import jakarta.validation.constraints.NotBlank;

/** One member row as the representative submits it at registration. No medical flag here — only staff set that. */
public record MemberInput(@NotBlank String nickname, @NotBlank String ageBand) {}
