package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import play.xponer.astronima.block.entity.HfDigesterBlockEntity;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.chem.FluoriteDigestion;
import play.xponer.astronima.sim.physio.ChemicalBurn;

import java.util.function.Consumer;

/**
 * Real, contact-hazardous hydrofluoric acid off the digester — design/halogens.md §22-25
 * (Part C2).
 *
 * <h2>Handling it is the hazard</h2>
 * Right-clicking the raw bottle simulates the real moment of risk: opening, testing or decanting
 * it by hand. There is nothing else to do with the raw item before C4 exists, so this is its
 * honest first "use" — and the lesson ("this liquid has no safe manual handling protocol yet") is
 * the same one a real lab teaches a new hire around unrated HF.
 *
 * <h2>No suit check, anywhere in this file</h2>
 * Deliberately: real HF crosses skin whether or not a suit is sealed, which is the one fact every
 * other contact model in this mod ({@code Barrier.GLOVES}) gets wrong for it. The dose itself is
 * applied later, ticking the same way {@code RadiationDose} does
 * ({@code AtmosphereEvents.applyChemicalBurn}) — this method only registers the contact.
 */
public class HydrofluoricAcidItem extends Item {

    /** One item's real mass, at this mod's own one-mole-per-item convention
     *  ({@link HfDigesterBlockEntity#MOL_PER_ITEM}, fixed by Part C1) and HF's own real molar mass
     *  ({@link FluoriteDigestion#HF_MOLAR_MASS}) — not a new number invented for this item. */
    public static final double CONTACT_GRAMS_PER_ITEM =
            HfDigesterBlockEntity.MOL_PER_ITEM * FluoriteDigestion.HF_MOLAR_MASS;

    public HydrofluoricAcidItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        held.shrink(1);

        double before = player.getData(ModAttachments.CHEMICAL_BURN_DOSE);
        double after = ChemicalBurn.contact(before, CONTACT_GRAMS_PER_ITEM);
        player.setData(ModAttachments.CHEMICAL_BURN_DOSE.get(), (float) after);

        level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, 0.6f, 0.7f);
        player.sendSystemMessage(Component.literal(
                "No sting, no visible burn - hydrofluoric acid crosses skin without a warning."
                        + " It is already working.").withStyle(ChatFormatting.DARK_RED));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("astronima.hydrofluoric_acid.warning")
                .withStyle(ChatFormatting.RED));
    }
}
