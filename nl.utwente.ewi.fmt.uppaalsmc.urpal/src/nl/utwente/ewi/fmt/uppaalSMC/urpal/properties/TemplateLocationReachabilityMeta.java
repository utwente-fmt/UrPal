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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.uppaal.engine.EngineException;
import com.uppaal.engine.QueryResult;
import com.uppaal.model.core2.Document;
import com.uppaal.model.core2.PrototypeDocument;
import com.uppaal.model.io2.XMLReader;
import com.uppaal.model.system.UppaalSystem;
import com.uppaal.model.system.symbolic.SymbolicState;

import nl.utwente.ewi.fmt.uppaalSMC.ChanceNode;
import nl.utwente.ewi.fmt.uppaalSMC.NSTA;
import nl.utwente.ewi.fmt.uppaalSMC.Serialization;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil;

@SanityCheck(name = "Template location Reachability meta", description = "")
public class TemplateLocationReachabilityMeta extends AbstractProperty {
	
	private static final String OPTIONS = "order 1\nreduction 1\nrepresentation 0\ntrace 1\nextrapolation 0\nhashsize 27\nreuse 0\nsmcparametric 1\nmodest 0\nstatistical 0.01 0.01 0.05 0.05 0.05 0.9 1.1 0.0 0.0 4096.0 0.01";
	@Override
	public void doCheck(NSTA nstaOrig, Document doc, UppaalSystem sys, Consumer<SanityCheckResult> cb) {
		long startTime = System.currentTimeMillis();
		NSTA nsta = EcoreUtil.copy(nstaOrig);
		
		DataVariableDeclaration dvd = DeclarationsFactory.eINSTANCE.createDataVariableDeclaration();
		Variable var = UppaalUtil.createVariable("_fl");
		ValueIndex index = DeclarationsFactory.eINSTANCE.createValueIndex();
		var.getIndex().add(index);
		dvd.getVariable().add(var);

		TypeReference tr = TypesFactory.eINSTANCE.createTypeReference();
		tr.setReferredType(nsta.getBool());
		dvd.setTypeDefinition(tr);

		nsta.getGlobalDeclarations().getDeclaration().add(dvd);
		dvd = EcoreUtil.copy(dvd);
		Variable varMeta = dvd.getVariable().get(0);
		varMeta.setName("_f");
		dvd.setPrefix(DataVariablePrefix.META);
		nsta.getGlobalDeclarations().getDeclaration().add(dvd);
		
		ChannelVariableDeclaration cvdHighest = UppaalUtil.createChannelDeclaration(nsta, "__copy__");
		cvdHighest.setBroadcast(true);
		cvdHighest.setUrgent(true);
		ChannelPriority cp = nsta.getGlobalDeclarations().getChannelPriority();
		if (cp == null) {
			cp = GlobalFactory.eINSTANCE.createChannelPriority();
			cp.getItem().add(GlobalFactory.eINSTANCE.createDefaultChannelPriority());
			nsta.getGlobalDeclarations().setChannelPriority(cp);

		}
		nsta.getGlobalDeclarations().getDeclaration().add(cvdHighest);
		ChannelList cpiHighest = GlobalFactory.eINSTANCE.createChannelList();
		cpiHighest.getChannelExpression().add(UppaalUtil.createIdentifier(cvdHighest.getVariable().get(0)));
		cp.getItem().add(cpiHighest);
		

		Template controller = UppaalUtil.createTemplate(nsta, "_Controller");
		Location init = UppaalUtil.createLocation(controller, "__init");
		init.setLocationTimeKind(LocationKind.COMMITED);
		controller.setInit(init);
		Location controllerDone = UppaalUtil.createLocation(controller, "done");
		Edge cEdge = UppaalUtil.createEdge(init, controllerDone);
		AssignmentExpression ass = ExpressionsFactory.eINSTANCE.createAssignmentExpression();
		ass.setOperator(AssignmentOperator.EQUAL);
		ass.setFirstExpr(UppaalUtil.createIdentifier(var));
		ass.setSecondExpr(UppaalUtil.createIdentifier(varMeta));
		cEdge.getUpdate().add(ass);
		UppaalUtil.addSynchronization(cEdge, cvdHighest.getVariable().get(0), SynchronizationKind.SEND);
		InstantiationList il = SystemFactory.eINSTANCE.createInstantiationList();
		il.getTemplate().add(controller);
		nsta.getSystemDeclarations().getSystem().getInstantiationList().add(il);

		Variable counterVar = UppaalUtil.addCounterVariable(nsta);
		
		List<com.uppaal.model.core2.Location> templateLocs = new ArrayList<>();
		AtomicInteger offset = new AtomicInteger();
		nsta.getTemplate().forEach(eTemplate -> {
			if (eTemplate == controller) return;
			templateLocs.addAll(UppaalUtil.getLocations(doc, eTemplate.getName()));
			
			if (eTemplate.getDeclarations() == null)
				eTemplate.setDeclarations(DeclarationsFactory.eINSTANCE.createLocalDeclarations());
			List<Location> locationList = new ArrayList<Location>(eTemplate.getLocation());

			eTemplate.getEdge().forEach(e -> {
				if (e.getTarget() instanceof ChanceNode) {
					return;
				}
				
				AssignmentExpression ass2 = ExpressionsFactory.eINSTANCE.createAssignmentExpression();
				ass2.setOperator(AssignmentOperator.EQUAL);
				IdentifierExpression id = UppaalUtil.createIdentifier(varMeta);
				id.getIndex().add(UppaalUtil.createLiteral("" + (offset.get() + locationList.indexOf(e.getTarget()))));
				ass2.setFirstExpr(id);
				ass2.setSecondExpr(UppaalUtil.createLiteral("true"));
				e.getUpdate().add(ass2);
				UppaalUtil.addCounterToEdge(e, counterVar);
			});
			offset.addAndGet(eTemplate.getLocation().size());
		});

		index.setSizeExpression(UppaalUtil.createLiteral("" + templateLocs.size()));
		((ValueIndex) varMeta.getIndex().get(0)).setSizeExpression(UppaalUtil.createLiteral("" + templateLocs.size()));
		
		String q = "E<>(forall (i : int[0, " + (templateLocs.size() - 1) + "]) _f[i])";
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

			if (!AWAKE) {
				engineQuery(tSys, "E<> (_Controller.done)", OPTIONS, (a, b) -> {});
			}
			maxMem = 0;
			engineQuery(tSys, q, OPTIONS, (qr, ts) -> {
				System.out.println("time milis: " + (System.currentTimeMillis() - startTime));
				System.out.println("max mem: " + (maxMem));

				if (qr.getStatus() == QueryResult.OK || qr.getStatus() == QueryResult.MAYBE_OK) {
					templateLocs.forEach(l -> l.setProperty("color", null));
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
						engineQuery(tSys, "E<> (_Controller.done)", OPTIONS, (qr2, ts2) -> {
							if (qr2.getException() != null) {
								qr2.getException().printStackTrace();
							}
							SymbolicState ss = ts2.get(ts2.size() - 1).getTarget();
							List<String> vars = tSys.getVariables();
							if (ss.getVariableValues().length != vars.size()) {
								throw new RuntimeException("Shits really on fire yo!");
							}
							Set<com.uppaal.model.core2.Location> reachable = new HashSet<>();
							Set<com.uppaal.model.core2.Location> unreachable = new HashSet<>();
							int varI = 0;
							for (String varName : vars) {
								Matcher matcher = Pattern.compile("_fl\\[(\\d+)\\]$").matcher(varName);
								if (matcher.matches()) {
									com.uppaal.model.core2.Location loc = templateLocs.get(Integer.parseInt(matcher.group(1)));
									if (ss.getVariableValues()[varI] == 0 && !((Boolean) loc.getPropertyValue("init"))) {
										loc.setProperty("color", Color.RED);
										unreachable.add(loc);
									} else {
										loc.setProperty("color", null);
										reachable.add(loc);
									}
									
								}
								varI++;
							}
							cb.accept(new SanityCheckResult() {

								@Override
								public void write(PrintStream out, PrintStream err) {
									err.println("Unreachable locations found:");
									unreachable
											.forEach(l -> out.println(l.getParent().getPropertyValue("name") + "." + l.getName()));
								}

								@Override
								public JPanel toPanel() {
									JPanel p = new JPanel();
									p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
									JLabel label = new JLabel("Unreachable locations found:");
									label.setForeground(Color.RED);
									p.add(label);
									unreachable.forEach(l -> {
										JLabel locLabel = new JLabel("\t" + l.getParent().getPropertyValue("name") + "." + l.getName());
										p.add(locLabel);
									});

									return p;
								}
							});
						});
					} catch (IOException | EngineException e) {
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
