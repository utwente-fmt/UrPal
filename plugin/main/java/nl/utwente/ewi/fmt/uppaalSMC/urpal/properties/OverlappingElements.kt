package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import com.uppaal.model.core2.Document
import com.uppaal.model.system.UppaalSystem
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil.getTemplatesSequence
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil.getLocations
import org.eclipse.emf.ecore.util.EcoreUtil
import java.awt.Color
import java.io.PrintStream
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

@SanityCheck(name = "Overlapping elements")
class OverlappingElements : AbstractProperty() {
    override fun doCheck(nsta: NSTA, doc: Document, sys: UppaalSystem, cb: (SanityCheckResult) -> Unit) {
        val overlappingLocs = doc.getTemplatesSequence().toSet().flatMap {t ->
            t.getLocations().groupBy {
                "${it.getPropertyValue("x")} ${it.getPropertyValue("y")}"
            }.filter { it.value.size > 1 }.values
        }
        cb(object : SanityCheckResult() {
            override fun write(out: PrintStream, err: PrintStream) {
                if (overlappingLocs.isEmpty()) {
                    out.println("No overlapping locations found!")
                }
                overlappingLocs.forEach { ls ->
                    err.println("Overlapping locations: " + ls.joinToString { "${it.parent.getPropertyValue("name")}.${it.name}" })
                }
            }

            override fun toPanel(): JPanel {

                val p = JPanel()
                p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
                if (overlappingLocs.isEmpty()) {
                    p.add(JLabel("No overlapping locations!"))
                } else {
                    val label = JLabel("Unused declarations found:")
                    label.foreground = Color.RED
                    p.add(label)
                    overlappingLocs.forEach { ls ->
                        val locLabel = JLabel(ls.joinToString { "${it.parent.getPropertyValue("name")}.${it.name}" })
                        ls.forEach { it.setProperty("color", Color.PINK) }
                        locLabel.foreground = Color.RED
                        p.add(locLabel)
                    }
                }
                return p
            }

        })
    }
}