package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import com.uppaal.model.core2.Document
import com.uppaal.model.system.UppaalSystem
import nl.utwente.ewi.fmt.uppaalSMC.DoubleType
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.MainUI
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.muml.uppaal.core.NamedElement
import org.muml.uppaal.declarations.Variable
import org.muml.uppaal.declarations.system.TemplateDeclaration
import org.muml.uppaal.types.PredefinedType
import java.awt.Color
import java.io.PrintStream
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

@SanityCheck(name = "Check for unused declarations")
class UnusedDeclarationsProperty : AbstractProperty() {
    override fun doCheck(nsta: NSTA, doc: Document, sys: UppaalSystem, cb: (SanityCheckResult) -> Unit) {
        val unusedVars = mutableListOf<NamedElement>()
        val problems = ArrayList(MainUI.getProblemr()?.get() ?: listOf())
        nsta.eAllContents().forEachRemaining {
            if (it is NamedElement &&
                    !(it is PredefinedType || it is DoubleType)) {
                if (EcoreUtil.UsageCrossReferencer.find(it, nsta).isEmpty()) {
                    if (!UppaalUtil.isInSelection(it)) {
                        problems.add(UppaalUtil.buildProblem(it, doc, "Unused declarations"))
                        unusedVars.add(it)
                    }
                }
            }
        }
        if (unusedVars.isNotEmpty()) MainUI.getProblemr().set(problems)
        val qualifiedNames = unusedVars.map {
            val sb = StringBuilder(it.name.replace("nta.", "") + "(" + it.eContainer().javaClass.simpleName
                    .replace("Declaration", "")
                    .replace("Impl", "") + ")")
            var obj: EObject? = it
            generateSequence { obj = obj?.eContainer(); obj }
                    .filterIsInstance<NamedElement>().forEach { c ->
                        sb.insert(0, c.name + ".")
                    }
            sb.toString()
        }
        cb(object : SanityCheckResult() {
            override fun write(out: PrintStream, err: PrintStream) {
                if (qualifiedNames.isEmpty()) {
                    out.println("No unused declarations found")
                } else {
                    err.println("Unused declarations found: ")
                    qualifiedNames.forEach(err::println)
                }
            }

            override fun toPanel(): JPanel {
                val p = JPanel()
                p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
                if (qualifiedNames.isEmpty()) {
                    p.add(JLabel("All declarations used!"))
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
