package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Consumer;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.input.CharSequenceInputStream;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.muml.uppaal.declarations.ChannelVariableDeclaration;
import org.muml.uppaal.declarations.DataVariableDeclaration;
import org.muml.uppaal.declarations.DataVariablePrefix;
import org.muml.uppaal.declarations.DeclarationsFactory;
import org.muml.uppaal.declarations.Variable;
import org.muml.uppaal.declarations.global.ChannelList;
import org.muml.uppaal.declarations.global.ChannelPriority;
import org.muml.uppaal.declarations.global.GlobalFactory;
import org.muml.uppaal.expressions.BinaryExpression;
import org.muml.uppaal.expressions.ExpressionsFactory;
import org.muml.uppaal.expressions.IdentifierExpression;
import org.muml.uppaal.expressions.LiteralExpression;
import org.muml.uppaal.templates.Edge;
import org.muml.uppaal.templates.Location;
import org.muml.uppaal.templates.LocationKind;
import org.muml.uppaal.templates.SynchronizationKind;
import org.muml.uppaal.templates.TemplatesFactory;
import org.muml.uppaal.types.TypeReference;
import org.muml.uppaal.types.TypesFactory;

import com.uppaal.engine.EngineException;
import com.uppaal.engine.QueryResult;
import com.uppaal.gui.simulator.symbolic.SymbolicSimulator;
import com.uppaal.model.core2.Document;
import com.uppaal.model.core2.PrototypeDocument;
import com.uppaal.model.io2.XMLReader;
import com.uppaal.model.system.SystemEdge;
import com.uppaal.model.system.SystemEdgeSelect;
import com.uppaal.model.system.SystemLocation;
import com.uppaal.model.system.UppaalSystem;
import com.uppaal.model.system.symbolic.SymbolicState;
import com.uppaal.model.system.symbolic.SymbolicTransition;

import nl.utwente.ewi.fmt.uppaalSMC.NSTA;
import nl.utwente.ewi.fmt.uppaalSMC.Serialization;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil;

@SanityCheck(name = "Invariant Violation", description = "")
public class InvariantViolationProperty extends AbstractProperty {
	private static final String OPTIONS = "order 0\nreduction 1\nrepresentation 0\ntrace 1\nextrapolation 0\nhashsize 27\nreuse 0\nsmcparametric 1\nmodest 0\nstatistical 0.01 0.01 0.05 0.05 0.05 0.9 1.1 0.0 0.0 4096.0 0.01";

