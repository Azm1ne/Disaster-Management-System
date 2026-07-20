package bd.dms.world.dto;

import java.math.BigDecimal;

/** One line of a camp's resource summary. {@code type} is translated client-side. */
public record ResourceView(String type, BigDecimal quantity, String unit) {}
