package com.comp90018.flashcards.domain.fsrs

import com.comp90018.flashcards.domain.model.Rating

/**
 * Expected results generated with the reference implementation, py-fsrs 6, with fuzzing off.
 * Each step is reviewed at [Step.reviewedAtSeconds] after the card was created, and the card's
 * state afterwards is listed. Times are in seconds.
 */
internal object FsrsReferenceVectors {
    data class Step(
        val rating: Rating,
        val reviewedAtSeconds: Long,
        val state: CardState,
        val step: Int?,
        val stability: Double,
        val difficulty: Double,
        val dueSeconds: Long,
    )

    private val ALL_GOOD_ON_TIME =
        listOf(
            Step(Rating.GOOD, 0L, CardState.LEARNING, 1, 2.3065, 2.118103970459016, 600L),
            Step(Rating.GOOD, 600L, CardState.REVIEW, null, 2.3065, 2.111214235785395, 173400L),
            Step(Rating.GOOD, 173400L, CardState.REVIEW, null, 10.971048263078135, 2.1043313908464483, 1123800L),
            Step(Rating.GOOD, 1123800L, CardState.REVIEW, null, 46.316858440073425, 2.0974554287524403, 5098200L),
            Step(Rating.GOOD, 5098200L, CardState.REVIEW, null, 162.99981577472244, 2.0905863426205262, 19181400L),
            Step(Rating.GOOD, 19181400L, CardState.REVIEW, null, 497.8765551245907, 2.083724125574744, 62208600L),
            Step(Rating.GOOD, 62208600L, CardState.REVIEW, null, 1347.918623024373, 2.0768687707460076, 178675800L),
            Step(Rating.GOOD, 178675800L, CardState.REVIEW, null, 3298.6456099035986, 2.0700202712721, 463709400L),
        )

    private val LAPSE_AND_RELEARN =
        listOf(
            Step(Rating.GOOD, 0L, CardState.LEARNING, 1, 2.3065, 2.118103970459016, 600L),
            Step(Rating.GOOD, 600L, CardState.REVIEW, null, 2.3065, 2.111214235785395, 173400L),
            Step(Rating.GOOD, 173400L, CardState.REVIEW, null, 10.971048263078135, 2.1043313908464483, 1123800L),
            Step(Rating.GOOD, 1123800L, CardState.REVIEW, null, 46.316858440073425, 2.0974554287524403, 5098200L),
            Step(Rating.AGAIN, 5098200L, CardState.RELEARNING, 0, 2.9338452901880046, 7.387715706030851, 5098800L),
            Step(Rating.HARD, 5098800L, CardState.RELEARNING, 0, 2.9338452901880046, 8.251072322512018, 5099700L),
            Step(Rating.GOOD, 5099700L, CardState.REVIEW, null, 2.9338452901880046, 8.238049619486343, 5358900L),
            Step(Rating.GOOD, 5358900L, CardState.REVIEW, null, 6.6413379663257, 8.225039939163695, 5963700L),
            Step(Rating.EASY, 5963700L, CardState.REVIEW, null, 20.7233318611082, 7.617159369267875, 7778100L),
        )

    private val HARD_EASY_AND_LATE =
        listOf(
            Step(Rating.HARD, 0L, CardState.LEARNING, 0, 1.2931, 5.112170705601056, 330L),
            Step(Rating.HARD, 330L, CardState.LEARNING, 0, 1.2931, 6.7404595108297, 660L),
            Step(Rating.GOOD, 660L, CardState.LEARNING, 1, 1.3358997622047517, 6.728947420615708, 1260L),
            Step(Rating.EASY, 260460L, CardState.REVIEW, null, 10.584773508175655, 5.621142448932516, 1210860L),
            Step(Rating.HARD, 2074860L, CardState.REVIEW, null, 30.10554058584886, 7.078338672136363, 4666860L),
            Step(Rating.AGAIN, 8122860L, CardState.RELEARNING, 0, 2.5458254149286477, 9.02489815812509, 8123460L),
            Step(Rating.AGAIN, 8123460L, CardState.RELEARNING, 0, 0.8499683133442265, 9.66471902456508, 8124060L),
            Step(Rating.GOOD, 8124060L, CardState.REVIEW, null, 0.9026828457879061, 9.650282674837353, 8210460L),
            Step(Rating.GOOD, 8228460L, CardState.REVIEW, null, 1.6204532816686978, 9.635860761459353, 8401260L),
        )

    private val EASY_FIRST =
        listOf(
            Step(Rating.EASY, 0L, CardState.REVIEW, null, 8.2956, 1.0, 691200L),
            Step(Rating.EASY, 691200L, CardState.REVIEW, null, 65.62422616189994, 1.0, 6393600L),
            Step(Rating.AGAIN, 6393600L, CardState.RELEARNING, 0, 3.5289566665711614, 7.0269895692968385, 6394200L),
            Step(Rating.GOOD, 6394200L, CardState.REVIEW, null, 3.5289566665711614, 7.0151909490243805, 6739800L),
        )

    private val AGAIN_REPEATEDLY =
        listOf(
            Step(Rating.AGAIN, 0L, CardState.LEARNING, 0, 0.212, 6.4133, 60L),
            Step(Rating.AGAIN, 60L, CardState.LEARNING, 0, 0.08335671711031604, 8.806304468856837, 120L),
            Step(Rating.AGAIN, 120L, CardState.LEARNING, 0, 0.03485140985964798, 9.592868765339693, 180L),
            Step(Rating.GOOD, 180L, CardState.LEARNING, 1, 0.04566983117260317, 9.57850426587119, 780L),
            Step(Rating.GOOD, 780L, CardState.REVIEW, null, 0.058791283965570755, 9.564154130902157, 87180L),
            Step(Rating.GOOD, 87180L, CardState.REVIEW, null, 0.3488142400406077, 9.549818346068092, 173580L),
        )

    val SEQUENCES: Map<String, List<Step>> =
        mapOf(
            "all good on time" to ALL_GOOD_ON_TIME,
            "lapse and relearn" to LAPSE_AND_RELEARN,
            "hard and easy, some late" to HARD_EASY_AND_LATE,
            "easy first" to EASY_FIRST,
            "again repeatedly" to AGAIN_REPEATEDLY,
        )
}
