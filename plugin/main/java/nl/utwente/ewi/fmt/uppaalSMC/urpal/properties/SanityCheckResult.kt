package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import java.io.PrintStream

import javax.swing.JPanel

abstract class SanityCheckResult {
    enum class Outcome { SATISFIED, VIOLATED, TIMEOUT, CANCELED, EXCEPTION }
    var name: String = "Generic sanity check"
    abstract fun getOutcome(): Outcome

    abstract fun write(out: PrintStream, err: PrintStream)

    abstract fun toPanel(): JPanel
}
