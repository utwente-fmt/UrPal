package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties;

import java.awt.Color;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.muml.uppaal.core.NamedElement;
import org.muml.uppaal.declarations.Variable;

import com.uppaal.model.core2.Document;
import com.uppaal.model.system.UppaalSystem;

import nl.utwente.ewi.fmt.uppaalSMC.NSTA;

@SanityCheck(name = "Check for unused elements")
public class UnusedVariablesProperty extends AbstractProperty {

	@Override
	public void doCheck(NSTA nsta, Document doc, UppaalSystem sys, Consumer<SanityCheckResult> cb) {
		List<Variable> unusedVars = new ArrayList<>();
		nsta.eAllContents().forEachRemaining(obj -> {
			if (obj instanceof Variable) {
				if (EcoreUtil.UsageCrossReferencer.find(obj, nsta).isEmpty()) {
					unusedVars.add((Variable) obj);
				}
			}
		});
		List<String> qualifiedNames = unusedVars.stream().map(v -> {
			StringBuilder sb = new StringBuilder(v.getName().replace("nta.", "") + "(" + v.eContainer().getClass().getSimpleName()
					.replace("Declaration", "")
					.replace("Impl", "") + ")");
			EObject obj = v;
			while ((obj = obj.eContainer()) != null) {
				if (obj instanceof NamedElement) {
					sb.insert(0, ((NamedElement) obj).getName() + ".");
				}
			}
			return sb.toString();
		}).collect(Collectors.toList());
		cb.accept(new SanityCheckResult() {

			@Override
			public void write(PrintStream out, PrintStream err) {
				if (qualifiedNames.isEmpty()) {
					out.println("No unused variables found");
				} else {
					err.println("Unused variables found: ");
					qualifiedNames.forEach(err::println);
				}
			}

			@Override
			public JPanel toPanel() {
				if (qualifiedNames.isEmpty()) {
					JPanel p = new JPanel();
					p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
					p.add(new JLabel("All variables used!"));
					return p;
				} else {
					JPanel p = new JPanel();
					p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
					JLabel label = new JLabel("Unused declarations found:");
					label.setForeground(Color.RED);
					p.add(label);
					qualifiedNames.forEach(n -> {
						JLabel locLabel = new JLabel(n);
						locLabel.setForeground(Color.RED);
						p.add(locLabel);
					});

					return p;
				}
			}
		});
	}

}
