package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import java.io.IOException

import com.uppaal.engine.EngineException
import com.uppaal.engine.QueryFeedback
import com.uppaal.engine.QueryResult
import com.uppaal.model.core2.Document
import com.uppaal.model.core2.Query
import com.uppaal.model.system.UppaalSystem
import com.uppaal.model.system.concrete.ConcreteTrace
import com.uppaal.model.system.symbolic.SymbolicState
import com.uppaal.model.system.symbolic.SymbolicTrace

import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import org.eclipse.xtext.tasks.Task
import java.awt.Color
import java.io.PrintStream
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel


abstract class AbstractProperty {

    protected abstract fun doCheck(nsta: NSTA, doc: Document, sys: UppaalSystem, cb: (SanityCheckResult) -> Unit)

    fun check(nsta: NSTA, doc: Document, sys: UppaalSystem, cb: (SanityCheckResult) -> Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit {
            doCheck(nsta, doc, sys) {
                it.name = this.javaClass.simpleName
                cb(it)
            }
        }
        try {
            future.get(15, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true);
            cb(timeoutResult())
        } catch (e: Exception) {
            cb(exceptionResult())
            throw e
        }
    }

    fun timeoutResult(): SanityCheckResult {
        val result = object : SanityCheckResult() {

            override fun write(out: PrintStream, err: PrintStream) {
                err.println("Timeout for $name")
            }

            override fun toPanel(): JPanel {
                val p = JPanel()
                p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
                val label = JLabel("Timeout")
                label.foreground = Color.RED
                p.add(label)
                return p
            }

            override fun getOutcome() = Outcome.TIMEOUT

        }
        result.name = this.javaClass.simpleName
        return result
    }
    fun exceptionResult(): SanityCheckResult {
        val result = object : SanityCheckResult() {

            override fun write(out: PrintStream, err: PrintStream) {
                err.println("Exception for $name")
            }

            override fun toPanel(): JPanel {
                val p = JPanel()
                p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
                val label = JLabel("Exception")
                label.foreground = Color.RED
                p.add(label)
                return p
            }

            override fun getOutcome() = Outcome.EXCEPTION

        }
        result.name = this.javaClass.simpleName
        return result
    }

    companion object {


        val properties = arrayOf(DeadlockProperty(), SystemLocationReachabilityMeta(),
//                TemplateLocationReachabilityMeta(),
                SystemEdgeReachabilityMeta(),
//                TemplateEdgeReachabilityMeta(),
                InvariantViolationProperty(), UnusedDeclarationsProperty())
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
