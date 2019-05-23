package nl.utwente.ewi.fmt.uppaalSMC.urpal.util

import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.SanityCheckResult

class SanityLogRepository : GenericRepository<SanityLog>("SanityLog") {
    init {
        set(null)
    }

    override fun set(p0: SanityLog?) = super.set(p0 ?: SanityLog())

    fun addToLog(sanityCheckResult: SanityCheckResult) = get().log.add(sanityCheckResult)
}