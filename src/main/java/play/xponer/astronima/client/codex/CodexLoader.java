package play.xponer.astronima.client.codex;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.xponer.astronima.Astronima;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the guides off disk, and again every time the player reloads resources.
 *
 * <p>Resources rather than data, on purpose: the book is a client thing, so <strong>F3+T reloads
 * it</strong>. Somebody writing a guide can keep the game open, save the file, tap two keys and read
 * what they just wrote. A guide system that needs a restart is a guide system nobody writes guides
 * for.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class CodexLoader implements ResourceManagerReloadListener {

    private static final Logger LOG = LoggerFactory.getLogger(CodexLoader.class);
    private static final String ROOT = "codex";
    private static final String SUFFIX = ".md";

    private static CodexBook book = CodexBook.EMPTY;

    /** Everything currently loaded. Empty before the first reload, never null. */
    public static CodexBook book() {
        return book;
    }

    @SubscribeEvent
    private static void onAddReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Astronima.MODID, "codex"),
                new CodexLoader());
    }

    /**
     * The language the guides are read in, as a file suffix.
     *
     * <p>A guide is a file, so translating one is writing another file beside it:
     * {@code sizing.md} and {@code sizing.ru.md}. That is deliberately not the lang-key system —
     * a lang file is for a label, and a page of prose in a JSON string is a page nobody will ever
     * edit. Whoever translates a guide should be looking at a guide.
     */
    private static String suffixFor(String language) {
        return "." + language.toLowerCase(java.util.Locale.ROOT).split("_")[0] + SUFFIX;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        String language = net.minecraft.client.Minecraft.getInstance()
                .getLanguageManager().getSelected();
        String wanted = suffixFor(language);
        java.util.Map<String, String> best = new java.util.LinkedHashMap<>();
        java.util.Set<String> translated = new java.util.HashSet<>();

        List<CodexPage> pages = new ArrayList<>();
        var found = resources.listResources(ROOT, at -> at.getPath().endsWith(SUFFIX));
        for (var entry : found.entrySet()) {
            Identifier at = entry.getKey();
            String path = at.getPath().substring(ROOT.length() + 1,
                    at.getPath().length() - SUFFIX.length());
            // "electrical/sizing.ru" is the Russian of "electrical/sizing", not a page of its own.
            int dot = path.lastIndexOf('.');
            String tag = dot < 0 ? "" : path.substring(dot);
            String id = dot < 0 ? path : path.substring(0, dot);
            boolean isWanted = (tag + SUFFIX).equals(wanted);
            if (!tag.isEmpty() && !isWanted) {
                continue;                       // a translation into some other language
            }
            if (!isWanted && translated.contains(id)) {
                continue;                       // already have it in the player's own language
            }
            try (InputStream stream = entry.getValue().open()) {
                best.put(id, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                if (isWanted) {
                    translated.add(id);
                }
            } catch (IOException unreadable) {
                // One bad file costs one page, never the book. A guide is hand-edited content and
                // the whole point is that editing it is safe.
                LOG.warn("could not read codex page {}", at, unreadable);
            }
        }
        best.forEach((id, source) -> pages.add(CodexPage.of(id, CodexMarkup.parse(source))));
        book = CodexBook.of(pages);
        List<String> broken = book.brokenLinks();
        if (!broken.isEmpty()) {
            LOG.warn("codex has links to pages that do not exist: {}", broken);
        }
        LOG.info("codex loaded {} pages in {} sections", book.byId().size(),
                book.sections().size());
    }
}
