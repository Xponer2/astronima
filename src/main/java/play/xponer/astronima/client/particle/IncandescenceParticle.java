package play.xponer.astronima.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import play.xponer.astronima.registry.ModParticles.IncandescenceOptions;
import play.xponer.astronima.sim.optics.EmberDrift;

/**
 * A mote of a genuinely hot surface's own black-body glow (design/presentation.md §3).
 *
 * <p>Carries no colour logic of its own: {@link IncandescenceOptions#color()} is
 * {@link play.xponer.astronima.sim.optics.BlackBody}'s real answer, computed server-side and
 * sent over rather than re-derived here, so the sprite stays a plain white glow shaped only
 * by alpha (see {@code tools/textures.py}'s {@code tex_retort_glow} for why).
 *
 * <p>Position is a closed-form function of age rather than accumulated velocity — the same
 * reasoning {@link EmberDrift} itself is built on — so the curl a real plume has is exact at
 * every tick instead of drifting off whatever {@code friction}/{@code gravity} happened to
 * integrate to.
 */
public final class IncandescenceParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private final float intensity01;
    private final float baseQuadSize;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final double phase;
    private final double riseSpeedPerTick;

    private IncandescenceParticle(
            ClientLevel level, double x, double y, double z,
            IncandescenceOptions options, SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, 0.0, 0.0, 0.0, sprites.first());
        this.sprites = sprites;
        this.intensity01 = options.intensity01();
        this.hasPhysics = false;
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        this.phase = random.nextDouble() * Math.PI * 2.0;

        boolean accent = options.accent();
        float sizeMultiplier = accent ? 1.9F : 1.0F;
        this.riseSpeedPerTick = (0.012 + random.nextFloat() * 0.018)
                * (0.4 + intensity01) * (accent ? 1.5 : 1.0);

        this.quadSize = (0.11F + intensity01 * 0.09F) * sizeMultiplier
                * (0.75F + random.nextFloat() * 0.5F);
        this.baseQuadSize = this.quadSize;
        this.lifetime = accent ? 30 + random.nextInt(14) : 26 + random.nextInt(16);

        setColor(ARGB.redFloat(options.color()), ARGB.greenFloat(options.color()), ARGB.blueFloat(options.color()));
        setAlpha(0.0F);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        setSpriteFromAge(sprites);
        setPos(spawnX + EmberDrift.outwardX(age, phase),
                spawnY + age * riseSpeedPerTick,
                spawnZ + EmberDrift.outwardZ(age, phase));

        // Fade in quickly, hold, then fade out over the back third - a pop-free presence
        // rather than a sprite that snaps on and off.
        float ageFrac = (float) age / lifetime;
        float fade;
        if (ageFrac < 0.15F) {
            fade = ageFrac / 0.15F;
        } else if (ageFrac > 0.6F) {
            fade = Mth.clamp(1.0F - (ageFrac - 0.6F) / 0.4F, 0.0F, 1.0F);
        } else {
            fade = 1.0F;
        }
        setAlpha(fade * (0.6F + intensity01 * 0.4F));
        quadSize = baseQuadSize * (0.85F + fade * 0.15F);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public int getLightCoords(float partialTick) {
        return LightCoordsUtil.addSmoothBlockEmission(super.getLightCoords(partialTick), intensity01);
    }

    public static final class Provider implements ParticleProvider<IncandescenceOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                IncandescenceOptions options, ClientLevel level, double x, double y, double z,
                double xa, double ya, double za, RandomSource random) {
            return new IncandescenceParticle(level, x, y, z, options, sprites, random);
        }
    }
}
