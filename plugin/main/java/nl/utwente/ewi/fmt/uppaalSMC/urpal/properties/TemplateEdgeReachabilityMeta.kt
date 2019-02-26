package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import java.awt.Color
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintStream
import java.util.ArrayList
import java.util.HashSet
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.xml.stream.XMLStreamException

import org.apache.commons.io.input.CharSequenceInputStream
import org.eclipse.emf.ecore.util.EcoreUtil
import org.muml.uppaal.declarations.DataVariablePrefix
import org.muml.uppaal.declarations.DeclarationsFactory
import org.muml.uppaal.declarations.ValueIndex
import org.muml.uppaal.declarations.global.ChannelPriority
import org.muml.uppaal.declarations.global.GlobalFactory
import org.muml.uppaal.declarations.system.SystemFactory
import org.muml.uppaal.expressions.AssignmentOperator
import org.muml.uppaal.expressions.ExpressionsFactory
import org.muml.uppaal.templates.LocationKind
import org.muml.uppaal.templates.SynchronizationKind
import org.muml.uppaal.types.TypesFactory

import com.uppaal.engine.EngineException
import com.uppaal.engine.QueryResult
import com.uppaal.model.core2.Document
import com.uppaal.model.core2.PrototypeDocument
import com.uppaal.model.io2.XMLReader
import com.uppaal.model.system.UppaalSystem

import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.Serialization
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil
import kotlin.math.max

@SanityCheck(name = "Template edge Reachability meta")
class TemplateEdgeReachabilityMeta : AbstractProperty() {

