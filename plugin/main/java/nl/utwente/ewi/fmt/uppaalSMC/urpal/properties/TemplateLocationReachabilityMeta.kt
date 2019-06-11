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

import nl.utwente.ewi.fmt.uppaalSMC.ChanceNode
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.Serialization
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil

@SanityCheck(name = "Template location Reachability meta")
class TemplateLocationReachabilityMeta : AbstractProperty() {

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

        val templateLocs = ArrayList<com.uppaal.model.core2.Location>()
        val offset = AtomicInteger()
        nstaTrans.template.forEach { eTemplate ->
            if (eTemplate === controller) return@forEach
            templateLocs.addAll(UppaalUtil.getLocations(doc, eTemplate.name))

            if (eTemplate.declarations == null)
                eTemplate.declarations = DeclarationsFactory.eINSTANCE.createLocalDeclarations()
            val locationList = ArrayList(eTemplate.location)

            eTemplate.edge.forEach { e ->
                if (e.target !is ChanceNode) {
                    val ass2 = ExpressionsFactory.eINSTANCE.createAssignmentExpression()
                    ass2.operator = AssignmentOperator.EQUAL
                    val id = UppaalUtil.createIdentifier(varMeta)
                    id.index.add(UppaalUtil.createLiteral("${offset.get() + locationList.indexOf(e.target)}"))
                    ass2.firstExpr = id
                    ass2.secondExpr = UppaalUtil.createLiteral("true")
                    e.update.add(ass2)
                    UppaalUtil.addCounterToEdge(e, counterVar)
                }
            }
            offset.addAndGet(eTemplate.location.size)
        }

        index.sizeExpression = UppaalUtil.createLiteral("${templateLocs.size}")
        (varMeta.index[0] as ValueIndex).sizeExpression = UppaalUtil.createLiteral("${templateLocs.size}")

        val q = if (templateLocs.size == 1) "E<>(true)" else "E<>(forall (i : int[0, ${templateLocs.size - 1}]) _f[i])"
        try {
            val proto = PrototypeDocument()
            proto.setProperty("synchronization", "")
            val tDoc = XMLReader(CharSequenceInputStream(Serialization().main(nstaTrans), "UTF-8"))
                    .parse(proto)
            val tSys = UppaalUtil.compile(tDoc)

            engineQuery(tSys, "E<> (_Controller.done)", OPTIONS) { _, _ -> }

            engineQuery(tSys, q, OPTIONS) { qr, _ ->

                if (qr.status == QueryResult.OK || qr.status == QueryResult.MAYBE_OK) {
                    templateLocs.forEach { l -> l.setProperty("color", null) }
                    cb(object : SanityCheckResult() {
                        override fun quality() = 1.0
                        override fun getOutcome() = Outcome.SATISFIED

                        override fun write(out: PrintStream, err: PrintStream) {
                            out.println("All locations reachable!")
                        }

                        override fun toPanel(): JPanel {
                            val p = JPanel()
                            p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
                            p.add(JLabel("All locations reachable!"))
                            return p
                        }
                    })
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
                            val unreachable = HashSet<com.uppaal.model.core2.Location>()
                            for ((varI, varName) in vars.withIndex()) {
                                val matcher = Pattern.compile("_fl\\[(\\d+)]$").matcher(varName)
                                if (matcher.matches()) {
                                    val loc = templateLocs[Integer.parseInt(matcher.group(1))]
                                    if (ss.variableValues[varI] == 0 && !(loc.getPropertyValue("init") as Boolean)) {
                                        loc.setProperty("color", Color.RED)
                                        unreachable.add(loc)
                                    }

                                }
                            }
                            cb(object : SanityCheckResult() {
                                override fun quality() = 0.0
                                override fun getOutcome() = Outcome.VIOLATED

                                override fun write(out: PrintStream, err: PrintStream) {
                                    err.println("Unreachable locations found:")
                                    unreachable
                                            .forEach { out.println("${it.parent.getPropertyValue("name")}.${it.name}") }
                                }

                                override fun toPanel(): JPanel {
                                    val p = JPanel()
                                    p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
                                    val label = JLabel("Unreachable locations found:")
                                    label.foreground = Color.RED
                                    p.add(label)
                                    unreachable.forEach {
                                        val locLabel = JLabel("\t${it.parent.getPropertyValue("name")}.${it.name}")
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
