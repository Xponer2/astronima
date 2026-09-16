package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.style.animation.StyleAnimation;
import com.lowdragmc.lowdraglib2.math.interpolate.Eases;
import com.lowdragmc.lowdraglib2.syncdata.ISubscription;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;

import java.util.List;

/**
 * The hover-expand preview a related-link pill shows its full title through. Split out of
 * {@code CodexUi} (design/codex-rework.md §9 / C7) — this is a self-contained animation/overlay
 * concern, wired from {@link CodexBlocks#renderPage} but otherwise unrelated to how a page's
 * blocks are drawn.
 */
final class CodexLinkPreview {

    /**
     * A related-link pill's title is truncated ({@code CodexBlocks#shortened}) to keep the row it
     * sits in from ever needing to resize around it. On hover, the full title still needs to be
     * readable somewhere - this plays a type-erase / expand / type-in sequence on a second,
     * purely visual copy of the pill, floated on top of everything else, so the real pill
     * (and every other pill beside it) never moves, resizes, or gains a bigger hitbox.
     *
     * <h2>Why a floating copy, not the pill itself</h2>
     * The obvious first idea - grow the pill in place - runs into two real constraints of this
     * framework, both confirmed against LDLib2's own source rather than assumed:
     * <ul>
     * <li>{@code z-index} only orders an element against its own siblings ({@code UIElement
     * .getSafeSortedChildren()}, sorted then painted per parent) - a pill that outgrows its own
     * box would still be painted-over by the sibling pill drawn right after it, not above it.
     * <li>{@code ScrollerView}'s viewport clips its own content - anything still inside it, grown
     * or not, is cut at the scroll window's edge.
     * </ul>
     * LDLib2's own {@code Selector} dropdown solves the identical "float above everything, clear
     * of any clipping ancestor" problem by reparenting its popup onto {@code ModularUI.ui
     * .rootElement} for the duration it needs to be shown, positioned there with {@code
     * TaffyPosition.ABSOLUTE} and world-to-root coordinate conversion. That is the exact pattern
     * copied here, not a guess at one.
     *
     * <h2>Why the preview never gets its own hitbox</h2>
     * {@code allowHitTest(false)} (a real, dedicated field on {@code UIElement} - confirmed in
     * source, distinct from {@code isVisible}/{@code isActive}) excludes the preview from hit
     * testing entirely. {@code UIElement.hitTest} still tests the real pill underneath on its own,
     * unchanged geometry regardless of what is painted on top of it, so hover and click keep
     * working exactly as before, scoped to the small original box - never the larger visual one.
     */
    static void wireExpandPreview(CodexUi.Session session, UIElement pill, String shortText, String fullText) {
        if (shortText.equals(fullText)) {
            return;
        }
        Label pillLabel = (Label) pill.getSafeChildren().get(0);

        UIElement preview = new UIElement().addClass("codex_related_btn");
        preview.layout(l -> l.positionType(TaffyPosition.ABSOLUTE).width(100).heightAuto());
        preview.style(s -> s.zIndex(1));
        Label previewLabel = new Label().setValue(Component.literal(shortText));
        CodexBlocks.wrapped(previewLabel);
        previewLabel.textStyle(style -> style.textColor(0xFF6FB0EE).fontSize(7));
        preview.addChild(previewLabel);
        // On both, not just the wrapper: UIElement.hitTest tests children BEFORE it ever
        // consults its own isAllowHitTest(), so a child with hit-testing still enabled remains a
        // valid hit target no matter what the parent is set to - the wrapper's own flag alone
        // only stops the wrapper itself from being picked when nothing under it matched. Since
        // previewLabel fills nearly the wrapper's whole box (wrapped() sets it to 100% width),
        // leaving it hit-testable meant almost the entire preview intercepted the pointer -
        // exactly the pill's text area - and swallowed every event meant for the real pill
        // underneath, which is why only that area, not the pill's padding sliver, broke.
        for (UIElement target : List.of(preview, previewLabel)) {
            target.setAllowHitTest(false);
        }

        PreviewRig rig = new PreviewRig(preview, previewLabel);
        for (UIElement target : List.of(pill, pillLabel)) {
            target.addEventListener(UIEvents.MOUSE_ENTER,
                    event -> expandPreview(session, pill, shortText, fullText, rig));
            target.addEventListener(UIEvents.MOUSE_LEAVE,
                    event -> collapsePreview(session, shortText, rig));
            // A click navigates (the row's own onClick, wired before this pill ever reached this
            // method) and the page rebuild is about to tear the real pill out of contentScroll -
            // but the floating preview lives on root, outside that tree entirely (that is the
            // whole point of it), so nothing about the rebuild touches it. Hover-leave is only
            // re-resolved on the next actual pointer movement, not on a click that leaves the
            // cursor sitting where it was, so without this the expanded preview stayed on screen,
            // now anchored to nothing, until the player happened to move the mouse. Detach it
            // outright here instead of playing the collapse animation - the page it was
            // explaining is already gone by the time it would finish.
            target.addEventListener(UIEvents.CLICK, event -> detachPreviewImmediately(rig));
        }
    }

    private static void detachPreviewImmediately(PreviewRig rig) {
        rig.anim.unsubscribe();
        if (rig.attached) {
            rig.preview.removeEventListener(UIEvents.TICK, rig.positionTick);
            rig.preview.getParent().removeChild(rig.preview);
            rig.attached = false;
        }
    }

