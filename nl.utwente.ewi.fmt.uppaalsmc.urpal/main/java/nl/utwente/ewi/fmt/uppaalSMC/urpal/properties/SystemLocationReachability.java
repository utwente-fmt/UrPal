package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.muml.uppaal.templates.RedefinedTemplate;
import org.muml.uppaal.templates.Template;

import com.uppaal.model.core2.Document;
import com.uppaal.model.system.UppaalSystem;

import nl.utwente.ewi.fmt.uppaalSMC.NSTA;
import nl.utwente.ewi.fmt.uppaalSMC.Serialization;
import on.p;

@SanityCheck(name = "System location reachability", description = "")
public class SystemLocationReachability extends AbstractProperty {

	@Override
	public void doCheck(NSTA nsta, Document doc, UppaalSystem sys, Consumer<SanityCheckResult> cb) {
		long startTime = System.currentTimeMillis();
		Map<String, String> locs = new ConcurrentHashMap<>();
		nsta.getSystemDeclarations().getSystem().getInstantiationList().forEach(il -> {
			il.getTemplate().forEach(at -> {
				String atName = at.getName();
				Template t;
				while (at instanceof RedefinedTemplate) {
					at = ((RedefinedTemplate) at).getReferredTemplate();
				}
				t = (Template) at;
				sys.getProcesses().stream().filter(p -> p.getTemplate().getPropertyValue("name").equals(atName)).forEach(p -> {
					String name = p.getName();
					t.getLocation().forEach(l -> {
						if (l.getComment() != null && !l.getComment().isEmpty()) {
							locs.put(name + "." + l.getName(), name + "." + l.getComment());
						} else {
							locs.put(name + "." + l.getName(), name + "." + l.getName());
						}
					});
				});
			});
		});
		Map<String, String> results = new ConcurrentHashMap<>();
		File temp = null;
		try {
			temp = File.createTempFile("nsta", ".xml");
			BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
			bw.write(new Serialization().main(nsta).toString());
			bw.close();
		} catch (IOException e1) {
			e1.printStackTrace();
			return;
		}
		File file = temp;
		AtomicInteger maxMem = new AtomicInteger();
		Pattern pattern = Pattern.compile("Resident memory used : (\\d*) KiB");
		locs.forEach((loc, real) -> {
			try {
				verifyta(file, "E<> (" + loc + ")", "-o 1 -u", (o, e) -> {
					Matcher match = pattern.matcher(o);
					if (match.find()) {
						int mem = Integer.parseInt(match.group(1));
						if (maxMem.get() < mem) {
							maxMem.set(mem);
						}
					}
					locs.remove(loc);
					if (o.contains("Formula is NOT satisfied")) {
						results.put(loc, real);
					} else if (!o.contains("Formula is satisfied")) {
						throw new RuntimeException("wtf ?");
					}
					if (!e.isEmpty()) {
						System.err.println(e);
					}
					if (locs.isEmpty()) {
						System.out.println("time milis: " + (System.currentTimeMillis() - startTime));
						System.out.println("mem: " + (maxMem.get()));
						cb.accept(new SanityCheckResult() {
							@Override
							public void write(PrintStream out, PrintStream err) {
								if (results.isEmpty()) {
									out.println("All locations reachable!");
								} else {
									err.println("Unreachable locations found:");
									results.forEach((loc, real) -> err.println(real));
								}
							}

							@Override
							public JPanel toPanel() {
								JPanel p = new JPanel();
								p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
								if (results.isEmpty()) {
									JLabel label = new JLabel("All locations reachable!");
//									label.setForeground(Color.GREEN);
									p.add(label);
								} else {
									JLabel label = new JLabel("Unreachable locations found:");
									label.setForeground(Color.RED);
									p.add(label);
									results.forEach((loc, real) -> {
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
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

}
