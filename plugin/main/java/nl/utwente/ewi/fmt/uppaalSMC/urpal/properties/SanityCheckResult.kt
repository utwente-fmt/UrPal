package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import java.io.PrintStream

import javax.swing.JPanel

abstract class SanityCheckResult {
    abstract fun write(out: PrintStream, err: PrintStream)

    abstract fun toPanel(): JPanel
}
