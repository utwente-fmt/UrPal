package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import com.uppaal.model.core2.Document
import com.uppaal.model.system.UppaalSystem

import nl.utwente.ewi.fmt.uppaalSMC.NSTA

@SanityCheck(name = "Depth first")
class DepthFirstBaseLine : AbstractProperty() {

    override fun doCheck(nsta: NSTA, doc: Document, sys: UppaalSystem, cb: (SanityCheckResult) -> Unit) {
        val startTime = System.currentTimeMillis()
        AbstractProperty.engineQuery(sys, "A[](true)", AbstractProperty.DEFAULT_OPTIONS_DFS) { _, _ ->
            println("time milis: ${System.currentTimeMillis() - startTime}")
            println("max mem: ${AbstractProperty.maxMem}")
        }

    }

}
