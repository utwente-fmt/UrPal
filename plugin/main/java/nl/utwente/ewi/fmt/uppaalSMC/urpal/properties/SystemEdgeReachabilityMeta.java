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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.muml.uppaal.declarations.system.InstantiationList;
import org.muml.uppaal.declarations.system.SystemFactory;
import org.muml.uppaal.expressions.AssignmentExpression;
import org.muml.uppaal.expressions.AssignmentOperator;
import org.muml.uppaal.expressions.ExpressionsFactory;
import org.muml.uppaal.expressions.IdentifierExpression;
import org.muml.uppaal.templates.Edge;
import org.muml.uppaal.templates.Location;
import org.muml.uppaal.templates.SynchronizationKind;
import org.muml.uppaal.templates.Template;
import org.muml.uppaal.templates.TemplatesFactory;
import org.muml.uppaal.types.TypeReference;
import org.muml.uppaal.types.TypesFactory;

import com.uppaal.engine.EngineException;
import com.uppaal.engine.QueryResult;
import com.uppaal.model.core2.Document;
import com.uppaal.model.core2.PrototypeDocument;
import com.uppaal.model.io2.XMLReader;
import com.uppaal.model.system.Process;
import com.uppaal.model.system.SystemEdge;
import com.uppaal.model.system.UppaalSystem;
import com.uppaal.model.system.symbolic.SymbolicState;

import nl.utwente.ewi.fmt.uppaalSMC.ExponentialLocation;
import nl.utwente.ewi.fmt.uppaalSMC.NSTA;
import nl.utwente.ewi.fmt.uppaalSMC.Serialization;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil;

@SanityCheck(name = "System edge Reachability meta")
public class SystemEdgeReachabilityMeta extends AbstractProperty {
    private static final String OPTIONS = "order 1\nreduction 1\nrepresentation 0\ntrace 1\nextrapolation 0\nhashsize 27\nreuse 0\nsmcparametric 1\nmodest 0\nstatistical 0.01 0.01 0.05 0.05 0.05 0.9 1.1 0.0 0.0 4096.0 0.01";

