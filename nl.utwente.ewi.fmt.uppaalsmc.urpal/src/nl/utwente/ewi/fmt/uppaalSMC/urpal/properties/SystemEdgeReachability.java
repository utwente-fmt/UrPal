package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties;

import java.awt.Color;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.muml.uppaal.templates.Edge;
import org.muml.uppaal.templates.Location;
import org.muml.uppaal.templates.LocationKind;
import org.muml.uppaal.templates.TemplatesFactory;

import com.uppaal.model.core2.Document;
import com.uppaal.model.system.Process;
import com.uppaal.model.system.UppaalSystem;

import nl.utwente.ewi.fmt.uppaalSMC.NSTA;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil;

@SanityCheck(name = "Edge Reachability", description = "")
public class SystemEdgeReachability extends AbstractProperty {

	@Override
	public void doCheck(NSTA nsta, Document doc, UppaalSystem sys, Consumer<SanityCheckResult> cb) {
		Set<Runnable> rs = new HashSet<>();
		AtomicInteger counter = new AtomicInteger();
		Map<String, String> results = new ConcurrentHashMap<>();
		nsta.getTemplate().forEach(t -> {
			int i = 0;
			Set<Process> ps = new HashSet<>();
			for (Process p : sys.getProcesses()) {
				if (p.getTemplate().getProperty("name").getValue().equals(t.getName()))
					ps.add(p);
			}

			for (Edge e : new ArrayList<>(t.getEdge())) {
				AtomicInteger edgeCount = new AtomicInteger(ps.size());
				
				Location l = UppaalUtil.createLocation(t, "edge" + i);
				l.setLocationTimeKind(LocationKind.COMMITED);
				
				Edge e2 = UppaalUtil.createEdge(l, e.getTarget());
				
				e.setTarget(l);
				l.setInvariant(EcoreUtil.copy(e2.getTarget().getInvariant()));
				String src = e.getSource().getComment() != null ? e.getSource().getComment() : e.getSource().getName();
				String dst = e2.getTarget().getComment() != null ? e2.getTarget().getComment()
						: e2.getTarget().getName();
				com.uppaal.model.core2.Edge uppaalEdge = UppaalUtil.getEdge(doc, t.getName(), i);
				uppaalEdge.setProperty("color", Color.RED);

				ps.forEach(p -> {
					String name = p.getName();
					rs.add(() -> {
						String edgeDescription = name + "(" + src + " - " + dst + ")";
						try {
							verifyta(nsta, "E<> (" + name + "." + l.getName() + ")", (out, err) -> {
								if (out.contains("Formula is NOT satisfied")) {
									results.put(edgeDescription, "unreachable");
								} else {
									if (!err.isEmpty()) {
										System.out.print(out);
										System.err.print(err);
									}
									uppaalEdge.setProperty("color", edgeCount.decrementAndGet() == 0 ? Color.BLACK : Color.YELLOW);
									cb.accept(null);
								}
								if (counter.decrementAndGet() == 0) {
									cb.accept(new SanityCheckResult() {
										@Override
										public void write(PrintStream out, PrintStream err) {
											if (results.isEmpty()) {
												out.println("All edges reachable!");
											} else {
												err.println("Unreachable edges found:");
												results.forEach((edge, __) -> err.println(edge));
											}
										}

										@Override
										public JPanel toPanel() {
											JPanel p = new JPanel();
											p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
											if (results.isEmpty()) {
												JLabel label = new JLabel("All edges reachable!");
												p.add(label);
											} else {
												JLabel label = new JLabel("Unreachable edges found:");
												label.setForeground(Color.RED);
												p.add(label);
												results.forEach((edge, __) -> {
													JLabel locLabel = new JLabel("\t" + edge);
													locLabel.setForeground(Color.RED);
													p.add(locLabel);
												});
											}
											return p;
										}
									});
								}
							});
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					});
				});

				i++;
			}
		});
		counter.set(rs.size());
		
		rs.forEach(r -> new Thread(r).start());
	}
}
