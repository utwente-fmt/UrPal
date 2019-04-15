package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import java.awt.Color
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintStream
import java.util.ArrayList
import java.util.HashSet
import java.util.regex.Pattern

import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.xml.stream.XMLStreamException

import org.apache.commons.io.input.CharSequenceInputStream
import org.eclipse.emf.ecore.util.EcoreUtil
import org.muml.uppaal.declarations.DataVariablePrefix
import org.muml.uppaal.declarations.DeclarationsFactory
import org.muml.uppaal.declarations.system.SystemFactory
import org.muml.uppaal.expressions.AssignmentOperator
import org.muml.uppaal.expressions.ExpressionsFactory
import org.muml.uppaal.templates.SynchronizationKind
import org.muml.uppaal.templates.TemplatesFactory
import org.muml.uppaal.types.TypesFactory

import com.uppaal.engine.EngineException
import com.uppaal.engine.QueryResult
import com.uppaal.model.core2.Document
import com.uppaal.model.core2.PrototypeDocument
import com.uppaal.model.io2.XMLReader
import com.uppaal.model.system.SystemEdge
import com.uppaal.model.system.UppaalSystem

import nl.utwente.ewi.fmt.uppaalSMC.ExponentialLocation
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.Serialization
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil

@SanityCheck(name = "System edge Reachability meta")
class SystemEdgeReachabilityMeta : AbstractProperty() {

