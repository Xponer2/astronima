package play.xponer.astronima.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import play.xponer.astronima.Astronima;

/** Key bindings for the mod's screens. */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class ModKeybinds {

    /**
     * The mod's own section in the controls screen.
     *
     * <p>All four bindings were filed under vanilla MISC, which puts them in a list with the
     * screenshot key and the perspective toggle and gives a player no way to find them as a group.
     * A translation guard turned this up sideways: the Russian file carried a name for
     * {@code key.categories.astronima}, a category that did not exist and could not have — 1.26
     * builds the key as {@code key.category.<namespace>.<path>}, singular. So the translation was
     * for a heading nobody would ever see, and the heading it should have named was vanilla's.
     */
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(Astronima.MODID, "main"));
    public static final KeyMapping OPEN_BIOMONITOR = new KeyMapping(
            "key.astronima.biomonitor",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_H,
            CATEGORY);

    /**
     * Turns the wire-layer part in your hand before you place it.
     *
     * <p>R, because that is what every builder's hand is already on for rotating a thing about to
     * be placed. The wrench still turns one that is already on the wall; this is the same decision
     * made before committing, which is the one that saves ten clicks.
     */
    public static final KeyMapping ROTATE_PART = new KeyMapping(
            "key.astronima.rotate_part",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            CATEGORY);

    /**
     * The codex, where every guide in the mod lives.
     *
     * <p>A keybind rather than an item, deliberately: the book is documentation, and gating
     * documentation behind a craft means the player who most needs it is the one who cannot open
     * it. K, because nothing else in this mod wants it.
     */
    public static final KeyMapping OPEN_CODEX = new KeyMapping(
            "key.astronima.codex",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_K,
            CATEGORY);

    /**
     * Hold to work the suit-repair applicator.
     *
     * <p>It was a hardcoded space bar, which is a key some people cannot comfortably hold and
     * everybody expects to rebind. Every key this mod reads is a {@link KeyMapping} now — if it is
     * in the controls screen it can be changed, and if it is not in the controls screen it should
     * not exist.
     */
    public static final KeyMapping REPAIR_HOLD = new KeyMapping(
            "key.astronima.repair_hold",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_SPACE,
            CATEGORY);

    /**
     * Flips between HUD pages.
     *
     * <p>Only does anything when something did not fit, which is rare — but a readout that is
     * missing without saying so is worse than one a keypress away, and this is the keypress. V,
     * because nothing else in this mod wants it and it is under the hand that is already there.
     */
    public static final KeyMapping HUD_PAGE = new KeyMapping(
            "key.astronima.hud_page",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_V,
            CATEGORY);

    public static final KeyMapping TOGGLE_SNAP = new KeyMapping(
            "key.astronima.toggle_snap",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            CATEGORY);

    /**
     * The one axis the mouse does not own — design/eva-mobility.md §1.2. Z/C rather than the more
     * conventional Q/E: Q already drops an item and E already opens the inventory, and stealing
     * either from under a player's hand mid-flight is a worse first impression than an unfamiliar
     * pair of keys. Held, not pressed — rolling is continuous for as long as the key is down,
     * exactly like turning the mouse is.
     */
    public static final KeyMapping ROLL_LEFT = new KeyMapping(
            "key.astronima.roll_left",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_Z,
            CATEGORY);
    public static final KeyMapping ROLL_RIGHT = new KeyMapping(
            "key.astronima.roll_right",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_C,
            CATEGORY);

    /**
     * Push off whatever solid face is targeted — design/eva-mobility.md §2, candidate #3 of
     * microgravity.md §7.3. X: unused elsewhere in this mod or vanilla's own default bindings,
     * ergonomically under the same hand already on WASD, and reads as "eXpel" for anyone hunting
     * for it in the controls screen.
     */
    public static final KeyMapping PUSH_OFF = new KeyMapping(
            "key.astronima.push_off",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_X,
            CATEGORY);

    /**
     * Fires or releases a real safety line — design/eva-mobility.md §3. One key, toggled: not
     * tethered, it fires at whatever solid point is aimed at within range; already tethered, it
     * lets go. T for "tether" — unused elsewhere in this mod.
     */
    public static final KeyMapping TETHER = new KeyMapping(
            "key.astronima.tether",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_T,
            CATEGORY);

    @SubscribeEvent
    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(HUD_PAGE);
        // The category has to be registered before the bindings that name it, or the controls
        // screen has nowhere to file them.
        event.registerCategory(CATEGORY);
        event.register(OPEN_CODEX);
        event.register(REPAIR_HOLD);
        event.register(OPEN_BIOMONITOR);
        event.register(ROTATE_PART);
        event.register(TOGGLE_SNAP);
        event.register(ROLL_LEFT);
        event.register(ROLL_RIGHT);
        event.register(PUSH_OFF);
        event.register(TETHER);
    }

    private ModKeybinds() {}
}
