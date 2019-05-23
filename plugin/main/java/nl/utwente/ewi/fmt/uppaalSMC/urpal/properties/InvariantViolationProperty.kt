package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import java.awt.Color
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintStream
import java.util.ArrayList

import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.xml.stream.XMLStreamException

import org.apache.commons.io.input.CharSequenceInputStream
import org.eclipse.emf.ecore.util.EcoreUtil
import org.muml.uppaal.declarations.DataVariablePrefix
import org.muml.uppaal.declarations.DeclarationsFactory
import org.muml.uppaal.declarations.global.ChannelPriority
import org.muml.uppaal.declarations.global.GlobalFactory
import org.muml.uppaal.expressions.BinaryExpression
import org.muml.uppaal.expressions.ExpressionsFactory
import org.muml.uppaal.templates.Location
import org.muml.uppaal.templates.LocationKind
import org.muml.uppaal.templates.SynchronizationKind
import org.muml.uppaal.templates.TemplatesFactory
import org.muml.uppaal.types.TypesFactory

import com.uppaal.engine.EngineException
import com.uppaal.engine.QueryResult
import com.uppaal.model.core2.Document
import com.uppaal.model.core2.PrototypeDocument
import com.uppaal.model.io2.XMLReader
import com.uppaal.model.system.SystemEdgeSelect
import com.uppaal.model.system.SystemLocation
import com.uppaal.model.system.UppaalSystem
import com.uppaal.model.system.symbolic.SymbolicState
import com.uppaal.model.system.symbolic.SymbolicTrace
import com.uppaal.model.system.symbolic.SymbolicTransition

import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.Serialization
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.MainUI
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil

@SanityCheck(name = "Invariant Violation")
class InvariantViolationProperty : AbstractProperty() {

