package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import com.uppaal.model.core2.Document
import com.uppaal.model.system.UppaalSystem
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.muml.uppaal.core.NamedElement
import org.muml.uppaal.declarations.Variable
import java.awt.Color
import java.io.PrintStream
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

@SanityCheck(name = "Check for unused elements")
class UnusedVariablesProperty : AbstractProperty() {
	override fun doCheck(nsta: NSTA, doc: Document, sys: UppaalSystem, cb: (SanityCheckResult) -> Unit) {
		val unusedVars = mutableListOf<Variable>()
		nsta.eAllContents().forEachRemaining {
			if (it is Variable) {
				if (EcoreUtil.UsageCrossReferencer.find(it, nsta).isEmpty()) {
					unusedVars.add(it)
				}
			}
		}
		val qualifiedNames = unusedVars.map  {
			val sb = StringBuilder(it.name.replace("nta.", "") + "(" + it.eContainer().javaClass.simpleName
					.replace("Declaration", "")
					.replace("Impl", "") + ")")
			var obj: EObject = it
			generateSequence { obj = obj.eContainer(); obj }
					.filterIsInstance<NamedElement>().forEach { c ->
				sb.insert(0, c.name + ".")
			}
			sb.toString()
		}
		cb(object : SanityCheckResult() {
			override fun write(out: PrintStream, err: PrintStream) {
				if (qualifiedNames.isEmpty()) {
					out.println("No unused variables found")
				} else {
					err.println("Unused variables found: ")
					qualifiedNames.forEach(err::println)
				}
			}

			override fun toPanel(): JPanel {
				val p = JPanel()
				p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
				if (qualifiedNames.isEmpty()) {
					p.add(JLabel("All variables used!"))
				} else {
					val label = JLabel("Unused declarations found:")
					label.foreground = Color.RED
					p.add(label)
					qualifiedNames.forEach {
						val locLabel = JLabel(it)
						locLabel.foreground = Color.RED
						p.add(locLabel)
					}
				}
				return p
			}
		})
	}
}
