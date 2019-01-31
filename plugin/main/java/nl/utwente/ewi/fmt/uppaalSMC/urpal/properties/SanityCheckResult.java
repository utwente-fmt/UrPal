package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties;

import java.io.PrintStream;

import javax.swing.JPanel;

public abstract class SanityCheckResult {
    public abstract void write(PrintStream out, PrintStream err);

    public abstract JPanel toPanel();
}
