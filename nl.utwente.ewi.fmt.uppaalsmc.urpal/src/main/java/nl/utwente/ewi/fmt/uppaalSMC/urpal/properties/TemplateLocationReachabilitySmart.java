package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.input.CharSequenceInputStream;

import com.uppaal.engine.EngineException;
import com.uppaal.engine.QueryResult;
import com.uppaal.model.core2.Document;
import com.uppaal.model.core2.PrototypeDocument;
import com.uppaal.model.io2.XMLReader;
import com.uppaal.model.system.UppaalSystem;

import nl.utwente.ewi.fmt.uppaalSMC.NSTA;
import nl.utwente.ewi.fmt.uppaalSMC.Serialization;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil;
import on.p;

@SanityCheck(name = "Template location reachability smart", description = "")
public class TemplateLocationReachabilitySmart extends AbstractProperty {

	@Override
	public void doCheck(NSTA nsta, Document doc, UppaalSystem sys, Consumer<SanityCheckResult> cb) {
		long startTime = System.currentTimeMillis();
		Map<String, Set<String>> locs = new ConcurrentHashMap<>();
		nsta.getTemplate().forEach(t -> {
			String tName = t.getName();
			Set<String> processNames = sys.getProcesses().stream()
					.filter(p -> p.getTemplate().getPropertyValue("name").equals(tName)).map(p -> p.getName()).collect(Collectors.toSet());
			t.getLocation().forEach(l -> {
				String lName = tName + "."
						+ ((l.getComment() != null && !l.getComment().isEmpty()) ? l.getComment() : l.getName());
				Set<String> instances = processNames.stream().map(p -> p + "." + l.getName()).collect(Collectors.toSet());
				locs.put(lName, instances);
			});
		});
		Map<String, String> results = new ConcurrentHashMap<>();

		try {
			File temp = File.createTempFile("loctest", ".xml");
			BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
			bw.write(new Serialization().main(nsta).toString());
			bw.close();
			PrototypeDocument proto = new PrototypeDocument();
			proto.setProperty("synchronization", "");
			Document tDoc = new XMLReader(new CharSequenceInputStream(new Serialization().main(nsta), "UTF-8"))
					.parse(proto);
			UppaalSystem tSys = UppaalUtil.compile(tDoc);
			locs.forEach((real, instances) -> {
				try {
					maxMem = 0;
					engineQuery(tSys, "E<> (" + String.join(" || ", instances) + ")", DEFAULT_OPTIONS_DFS, (o, e) -> {
						locs.remove(real);
						if (o.getStatus() != QueryResult.OK && o.getStatus() != QueryResult.MAYBE_OK) {
							results.put(real, "");
						}
						if (!e.isEmpty()) {
							System.err.println(e);
						}
						if (locs.isEmpty()) {
							System.out.println("time milis: " + (System.currentTimeMillis() - startTime));
							System.out.println("mem: " + maxMem);
							cb.accept(new SanityCheckResult() {
								@Override
								public void write(PrintStream out, PrintStream err) {
									if (results.isEmpty()) {
										out.println("All locations reachable!");
									} else {
										err.println("Unreachable locations found:");
										results.forEach((real, loc) -> err.println(real));
									}
								}

								@Override
								public JPanel toPanel() {
									JPanel p = new JPanel();
									p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
									if (results.isEmpty()) {
										JLabel label = new JLabel("All locations reachable!");
										// label.setForeground(Color.GREEN);
										p.add(label);
									} else {
										JLabel label = new JLabel("Unreachable locations found:");
										label.setForeground(Color.RED);
										p.add(label);
										results.forEach((real, loc) -> {
											JLabel locLabel = new JLabel("\t" + real);
											locLabel.setForeground(Color.RED);
											p.add(locLabel);
										});
									}
									return p;
								}
							});
						}
					});
				} catch (IOException | EngineException e) {
					e.printStackTrace();
				}
			});
		} catch (EngineException | XMLStreamException | IOException e) {
			e.printStackTrace();
		}
	}

}
