package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties;

import java.awt.Color;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.uppaal.engine.EngineException;
import com.uppaal.engine.QueryResult;
import com.uppaal.gui.simulator.symbolic.SymbolicSimulator;
import com.uppaal.model.core2.Document;
import com.uppaal.model.system.UppaalSystem;

import nl.utwente.ewi.fmt.uppaalSMC.NSTA;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil;

@SanityCheck(name = "Deadlocks", description = "")
public class DeadlockProperty extends AbstractProperty {

	@Override
	public void doCheck(NSTA nsta, Document doc, UppaalSystem sys, Consumer<SanityCheckResult> cb) {
		Set<Runnable> cbs = new HashSet<>();
		AtomicInteger i = new AtomicInteger();
		Set<String> locs = sys.getProcesses().stream()
				.flatMap(p ->
					p.getLocations().stream().filter(l -> {
						boolean result = p.getEdges().stream().allMatch(e ->
							!e.getEdge().getSource().equals(l.getLocation())
						);
						if (result) {
							if (l.getName() == null || l.getName().isEmpty()) {
								l.getLocation().setProperty("name", "__l" + i.getAndIncrement());
								cbs.add(() -> {
									l.getLocation().setProperty("name", null);
								});
							}
						}
						return result;
					})
					.map(l -> p.getName() + "." + l.getName())
				)
				.collect(Collectors.toSet());
		String query;
		if (locs.isEmpty()) {
			query = "A[] (!deadlock)";
		} else {
			query = "A[] (deadlock imply " + String.join(" or ", locs) + ")";
		}
		System.out.println(query);
		try {
			engineQuery(sys, query, "trace 1", (qr, t) -> {
				cb.accept(new SanityCheckResult() {

					@Override
					public void write(PrintStream out, PrintStream err) {
						if (qr.getStatus() == QueryResult.OK) {
							out.println("No unwanted deadlocks found!");
						} else {
							err.println("Unwanted deadlocks found! See trace below:");
							t.subList(1, t.size()).forEach(s -> System.err.println(s.traceFormat()));
						}
					}

					@Override
					public JPanel toPanel() {
						JPanel p = new JPanel();
						p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
						if (qr.getStatus() == QueryResult.OK) {
							JLabel label = new JLabel("No unwanted deadlocks!");
							p.add(label);
						} else {
							
							JLabel label = new JLabel("Unwanted deadlocks found! Click button below to load trace:");
							label.setForeground(Color.RED);
							p.add(label);
							JButton button = new JButton("Load trace");
							button.addActionListener(a -> {
								SymbolicSimulator sim = UppaalUtil.getSystemInspector(button).simulator;
								sim.uppaalSystem.set(sys);
								sim.a(t, 0);
							});
							p.add(button);
						}
						return p;
					}
				});
				cbs.forEach(Runnable::run);
			});
		} catch (IOException | EngineException e1) {
			e1.printStackTrace();
		}
	}

}
