package nl.utwente.ewi.fmt.uppaalSMC.urpal.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.uppaal.engine.EngineException
import com.uppaal.model.core2.PrototypeDocument
import com.uppaal.model.io2.XMLReader
import com.uppaal.model.system.UppaalSystem
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.AbstractProperty
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.MainUI.load
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil
import java.io.IOException


class Application : CliktCommand() {
    val input by argument(help="Input Uppaal model").file(exists = true, folderOkay = false, readable = true)
    val output by option(help="Output results file").file()
    val checks by option(help="Selected checks, comma separated. Run all if absent.")
            .choice(*AbstractProperty.properties.map { it.shortName() to it }.toTypedArray())
            .split(",")

    override fun run() {
        val doc = XMLReader(input.inputStream()).parse(PrototypeDocument())
        val nsta = load(doc)
        val sys: UppaalSystem
        try {
            sys = UppaalUtil.compile(doc)
        } catch (e: EngineException) {
            e.printStackTrace()
            return
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }

        val actualChecks = if (checks?.isEmpty() != false) AbstractProperty.properties else checks!!.toTypedArray()
        val results = mutableMapOf<AbstractProperty, Double>()
        actualChecks.forEach { property ->
            property.javaClass.newInstance().check(nsta, doc, sys) { cb ->
                results[property] = cb.quality()
            }
        }
        val text = results.map { "${it.key.shortName()}, ${it.value}" }.joinToString("\n")
        output?.writeText(text)
        print(text)
        UppaalUtil.engine.cancel()
        System.exit(0)
    }
}
fun main(args: Array<String>) = Application().main(args)