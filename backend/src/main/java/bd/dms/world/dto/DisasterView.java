package bd.dms.world.dto;

import java.util.List;

/** A whole disaster world: its headline facts, the areas to draw, and the camps inside it. */
public record DisasterView(
        Long id,
        String code,
        String type,
        String status,
        String nameEn,
        String nameBn,
        List<AffectedAreaView> affectedAreas,
        List<CampSummary> camps) {}
