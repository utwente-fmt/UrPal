package nl.utwente.ewi.fmt.uppaalSMC.urpal.util

import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.SanityCheckResult

data class SanityLog(val log: MutableList<SanityCheckResult> = mutableListOf()) {
    override fun toString() = log.joinToString("\n")
}