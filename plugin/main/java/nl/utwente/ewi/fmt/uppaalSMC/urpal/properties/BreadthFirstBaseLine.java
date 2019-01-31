package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties;

import java.io.IOException;
import java.util.function.Consumer;

import com.uppaal.engine.EngineException;
import com.uppaal.model.core2.Document;
import com.uppaal.model.system.UppaalSystem;

import nl.utwente.ewi.fmt.uppaalSMC.NSTA;

@SanityCheck(name = "Breadth first")
public class BreadthFirstBaseLine extends AbstractProperty {

    @Override
    public void doCheck(NSTA nsta, Document doc, UppaalSystem sys, Consumer<SanityCheckResult> cb) {
        long startTime = System.currentTimeMillis();
        try {
            engineQuery(sys, "A[](true)", DEFAULT_OPTIONS_BFS, (gf, ts) -> {
                System.out.println("time milis: " + (System.currentTimeMillis() - startTime));
                System.out.println("max mem: " + (maxMem));
            });
        } catch (IOException | EngineException e) {
            e.printStackTrace();
        }
    }

}