    override fun doCheck(nsta: NSTA, doc: Document, sys: UppaalSystem, cb: (SanityCheckResult) -> Unit) {
        val nstaTrans = EcoreUtil.copy(nsta)
        val dvd = DeclarationsFactory.eINSTANCE.createDataVariableDeclaration()
        dvd.prefix = DataVariablePrefix.META
        val ref = TypesFactory.eINSTANCE.createTypeReference()
        ref.referredType = nstaTrans.int
        dvd.typeDefinition = ref
        val violatedVar = UppaalUtil.createVariable("__isViolated__")
        dvd.variable.add(violatedVar)
        nstaTrans.globalDeclarations.declaration.add(dvd)

        val violate = ExpressionsFactory.eINSTANCE.createAssignmentExpression()

        val id = ExpressionsFactory.eINSTANCE.createIdentifierExpression()
        id.identifier = violatedVar
        violate.firstExpr = id

        val lit = ExpressionsFactory.eINSTANCE.createLiteralExpression()
        lit.text = "1"
        violate.secondExpr = lit

        val cvdHigh = UppaalUtil.createChannelDeclaration(nstaTrans, "_high")
        nstaTrans.globalDeclarations.declaration.add(cvdHigh)
        cvdHigh.isBroadcast = true

        val cvdHighest = UppaalUtil.createChannelDeclaration(nstaTrans, "_highest")
        nstaTrans.globalDeclarations.declaration.add(cvdHighest)
        cvdHighest.isBroadcast = true

        var cp: ChannelPriority? = nstaTrans.globalDeclarations.channelPriority
        if (cp == null) {
            cp = GlobalFactory.eINSTANCE.createChannelPriority()!!
            cp.item.add(GlobalFactory.eINSTANCE.createDefaultChannelPriority())
            nstaTrans.globalDeclarations.channelPriority = cp
        }

        val cpiHigh = GlobalFactory.eINSTANCE.createChannelList()
        val cpiHighest = GlobalFactory.eINSTANCE.createChannelList()

        cpiHigh.channelExpression.add(UppaalUtil.createIdentifier(cvdHigh.variable[0]))
        cpiHighest.channelExpression.add(UppaalUtil.createIdentifier(cvdHighest.variable[0]))

        cp.item.add(cpiHigh)
        cp.item.add(cpiHighest)

        nstaTrans.template.forEach { t ->
            var lBad: Location? = null
            var i = 0

            for (e in ArrayList(t.edge)) {
                val target = e.target
                if (target.invariant == null) {
                    continue
                }
                if (lBad == null) {
                    lBad = TemplatesFactory.eINSTANCE.createLocation()!!
                    lBad.name = "__invariantViolated__"
                    lBad.locationTimeKind = LocationKind.COMMITED
                    t.location.add(lBad)
                }
                val lCopy = TemplatesFactory.eINSTANCE.createLocation()
                lCopy.name = "__copy_$i"
                lCopy.locationTimeKind = LocationKind.COMMITED
                t.location.add(lCopy)

                e.target = lCopy

                val eBad = TemplatesFactory.eINSTANCE.createEdge()
                eBad.source = lCopy
                eBad.target = lBad
                eBad.update.add(EcoreUtil.copy<BinaryExpression>(violate))
                UppaalUtil.addSynchronization(eBad, cvdHigh.variable[0], SynchronizationKind.SEND)
                t.edge.add(eBad)

                val eGood = TemplatesFactory.eINSTANCE.createEdge()
                eGood.source = lCopy
                eGood.target = target
                eGood.guard = UppaalUtil.invariantToGuard(EcoreUtil.copy(target.invariant))
                UppaalUtil.addSynchronization(eGood, cvdHighest.variable[0], SynchronizationKind.SEND)
                t.edge.add(eGood)
                i++
            }
        }
        try {
            val temp = File.createTempFile("invarianttest", ".xml")
            val bw = BufferedWriter(FileWriter(temp))
            bw.write(Serialization().main(nstaTrans).toString())
            bw.close()
            val proto = PrototypeDocument()
            proto.setProperty("synchronization", "")
            val tDoc = XMLReader(CharSequenceInputStream(Serialization().main(nstaTrans), "UTF-8"))
                    .parse(proto)
            val tSys = UppaalUtil.compile(tDoc)
            engineQuery(tSys, "A[] (not __isViolated__)", OPTIONS) { qr, ts ->
                val tsFinal = if (ts.isEmpty) ts else transformTrace(ts, sys)
                cb(object : SanityCheckResult() {
                    override fun getOutcome() = if (qr.status == QueryResult.MAYBE_OK || qr.status == QueryResult.OK)
                        Outcome.SATISFIED else Outcome.VIOLATED

                    override fun write(out: PrintStream, err: PrintStream) {
                        if (qr.status == QueryResult.MAYBE_OK || qr.status == QueryResult.OK) {
                            out.println("No invariant violations found!")
                        } else if (qr.status == QueryResult.UNCHECKED) {
                            err.println("An unknown error has occurred! See trace in the GUI:")
                        } else {
                            err.println("Location invariant violated! See trace in the GUI:")
                            // ts.forEach(s -> System.err.println(s.traceFormat()));
                        }
                    }

                    override fun toPanel(): JPanel {
                        val p = JPanel()
                        p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
                        if (qr.status == QueryResult.MAYBE_OK || qr.status == QueryResult.OK) {
                            val label = JLabel("No invariant violations found!")
                            // label.setForeground(Color.GREEN);
                            p.add(label)
                        } else {
                            val label = JLabel(
                                    (if (qr.status == QueryResult.UNCHECKED)
                                        "An unknown error has occurred"
                                    else
                                        "Invariant violation found!") + " Click button below to load trace:")
                            label.foreground = Color.RED
                            p.add(label)
                            val button = JButton("Load trace")
                            button.addActionListener {
                                MainUI.getTracer().set(tsFinal)
                            }
                            p.add(button)

                        }
                        return p
                    }
                })
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: XMLStreamException) {
            e.printStackTrace()
        } catch (e: EngineException) {
            e.printStackTrace()
        }

    }

    private fun transformTrace(ts: SymbolicTrace, origSys: UppaalSystem): SymbolicTrace {
        var prev: SymbolicState? = null
        val result = SymbolicTrace()
        val it = ts.iterator()
        outer@ while (it.hasNext()) {
            var curr = it.next()
            val edgesWs = curr.edges
            if (prev != null) {
                for (i in edgesWs.indices) {
                    val edgeWs = edgesWs[i]
                    val origProccess = origSys.getProcess(edgeWs.process.index)
                    val origEdge = origProccess.getEdge(edgeWs.index)

                    edgesWs[i] = SystemEdgeSelect(origEdge, edgeWs.selectList)
                }
            }
            var transTarget: SymbolicState

            var locations: Array<SystemLocation>
            inner@ while (true) {
                transTarget = curr.target
                locations = transTarget.locations
                for (i in locations.indices) {
                    val location = locations[i]
                    val origProcess = origSys.getProcess(i)
                    if (origProcess.locations.size <= location.index) {
                        if (it.hasNext()) {
                            curr = it.next()
                            continue@inner
                        } else {
                            break@outer
                        }
                    }
                    locations[i] = origProcess.getLocation(location.index)
                }
                break
            }

            val origTarget = SymbolicState(locations, transTarget.variableValues,
                    transTarget.polyhedron)
            result.add(SymbolicTransition(prev, edgesWs, origTarget))
            prev = origTarget
        }
        return result
    }

    companion object {
        private const val OPTIONS = "order 0\nreduction 1\nrepresentation 0\ntrace 1\nextrapolation 0\nhashsize 27\nreuse 0\nsmcparametric 1\nmodest 0\nstatistical 0.01 0.01 0.05 0.05 0.05 0.9 1.1 0.0 0.0 4096.0 0.01"
    }
}
