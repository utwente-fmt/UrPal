package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.input.CharSequenceInputStream;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.muml.uppaal.declarations.ChannelVariableDeclaration;
import org.muml.uppaal.declarations.DataVariableDeclaration;
import org.muml.uppaal.declarations.DataVariablePrefix;
import org.muml.uppaal.declarations.DeclarationsFactory;
import org.muml.uppaal.declarations.ValueIndex;
import org.muml.uppaal.declarations.Variable;
import org.muml.uppaal.declarations.global.ChannelList;
import org.muml.uppaal.declarations.global.ChannelPriority;
import org.muml.uppaal.declarations.global.GlobalFactory;
import org.muml.uppaal.declarations.system.InstantiationList;
import org.muml.uppaal.declarations.system.SystemFactory;
import org.muml.uppaal.expressions.AssignmentExpression;
import org.muml.uppaal.expressions.AssignmentOperator;
import org.muml.uppaal.expressions.ExpressionsFactory;
import org.muml.uppaal.expressions.IdentifierExpression;
import org.muml.uppaal.templates.Edge;
import org.muml.uppaal.templates.Location;
import org.muml.uppaal.templates.LocationKind;
import org.muml.uppaal.templates.SynchronizationKind;
import org.muml.uppaal.templates.Template;
import org.muml.uppaal.types.TypeReference;
import org.muml.uppaal.types.TypesFactory;

import com.uppaal.engine.CannotEvaluateException;
import com.uppaal.engine.EngineException;
import com.uppaal.engine.QueryResult;
import com.uppaal.model.core2.Document;
import com.uppaal.model.core2.PrototypeDocument;
import com.uppaal.model.io2.XMLReader;
import com.uppaal.model.system.SystemLocation;
import com.uppaal.model.system.UppaalSystem;
import com.uppaal.model.system.symbolic.SymbolicState;

import nl.utwente.ewi.fmt.uppaalSMC.ChanceNode;
import nl.utwente.ewi.fmt.uppaalSMC.NSTA;
import nl.utwente.ewi.fmt.uppaalSMC.Serialization;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil;

@SanityCheck(name = "System location Reachability meta smart", description = "")
public class SystemLocationReachabilityMetaSmart extends AbstractProperty {
	private static final String OPTIONS = "order 1\nreduction 1\nrepresentation 0\ntrace 0\nextrapolation 0\nhashsize 27\nreuse 0\nsmcparametric 1\nmodest 0\nstatistical 0.01 0.01 0.05 0.05 0.05 0.9 1.1 0.0 0.0 4096.0 0.01";

