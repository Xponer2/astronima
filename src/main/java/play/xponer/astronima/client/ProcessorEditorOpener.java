package play.xponer.astronima.client;

import net.minecraft.client.Minecraft;
import play.xponer.astronima.client.screen.ProcessorEditorUi;
import play.xponer.astronima.network.ProcessorProgramPayload;

/** Opens a mounted {@link play.xponer.astronima.sim.logic.PartType#PROCESSOR}'s code editor. */
public final class ProcessorEditorOpener {

    public static void open(String program, ProcessorProgramPayload.At at) {
        Minecraft.getInstance().setScreen(ProcessorEditorUi.screen(program, at));
    }

    private ProcessorEditorOpener() {}
}
