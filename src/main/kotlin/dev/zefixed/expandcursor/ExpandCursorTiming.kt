package dev.zefixed.expandcursor

object ExpandCursorTiming {

    const val FULL_END = 0.10
    const val COLLAPSE_END = 0.40
    const val HIDDEN_END = 0.60
    const val EXPAND_END = 0.90

    fun cycleMillis(halfPeriodMillis: Int): Long = halfPeriodMillis.coerceIn(10, 1500).toLong() * 2

    fun scaleAt(elapsedMillis: Long, cycleMillis: Long): Float {
        if (cycleMillis <= 0) return 1f
        val u = elapsedMillis.mod(cycleMillis)
        val p = u.toDouble() / cycleMillis
        return when {
            p < FULL_END -> 1.0
            p < COLLAPSE_END -> 1.0 - easeInOutCubic((p - FULL_END) / (COLLAPSE_END - FULL_END))
            p < HIDDEN_END -> 0.0
            p < EXPAND_END -> easeInOutCubic((p - HIDDEN_END) / (EXPAND_END - HIDDEN_END))
            else -> 1.0
        }.toFloat()
    }

    fun easeInOutCubic(progress: Double): Double =
        if (progress < 0.5) {
            4.0 * progress * progress * progress
        } else {
            1.0 - Math.pow(-2.0 * progress + 2.0, 3.0) / 2.0
        }
}
