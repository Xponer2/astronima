package play.xponer.astronima.client.render;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import play.xponer.astronima.Astronima;

/**
 * Client-only, per the "every mechanic ships a /astronima command to set and read its state" rule
 * — {@link RetortPhotonSparks} is a pure client render concern (no server-side state to inspect),
 * so this registers on the client dispatcher ({@code RegisterClientCommandsEvent}) rather than
 * alongside the server-side {@code AstronimaCommand} tree.
 *
 * <p>Exists specifically so a person can get the exact emitter this mod builds in code into
 * {@code /photon_editor} and change it visually, per the workflow design/vfx-craft.md §1.1 settled
 * on: read a real exported project instead of guessing the format blind.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class RetortSparksCommand {

    @SubscribeEvent
    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astronima_fx")
                .then(Commands.literal("export_retort_sparks")
                        .executes(RetortSparksCommand::exportDefault)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> exportNamed(context.getSource(),
                                        StringArgumentType.getString(context, "name"))))));
    }

    private static int exportDefault(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return exportNamed(context.getSource(), "retort_sparks");
    }

    private static int exportNamed(CommandSourceStack source, String name) {
        // Not source.getLevel() (a server-oriented accessor): Photon's own client commands read
        // Minecraft.getInstance() directly for exactly this reason (ClientCommands.java).
        var level = Minecraft.getInstance().level;
        if (level == null) {
            source.sendFailure(Component.literal("astronima_fx: no client level loaded"));
            return 0;
        }
        var file = RetortPhotonSparks.defaultProjectFile(name);
        try {
            RetortPhotonSparks.exportProject(level, file);
            source.sendSuccess(() -> Component.literal("astronima_fx: exported ")
                    .append(Component.literal(file.getName())
                            .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                                    .withClickEvent(new ClickEvent.OpenFile(file.getAbsolutePath()))))
                    .append(" — open it from /photon_editor"), false);
        } catch (Exception e) {
            source.sendFailure(Component.literal("astronima_fx: export failed — " + e));
        }
        return 1;
    }

    private RetortSparksCommand() {}
}