    @Override
    public void doCheck(NSTA nstaOrig, Document doc, UppaalSystem sys, Consumer<SanityCheckResult> cb) {
        NSTA nsta = EcoreUtil.copy(nstaOrig);

        ChannelVariableDeclaration cvd = UppaalUtil.createChannelDeclaration(nsta, "__copy__");
        cvd.setBroadcast(true);
        cvd.setUrgent(true);
        nsta.getGlobalDeclarations().getDeclaration().add(cvd);

        Template controller = UppaalUtil.createTemplate(nsta, "_Controller");
        Location init = UppaalUtil.createLocation(controller, "__init");
        controller.setInit(init);
        Location controllerDone = UppaalUtil.createLocation(controller, "done");
        Edge cEdge = UppaalUtil.createEdge(init, controllerDone);
        UppaalUtil.addSynchronization(cEdge, cvd.getVariable().get(0), SynchronizationKind.SEND);
        InstantiationList il = SystemFactory.eINSTANCE.createInstantiationList();
        il.getTemplate().add(controller);
        nsta.getSystemDeclarations().getSystem().getInstantiationList().add(il);

        Variable counterVar = UppaalUtil.addCounterVariable(nsta);

        nsta.getTemplate().forEach(eTemplate -> {

            if (eTemplate == controller)
                return;
            if (eTemplate.getDeclarations() == null)
                eTemplate.setDeclarations(DeclarationsFactory.eINSTANCE.createLocalDeclarations());
            List<Edge> edgeList = new ArrayList<>(eTemplate.getEdge());
            if (edgeList.size() == 0) {
                return;
            }

            DataVariableDeclaration dvd = DeclarationsFactory.eINSTANCE.createDataVariableDeclaration();
            Variable var = UppaalUtil.createVariable("_fl");
            ValueIndex index = DeclarationsFactory.eINSTANCE.createValueIndex();
            index.setSizeExpression(UppaalUtil.createLiteral("" + edgeList.size()));
            var.getIndex().add(index);
            dvd.getVariable().add(var);

            TypeReference tr = TypesFactory.eINSTANCE.createTypeReference();
            tr.setReferredType(nsta.getBool());
            dvd.setTypeDefinition(tr);

            eTemplate.getDeclarations().getDeclaration().add(dvd);
            dvd = EcoreUtil.copy(dvd);
            Variable varMeta = dvd.getVariable().get(0);
            varMeta.setName("_f");
            dvd.setPrefix(DataVariablePrefix.META);
            eTemplate.getDeclarations().getDeclaration().add(dvd);

            Location newInit = TemplatesFactory.eINSTANCE.createLocation();
            newInit.setName("__init__");
            eTemplate.getLocation().add(newInit);

            Edge edge = UppaalUtil.createEdge(newInit, eTemplate.getInit());
            AssignmentExpression ass = ExpressionsFactory.eINSTANCE.createAssignmentExpression();
            ass.setOperator(AssignmentOperator.EQUAL);
            ass.setFirstExpr(UppaalUtil.createIdentifier(var));
            ass.setSecondExpr(UppaalUtil.createIdentifier(varMeta));
            edge.getUpdate().add(ass);
            UppaalUtil.addSynchronization(edge, cvd.getVariable().get(0), SynchronizationKind.RECEIVE);
            eTemplate.setInit(newInit);

            edgeList.forEach(e -> {
                IdentifierExpression id = UppaalUtil.createIdentifier(varMeta);
                id.getIndex().add(UppaalUtil.createLiteral("" + edgeList.indexOf(e)));
                AssignmentExpression ass2 = ExpressionsFactory.eINSTANCE.createAssignmentExpression();
                ass2.setOperator(AssignmentOperator.EQUAL);
                ass2.setFirstExpr(id);
                ass2.setSecondExpr(UppaalUtil.createLiteral("true"));
                e.getUpdate().add(ass2);
                if (e != edge)
                    UppaalUtil.addCounterToEdge(e, counterVar);
            });

            eTemplate.getLocation().stream().filter(l -> l instanceof ExponentialLocation)
                    .forEach(l -> ((ExponentialLocation) l).setExitRate(null));
        });
        Set<String> qs = new HashSet<>();
        qs.add("true");
        sys.getProcesses().forEach(p -> {
            String name = p.getName();
            int lSize = p.getEdges().size();
            if (lSize > 0)
                qs.add("(forall (i : int[0, " + (lSize - 1) + "]) " + name + "._f[i])");
        });

        String q = qs.stream().collect(Collectors.joining(" && ", "E<> (", ")"));
        try {

            File temp = File.createTempFile("edgetest", ".xml");
            BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
            bw.write(new Serialization().main(nsta).toString());
            bw.close();
            System.out.println(q);
            PrototypeDocument proto = new PrototypeDocument();
            proto.setProperty("synchronization", "");
            Document tDoc = new XMLReader(new CharSequenceInputStream(new Serialization().main(nsta), "UTF-8"))
                    .parse(proto);
            UppaalSystem tSys = UppaalUtil.compile(tDoc);

            engineQuery(tSys, "E<> (_Controller.done)", OPTIONS, (a, b) -> {
            });
            engineQuery(tSys, q, OPTIONS, (qr, ts) -> {
                if (qr.getStatus() == QueryResult.OK || qr.getStatus() == QueryResult.MAYBE_OK) {
                    sys.getProcesses().forEach(p -> p.getEdges().stream().map(SystemEdge::getEdge).distinct()
                            .forEach(l -> l.setProperty("color", null)));
                    cb.accept(new SanityCheckResult() {
                        @Override
                        public void write(PrintStream out, PrintStream err) {
                            out.println("All edges reachable!");
                        }

                        @Override
                        public JPanel toPanel() {
                            JPanel p = new JPanel();
                            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
                            p.add(new JLabel("All edges reachable!"));
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
                            Set<com.uppaal.model.core2.Edge> allEdges = new HashSet<>();
                            Set<com.uppaal.model.core2.Edge> reachable = new HashSet<>();
                            Set<com.uppaal.model.core2.Edge> unreachable = new HashSet<>();
                            Set<SystemEdge> unreachableSysEdges = new HashSet<>();
                            int varI = 0;
                            for (String varName : vars) {
                                Matcher matcher = Pattern.compile("(.*)\\._fl\\[(\\d+)]$").matcher(varName);
                                if (matcher.matches()) {
                                    Process process = sys.getProcess(sys.getProcessIndex(matcher.group(1)));
                                    SystemEdge sysEdge = process.getEdge(Integer.parseInt(matcher.group(2)));
                                    allEdges.add(sysEdge.getEdge());
                                    if (ss.getVariableValues()[varI] == 0) {
                                        unreachableSysEdges.add(sysEdge);
                                        unreachable.add(sysEdge.getEdge());
                                    } else {
                                        reachable.add(sysEdge.getEdge());
                                    }
                                }
                                varI++;
                            }
                            allEdges.forEach(e -> e.setProperty("color", !unreachable.contains(e) ? null
                                    : reachable.contains(e) ? Color.YELLOW : Color.RED));
                            cb.accept(new SanityCheckResult() {

                                @Override
                                public void write(PrintStream out, PrintStream err) {
                                    err.println("Unreachable edges found:");
                                    unreachableSysEdges
                                            .forEach(sl -> out.println(sl.getProcessName() + "(" + sl.getName() + ")"));
                                }

                                @Override
                                public JPanel toPanel() {
                                    JPanel p = new JPanel();
                                    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
                                    JLabel label = new JLabel("Unreachable edges found:");
                                    label.setForeground(Color.RED);
                                    p.add(label);
                                    unreachableSysEdges.forEach(sl -> {
                                        JLabel locLabel = new JLabel("\t" + sl.getProcessName() + "(" + sl.getName() + ")");
                                        locLabel.setForeground(Color.RED);
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
            });
        } catch (EngineException | IOException | XMLStreamException e) {
            e.printStackTrace();
        }
    }
}