    override fun doCheck(nsta: NSTA, doc: Document, sys: UppaalSystem, cb: (SanityCheckResult) -> Unit) {
        val nstaTrans = EcoreUtil.copy(nsta)

        val cvd = UppaalUtil.createChannelDeclaration(nstaTrans, "__copy__")
        cvd.isBroadcast = true
        cvd.isUrgent = true
        nstaTrans.globalDeclarations.declaration.add(cvd)

        val controller = UppaalUtil.createTemplate(nstaTrans, "_Controller")
        val init = UppaalUtil.createLocation(controller, "__init")
        controller.init = init
        val controllerDone = UppaalUtil.createLocation(controller, "done")
        val cEdge = UppaalUtil.createEdge(init, controllerDone)
        UppaalUtil.addSynchronization(cEdge, cvd.variable[0], SynchronizationKind.SEND)
        val il = SystemFactory.eINSTANCE.createInstantiationList()
        il.template.add(controller)
        nstaTrans.systemDeclarations.system.instantiationList.add(il)

        val counterVar = UppaalUtil.addCounterVariable(nstaTrans)

        nstaTrans.template.filter { it !== controller && it.edge.size != 0 }.forEach { eTemplate ->
            if (eTemplate.declarations == null)
                eTemplate.declarations = DeclarationsFactory.eINSTANCE.createLocalDeclarations()
            val edgeList = ArrayList(eTemplate.edge)

            var dvd = DeclarationsFactory.eINSTANCE.createDataVariableDeclaration()
            val flagVar = UppaalUtil.createVariable("_fl")
            val index = DeclarationsFactory.eINSTANCE.createValueIndex()
            index.sizeExpression = UppaalUtil.createLiteral("${edgeList.size}")
            flagVar.index.add(index)
            dvd.variable.add(flagVar)

            val tr = TypesFactory.eINSTANCE.createTypeReference()
            tr.referredType = nstaTrans.bool
            dvd.typeDefinition = tr

            eTemplate.declarations.declaration.add(dvd)
            dvd = EcoreUtil.copy(dvd)
            val varMeta = dvd.variable[0]
            varMeta.name = "_f"
            dvd.prefix = DataVariablePrefix.META
            eTemplate.declarations.declaration.add(dvd)

            val newInit = TemplatesFactory.eINSTANCE.createLocation()
            newInit.name = "__init__"
            eTemplate.location.add(newInit)

            val edge = UppaalUtil.createEdge(newInit, eTemplate.init)
            val ass = ExpressionsFactory.eINSTANCE.createAssignmentExpression()
            ass.operator = AssignmentOperator.EQUAL
            ass.firstExpr = UppaalUtil.createIdentifier(flagVar)
            ass.secondExpr = UppaalUtil.createIdentifier(varMeta)
            edge.update.add(ass)
            UppaalUtil.addSynchronization(edge, cvd.variable[0], SynchronizationKind.RECEIVE)
            eTemplate.init = newInit

            edgeList.forEach { e ->
                val id = UppaalUtil.createIdentifier(varMeta)
                id.index.add(UppaalUtil.createLiteral("${edgeList.indexOf(e)}"))
                val ass2 = ExpressionsFactory.eINSTANCE.createAssignmentExpression()
                ass2.operator = AssignmentOperator.EQUAL
                ass2.firstExpr = id
                ass2.secondExpr = UppaalUtil.createLiteral("true")
                e.update.add(ass2)
                if (e !== edge)
                    UppaalUtil.addCounterToEdge(e, counterVar)
            }

            eTemplate.location.filterIsInstance<ExponentialLocation>().forEach { it.exitRate = null }
        }
        val qs = HashSet<String>()
        qs.add("true")
        sys.processes.forEach { p ->
            val name = p.name
            val lSize = p.edges.size
            if (lSize > 0)
                qs.add("(forall (i : int[0, ${lSize - 1}]) $name._f[i])")
        }

        val q = qs.joinToString(" && ", "E<> (", ")")
        try {

            val temp = File.createTempFile("edgetest", ".xml")
            val bw = BufferedWriter(FileWriter(temp))
            bw.write(Serialization().main(nstaTrans).toString())
            bw.close()
            println(q)
            val proto = PrototypeDocument()
            proto.setProperty("synchronization", "")
            val tDoc = XMLReader(CharSequenceInputStream(Serialization().main(nstaTrans), "UTF-8"))
                    .parse(proto)
            val tSys = UppaalUtil.compile(tDoc)

            AbstractProperty.engineQuery(tSys, "E<> (_Controller.done)", OPTIONS) { _, _ -> }
            AbstractProperty.engineQuery(tSys, q, OPTIONS) { qr, _ ->
                fun doOk() {
                    sys.processes
                            .flatMap { it.edges.map { e -> e.edge } }.distinct()
                            .forEach { l -> l.setProperty("color", null) }

                    cb(object : SanityCheckResult() {
                        override fun write(out: PrintStream, err: PrintStream) {
                            out.println("All edges reachable!")
                        }

                        override fun toPanel(): JPanel {
                            val p = JPanel()
                            p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
                            p.add(JLabel("All edges reachable!"))
                            return p
                        }
                    })
                }
                if (qr.status == QueryResult.OK || qr.status == QueryResult.MAYBE_OK) {
                    doOk()
                } else {
                    try {
                        AbstractProperty.engineQuery(tSys, "E<> (_Controller.done)", OPTIONS) { qr2, ts2 ->
                            if (qr2.exception != null) {
                                qr2.exception.printStackTrace()
                            }
                            val ss = ts2[ts2.size() - 1].target
                            val vars = tSys.variables
                            if (ss.variableValues.size != vars.size) {
                                throw RuntimeException("Shits really on fire yo!")
                            }
                            val allEdges = HashSet<com.uppaal.model.core2.Edge>()
                            val reachable = HashSet<com.uppaal.model.core2.Edge>()
                            val unreachable = HashSet<com.uppaal.model.core2.Edge>()
                            val unreachableSysEdges = HashSet<SystemEdge>()
                            for ((varI, varName) in vars.withIndex()) {
                                val matcher = Pattern.compile("(.*)\\._fl\\[(\\d+)]$").matcher(varName)
                                if (matcher.matches()) {
                                    val process = sys.getProcess(sys.getProcessIndex(matcher.group(1)))
                                    val sysEdge = process.getEdge(Integer.parseInt(matcher.group(2)))
                                    allEdges.add(sysEdge.edge)
                                    if (ss.variableValues[varI] == 0) {
                                        unreachableSysEdges.add(sysEdge)
                                        unreachable.add(sysEdge.edge)
                                    } else {
                                        reachable.add(sysEdge.edge)
                                    }
                                }
                            }
                            if (unreachableSysEdges.isEmpty()) {
                                doOk()
                            } else {

                                allEdges.forEach { e ->
                                    e.setProperty("color", if (unreachable.contains(e)) {
                                        if (reachable.contains(e)) Color.YELLOW else Color.RED
                                    } else null)
                                }
                                cb(object : SanityCheckResult() {

                                    override fun write(out: PrintStream, err: PrintStream) {
                                        err.println("Unreachable edges found:")
                                        unreachableSysEdges
                                                .forEach { sl -> out.println(sl.processName + "(" + sl.name + ")") }
                                    }

                                    override fun toPanel(): JPanel {
                                        val p = JPanel()
                                        p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
                                        val label = JLabel("Unreachable edges found:")
                                        label.foreground = Color.RED
                                        p.add(label)
                                        unreachableSysEdges.forEach { sl ->
                                            val locLabel = JLabel("\t" + sl.processName + "(" + sl.name + ")")
                                            locLabel.foreground = Color.RED
                                            p.add(locLabel)
                                        }
                                        return p
                                    }
                                })
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } catch (e: EngineException) {
                        e.printStackTrace()
                    }

                }
            }
        } catch (e: EngineException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: XMLStreamException) {
            e.printStackTrace()
        }

    }

    companion object {
        private const val OPTIONS = "order 1\nreduction 1\nrepresentation 0\ntrace 1\nextrapolation 0\nhashsize 27\nreuse 0\nsmcparametric 1\nmodest 0\nstatistical 0.01 0.01 0.05 0.05 0.05 0.9 1.1 0.0 0.0 4096.0 0.01"
    }
}
