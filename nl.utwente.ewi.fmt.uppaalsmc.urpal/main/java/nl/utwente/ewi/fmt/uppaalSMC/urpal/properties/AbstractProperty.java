package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties;

import static nl.utwente.ewi.fmt.uppaalSMC.urpal.Application.DEBUG;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;

import com.uppaal.engine.EngineException;
import com.uppaal.engine.QueryFeedback;
import com.uppaal.engine.QueryResult;
import com.uppaal.model.core2.Document;
import com.uppaal.model.core2.Query;
import com.uppaal.model.system.UppaalSystem;
import com.uppaal.model.system.concrete.ConcreteTransitionRecord;
import com.uppaal.model.system.symbolic.SymbolicState;
import com.uppaal.model.system.symbolic.SymbolicTransition;

import nl.utwente.ewi.fmt.uppaalSMC.NSTA;
import nl.utwente.ewi.fmt.uppaalSMC.Serialization;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil;;

public abstract class AbstractProperty {
	public static final AbstractProperty[] properties = {/* new BreadthFirstBaseLine(), new DepthFirstBaseLine(),*/
			new DeadlockProperty(),
			/*new SystemLocationReachabilitySmart(), */new SystemLocationReachabilityMeta(),/* new SystemLocationReachabilityMetaSmart(),*/ 
			/*new TemplateLocationReachabilitySmart(),*/ new TemplateLocationReachabilityMeta(), /*new TemplateLocationReachabilityMetaSmart(),*/
			/*new SystemEdgeReachability(),*/ new SystemEdgeReachabilityMeta(), /*new SystemEdgeReachabilityMetaSmart(),*/
			new TemplateEdgeReachabilityMeta(), /*new TemplateEdgeReachabilityMetaSmart(),*/
			
			new InvariantViolationProperty(),
			new UnusedVariablesProperty() };
	static final String DEFAULT_OPTIONS_DFS = "order 1\nreduction 1\nrepresentation 0\ntrace 0\nextrapolation 0\nhashsize 27\nreuse 1\nsmcparametric 1\nmodest 0\nstatistical 0.01 0.01 0.05 0.05 0.05 0.9 1.1 0.0 0.0 4096.0 0.01";
	static final String DEFAULT_OPTIONS_BFS = "order 0\nreduction 1\nrepresentation 0\ntrace 0\nextrapolation 0\nhashsize 27\nreuse 1\nsmcparametric 1\nmodest 0\nstatistical 0.01 0.01 0.05 0.05 0.05 0.9 1.1 0.0 0.0 4096.0 0.01";
	public static int STATE_SPACE_SIZE = 0;
	static boolean AWAKE = false;

	public abstract void doCheck(NSTA nsta, Document doc, UppaalSystem system, Consumer<SanityCheckResult> cb);

	private static Semaphore sem = new Semaphore(Runtime.getRuntime().availableProcessors());

	private static Set<Thread> threads = new HashSet<>();

//	public static void interruptThreads() {
//		threads.forEach(Thread::interrupt);
//	}

	static void verifyta(File file, String query, String options, BiConsumer<String, String> cb)
			throws IOException {
		File temp = File.createTempFile("query", ".q");
		BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
		bw.write(query);
		bw.close();
		String cmd = "verifyta " + options + " " + file.getAbsolutePath() + " " + temp.getAbsolutePath();
		if (DEBUG)
			System.out.println("verifyta: " + query);
		new Thread(() -> {
			try {
				sem.acquire();
				threads.add(Thread.currentThread());
				try {
					Process proc = Runtime.getRuntime().exec(cmd);
					cb.accept(IOUtils.toString(new BufferedReader(new InputStreamReader(proc.getInputStream()))),
							IOUtils.toString(new BufferedReader(new InputStreamReader(proc.getErrorStream()))));
				} catch (Exception e) {
					e.printStackTrace();
					cb.accept(null, null);
				}
				threads.remove(Thread.currentThread());
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			} finally {
				sem.release();
			}
		}).start();
	}

	private static void verifyta(NSTA nsta, String query, String options, BiConsumer<String, String> cb)
			throws IOException {
		File temp = File.createTempFile("nsta", ".xml");
		BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
		bw.write(new Serialization().main(nsta).toString());
		bw.close();
		verifyta(temp, query, options, cb);
	}

	static void verifyta(NSTA nsta, String query, BiConsumer<String, String> cb) throws IOException {
		verifyta(nsta, query, "", cb);
	}

	public static long maxMem = 0;

	public static void engineQuery(UppaalSystem sys, SymbolicState init, String query, String options,
			BiConsumer<QueryResult, ArrayList<SymbolicTransition>> cb) throws IOException, EngineException {
		ArrayList<SymbolicTransition> trace = new ArrayList<>();

		QueryFeedback qf = new QueryFeedback() {

			public void setTrace(char paramChar, String paramString, ArrayList<SymbolicTransition> paramArrayList,
					int paramInt, QueryResult paramQueryResult) {
				trace.addAll(paramArrayList);
			}

			public void setTraceSMC(char paramChar, String paramString,
					ArrayList<ConcreteTransitionRecord> paramArrayList, int paramInt, QueryResult paramQueryResult) {
			}

			public void setSystemInfo(long paramLong1, long paramLong2, long paramLong3) {
			}

			public void setResultText(String paramString) {
			}

			public void setProgressAvail(boolean paramBoolean) {
			}

			public void setProgress(int paramInt, long paramLong1, long memory, long paramLong3, long paramLong4,
					long paramLong5, long paramLong6, long millis, long paramLong8, long paramLong9) {
				if (maxMem < memory) {
					maxMem = memory;
				}
			}

			public void setLength(int paramInt) {
			}

			public void setFeedback(String paramString) {
			}

			public void setCurrent(int paramInt) {
			}

			public void appendText(String paramString) {
			}
		};
		QueryResult result = init == null ? UppaalUtil.engine.query(sys, options, new Query(query, ""), qf)
				: UppaalUtil.engine.query(sys, init, options, new Query(query, ""), qf);

		cb.accept(result, trace);
	}

	public static void engineQuery(UppaalSystem sys, String query, String options,
			BiConsumer<QueryResult, ArrayList<SymbolicTransition>> cb) throws IOException, EngineException {
		engineQuery(sys, null, query, options, cb);
	}
}
