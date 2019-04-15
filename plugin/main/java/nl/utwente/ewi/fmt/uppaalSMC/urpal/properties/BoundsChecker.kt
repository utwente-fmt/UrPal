package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import com.uppaal.model.core2.Document
import com.uppaal.model.system.UppaalSystem
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import org.muml.uppaal.types.RangeTypeSpecification

@SanityCheck(name="Bounds checker")
class BoundsChecker : AbstractProperty() {
    override fun doCheck(nsta: NSTA, doc: Document, sys: UppaalSystem, cb: (SanityCheckResult) -> Unit) {
        nsta.eAllContents().forEach {
            if (it is RangeTypeSpecification) {
                if (it.bounds == null) {

                }
            }
        }
    }
}