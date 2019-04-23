package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import java.io.IOException
import java.util.ArrayList

import com.uppaal.engine.EngineException
import com.uppaal.engine.QueryFeedback
import com.uppaal.engine.QueryResult
import com.uppaal.model.core2.Document
import com.uppaal.model.core2.Query
import com.uppaal.model.system.UppaalSystem
import com.uppaal.model.system.concrete.ConcreteTrace
import com.uppaal.model.system.concrete.ConcreteTransitionRecord
import com.uppaal.model.system.symbolic.SymbolicState
import com.uppaal.model.system.symbolic.SymbolicTrace
import com.uppaal.model.system.symbolic.SymbolicTransition

import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil

abstract class AbstractProperty {

    abstract fun doCheck(nsta: NSTA, doc: Document, sys: UppaalSystem, cb: (SanityCheckResult) -> Unit)

    companion object {
        val properties = arrayOf(DeadlockProperty(), SystemLocationReachabilityMeta(),
                TemplateLocationReachabilityMeta(),
                SystemEdgeReachabilityMeta(),
                TemplateEdgeReachabilityMeta(),
                InvariantViolationProperty(), UnusedVariablesProperty())
        internal const val DEFAULT_OPTIONS_DFS = "order 1\nreduction 1\nrepresentation 0\ntrace 0\nextrapolation 0\nhashsize 27\nreuse 1\nsmcparametric 1\nmodest 0\nstatistical 0.01 0.01 0.05 0.05 0.05 0.9 1.1 0.0 0.0 4096.0 0.01"
        internal const val DEFAULT_OPTIONS_BFS = "order 0\nreduction 1\nrepresentation 0\ntrace 0\nextrapolation 0\nhashsize 27\nreuse 1\nsmcparametric 1\nmodest 0\nstatistical 0.01 0.01 0.05 0.05 0.05 0.9 1.1 0.0 0.0 4096.0 0.01"
        var STATE_SPACE_SIZE = 0

        internal var maxMem: Long = 0

        @Throws(IOException::class, EngineException::class)
        internal fun engineQuery(sys: UppaalSystem, init: SymbolicState?, query: String, options: String,
                                 cb: (QueryResult, SymbolicTrace) -> Unit) {
            var trace = SymbolicTrace()

            val qf = object : QueryFeedback {

                override fun setTrace(paramChar: Char, paramString: String?, paramConcreteTrace: ConcreteTrace, paramQueryResult: QueryResult) {
                }

                override fun setTrace(paramChar: Char, paramString: String?, paramSymbolicTrace: SymbolicTrace, paramQueryResult: QueryResult) {
                    trace = paramSymbolicTrace
                }

                override fun setSystemInfo(paramLong1: Long, paramLong2: Long, paramLong3: Long) {}

                override fun setResultText(paramString: String) {}

                override fun setProgressAvail(paramBoolean: Boolean) {}

                override fun setProgress(paramInt: Int, paramLong1: Long, memory: Long, paramLong3: Long, paramLong4: Long,
                                         paramLong5: Long, paramLong6: Long, millis: Long, paramLong8: Long, paramLong9: Long) {
                    if (maxMem < memory) {
                        maxMem = memory
                    }
                }

                override fun setLength(paramInt: Int) {}

                override fun setFeedback(paramString: String?) {}

                override fun setCurrent(paramInt: Int) {}

                override fun appendText(paramString: String) {}
            }
            val result = if (init == null)
                UppaalUtil.engine.query(sys, options, Query(query, ""), qf)
            else
                UppaalUtil.engine.query(sys, init, options, Query(query, ""), qf)

            cb(result, trace)
        }

        @Throws(IOException::class, EngineException::class)
        internal fun engineQuery(sys: UppaalSystem, query: String, options: String,
                                 cb: (QueryResult, SymbolicTrace) -> Unit) {
            engineQuery(sys, null, query, options, cb)
        }
    }
}
