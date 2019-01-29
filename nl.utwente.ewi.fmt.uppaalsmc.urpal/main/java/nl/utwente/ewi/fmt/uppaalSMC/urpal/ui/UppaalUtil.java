package nl.utwente.ewi.fmt.uppaalSMC.urpal.ui;

import java.awt.Container;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.muml.uppaal.declarations.ChannelVariableDeclaration;
import org.muml.uppaal.declarations.DataVariableDeclaration;
import org.muml.uppaal.declarations.DataVariablePrefix;
import org.muml.uppaal.declarations.DeclarationsFactory;
import org.muml.uppaal.declarations.Parameter;
import org.muml.uppaal.declarations.Variable;
import org.muml.uppaal.declarations.VariableDeclaration;
import org.muml.uppaal.expressions.CompareExpression;
import org.muml.uppaal.expressions.CompareOperator;
import org.muml.uppaal.expressions.Expression;
import org.muml.uppaal.expressions.ExpressionsFactory;
import org.muml.uppaal.expressions.IdentifierExpression;
import org.muml.uppaal.expressions.IncrementDecrementOperator;
import org.muml.uppaal.expressions.LiteralExpression;
import org.muml.uppaal.expressions.LogicalExpression;
import org.muml.uppaal.expressions.LogicalOperator;
import org.muml.uppaal.expressions.NegationExpression;
import org.muml.uppaal.expressions.PostIncrementDecrementExpression;
import org.muml.uppaal.templates.Location;
import org.muml.uppaal.templates.Synchronization;
import org.muml.uppaal.templates.SynchronizationKind;
import org.muml.uppaal.templates.Template;
import org.muml.uppaal.templates.TemplatesFactory;
import org.muml.uppaal.types.IntegerBounds;
import org.muml.uppaal.types.RangeTypeSpecification;
import org.muml.uppaal.types.TypeDefinition;
import org.muml.uppaal.types.TypeReference;
import org.muml.uppaal.types.TypesFactory;

import com.uppaal.engine.Engine;
import com.uppaal.engine.EngineException;
import com.uppaal.engine.EngineStub;
import com.uppaal.engine.Problem;
import com.uppaal.gui.SystemInspector;
import com.uppaal.model.core2.AbstractTemplate;
import com.uppaal.model.core2.Document;
import com.uppaal.model.core2.Edge;
import com.uppaal.model.core2.Node;
import com.uppaal.model.system.UppaalSystem;
import com.uppaal.plugin.Repository;

import nl.utwente.ewi.fmt.uppaalSMC.NSTA;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.AbstractProperty;

public class UppaalUtil {
	public static Engine engine;
	
