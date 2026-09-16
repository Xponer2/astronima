package play.xponer.astronima.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import play.xponer.astronima.sim.sky.RegolithBallistics;

/**
 * One grain of rock thrown up by an impact, on a real ballistic arc under this body's own gravity
 * (design/sky.md §3's impact-flash row).
 *
 * <p><strong>The point of this class is what it is not.</strong> The first cut of the impact event
 * used vanilla {@code CLOUD} particles, which fall at Earth gravity through Earth air — a plume
 * that billows and settles like dust on a windy day, on a body that has neither. Every grain here
 * instead follows {@link RegolithBallistics}: an exact parabola, no drag term because there is no
 * air, at the same gravity {@code Microgravity} already gives the player's own falls. The slow,
 * high, unhurried arc is the read on this body's gravity, visible across a valley without an
 * instrument.
 *
 * <p>Position is a closed-form function of age rather than accumulated velocity, the same
 * construction {@link IncandescenceParticle} established: the arc is exact at every tick instead
 * of drifting off whatever a per-tick integrator happened to accumulate, and a test can hold any
 * tick's height directly.
 */
public final class RegolithParticle extends SingleQuadParticle {

    /** Slowest and fastest a grain leaves the crater, blocks/tick. Invented (a real ejecta speed
     * distribution is a power law spanning many orders of magnitude); the arc each one then flies
     * is not. */
    private static final double MIN_UP_SPEED = 0.10;
    private static final double MAX_UP_SPEED = 0.30;

    private static final double MAX_SIDEWAYS_SPEED = 0.09;

    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final double upSpeed;
    private final double sidewaysX;
    private final double sidewaysZ;
    private final float spinPerTick;

    private RegolithParticle(ClientLevel level, double x, double y, double z,
                             SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, 0.0, 0.0, 0.0, sprites.get(random));
        // The arc is computed, not integrated, so vanilla's own gravity must not also act on it.
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;

        this.upSpeed = MIN_UP_SPEED + random.nextDouble() * (MAX_UP_SPEED - MIN_UP_SPEED);
        double heading = random.nextDouble() * Math.PI * 2.0;
        double sideways = random.nextDouble() * MAX_SIDEWAYS_SPEED;
        this.sidewaysX = Math.cos(heading) * sideways;
        this.sidewaysZ = Math.sin(heading) * sideways;
        this.spinPerTick = (random.nextFloat() - 0.5F) * 0.35F;

        // Exactly as long as its own arc lasts: a grain vanishing mid-flight, or lying about
        // where it landed, would break the one thing the arc is here to show.
        this.lifetime = (int) Math.ceil(RegolithBallistics.hangTimeTicks(upSpeed));
        this.quadSize = 0.045F + random.nextFloat() * 0.055F;

        float shade = 0.30F + random.nextFloat() * 0.22F;
        setColor(shade, shade * 0.96F, shade * 0.90F);
        setAlpha(1.0F);
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
        setPos(spawnX + RegolithBallistics.horizontalAt(sidewaysX, age),
                spawnY + RegolithBallistics.heightAt(upSpeed, age),
                spawnZ + RegolithBallistics.horizontalAt(sidewaysZ, age));
        this.oRoll = this.roll;
        this.roll += spinPerTick;

        // Only the last few ticks fade, so a grain reads as landing rather than dissolving in
        // mid-air - the arc has to be followed all the way down to be worth drawing at all.
        float remaining = lifetime - age;
        setAlpha(Mth.clamp(remaining / 4.0F, 0.0F, 1.0F));
    }

    @Override
    protected Layer getLayer() {
        return Layer.OPAQUE;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xa, double ya, double za, RandomSource random) {
            return new RegolithParticle(level, x, y, z, sprites, random);
        }
    }
}