	@Override
	public void doCheck(NSTA nstaOrig, Document doc, UppaalSystem sys, Consumer<SanityCheckResult> cb) {
		long startTime = System.currentTimeMillis();
		NSTA nsta = EcoreUtil.copy(nstaOrig);

		ChannelVariableDeclaration cvdHighest = UppaalUtil.createChannelDeclaration(nsta, "_stopchan");
		ChannelPriority cp = nsta.getGlobalDeclarations().getChannelPriority();
		if (cp == null) {
			cp = GlobalFactory.eINSTANCE.createChannelPriority();
			cp.getItem().add(GlobalFactory.eINSTANCE.createDefaultChannelPriority());
			nsta.getGlobalDeclarations().setChannelPriority(cp);

		}
		nsta.getGlobalDeclarations().getDeclaration().add(cvdHighest);
		cvdHighest.setBroadcast(true);
		ChannelList cpiHighest = GlobalFactory.eINSTANCE.createChannelList();
		cpiHighest.getChannelExpression().add(UppaalUtil.createIdentifier(cvdHighest.getVariable().get(0)));
		cp.getItem().add(cpiHighest);

		Template stopper = UppaalUtil.createTemplate(nsta, "_Stopper");


		Location init = UppaalUtil.createLocation(stopper, "_init");
		stopper.setInit(init);
		Location init2 = UppaalUtil.createLocation(stopper, "_init2");
		init2.setLocationTimeKind(LocationKind.COMMITED);
		Location doneDone = UppaalUtil.createLocation(stopper, "done");
		doneDone.setInvariant(UppaalUtil.createLiteral("false"));
		Edge cEdge = UppaalUtil.createEdge(init2, doneDone);
		UppaalUtil.addSynchronization(cEdge, cvdHighest.getVariable().get(0), SynchronizationKind.SEND);
		InstantiationList il = SystemFactory.eINSTANCE.createInstantiationList();
		il.getTemplate().add(stopper);
		nsta.getSystemDeclarations().getSystem().getInstantiationList().add(il);

		Variable counterVar = UppaalUtil.addCounterVariable(nsta);

		nsta.getTemplate().forEach(eTemplate -> {

			if (eTemplate == stopper)
				return;
			if (eTemplate.getDeclarations() == null)
				eTemplate.setDeclarations(DeclarationsFactory.eINSTANCE.createLocalDeclarations());
			List<Location> locationList = new ArrayList<Location>(eTemplate.getLocation());

			DataVariableDeclaration dvd = DeclarationsFactory.eINSTANCE.createDataVariableDeclaration();
			dvd.setPrefix(DataVariablePrefix.META);
			Variable varMeta = UppaalUtil.createVariable("_f");
			ValueIndex index = DeclarationsFactory.eINSTANCE.createValueIndex();
			index.setSizeExpression(UppaalUtil
					.createLiteral("" + locationList.stream().filter(l -> !(l instanceof ChanceNode)).count()));
			varMeta.getIndex().add(index);
			dvd.getVariable().add(varMeta);

			TypeReference tr = TypesFactory.eINSTANCE.createTypeReference();
			tr.setReferredType(nsta.getBool());
			dvd.setTypeDefinition(tr);

			eTemplate.getDeclarations().getDeclaration().add(dvd);

			eTemplate.getEdge().forEach(e -> {
				if (e.getTarget() instanceof ChanceNode) {
					return;
				}
				AssignmentExpression ass2 = ExpressionsFactory.eINSTANCE.createAssignmentExpression();
				ass2.setOperator(AssignmentOperator.EQUAL);
				IdentifierExpression id = UppaalUtil.createIdentifier(varMeta);
				id.getIndex().add(UppaalUtil.createLiteral("" + locationList.indexOf(e.getTarget())));
				ass2.setFirstExpr(id);
				ass2.setSecondExpr(UppaalUtil.createLiteral("true"));
				e.getUpdate().add(ass2);
				UppaalUtil.addCounterToEdge(e, counterVar);
			});
		});
		Set<String> qs = new HashSet<>();

		sys.getProcesses().forEach(p -> {
			String name = p.getName();
			int lSize = p.getLocations().size();
			qs.add("(forall (i : int[0, " + (lSize - 1) + "]) " + name + "._f[i])");
		});
		maxMem = 0;

		String q = qs.stream().collect(Collectors.joining(" && ", "E<> (", ")"));
		try {
			File temp = File.createTempFile("loctest", ".xml");
			BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
			bw.write(new Serialization().main(nsta).toString());
			bw.close();
			System.out.println(q);
			PrototypeDocument proto = new PrototypeDocument();
			proto.setProperty("synchronization", "");
			Document tDoc = new XMLReader(new CharSequenceInputStream(new Serialization().main(nsta), "UTF-8"))
					.parse(proto);
			UppaalSystem tSys = UppaalUtil.compile(tDoc);

			engineQuery(tSys, q, OPTIONS, (qr, ts) -> {
				System.out.println("max mem: " + (maxMem));

				if (qr.getStatus() == QueryResult.OK || qr.getStatus() == QueryResult.MAYBE_OK) {
					sys.getProcesses().forEach(p -> p.getLocations().stream().map(SystemLocation::getLocation)
							.distinct().forEach(l -> l.setProperty("color", null)));

					System.out.println("time milis: " + (System.currentTimeMillis() - startTime));
					cb.accept(new SanityCheckResult() {
						@Override
						public void write(PrintStream out, PrintStream err) {
							out.println("All locations reachable!");
						}

						@Override
						public JPanel toPanel() {
							JPanel p = new JPanel();
							p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
							p.add(new JLabel("All locations reachable!"));
							return p;
						}
					});
				} else {
					try {
						SymbolicState initState = UppaalUtil.engine.getInitialState(tSys);
						SystemLocation[] locs = initState.getLocations();
						locs[locs.length - 1] = tSys.getLocation(tSys.getNoOfProcesses() - 1, 1);
						initState.setLocations(locs);
						Set<com.uppaal.model.core2.Location> allLocs = new HashSet<>();
						Set<com.uppaal.model.core2.Location> reachable = new HashSet<>();
						Set<com.uppaal.model.core2.Location> unreachable = new HashSet<>();
						Set<SystemLocation> unreachableSysLocs = new HashSet<>();
						sys.getProcesses().forEach(p -> {
							for (int i = 0; i < p.getLocations().size(); i++) {
								SystemLocation sysLoc = p.getLocation(i);
								String query = "E<>(" + p.getName() + "._f[" + i + "])";
								try {
									engineQuery(tSys, initState, query, OPTIONS, (qr2, ts2) -> {
										System.out.println("hay");
										allLocs.add(sysLoc.getLocation());
										if (qr2.getStatus() != QueryResult.OK && !((Boolean) sysLoc.getLocation().getPropertyValue("init"))) {
											unreachableSysLocs.add(sysLoc);
											unreachable.add(sysLoc.getLocation());
										} else {
											reachable.add(sysLoc.getLocation());
										}
									});
								} catch (IOException | EngineException e) {
									e.printStackTrace();
								}
							}
						});
						allLocs.forEach(l -> {
							l.setProperty("color",
									!unreachable.contains(l) ? null : reachable.contains(l) ? Color.YELLOW : Color.RED);
						});
						System.out.println("time milis: " + (System.currentTimeMillis() - startTime));
						cb.accept(new SanityCheckResult() {

							@Override
							public void write(PrintStream out, PrintStream err) {
								err.println("Unreachable locations found:");
								unreachableSysLocs.forEach(sl -> out.println(sl.getProcessName() + "." + sl.getName()));
							}

							@Override
							public JPanel toPanel() {
								JPanel p = new JPanel();
								p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
								JLabel label = new JLabel("Unreachable locations found:");
								label.setForeground(Color.RED);
								p.add(label);
								unreachableSysLocs.forEach(sl -> {
									JLabel locLabel = new JLabel("\t" + sl.getProcessName() + "." + sl.getName());
									locLabel.setForeground(Color.RED);
									p.add(locLabel);
								});

								return p;
							}
						});
					} catch (EngineException | CannotEvaluateException e) {
						e.printStackTrace();
					}
				}
				System.out.println("bazinga!");
			});
		} catch (EngineException | IOException | XMLStreamException e) {
			e.printStackTrace();
		}
	}
}