    override fun doCheck(nsta: NSTA, doc: Document, sys: UppaalSystem, cb: (SanityCheckResult) -> Unit) {
        val nstaTrans = EcoreUtil.copy(nsta)

        var dvd = DeclarationsFactory.eINSTANCE.createDataVariableDeclaration()
        val flagVar = UppaalUtil.createVariable("_fl")
        val index = DeclarationsFactory.eINSTANCE.createValueIndex()
        flagVar.index.add(index)
        dvd.variable.add(flagVar)

        val tr = TypesFactory.eINSTANCE.createTypeReference()
        tr.referredType = nstaTrans.bool
        dvd.typeDefinition = tr

        nstaTrans.globalDeclarations.declaration.add(dvd)
        dvd = EcoreUtil.copy(dvd)
        val varMeta = dvd.variable[0]
        varMeta.name = "_f"
        dvd.prefix = DataVariablePrefix.META
        nstaTrans.globalDeclarations.declaration.add(dvd)

        val cvdHighest = UppaalUtil.createChannelDeclaration(nstaTrans, "__copy__")
        cvdHighest.isBroadcast = true
        cvdHighest.isUrgent = true
        var cp: ChannelPriority? = nstaTrans.globalDeclarations.channelPriority
        if (cp == null) {
            cp = GlobalFactory.eINSTANCE.createChannelPriority()!!
            cp.item.add(GlobalFactory.eINSTANCE.createDefaultChannelPriority())
            nstaTrans.globalDeclarations.channelPriority = cp

        }
        nstaTrans.globalDeclarations.declaration.add(cvdHighest)
        val cpiHighest = GlobalFactory.eINSTANCE.createChannelList()
        cpiHighest.channelExpression.add(UppaalUtil.createIdentifier(cvdHighest.variable[0]))
        cp.item.add(cpiHighest)


        val controller = UppaalUtil.createTemplate(nstaTrans, "_Controller")
        val init = UppaalUtil.createLocation(controller, "__init")
        init.locationTimeKind = LocationKind.COMMITED
        controller.init = init
        val controllerDone = UppaalUtil.createLocation(controller, "done")
        val cEdge = UppaalUtil.createEdge(init, controllerDone)
        val ass = ExpressionsFactory.eINSTANCE.createAssignmentExpression()
        ass.operator = AssignmentOperator.EQUAL
        ass.firstExpr = UppaalUtil.createIdentifier(flagVar)
        ass.secondExpr = UppaalUtil.createIdentifier(varMeta)
        cEdge.update.add(ass)
        UppaalUtil.addSynchronization(cEdge, cvdHighest.variable[0], SynchronizationKind.SEND)
        val il = SystemFactory.eINSTANCE.createInstantiationList()
        il.template.add(controller)
        nstaTrans.systemDeclarations.system.instantiationList.add(il)

        val counterVar = UppaalUtil.addCounterVariable(nstaTrans)

        val templateEdges = ArrayList<com.uppaal.model.core2.Edge>()
        val offset = AtomicInteger()
        nstaTrans.template.forEach { eTemplate ->
            if (eTemplate === controller) return@forEach
            templateEdges.addAll(UppaalUtil.getEdges(doc, eTemplate.name))

            if (eTemplate.declarations == null)
                eTemplate.declarations = DeclarationsFactory.eINSTANCE.createLocalDeclarations()
            val edgeList = ArrayList(eTemplate.edge)

            eTemplate.edge.forEach { e ->
                val ass2 = ExpressionsFactory.eINSTANCE.createAssignmentExpression()
                ass2.operator = AssignmentOperator.EQUAL
                val id = UppaalUtil.createIdentifier(varMeta)
                id.index.add(UppaalUtil.createLiteral("" + (offset.get() + edgeList.indexOf(e))))
                ass2.firstExpr = id
                ass2.secondExpr = UppaalUtil.createLiteral("true")
                e.update.add(ass2)
                UppaalUtil.addCounterToEdge(e, counterVar)
            }
            offset.addAndGet(eTemplate.edge.size)
        }

        index.sizeExpression = UppaalUtil.createLiteral("${max(templateEdges.size, 1)}")
        (varMeta.index[0] as ValueIndex).sizeExpression = UppaalUtil.createLiteral("${max(templateEdges.size, 1)}")

        val q = if (templateEdges.size == 0) "E<>(true)" else "E<>(forall (i : int[0, ${templateEdges.size - 1}]) _f[i])"
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
                if (qr.status == QueryResult.OK || qr.status == QueryResult.MAYBE_OK) {
                    templateEdges.forEach { it.setProperty("color", null) }
                    cb(object : SanityCheckResult() {
                        override fun write(out: PrintStream, err: PrintStream) {
                            out.println("All edge reachable!")
                        }

                        override fun toPanel(): JPanel {
                            val p = JPanel()
                            p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
                            p.add(JLabel("All edge reachable!"))
                            return p
                        }
                    })
                } else {
                    try {
                        AbstractProperty.engineQuery(tSys, "E<> (_Controller.done)", OPTIONS) { qr2, ts2 ->
                            if (qr2.exception != null) {
                                qr2.exception.printStackTrace()
                            }
                            val ss = ts2[ts2.size - 1].target
                            val vars = tSys.variables
                            if (ss.variableValues.size != vars.size) {
                                throw RuntimeException("Shits really on fire yo!")
                            }
                            val unreachable = HashSet<com.uppaal.model.core2.Edge>()
                            for ((varI, varName) in vars.withIndex()) {
                                val matcher = Pattern.compile("_fl\\[(\\d+)]$").matcher(varName)
                                if (matcher.matches()) {
                                    val edge = templateEdges[Integer.parseInt(matcher.group(1))]
                                    if (ss.variableValues[varI] == 0) {
                                        edge.setProperty("color", Color.RED)
                                        unreachable.add(edge)
                                    }

                                }
                            }
                            cb(object : SanityCheckResult() {

                                override fun write(out: PrintStream, err: PrintStream) {
                                    err.println("Unreachable edges found:")
                                    unreachable.forEach { l -> out.println("${l.parent.getPropertyValue("name")}(${l.name})") }
                                }

                                override fun toPanel(): JPanel {
                                    val p = JPanel()
                                    p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
                                    val label = JLabel("Unreachable edges found:")
                                    label.foreground = Color.RED
                                    p.add(label)
                                    unreachable.forEach { l ->
                                        val locLabel = JLabel("\t${l.parent.getPropertyValue("name")}(${l.name})")
                                        p.add(locLabel)
                                    }

                                    return p
                                }
                            })
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
