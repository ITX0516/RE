package com.badukai.next.game

/**
 * Named constants for game-wide magic numbers.
 */
object GameConstants {
    /** Default komi (Chinese rules) */
    const val DEFAULT_KOMI = 7.5f

    /** Number of candidate moves to show in the analysis panel */
    const val CANDIDATE_DISPLAY_COUNT = 10

    /** Number of top candidate move points to highlight on the board */
    const val TOP_CANDIDATE_COUNT = 3

    /** Visits for lz-analyze (lower = faster, higher = more accurate) */
    const val ANALYSIS_VISITS = 100

    /** Winrate change threshold for "slight mistake" eye marker (5-10%) */
    const val MISTAKE_THRESHOLD_MIN = 0.05f
    /** Winrate change threshold for "big mistake" eye marker (>=10%) */
    const val MISTAKE_THRESHOLD_MAX = 0.10f

    /** Territory ownership threshold for "settled" */
    const val TERRITORY_THRESHOLD = 0.15f

    /** GTP command timeout for regular commands (ms) — 15s for complex positions */
    const val GTP_TIMEOUT_DEFAULT = 15000
    /** GTP command timeout for final_score (full-board scoring can be slow) */
    const val GTP_TIMEOUT_SCORE = 30000
    /** GTP command timeout for genmove — must exceed AI_MOVE_TIME_MAX (120s) + lag buffer */
    const val GTP_TIMEOUT_GENMOVE = 130000
    /** GTP command timeout for lz-analyze protocol_version flush */
    const val GTP_TIMEOUT_FLUSH = 1500

    /** lz-analyze interval in centiseconds (10cs = 100ms) */
    const val LZ_ANALYZE_INTERVAL_CS = 10
    /** Max iterations to wait for lz-analyze info line */
    const val LZ_ANALYZE_MAX_RETRIES = 3
    /** Timeout per lz-analyze retry (ms) */
    const val LZ_ANALYZE_RETRY_TIMEOUT = 4000

    /** kata-analyze interval in centiseconds (10cs = 100ms) */
    const val KATA_ANALYZE_INTERVAL_CS = 10
    /** Max iterations to wait for kata-analyze info line */
    const val KATA_ANALYZE_MAX_RETRIES = 3
    /** Timeout per kata-analyze retry (ms) */
    const val KATA_ANALYZE_RETRY_TIMEOUT = 4000

    /** Min AI move time in seconds */
    const val AI_MOVE_TIME_MIN = 1
    /** Max AI move time in seconds */
    const val AI_MOVE_TIME_MAX = 120
    /** Default AI move time in seconds */
    const val AI_MOVE_TIME_DEFAULT = 20

    /** Board size range */
    const val BOARD_SIZE_MIN = 9
    const val BOARD_SIZE_MAX = 19

    /** Handicap range */
    const val HANDICAP_MAX = 9

    /** lz-analyze winrate unit: 10000 = 100% */
    const val WINRATE_UNIT = 10000f
}