package com.fintech.qa.core.data;

import com.fintech.qa.core.config.ConfigManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-run <strong>unique</strong> synthetic-data tokens for re-runnable CRUD scenarios.
 *
 * <p>A "create" scenario must mint a fresh natural key on every run so it never collides
 * with an entity left behind by a prior run. This helper supplies a JVM-run-stable
 * {@link #runId() run id} plus monotonically increasing {@link #token() tokens} and
 * {@link #name(String) names} built on top of it.</p>
 *
 * <p><strong>Opt-in only.</strong> This class changes no existing deterministic behaviour:
 * card PANs remain deterministic-from-fake-BIN (the PAN is not a CRUD collision key). Use
 * the {@code unique*} variants on {@link TestDataFactory} when a scenario needs a fresh
 * natural key.</p>
 *
 * <p><strong>Run id resolution.</strong> The run id is resolved from configuration first
 * so CI can inject a build number for traceability:</p>
 * <ol>
 *   <li>{@code test.run.id} via {@link ConfigManager} (mapped from the {@code TEST_RUN_ID}
 *       environment variable);</li>
 *   <li>otherwise a value derived <em>once</em> from {@link System#currentTimeMillis()}
 *       rendered in base&nbsp;36 and cached for the life of the JVM.</li>
 * </ol>
 *
 * <p>These tokens are non-PII identifiers (usernames, references) and are clearly synthetic,
 * so they require no masking. Do not, however, log full synthetic account numbers or IBANs
 * in clear text elsewhere; those still flow through the models' masked display getters.</p>
 *
 * <p><strong>Thread-safety:</strong> the counter is an {@link AtomicLong} and the cached run
 * id is published via a synchronized lazy initialiser, so tokens are safe to mint from
 * parallel test threads.</p>
 *
 * <p><strong>Numeric keys across JVMs.</strong> When a value must be <em>numeric only</em>
 * (e.g. a synthetic account number or the digit body of an IBAN) and stay collision-free
 * across parallel/sharded JVMs that did not set {@code TEST_RUN_ID}, use
 * {@link #numericToken()} rather than stripping non-digits from {@link #token()}: stripping
 * would discard the alphabetic characters of a fallback base-36 run id, leaving only the
 * per-JVM counter (which restarts at 1 in every JVM) and re-introducing cross-JVM
 * collisions. {@link #numericToken()} folds a per-run numeric <em>seed</em> derived from the
 * run id into every value, so two JVMs produce disjoint numeric sequences.</p>
 *
 * <pre>{@code
 * // Stable for the whole JVM run (or the CI-injected build number):
 * String run = UniqueData.runId();              // e.g. "lkq8z3a1" or "build-4217"
 *
 * // Unique per call, parallel-safe:
 * String ref = UniqueData.token();              // e.g. "lkq8z3a1_1", "lkq8z3a1_2"
 *
 * // Numeric-only, unique per call AND across JVMs (no TEST_RUN_ID required):
 * String num = UniqueData.numericToken();       // e.g. "01874450291", differs per JVM run
 *
 * // Sanitized, human-readable base + unique suffix:
 * String user = UniqueData.name("Ada Testwell"); // e.g. "Ada_Testwell_lkq8z3a1_3"
 * }</pre>
 */
public final class UniqueData {

    private static final Logger log = LoggerFactory.getLogger(UniqueData.class);

    /** Config key for an externally-supplied run id (maps to the {@code TEST_RUN_ID} env var). */
    private static final String RUN_ID_KEY = "test.run.id";

    /** Fallback base name when the supplied base is {@code null}/blank. */
    private static final String DEFAULT_BASE = "qa";

    /** Characters allowed in a sanitized name; everything else becomes {@code '_'}. */
    private static final String DISALLOWED_CHARS = "[^A-Za-z0-9._-]";

    /** Process-wide, thread-safe sequence so every token is unique within the run. */
    private static final AtomicLong COUNTER = new AtomicLong(0);

    /** Lock guarding lazy, once-only initialisation of {@link #cachedRunId}. */
    private static final Object RUN_ID_LOCK = new Object();

    /** Cached run id, derived once per JVM when not supplied via config. */
    private static volatile String cachedRunId;

    /** Lock guarding lazy, once-only computation of {@link #cachedNumericSeed}. */
    private static final Object SEED_LOCK = new Object();

    /**
     * Per-run numeric seed (decimal digits, no sign) derived once from {@link #runId()}.
     * {@code 0L} means "not yet computed"; a real seed is always {@code >= 1} because we add 1.
     */
    private static volatile long cachedNumericSeed;

    private UniqueData() {
        throw new AssertionError("UniqueData is a static utility and must not be instantiated");
    }

    /**
     * Returns a token that is stable for the whole JVM run.
     *
     * <p>Resolved from {@code test.run.id} (env {@code TEST_RUN_ID}) when present so CI can
     * inject a build number for traceability; otherwise derived once from
     * {@link System#currentTimeMillis()} in base&nbsp;36 and cached, so every call in the
     * run returns the same id.</p>
     *
     * <p>The double-checked-lock cache ({@link #cachedRunId}) applies <em>only</em> to the
     * fallback path: when {@code test.run.id} is configured the value is re-read cheaply from
     * {@link ConfigManager} on every call (it is already stable for the run), and the cache is
     * never populated. The clock is read at most once per JVM, so the fallback id is also
     * stable for the run.</p>
     *
     * @return the run-stable id (never {@code null} or blank)
     */
    public static String runId() {
        String configured = ConfigManager.get(RUN_ID_KEY, null);
        if (StringUtils.isNotBlank(configured)) {
            return configured.trim();
        }
        String local = cachedRunId;
        if (local == null) {
            synchronized (RUN_ID_LOCK) {
                local = cachedRunId;
                if (local == null) {
                    local = Long.toString(System.currentTimeMillis(), 36);
                    cachedRunId = local;
                    log.debug("UniqueData: derived run id '{}' for this JVM run", local);
                }
            }
        }
        return local;
    }

    /**
     * Returns a token unique to this call within the run: {@code runId() + "_" + counter},
     * where {@code counter} is a process-wide monotonically increasing value.
     *
     * <p>Safe to call from parallel test threads.</p>
     *
     * @return a fresh unique token (e.g. {@code "lkq8z3a1_7"})
     */
    public static String token() {
        return runId() + "_" + COUNTER.incrementAndGet();
    }

    /**
     * Returns a per-run numeric seed (decimal digits only) derived <em>once</em> from
     * {@link #runId()}.
     *
     * <p>The seed differs per JVM run even on the fallback path (no {@code TEST_RUN_ID}),
     * because it is folded from the resolved run id via {@code Math.abs((long) runId().hashCode())}.
     * It is cached for the life of the JVM (the run id is itself run-stable), so every call in
     * the run returns the same seed. The value is always {@code >= 1} so it never collapses to
     * an all-zero prefix.</p>
     *
     * @return the run-stable numeric seed ({@code >= 1})
     */
    private static long numericSeed() {
        long local = cachedNumericSeed;
        if (local == 0L) {
            synchronized (SEED_LOCK) {
                local = cachedNumericSeed;
                if (local == 0L) {
                    // Absolute value of the run id's hash, +1 so the seed is always >= 1.
                    // Math.abs(Integer.MIN_VALUE) is negative as an int, so widen to long first.
                    local = Math.abs((long) runId().hashCode()) + 1L;
                    cachedNumericSeed = local;
                    log.debug("UniqueData: derived numeric seed '{}' for this JVM run", local);
                }
            }
        }
        return local;
    }

    /**
     * Returns a <strong>numeric-only</strong> token that is unique to this call within the run
     * and remains collision-free <em>across</em> parallel/sharded JVMs even when no
     * {@code TEST_RUN_ID} is configured.
     *
     * <p>The value is {@code numericSeed()} concatenated with the process-wide monotonic
     * counter (zero-padded to a stable minimum width): the per-run seed differs per JVM, so two
     * JVMs produce disjoint sequences, while the counter guarantees uniqueness within a JVM.
     * Use this (not {@code token().replaceAll("[^0-9]", "")}) wherever a numeric natural key
     * such as a synthetic account number or IBAN body is needed.</p>
     *
     * <p>Safe to call from parallel test threads.</p>
     *
     * @return a fresh numeric-only unique token (e.g. {@code "187445000007"})
     */
    public static String numericToken() {
        long counter = COUNTER.incrementAndGet();
        // Pad the counter to a stable minimum so the seed and counter never visually merge
        // ambiguously; the seed prefix is what makes the value differ across JVMs.
        return numericSeed() + StringUtils.leftPad(Long.toString(counter), 6, '0');
    }

    /**
     * Returns a numeric-only unique token of <strong>exactly</strong> {@code length} digits,
     * derived from {@link #numericToken()}.
     *
     * <p>If the composed seed-and-counter value is longer than {@code length}, the trailing
     * {@code length} digits are kept (preserving the always-changing counter end); if it is
     * shorter it is left-padded with {@code '0'}. The cross-JVM uniqueness property of
     * {@link #numericToken()} is preserved as long as {@code length} is wide enough to retain
     * distinguishing digits (callers use fixed widths of 15+ here).</p>
     *
     * @param length the exact number of digits to return; must be {@code >= 1}
     * @return a numeric-only token of exactly {@code length} digits
     * @throws IllegalArgumentException if {@code length < 1}
     */
    public static String numericToken(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("length must be >= 1, was " + length);
        }
        String raw = numericToken();
        if (raw.length() > length) {
            return raw.substring(raw.length() - length);
        }
        return StringUtils.leftPad(raw, length, '0');
    }

    /**
     * Returns a sanitized, human-readable unique name: {@code base + "_" + token()}.
     *
     * <p>Any character outside {@code [A-Za-z0-9._-]} in the result is replaced with
     * {@code '_'} so the value is safe to use as a username or reference. A {@code null} or
     * blank {@code base} falls back to {@code "qa"}.</p>
     *
     * @param base the human-readable base name (e.g. a fake holder name); may be {@code null}/blank
     * @return a sanitized unique name (e.g. {@code "Ada_Testwell_lkq8z3a1_8"})
     */
    public static String name(String base) {
        String effectiveBase = StringUtils.isBlank(base) ? DEFAULT_BASE : base;
        String raw = effectiveBase + "_" + token();
        return raw.replaceAll(DISALLOWED_CHARS, "_");
    }
}
