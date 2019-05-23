package nl.utwente.ewi.fmt.uppaalSMC.urpal.util

import com.uppaal.plugin.Repository
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.SanityCheckResult
import java.beans.PropertyChangeListener

class SanityCheckRepository : Repository<SanityCheckResult> {
    override fun getName() = "SanityCheckResult"

    override fun addListener(listener: PropertyChangeListener?) = TODO("not implemented")
    override fun addListener(change: Repository.ChangeType?, listener: PropertyChangeListener?) = TODO("not implemented")
    override fun removeListener(listener: PropertyChangeListener?) = TODO("not implemented")
    override fun removeListener(change: Repository.ChangeType?, listener: PropertyChangeListener?) = TODO("not implemented")
    override fun get() = TODO("not implemented")
    override fun set(newitem: SanityCheckResult?) = TODO("not implemented")
    override fun fire(change: Repository.ChangeType?) = TODO("not implemented")
}