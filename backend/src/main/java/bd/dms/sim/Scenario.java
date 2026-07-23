package bd.dms.sim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The scripted Jamuna scenario as a <b>pure function of tick number</b>: {@link #stateAt(long)}
 * deterministically yields the whole simulated world at tick {@code t}. Because it is pure —
 * no clock, no database, no shared {@link java.util.Random} — the same tick always produces an
 * equal {@link ScenarioState}, which is exactly what makes the simulation reproducible
 * tick-for-tick and lets a headless runner assert on it directly.
 *
 * <p>The story runs over {@link #LENGTH} ticks (~30 min of sim-time each) in four phases:
 * <ul>
 *   <li><b>SURGE</b> — riverine camps fill toward/past capacity; water &amp; food deplete.</li>
 *   <li><b>NEW_CAMP</b> — the seeded-closed {@code jam-char-relief} opens and absorbs overflow.</li>
 *   <li><b>RELIEF_CONVOY</b> — water &amp; food are replenished at the worst-hit camps.</li>
 *   <li><b>RECOVERY</b> — the flood recedes; populations gently decline and hold.</li>
 * </ul>
 * The Patuakhali cyclone is quiescent: its camps hold their baseline for every tick, proving a
 * multi-disaster world where one disaster is stable.
 *
 * <p>{@code stateAt(0)} equals the V4/V5/V6 seed by construction (pressure and jitter are zero
 * at tick 0); {@code ScenarioSeedConsistencyTest} guards that against drift.
 */
public final class Scenario {

    /** Total ticks in the scripted run; the world holds at {@code stateAt(LENGTH)} thereafter. */
    public static final long LENGTH = 60;

    /** The tick at which the overflow camp opens (drives the NEW_CAMP phase). */
    private static final long CHAR_RELIEF_OPEN_TICK = 20;
    private static final long CONVOY_TICK = 35;
    private static final long RECOVERY_START_TICK = 45;

    /** Sim-time anchor: flood onset. Each tick advances sim-time by 30 minutes. */
    private static final Instant SCENARIO_START = Instant.parse("2024-07-15T06:00:00Z");
    private static final long MINUTES_PER_TICK = 30;

    private static final String OPEN = "OPEN";
    private static final String CLOSED = "CLOSED";

    /** Per-person resource demand — matches the V6 seed (WATER 3, FOOD 2, MEDICAL 1). */
    private static final Map<String, Integer> PER_PERSON =
            Map.of("WATER", 3, "FOOD", 2, "MEDICAL", 1);

    private Scenario() {}

    /**
     * One camp's fixed facts plus its scripted surge. {@code surgeAdd} is how many people the
     * flood adds at peak pressure; {@code openTick} &gt; 0 marks a camp that starts closed and
     * opens mid-run (the overflow camp). Quiescent camps have {@code surgeAdd == 0}.
     */
    private record Baseline(
            String code, int basePopulation, String baseStatus, long openTick, int surgeAdd) {}

    // The seeded world (must equal V4/V5). Jamuna camps carry a scripted surge; the Patuakhali
    // cyclone camps are quiescent (surgeAdd 0) so they hold their baseline for every tick.
    /** Which of the three data-quality conditions this camp+resource combo is scripted to have.
     * NORMAL is every combo except the two below — one is scripted stale-prone (its reports
     * arrive with gaps), one conflicting-prone (its reports sometimes disagree for the same
     * tick). Deterministic and fixed, so forecast confidence behaviour is reproducible and
     * assertable, exactly like the rest of the scripted scenario. */
    public enum DataQualityCondition { NORMAL, STALE_PRONE, CONFLICTING_PRONE }

    private static final String STALE_PRONE_CAMP = "jam-roumari";
    private static final String STALE_PRONE_RESOURCE = "WATER";
    private static final String CONFLICTING_PRONE_CAMP = "jam-saghata";
    private static final String CONFLICTING_PRONE_RESOURCE = "FOOD";

    public static DataQualityCondition dataQualityCondition(String campCode, String resourceType) {
        if (STALE_PRONE_CAMP.equals(campCode) && STALE_PRONE_RESOURCE.equals(resourceType)) {
            return DataQualityCondition.STALE_PRONE;
        }
        if (CONFLICTING_PRONE_CAMP.equals(campCode) && CONFLICTING_PRONE_RESOURCE.equals(resourceType)) {
            return DataQualityCondition.CONFLICTING_PRONE;
        }
        return DataQualityCondition.NORMAL;
    }

    /** Stale-prone combos only get an observation written every 3rd tick — a real reporting gap. */
    public static boolean shouldRecordObservation(String campCode, String resourceType, long tick) {
        return dataQualityCondition(campCode, resourceType) != DataQualityCondition.STALE_PRONE
                || tick % 3 == 0;
    }

    private static final List<Baseline> BASELINES = List.of(
            // Jamuna flood — active, riverine camps surge toward/over capacity.
            new Baseline("jam-kurigram-sadar", 1080, OPEN, 0, 250),
            new Baseline("jam-chilmari", 790, OPEN, 0, 150),
            new Baseline("jam-ulipur", 610, OPEN, 0, 420),
            new Baseline("jam-rajarhat", 540, OPEN, 0, 150),
            new Baseline("jam-nageshwari", 300, OPEN, 0, 500),
            new Baseline("jam-roumari", 480, OPEN, 0, 120),
            new Baseline("jam-fulchhari", 940, OPEN, 0, 260),
            new Baseline("jam-saghata", 420, OPEN, 0, 330),
            new Baseline("jam-sundarganj", 700, OPEN, 0, 260),
            new Baseline("jam-gaibandha-sadar", 1350, OPEN, 0, 350),
            // The overflow camp: seeded closed, opens at tick 20 and absorbs arrivals.
            new Baseline("jam-char-relief", 0, CLOSED, CHAR_RELIEF_OPEN_TICK, 320),
            // Patuakhali cyclone — stable; every camp holds its baseline for the whole run.
            new Baseline("pat-sadar", 250, OPEN, 0, 0),
            new Baseline("pat-kalapara", 180, OPEN, 0, 0),
            new Baseline("pat-galachipa", 120, OPEN, 0, 0),
            new Baseline("pat-bauphal", 90, OPEN, 0, 0),
            new Baseline("pat-rangabali", 60, CLOSED, 0, 0));

    /** The whole simulated world at {@code tick}. Ticks past {@link #LENGTH} hold the final state. */
    public static ScenarioState stateAt(long tick) {
        long t = Math.max(0, Math.min(tick, LENGTH));
        Map<String, CampState> camps = new LinkedHashMap<>();
        for (Baseline b : BASELINES) {
            camps.put(b.code(), campStateAt(b, t));
        }
        return new ScenarioState(camps);
    }

    private static CampState campStateAt(Baseline b, long t) {
        if (b.openTick() > 0) {
            return overflowCampStateAt(b, t);
        }
        int population = b.basePopulation() + surgeDelta(b, t);
        boolean active = b.surgeAdd() > 0; // a Jamuna flood camp; quiescent camps hold baseline supply
        return camp(b.code(), population, b.baseStatus(), active, t);
    }

    /** How many people the surge has added to an always-open camp at tick {@code t}. */
    private static int surgeDelta(Baseline b, long t) {
        if (b.surgeAdd() == 0) {
            return 0; // quiescent camp — no movement, so its resources also stay at baseline
        }
        double shaped = pressure(t) * b.surgeAdd();
        double jitter = noise(b.code(), t) * b.surgeAdd() * 0.03 * pressure(t);
        return (int) Math.round(shaped + jitter);
    }

    /** The overflow camp: closed and empty until it opens, then fills and partially recedes. */
    private static CampState overflowCampStateAt(Baseline b, long t) {
        if (t < b.openTick()) {
            return camp(b.code(), 0, CLOSED, true, t);
        }
        double ramp;
        if (t <= CONVOY_TICK) {
            ramp = (double) (t - b.openTick()) / (CONVOY_TICK - b.openTick()); // 0 -> 1 while filling
        } else if (t < RECOVERY_START_TICK) {
            ramp = 1.0;
        } else {
            double recede = (double) (t - RECOVERY_START_TICK) / (LENGTH - RECOVERY_START_TICK);
            ramp = 1.0 - 0.4 * recede; // eases back as arrivals settle or move on
        }
        double jitter = noise(b.code(), t) * b.surgeAdd() * 0.03;
        int population = (int) Math.round(b.surgeAdd() * ramp + jitter);
        return camp(b.code(), Math.max(0, population), OPEN, true, t);
    }

    /**
     * Flood pressure in [0,1]: rises through the surge, plateaus while the crisis holds, then
     * recedes during recovery. Drives every open camp's population and (inversely) supply
     * coverage. Zero at tick 0 so {@code stateAt(0)} equals the seed exactly.
     */
    private static double pressure(long t) {
        if (t <= CHAR_RELIEF_OPEN_TICK) {
            return (double) t / CHAR_RELIEF_OPEN_TICK; // 0 -> 1 across the surge
        }
        if (t < RECOVERY_START_TICK) {
            return 1.0; // crisis holds
        }
        double recede = (double) (t - RECOVERY_START_TICK) / (LENGTH - RECOVERY_START_TICK);
        return 1.0 - 0.7 * recede; // 1 -> 0.3 across recovery
    }

    /**
     * Supply coverage in (0,1] for a resource type: how well stock keeps up with demand. Water
     * and food fall into shortage as population outruns supply, then jump when the relief convoy
     * arrives; medical aid holds steadier. 1.0 at tick 0 so seeded quantities are reproduced.
     */
    private static double coverage(String type, long t) {
        double floor = "MEDICAL".equals(type) ? 0.85 : 0.55;
        double preConvoy = 1.0 - (1.0 - floor) * pressure(t); // depletes with the surge
        if (t < CONVOY_TICK) {
            return preConvoy;
        }
        double afterConvoy = Math.min(1.1, preConvoy + 0.5); // convoy replenishes stock
        double settle = Math.min(1.0, (double) (t - CONVOY_TICK) / (LENGTH - CONVOY_TICK));
        return afterConvoy - (afterConvoy - 1.0) * settle; // eases toward steady 1.0
    }

    /**
     * Builds a camp's state. Resources are {@code perPerson × population × coverage}: an active
     * flood camp's coverage follows the depletion/convoy curve, while a quiescent camp holds a
     * steady 1.0. This mirrors the V6 seed, which scales every camp's stock by its population
     * regardless of open/closed status (an empty camp is simply 0).
     */
    private static CampState camp(String code, int population, String status, boolean active, long t) {
        Map<String, BigDecimal> resources = new LinkedHashMap<>();
        for (var entry : PER_PERSON.entrySet()) {
            double coverage = active ? coverage(entry.getKey(), t) : 1.0;
            resources.put(entry.getKey(), quantity(entry.getValue(), population, coverage));
        }
        return new CampState(code, population, status, resources);
    }

    private static BigDecimal quantity(int perPerson, int population, double coverage) {
        return BigDecimal.valueOf((long) perPerson * population)
                .multiply(BigDecimal.valueOf(coverage))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Deterministic per-camp, per-tick jitter in [-1,1] — a pure hash, never a shared Random. */
    static double noise(String code, long tick) {
        long h = 1125899906842597L; // FNV-ish seed
        for (int i = 0; i < code.length(); i++) {
            h = 31 * h + code.charAt(i);
        }
        h = 31 * h + tick;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return (double) (h % 1000) / 1000.0; // in (-1,1)
    }

    /** The narrative phase at {@code tick}, used for the DEMO clock label. */
    public static String phaseAt(long tick) {
        long t = Math.max(0, Math.min(tick, LENGTH));
        if (t < CHAR_RELIEF_OPEN_TICK) {
            return "SURGE";
        }
        if (t < CONVOY_TICK) {
            return "NEW_CAMP";
        }
        if (t < RECOVERY_START_TICK) {
            return "RELIEF_CONVOY";
        }
        return "RECOVERY";
    }

    /** Sim-time at {@code tick} (30 min per tick from flood onset), as an ISO-8601 instant. */
    public static Instant simTimeAt(long tick) {
        long t = Math.max(0, Math.min(tick, LENGTH));
        return SCENARIO_START.plus(t * MINUTES_PER_TICK, ChronoUnit.MINUTES);
    }
}
