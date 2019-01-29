package nl.utwente.ewi.fmt.uppaalSMC.urpal;

/**
 * A sample Java code demonstrating the use of lib/model.jar.
 * The source code assumes that ModelDemo.java is inside Uppaal distribution.
 * Use the following commands to compile and run:
 *
 *     javac -cp lib/model.jar demo/ModelDemo.java
 *     java -cp demo:lib/model.jar:uppaal.jar ModelDemo hardcoded
 *
 * ModelDemo will produce result.xml and save a random trace into result.xtr.
 * ModelDemo can also read an external model file (use Control+C to stop):
 *
 *     java -cp demo:lib/model.jar:uppaal.jar ModelDemo demo/train-gate.xml
 *
 * @author Marius Mikucionis marius@cs.aau.dk
 */

import com.uppaal.engine.CannotEvaluateException;
import com.uppaal.engine.Engine;
import com.uppaal.engine.EngineException;
import com.uppaal.engine.EngineStub;
import com.uppaal.engine.Problem;
import com.uppaal.engine.QueryFeedback;
import com.uppaal.engine.QueryResult;
import com.uppaal.model.core2.Document;
import com.uppaal.model.core2.Edge;
import com.uppaal.model.core2.Location;
import com.uppaal.model.core2.Property;
import com.uppaal.model.core2.PrototypeDocument;
import com.uppaal.model.core2.Query;
import com.uppaal.model.core2.Template;
import com.uppaal.model.system.SystemEdge;
import com.uppaal.model.system.SystemLocation;
import com.uppaal.model.system.symbolic.SymbolicState;
import com.uppaal.model.system.symbolic.SymbolicTransition;
import com.uppaal.plugin.Repository;
import com.uppaal.model.system.concrete.ConcreteTransitionRecord;
import com.uppaal.model.system.UppaalSystem;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ModelDemo
{
    /**
     * Valid kinds of labels on locations.
     */
    public enum LKind {
        name, init, urgent, committed, invariant, exponentialrate, comments
    };
    /**
     * Valid kinds of labels on edges.
     */
    public enum EKind {
        select, guard, synchronisation, assignment, comments
    };
    /**
     * Sets a label on a location.
     * @param l the location on which the label is going to be attached
     * @param kind a kind of the label
     * @param value the label value (either boolean or String)
     * @param x the x coordinate of the label
     * @param y the y coordinate of the label
     */
    public static void setLabel(Location l, LKind kind, Object value, int x, int y) {
        l.setProperty(kind.name(), value);
        Property p = l.getProperty(kind.name());
        p.setProperty("x", x);
        p.setProperty("y", y);
    }
    /**
     * Adds a location to a template.
     * @param t the template
     * @param name a name for the new location
     * @param x the x coordinate of the location
     * @param y the y coordinate of the location
     * @return the new location instance
     */
    public static Location addLocation(Template t, String name, String exprate,
									   int x, int y)
    {
        Location l = t.createLocation();
        t.insert(l, null);
        l.setProperty("x", x);
        l.setProperty("y", y);
		if (name != null)
			setLabel(l, LKind.name, name, x, y-28);
		if (exprate != null)
			setLabel(l, LKind.exponentialrate, exprate, x, y-28-12);
        return l;
    }
    /**
     * Sets a label on an edge.
     * @param e the edge
     * @param kind the kind of the label
     * @param value the content of the label
     * @param x the x coordinate of the label
     * @param y the y coordinate of the label
     */
    public static void setLabel(Edge e, EKind kind, String value, int x, int y) {
        e.setProperty(kind.name(), value);
        Property p = e.getProperty(kind.name());
        p.setProperty("x", x);
        p.setProperty("y", y);
    }
    /**
     * Adds an edge to the template
     * @param t the template where the edge belongs
     * @param source the source location
     * @param target the target location
     * @param guard guard expression
     * @param sync synchronization expression
     * @param update update expression
     * @return
     */
    public static Edge addEdge(Template t, Location source, Location target,
							   String guard, String sync, String update)
    {
        Edge e = t.createEdge();
        t.insert(e, null);
        e.setSource(source);
        e.setTarget(target);
        int x = (source.getX()+target.getX())/2;
        int y = (source.getY()+target.getY())/2;
        if (guard != null) {
            setLabel(e, EKind.guard, guard, x-15, y-28);
        }
        if (sync != null) {
            setLabel(e, EKind.synchronisation, sync, x-15, y-14);
        }
        if (update != null) {
            setLabel(e, EKind.assignment, update, x-15, y);
        }
        return e;
    }

    public static void print(UppaalSystem sys, SymbolicState s) {
        System.out.print("(");
		        for (SystemLocation l: s.getLocations()) {
            System.out.print(l.getName()+", ");
        }
        int val[] = s.getVariableValues();
        for (int i=0; i<sys.getNoOfVariables(); i++) {
            System.out.print(sys.getVariableName(i)+"="+val[i]+", ");
        }
        List<String> constraints = new ArrayList<String>();
        s.getPolyhedron().getAllConstraints(constraints);
        for (String cs : constraints) {
            System.out.print(cs+", ");
        }
        System.out.println(")");
    }

    public static Document createSampleModel()
    {
		// create a new Uppaal model with default properties:
		Document doc = new Document(new PrototypeDocument());
		// add global variables:
		doc.setProperty("declaration", "int v;\n\nclock x,y,z;meta int l = 0;");
		// add a TA template:
		Template t = doc.createTemplate(); doc.insert(t, null);
		t.setProperty("name", "Experiment");
		// the template has initial location:
		Location l0 = addLocation(t, "L0", "1", 0, 0);
		l0.setProperty("init", true);
		// add another location to the right:
		Location l1 = addLocation(t, "L1", null, 150, 0);
		setLabel(l1, LKind.invariant, "x<=10", l1.getX()-7, l1.getY()+10);
		// add another location below to the right:
		Location l2 = addLocation(t, "L2", null, 150, 150);
		setLabel(l2, LKind.invariant, "y<=20", l2.getX()-7, l2.getY()+10);
		// add another location below:
		Location l3 = addLocation(t, "L3", "1", 0, 150);
		// add another location below:
		Location lf = addLocation(t, "Final", null, -150, 150);
		// create an edge L0->L1 with an update
		Edge e = addEdge(t, l0, l1, "v<2", null, "v=1,\nx=0");
		e.setProperty(EKind.comments.name(), "Execute L0->L1 with v=1");
		// create some more edges:
		addEdge(t, l1, l2, "x>=5", null, "v=2,\ny=0");
		addEdge(t, l2, l3, "y>=10", null, "v=3,\nz=0");
		addEdge(t, l3, l0, null, null, "v=4");
		addEdge(t, l3, lf, null, null, "v=5");
		// add system declaration:
		doc.setProperty("system",
						"Exp1=Experiment();\n"+
						"Exp2=Experiment();\n\n"+
						"system Exp1, Exp2;");
		return doc;
    }

    public static Document loadModel(String location) throws IOException
    {
		try {
			// try URL scheme (useful to fetch from Internet):
			return new PrototypeDocument().load(new URL(location));
		} catch (MalformedURLException ex) {
			// not URL, retry as it were a local filepath:
			return new PrototypeDocument().load(new URL("file", null, location));
		}
    }

    public static Engine connectToEngine() throws EngineException, IOException, URISyntaxException
    {
		String os = System.getProperty("os.name");
		String path = "/home/ramon.onis/master/macs/uppaal/bin-Linux/server";
		Engine engine = new Engine();
		engine.setServerPath(path);
		engine.setServerHost("localhost");
		engine.setConnectionMode(EngineStub.BOTH);
		engine.connect();
		return engine;
    }

    public static UppaalSystem compile(Engine engine, Document doc)
		throws EngineException, IOException
    {
		// compile the model into system:
		ArrayList<Problem> problems = new ArrayList<Problem>();
		UppaalSystem sys = engine.getSystem(doc, problems);
		if (!problems.isEmpty()) {
			boolean fatal = false;
			System.out.println("There are problems with the document:");
			for (Problem p : problems) {
				System.out.println(p.toString());
				if (!"warning".equals(p.getType())) { // ignore warnings
					fatal = true;
				}
			}
			if (fatal) {
				System.exit(1);
			}
		}
		return sys;
    }

    public static ArrayList<SymbolicTransition> symbolicSimulation(Engine engine,
																   UppaalSystem sys)
		throws EngineException, IOException, CannotEvaluateException
    {
		ArrayList<SymbolicTransition> trace = new ArrayList<SymbolicTransition>();
		// compute the initial state:
		SymbolicState state = engine.getInitialState(sys);
		// add the initial transition to the trace:
		trace.add(new SymbolicTransition(null, null, state));
		while (state != null) {
			print(sys, state);
			// compute the successors (including "deadlock"):
			ArrayList<SymbolicTransition> trans = engine.getTransitions(sys, state);
			// select a random transition:
			int n = (int)Math.floor(Math.random()*trans.size());
			SymbolicTransition tr = trans.get(n);
			// check the number of edges involved:
			if (tr.getSize()==0) {
				// no edges, something special (like "deadlock"):
				System.out.println(tr.getEdgeDescription());
			} else {
				// one or more edges involved, print them:
				for (SystemEdge e: tr.getEdges()) {
					System.out.print(e.getProcessName()+": "
									 + e.getEdge().getSource().getPropertyValue("name")
									 + " \u2192 "
									 + e.getEdge().getTarget().getPropertyValue("name")+", ");
				}
			}
			// jump to a successor state (null in case of deadlock):
			state = tr.getTarget();
			// if successfull, add the transition to the trace:
			if (state != null) trace.add(tr);
		}
        return trace;
    }

    public static void saveXTRFile(ArrayList<SymbolicTransition> trace, String file)
		throws IOException
    {
		/* BNF for the XTR format just in case
		   (it may change, thus don't rely on it)
		   <XTRFomat>  := <state> ( <state> <transition> ".\n" )* ".\n"
		   <state>     := <locations> ".\n" <polyhedron> ".\n" <variables> ".\n"
		   <locations> := ( <locationId> "\n" )*
		   <polyhedron> := ( <constraint> ".\n" )*
		   <constraint> := <clockId> "\n" clockId "\n" bound "\n"
		   <variables> := ( <varValue> "\n" )*
		   <transition> := ( <processId> <edgeId> )* ".\n"
		*/
		FileWriter out = new FileWriter(file);
		Iterator<SymbolicTransition> it = trace.iterator();
		it.next().getTarget().writeXTRFormat(out);
		while (it.hasNext()) {
			it.next().writeXTRFormat(out);
		}
		out.write(".\n");
		out.close();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length<1) {
            System.out.println("This is a demo of Uppaal model.jar API");
            System.out.println("Use one of the following arguments:");
            System.out.println("  hardcoded");
            System.out.println("  <URL>");
            System.out.println("  <path/file.xml>");
            System.exit(0);
        }
        try {
			Document doc = null;
			if ("hardcoded".equals(args[0])) {
				// create a hardcoded model:
				doc = createSampleModel();
			} else {
				// load model from a file/internet:
				doc = loadModel(args[0]);
			}
            // save the model into a file:
			doc.save("result.xml");

			// connect to the engine server:
			Engine engine = connectToEngine();

            // create a link to a local Uppaal process:
			UppaalSystem sys = compile(engine, doc);

			// perform a random symbolic simulation and get a trace:
			ArrayList<SymbolicTransition> trace = symbolicSimulation(engine, sys);

			// save the trace to an XTR file:
			saveXTRFile(trace, "result.xtr");

			// simple model-checking:
            Query query = new Query("E<> Exp1.Final", "can Exp1 finish?");
            System.out.println("===== Simple check =====");
			System.out.println("Result: "
							   +engine.query(sys, options, query, qf));

			// SMC model-checking:
			Query smcq = new Query("Pr[<=30](<> Exp1.Final)", "what is the probability of finishing?");
            System.out.println("===== SMC check =====");
			System.out.println("Result: "
							   +engine.query(sys, options, smcq, qf));

			// Model-checking with customized initial state:
            SymbolicState state = engine.getInitialState(sys);
            int vi = -1; // variable v index
            for (int i=0; i<sys.getNoOfVariables(); i++) {
                if ("v".equals(sys.getVariableName(i))) {
					vi = i; break;
				}
            }

			// Verification with custom initial state:
			int[] vars = state.getVariableValues();
			if (vi<0 || vi>=vars.length) {
				System.err.println("Variable v was not found");
				System.exit(1);
			}

			// set variable v to value 2:
			state.getVariableValues()[vi] = 2;
			// add constrain "x-0<=5":
			state.getPolyhedron().addNonStrictConstraint(1, 0, 5);
			// add constrain "0-x<=-5" (equivalent to "x>=5"):
			state.getPolyhedron().addNonStrictConstraint(0, 1, -5);
			// Notice that all other clocks will be constrained too,
			// because of other (initial) constrains like: x==y, y==z

			System.out.println("===== Custom check ===== ");
			System.out.println("Result: "
							   +engine.query(sys, state, options, query, qf));

            System.out.println("===== Custom SMC ===== ");
			System.out.println("Result: "
							   +engine.query(sys, state, options, smcq, qf));

			Query smcsim = new Query("simulate 1 [<=30] { v, x, y }", "get simulation trajectories");
            System.out.println("===== Custom Concrete Simulation ===== ");
			System.out.println("Result: "
							   +engine.query(sys, state, options, smcsim, qf));
			engine.disconnect();
        } catch (CannotEvaluateException ex) {
            ex.printStackTrace(System.err);
            System.exit(1);
        } catch (EngineException ex) {
            ex.printStackTrace(System.err);
            System.exit(1);
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
            System.exit(1);
        } catch (URISyntaxException ex) {
            ex.printStackTrace(System.err);
            System.exit(1);
		}
    }

    public static final String options = "order 0\n"
		+ "reduction 1\n"
		+ "representation 0\n"
		+ "trace 1\n"
		+ "extrapolation 0\n"
		+ "hashsize 27\n"
		+ "reuse 1\n"
		+ "smcparametric 1\n"
		+ "modest 0\n"
		+ "statistical 0.01 0.01 0.05 0.05 0.05 0.9 1.1 0.0 0.0 1280.0 0.01";

    public static QueryFeedback qf =
		new QueryFeedback() {
			@Override
			public void setProgressAvail(boolean availability)
			{
			}

			@Override
			public void setProgress(int load, long vm, long rss, long cached, long avail, long swap, long swapfree, long user, long sys, long timestamp)
			{
			}

			@Override
			public void setSystemInfo(long vmsize, long physsize, long swapsize)
			{
			}

			@Override
			public void setLength(int length)
			{
			}

			@Override
			public void setCurrent(int pos)
			{
			}

			@Override
			public void setTrace(char result, String feedback,
								 ArrayList<SymbolicTransition> trace, int cycle,
								 QueryResult queryVerificationResult)
			{
				System.out.println("feedback");
			}

			public void setTraceSMC(char result, String feedback,
									ArrayList<ConcreteTransitionRecord> trace, int cycle,
									QueryResult queryVerificationResult)
			{
			}
			@Override
			public void setFeedback(String feedback)
			{
				if (feedback != null && feedback.length() > 0) {
					System.out.println(feedback);
				}
			}

			@Override
			public void appendText(String s)
			{
				if (s != null && s.length() > 0) {
					System.out.println(s);
				}
			}

			@Override
			public void setResultText(String s)
			{
				if (s != null && s.length() > 0) {
					System.out.println(s);
				}
			}
		};
}