    /** Runtime state for one related-link pill's floating preview - see {@link #wireExpandPreview}. */
    private static final class PreviewRig {
        final UIElement preview;
        final Label previewLabel;
        boolean attached = false;
        ISubscription anim = () -> {};
        /**
         * Re-anchors the preview to the pill's live position every frame while attached, kept
         * by reference so it can later be removed by identity ({@code removeEventListener} has
         * no other way to find it).
         *
         * <p><strong>Why this has to be separate from {@link #anim}, not folded into it.</strong>
         * {@code expandPreview}/{@code collapsePreview} snapped the preview's position once, at
         * the moment it first attached, then never touched it again - correct only as long as
         * nothing under it moves. The pill lives inside a {@code ScrollerView}; scrolling while
         * hovered, mid-expand, or mid-collapse all left the preview stranded at its original
         * screen position while the real pill moved out from under it. {@code anim} only runs
         * for the width/typewriter phases and goes idle the moment the preview finishes
         * expanding - exactly the state a player pauses in to read the full title, and exactly
         * when a scroll was most likely to catch it unrepositioned. This ticks for the entire
         * attached lifetime instead, independent of which (if any) animation is mid-flight.
         */
        UIEventListener positionTick;

        PreviewRig(UIElement preview, Label previewLabel) {
            this.preview = preview;
            this.previewLabel = previewLabel;
        }
    }

    /** Where {@code pill} is right now, converted into the root's own layout space. */
    private static Vector2f livePosition(UIElement root, UIElement pill) {
        Vector2f worldPos = pill.localToWorld(new Vector2f(pill.getPositionX(), pill.getPositionY()));
        return root.worldToLocalLayoutOffset(worldPos);
    }

    private static void expandPreview(CodexUi.Session session, UIElement pill, String shortText, String fullText, PreviewRig rig) {
        rig.anim.unsubscribe();
        UIElement root = session.modularUI.ui.rootElement;
        if (!rig.attached) {
            Vector2f localPos = livePosition(root, pill);
            rig.preview.layout(l -> l.left(localPos.x).top(localPos.y).width(100));
            rig.previewLabel.setValue(Component.literal(shortText));
            root.addChild(rig.preview);
            rig.attached = true;
            rig.positionTick = event -> {
                Vector2f pos = livePosition(root, pill);
                rig.preview.layout(l -> l.left(pos.x).top(pos.y));
            };
            rig.preview.addEventListener(UIEvents.TICK, rig.positionTick);
        }
        int expandedWidth = Math.min(240, Math.max(100, 24 + fullText.length() * 5));
        // The current label text, not always a hardcoded shortText: if the pointer left and came
        // back mid-collapse, the label may already be showing a partially-erased string, and
        // starting the erase from there instead of snapping back to shortText first avoids a
        // one-frame flash of the full short label before erasing resumes.
        String from = rig.previewLabel.getValue().getString();
        rig.anim = typewriter(rig.preview, rig.previewLabel, from, "", () ->
                rig.anim = rig.preview.animation()
                        .ease(Eases.QUART_OUT)
                        .duration(0.09f)
                        .lss("width", expandedWidth)
                        .onFinished(el -> rig.anim = typewriter(rig.preview, rig.previewLabel, "", fullText, () -> {}))
                        .start());
    }

    private static void collapsePreview(CodexUi.Session session, String shortText, PreviewRig rig) {
        if (!rig.attached) {
            return;
        }
        rig.anim.unsubscribe();
        String current = rig.previewLabel.getValue().getString();
        rig.anim = typewriter(rig.preview, rig.previewLabel, current, "", () ->
                rig.anim = rig.preview.animation()
                        .ease(Eases.QUART_OUT)
                        .duration(0.09f)
                        .lss("width", 100)
                        .onFinished(el -> rig.anim = typewriter(rig.preview, rig.previewLabel, "", shortText, () -> {
                            rig.preview.removeEventListener(UIEvents.TICK, rig.positionTick);
                            UIElement root = session.modularUI.ui.rootElement;
                            root.removeChild(rig.preview);
                            rig.attached = false;
                        }))
                        .start());
    }

    /**
     * Reveals or erases {@code label}'s text one character at a time by driving a real, if
     * otherwise inert, animation on {@code driver} and reading its linear progress back out on
     * every frame - {@code onInterpolate} only ever fires for a target that actually has at
     * least one animated property (confirmed in {@code StyleAnimation}'s own source: it builds
     * one executor per animated property and never calls back if that list is empty), so this
     * rides a same-value opacity animation purely for its per-frame ticks and duration/easing/
     * onFinished plumbing, and ignores the (unchanged) opacity value itself.
     */
    private static ISubscription typewriter(UIElement driver, Label label, String from, String to, Runnable onFinished) {
        boolean typingIn = to.length() >= from.length();
        String full = typingIn ? to : from;
        int startLen = typingIn ? 0 : from.length();
        int endLen = typingIn ? to.length() : 0;
        float duration = Math.max(0.04f, Math.min(0.25f, Math.abs(endLen - startLen) * 0.014f));
        return driver.animation()
                .ease(Eases.LINEAR)
                .duration(duration)
                .style(PropertyRegistry.OPACITY, 1f)
                .onInterpolate((runtime, element) -> {
                    float t = runtime.getInterpolator().getNormalizedTime();
                    int len = Math.max(0, Math.min(full.length(), Math.round(startLen + (endLen - startLen) * t)));
                    label.setValue(Component.literal(full.substring(0, len)));
                })
                .onFinished(element -> {
                    label.setValue(Component.literal(full.substring(0, endLen)));
                    onFinished.run();
                })
                .start();
    }

    private CodexLinkPreview() {}
}