	@Override
	public void doCheck(NSTA nstaOrig, Document doc, UppaalSystem sys, Consumer<SanityCheckResult> cb) {
		long startTime = System.currentTimeMillis();
		maxMem = 0;
		NSTA nsta = EcoreUtil.copy(nstaOrig);
		DataVariableDeclaration dvd = DeclarationsFactory.eINSTANCE.createDataVariableDeclaration();
		dvd.setPrefix(DataVariablePrefix.META);
		TypeReference ref = TypesFactory.eINSTANCE.createTypeReference();
		ref.setReferredType(nsta.getInt());
		dvd.setTypeDefinition(ref);
		Variable var = UppaalUtil.createVariable("__isViolated__");
		dvd.getVariable().add(var);
		nsta.getGlobalDeclarations().getDeclaration().add(dvd);

		BinaryExpression violate = ExpressionsFactory.eINSTANCE.createAssignmentExpression();

		IdentifierExpression id = ExpressionsFactory.eINSTANCE.createIdentifierExpression();
		id.setIdentifier(var);
		violate.setFirstExpr(id);

		LiteralExpression lit = ExpressionsFactory.eINSTANCE.createLiteralExpression();
		lit.setText("1");
		violate.setSecondExpr(lit);

		ChannelVariableDeclaration cvdHigh = UppaalUtil.createChannelDeclaration(nsta, "_high");
		nsta.getGlobalDeclarations().getDeclaration().add(cvdHigh);
		cvdHigh.setBroadcast(true);

		ChannelVariableDeclaration cvdHighest = UppaalUtil.createChannelDeclaration(nsta, "_highest");
		nsta.getGlobalDeclarations().getDeclaration().add(cvdHighest);
		cvdHighest.setBroadcast(true);

		ChannelPriority cp = nsta.getGlobalDeclarations().getChannelPriority();
		if (cp == null) {
			cp = GlobalFactory.eINSTANCE.createChannelPriority();
			cp.getItem().add(GlobalFactory.eINSTANCE.createDefaultChannelPriority());
			nsta.getGlobalDeclarations().setChannelPriority(cp);

		}

		ChannelList cpiHigh = GlobalFactory.eINSTANCE.createChannelList();
		ChannelList cpiHighest = GlobalFactory.eINSTANCE.createChannelList();

		cpiHigh.getChannelExpression().add(UppaalUtil.createIdentifier(cvdHigh.getVariable().get(0)));
		cpiHighest.getChannelExpression().add(UppaalUtil.createIdentifier(cvdHighest.getVariable().get(0)));

		cp.getItem().add(cpiHigh);
		cp.getItem().add(cpiHighest);

		nsta.getTemplate().forEach(t -> {
			Location lBad = null;
			int i = 0;

			for (Edge e : new ArrayList<>(t.getEdge())) {
				Location target = e.getTarget();
				if (target.getInvariant() == null) {
					continue;
				}
				if (lBad == null) {
					lBad = TemplatesFactory.eINSTANCE.createLocation();
					lBad.setName("__invariantViolated__");
					lBad.setLocationTimeKind(LocationKind.COMMITED);
					t.getLocation().add(lBad);
				}
				Location lCopy = TemplatesFactory.eINSTANCE.createLocation();
				lCopy.setName("__copy_" + i);
				lCopy.setLocationTimeKind(LocationKind.COMMITED);
				t.getLocation().add(lCopy);

				e.setTarget(lCopy);

				Edge eBad = TemplatesFactory.eINSTANCE.createEdge();
				eBad.setSource(lCopy);
				eBad.setTarget(lBad);
				eBad.getUpdate().add(EcoreUtil.copy(violate));
				UppaalUtil.addSynchronization(eBad, cvdHigh.getVariable().get(0), SynchronizationKind.SEND);
				t.getEdge().add(eBad);

				Edge eGood = TemplatesFactory.eINSTANCE.createEdge();
				eGood.setSource(lCopy);
				eGood.setTarget(target);
				eGood.setGuard(UppaalUtil.invariantToGuard(target.getInvariant()));
				UppaalUtil.addSynchronization(eGood, cvdHighest.getVariable().get(0), SynchronizationKind.SEND);
				t.getEdge().add(eGood);
				i++;
			}
		});
		try {
			File temp = File.createTempFile("invarianttest", ".xml");
			BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
			bw.write(new Serialization().main(nsta).toString());
			bw.close();
			PrototypeDocument proto = new PrototypeDocument();
			proto.setProperty("synchronization", "");
			Document tDoc = new XMLReader(new CharSequenceInputStream(new Serialization().main(nsta), "UTF-8"))
					.parse(proto);
			UppaalSystem tSys = UppaalUtil.compile(tDoc);
			engineQuery(tSys, "A[] (not __isViolated__)", OPTIONS, (qr, ts) -> {
				ArrayList<SymbolicTransition> tsFinal = ts.isEmpty() ? ts : transformTrace(ts, sys);
				System.out.println("trace size: " + tsFinal.size());
				System.out.println("time milis: " + (System.currentTimeMillis() - startTime));
				System.out.println("max mem: " + (maxMem));
				cb.accept(new SanityCheckResult() {
					@Override
					public void write(PrintStream out, PrintStream err) {
						if (qr.getStatus() == QueryResult.MAYBE_OK || qr.getStatus() == QueryResult.OK) {
							out.println("No invariant violations found!");
						} else if (qr.getStatus() == QueryResult.UNCHECKED) {
							err.println("An unknown error has occurred! See trace in the GUI:");
						} else {
							err.println("Location invariant violated! See trace in the GUI:");
							// ts.forEach(s -> System.err.println(s.traceFormat()));
						}
					}

					@Override
					public JPanel toPanel() {
						JPanel p = new JPanel();
						p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
						if (qr.getStatus() == QueryResult.MAYBE_OK || qr.getStatus() == QueryResult.OK) {
							JLabel label = new JLabel("No invariant violations found!");
							// label.setForeground(Color.GREEN);
							p.add(label);
						} else {
							JLabel label = new JLabel(
									(qr.getStatus() == QueryResult.UNCHECKED ? "An unknown error has occurred"
											: "Invariant violation found!") + " Click button below to load trace:");
							label.setForeground(Color.RED);
							p.add(label);
							JButton button = new JButton("Load trace");
							button.addActionListener(a -> {
								SymbolicSimulator sim = UppaalUtil.getSystemInspector(button).simulator;
								sim.uppaalSystem.set(sys);
								sim.a(tsFinal, 0);
							});
							p.add(button);

						}
						return p;
					}
				});
			});
		} catch (IOException | XMLStreamException | EngineException e) {
			e.printStackTrace();
		}
	}

	private ArrayList<SymbolicTransition> transformTrace(ArrayList<SymbolicTransition> ts, UppaalSystem origSys) {
		SymbolicState prev = null;
		ArrayList<SymbolicTransition> result = new ArrayList<>();
		Iterator<SymbolicTransition> it = ts.iterator();
		outer: while (it.hasNext()) {
			SymbolicTransition curr = it.next();
			SystemEdgeSelect[] edgesWs = curr.getEdges();
			if (prev != null) {
				for (int i = 0; i < edgesWs.length; i++) {
					SystemEdgeSelect edgeWs = edgesWs[i];
					com.uppaal.model.system.Process origProccess = origSys.getProcess(edgeWs.getProcess().getIndex());
					SystemEdge origEdge = origProccess.getEdge(edgeWs.getIndex());

					edgesWs[i] = new SystemEdgeSelect(origEdge, edgeWs.getSelectList());
				}
			}
			SymbolicState transTarget;

			SystemLocation[] locations;
			inner: while (true) {
				transTarget = curr.getTarget();
				locations = transTarget.getLocations();
				for (int i = 0; i < locations.length; i++) {
					SystemLocation location = locations[i];
					com.uppaal.model.system.Process origProcess = origSys.getProcess(i);
					if (origProcess.getLocations().size() <= location.getIndex()) {
						if (it.hasNext()) {
							curr = it.next();
							continue inner;
						} else {
							break outer;
						}
					}
					locations[i] = origProcess.getLocation(location.getIndex());
				}
				break;
			}

			SymbolicState origTarget = new SymbolicState(locations, transTarget.getVariableValues(),
					transTarget.getPolyhedron());
			result.add(new SymbolicTransition(prev, edgesWs, origTarget));
			prev = origTarget;
		}
		return result;
	}
}
