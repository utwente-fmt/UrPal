package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import java.io.PrintStream

import javax.swing.JPanel

abstract class SanityCheckResult {
    enum class Outcome { SATISFIED, VIOLATED, TIMEOUT, CANCELED, EXCEPTION }
    var name: String = "Generic sanity check"
    val epoch = System.currentTimeMillis() / 1000L
    var duration = 0
    abstract fun getOutcome(): Outcome

    abstract fun write(out: PrintStream, err: PrintStream)

    abstract fun toPanel(): JPanel

    override fun toString() = listOf(epoch, duration, name, getOutcome()).joinToString(",")
}
