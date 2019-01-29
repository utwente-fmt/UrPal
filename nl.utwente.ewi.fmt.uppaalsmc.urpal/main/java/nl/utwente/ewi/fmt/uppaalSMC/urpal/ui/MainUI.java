package nl.utwente.ewi.fmt.uppaalSMC.urpal.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.inject.Injector;
import com.uppaal.engine.EngineException;
import com.uppaal.model.core2.Document;
import com.uppaal.model.io2.XMLWriter;
import com.uppaal.model.system.UppaalSystem;
import com.uppaal.plugin.Plugin;
import com.uppaal.plugin.PluginWorkspace;
import com.uppaal.plugin.Registry;
import com.uppaal.plugin.Repository;
import com.uppaal.plugin.Repository$ChangeType;

import nl.utwente.ewi.fmt.uppaalSMC.parser.UppaalSMCStandaloneSetup;
import nl.utwente.ewi.fmt.uppaalSMC.NSTA;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.AbstractProperty;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.SanityCheck;

@SuppressWarnings("serial")
public class MainUI extends JPanel implements Plugin, PluginWorkspace, PropertyChangeListener {
    private Set<PropertyPanel> panels = new HashSet<>();
    private ResourceSet rs;
    protected long last = 0l;
    private Repository<Document> docr;
    private boolean selected;
    private double zoom;

    private PluginWorkspace[] workspaces = new PluginWorkspace[1];

    private NSTA load(Document d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            new XMLWriter(out).visitDocument(d);
            InputStream in = new ByteArrayInputStream(out.toByteArray());
            Resource resource = rs.createResource(URI.createURI("dummy:/" + System.currentTimeMillis() + ".xml"), null);
            resource.load(in, rs.getLoadOptions());

            return (NSTA) resource.getContents().get(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private class PropertyPanel extends JPanel {
        private AbstractProperty property;
        private boolean enabled;
        private Component component;

        private void redoStuff() {
            Component c = component;
            if (c == null)
                c = this;
            while (c.getParent() != null) {
                c = c.getParent();
            }
            c.revalidate();
            c.repaint();
        }

        public PropertyPanel(AbstractProperty p) {
            super();
            property = p;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createTitledBorder(property.getClass().getAnnotation(SanityCheck.class).name()));

            JCheckBox checkBox = new JCheckBox(property.getClass().getAnnotation(SanityCheck.class).name());
            checkBox.addItemListener(a -> {
                enabled = a.getStateChange() == ItemEvent.SELECTED;
                if (enabled) {
                    doCheck(null, null, null);
                } else {
                    if (component != null)
                        remove(component);
                }
                redoStuff();
            });
            add(checkBox);
        }

        void doCheck(NSTA nsta, Document doc, UppaalSystem sys) {
            if (nsta == null) {
                nsta = load(doc = docr.get());
                try {
                    sys = UppaalUtil.compile(doc);
                } catch (EngineException | IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
            property.doCheck(nsta, doc, sys, pr -> {
                if (pr == null) {
                    docr.fire(ChangeType.valueOf("UPDATED"));
                    return;
                }
                //pr.write(System.out, System.err);
                if (component != null)
                    remove(component);
                add(component = pr.toPanel());
                redoStuff();
            });
        }
    }

    public MainUI() {
    }

    @SuppressWarnings("unchecked")
    public MainUI(Registry r) {
        super();
        docr = r.getRepository("EditorDocument");
        workspaces[0] = this;
        r.addListener(this);

        Injector injector = new UppaalSMCStandaloneSetup().createInjectorAndDoEMFRegistration();;
        rs = injector.getInstance(XtextResourceSet.class);

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel jp = new JPanel();
        jp.add(new JLabel("States to explore per check (<=0 for unlimited): "));
        JTextField nStates = new JFormattedTextField(NumberFormat.getIntegerInstance());
        nStates.setText("" + AbstractProperty.STATE_SPACE_SIZE);
        nStates.setPreferredSize(new Dimension(128, nStates.getPreferredSize().height));
        nStates.addPropertyChangeListener("value", evt -> {
            System.out.println(evt.getNewValue());
            AbstractProperty.STATE_SPACE_SIZE = Integer.parseInt(evt.getNewValue().toString());
        });
        jp.add(nStates);
        add(jp);
        JButton runButton = new JButton("Run selected checks");
        runButton.addActionListener(e -> {
            doCheck();
        });
        add(runButton);
        for (AbstractProperty p : AbstractProperty.properties) {
            PropertyPanel pp = new PropertyPanel(p);
            add(pp);
            panels.add(pp);
        }

        docr.addListener(this);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_F6 && e.getID() == KeyEvent.KEY_PRESSED) {
                    doCheck();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public Icon getIcon() {
        return null;
    }

    @Override
    public String getTitleToolTip() {
        return "Detect commonly made errors";
    }

    @Override
    public Component getComponent() {
        JScrollPane scroll = new JScrollPane(this);
        scroll.setPreferredSize(new Dimension(790, 470));
        return scroll;
    }

    @Override
    public int getDevelopmentIndex() {
        return 350;
    }

    @Override
    public boolean getCanZoom() {
        return false;
    }

    @Override
    public boolean getCanZoomToFit() {
        return false;
    }

    @Override
    public double getZoom() {
        return zoom;
    }

    @Override
    public void setZoom(double value) {
        zoom = value;
    }

    @Override
    public void zoomToFit() {
    }

    @Override
    public void zoomIn() {
    }

    @Override
    public void zoomOut() {
    }

    @Override
    public void setActive(boolean selected) {
        this.selected = selected;
    }
    public void doCheck() {
        if (panels.stream().filter(p -> p.enabled).count() > 0) {
            Document d = docr.get();
            NSTA nsta = load(d);
            UppaalSystem sys;
            try {
                sys = UppaalUtil.compile(d);
            } catch (EngineException | IOException e) {
                e.printStackTrace();
                return;
            }
            new Thread(() -> {
                panels.stream().filter(p -> p.enabled).forEach(p -> p.doCheck(nsta, d, sys));
            }).start();
        }
    }

    @Override
    public PluginWorkspace[] getWorkspaces() {
        return workspaces;
    }

    @Override
    public String getTitle() {
        return "Sanity Checker";
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        setActive(selected);
    }
}