	static {
		try {
			engine = connectToEngine();
		} catch (EngineException | IOException | URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static SystemInspector getSystemInspector(Container c) {
		Container parent = c.getParent();
		while (parent != null) {
			if (parent instanceof SystemInspector) {
				return (SystemInspector) parent;
			}
			parent = parent.getParent();
		}
		return null;
	}

	public static Edge getEdge(Document doc, String templateName, int i) {
		int io = i;
		AbstractTemplate t = doc.getTemplate(templateName);
		Node n = t.first;
		while (n != null) {
			if (n instanceof com.uppaal.model.core2.Edge) {
				if (i == 0) {
					return (Edge) n;
				} else {
					i--;
				}
			}
			n = n.next;
		}
		throw new IllegalArgumentException("Cant find edge " + io + " in template" + templateName);
	}

	public static List<com.uppaal.model.core2.Location> getLocations(Document doc, String templateName) {
		AbstractTemplate t = doc.getTemplate(templateName);
		Node n = t.first;
		List<com.uppaal.model.core2.Location> result = new ArrayList<>();
		while (n != null) {
			if (n instanceof com.uppaal.model.core2.Location) {
				result.add((com.uppaal.model.core2.Location) n);
			}
			n = n.next;
		}
		return result;
	}

	public static List<Edge> getEdges(Document doc, String templateName) {
		AbstractTemplate t = doc.getTemplate(templateName);
		Node n = t.first;
		List<Edge> result = new ArrayList<>();
		while (n != null) {
			if (n instanceof Edge) {
				result.add((Edge) n);
			}
			n = n.next;
		}
		return result;
	}

	public static Variable createVariable(String name) {
		Variable result = DeclarationsFactory.eINSTANCE.createVariable();
		result.setName(name);
		return result;
	}

	public static Parameter createParameter(TypeDefinition type, String name) {
		Parameter result = DeclarationsFactory.eINSTANCE.createParameter();
		VariableDeclaration vd = DeclarationsFactory.eINSTANCE.createDataVariableDeclaration();
		vd.setTypeDefinition(type);
		vd.getVariable().add(createVariable(name));
		result.setVariableDeclaration(vd);
		return result;
	}

	private static Engine connectToEngine() throws EngineException, IOException, URISyntaxException {

		String os = System.getProperty("os.name");
		String path = new File(Repository.class.getProtectionDomain().getCodeSource().getLocation().toURI())
				.getParentFile().getPath();
		if ("Linux".equals(os)) {
			path += "/bin-Linux/server";
		} else {
			path += "\\bin-Windows\\server.exe";
		}
		Engine engine = new Engine();
		engine.setServerPath(path);
		engine.setServerHost("localhost");
		engine.setConnectionMode(EngineStub.BOTH);
		engine.connect();
		return engine;
	}

	public static Expression negate(Expression ex) {
		if (ex instanceof CompareExpression) {
			// omits UPPAAL bug with negated comparisons
			CompareExpression comp = EcoreUtil.copy((CompareExpression) ex);
			IdentifierExpression id = (IdentifierExpression) (comp.getFirstExpr() instanceof IdentifierExpression ? comp.getFirstExpr() :
				comp.getSecondExpr() instanceof IdentifierExpression ? comp.getSecondExpr() : null);
			if (id != null && id.isDerivative()) {
				return null;
			}
			switch (comp.getOperator()) {
			case GREATER:
				comp.setOperator(CompareOperator.LESS_OR_EQUAL);
				break;
			case GREATER_OR_EQUAL:
				comp.setOperator(CompareOperator.LESS);
				break;
			case EQUAL:
				comp.setOperator(CompareOperator.UNEQUAL);
				break;
			case UNEQUAL:
				comp.setOperator(CompareOperator.EQUAL);
				break;
			case LESS_OR_EQUAL:
				comp.setOperator(CompareOperator.GREATER);
				break;
			case LESS:
				comp.setOperator(CompareOperator.GREATER_OR_EQUAL);
				break;
			}
			return comp;
		}
		NegationExpression result = ExpressionsFactory.eINSTANCE.createNegationExpression();
		result.setNegatedExpression(EcoreUtil.copy(ex));
		return result;
	}

	public static UppaalSystem compile(Document doc) throws EngineException, IOException {
		reconnect();
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

	public static void reconnect() throws EngineException, IOException {
		engine.disconnect();
		engine.connect();
	}
	
	public static Location createLocation(Template t, String n) {
		Location location = TemplatesFactory.eINSTANCE.createLocation();
		location.setName(n);
		t.getLocation().add(location);
		return location;
	}
	
	public static Template createTemplate(NSTA nsta, String name) {
		Template template = TemplatesFactory.eINSTANCE.createTemplate();
		template.setName(name);
		nsta.getTemplate().add(template);
		return template;
	}
	
	public static org.muml.uppaal.templates.Edge createEdge(Location source, Location target) {
		if (source.getParentTemplate() != target.getParentTemplate()) {
			throw new IllegalArgumentException("Locations are not in same template");
		}
		org.muml.uppaal.templates.Edge edge = TemplatesFactory.eINSTANCE.createEdge();
		edge.setSource(source);
		edge.setTarget(target);
		source.getParentTemplate().getEdge().add(edge);
		return edge;
	}

	public static Template getTemplate(NSTA nsta, AbstractTemplate template) {
		for (Template t : nsta.getTemplate()) {
			if (t.getName().equals(template.getPropertyValue("name")))
				return t;
		}
		return null;
	}
	
	public static Synchronization addSynchronization(org.muml.uppaal.templates.Edge edge, Variable channel, SynchronizationKind kind) {
		Synchronization sync = TemplatesFactory.eINSTANCE.createSynchronization();
		sync.setKind(kind);
		sync.setChannelExpression(createIdentifier(channel));
		edge.setSynchronization(sync);
		return sync;
	}
	
	public static Variable addCounterVariable(NSTA nsta) {
		if (AbstractProperty.STATE_SPACE_SIZE <= 0) return null;
		DataVariableDeclaration counter = DeclarationsFactory.eINSTANCE.createDataVariableDeclaration();
		Variable counterVar = UppaalUtil.createVariable("_counter");
		RangeTypeSpecification range = TypesFactory.eINSTANCE.createRangeTypeSpecification();
		IntegerBounds bounds = TypesFactory.eINSTANCE.createIntegerBounds();
		bounds.setLowerBound(createLiteral("0"));
		bounds.setUpperBound(createLiteral("" + AbstractProperty.STATE_SPACE_SIZE * 10));
		range.setBounds(bounds);
		counter.setTypeDefinition(range);
		counter.setPrefix(DataVariablePrefix.META);
		counter.getVariable().add(counterVar);
		nsta.getGlobalDeclarations().getDeclaration().add(counter);
		return counterVar;
	}
	
	public static void addCounterToEdge(org.muml.uppaal.templates.Edge edge, Variable counterVar) {
		if (AbstractProperty.STATE_SPACE_SIZE <= 0) return;
		if (edge.getSynchronization() != null && edge.getSynchronization().getKind().equals(SynchronizationKind.RECEIVE)) return;
		PostIncrementDecrementExpression incr = ExpressionsFactory.eINSTANCE.createPostIncrementDecrementExpression();
		incr.setExpression(UppaalUtil.createIdentifier(counterVar));
		incr.setOperator(IncrementDecrementOperator.INCREMENT);
		edge.getUpdate().add(incr);
		CompareExpression compare = ExpressionsFactory.eINSTANCE.createCompareExpression();
		compare.setFirstExpr(UppaalUtil.createIdentifier(counterVar));
		compare.setSecondExpr(UppaalUtil.createLiteral("" + AbstractProperty.STATE_SPACE_SIZE));
		compare.setOperator(CompareOperator.LESS);
		if (edge.getGuard() == null) {
			edge.setGuard(compare);
		} else {
			LogicalExpression and = ExpressionsFactory.eINSTANCE.createLogicalExpression();
			and.setFirstExpr(compare);
			and.setSecondExpr(edge.getGuard());
			and.setOperator(LogicalOperator.AND);
			edge.setGuard(and);
		}
	}
	
	public static IdentifierExpression createIdentifier(Variable var) {
		IdentifierExpression result = ExpressionsFactory.eINSTANCE.createIdentifierExpression();
		result.setIdentifier(var);
		return result;
	}
	
	public static LiteralExpression createLiteral(String literal) {
		LiteralExpression result = ExpressionsFactory.eINSTANCE.createLiteralExpression();
		result.setText(literal);
		return result;
	}

	public static ChannelVariableDeclaration createChannelDeclaration(NSTA nsta, String string) {
		ChannelVariableDeclaration cvd = DeclarationsFactory.eINSTANCE.createChannelVariableDeclaration();
		cvd.getVariable().add(UppaalUtil.createVariable(string));
		TypeReference tr = TypesFactory.eINSTANCE.createTypeReference();
		tr.setReferredType(nsta.getChan());
		cvd.setTypeDefinition(tr);
		return cvd;
	}

	public static Expression invariantToGuard(Expression invariant) {
		invariant = EcoreUtil.copy(invariant);
		Expression current = invariant;
		while (current != null) {
			Expression constr;
			if (current instanceof LogicalExpression) {
				LogicalExpression logic = (LogicalExpression) current;
				if (logic.getOperator() == LogicalOperator.AND) {
					constr = logic.getSecondExpr();
					current = logic.getFirstExpr();
				} else {
					current = null;
					break;
				}
			} else {
				constr = current;
				current = null;
			}
			if (constr instanceof CompareExpression) {
				CompareExpression compare = (CompareExpression) constr;
				if (Arrays.asList(compare.getFirstExpr(), compare.getSecondExpr()).stream()
						.filter(IdentifierExpression.class::isInstance).map(IdentifierExpression.class::cast)
						.anyMatch(ie -> ie.isDerivative())) {
					compare.setFirstExpr(createLiteral("0"));
					compare.setSecondExpr(createLiteral("0"));
					compare.setOperator(CompareOperator.EQUAL);
				}
			}
		}
		return invariant;
		
	}

}
