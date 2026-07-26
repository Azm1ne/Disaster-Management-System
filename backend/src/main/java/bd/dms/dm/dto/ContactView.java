package bd.dms.dm.dto;

import bd.dms.user.Role;

public record ContactView(Long userId, Role role, String nameEn, String nameBn) {}
