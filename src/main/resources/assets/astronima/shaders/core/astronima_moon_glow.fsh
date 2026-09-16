#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 localPos;

out vec4 fragColor;

// Deliberately restrained: a real airless moon has no atmosphere to scatter light into a corona
// the way the Sun's own plasma does, so this is not the sun halo's shader shrunk down - it is a
// much smaller, much dimmer, un-textured falloff, closer to what stray light around a bright
// disc actually looks like to an eye/lens rather than a claim about lunar physics. Kept because
// the alternative - nothing at all past the disc's own hard edge - read as flatter than the rest
// of this sky once the Sun had its own bloom.
void main() {
    float d = length(localPos);
    if (d > 1.0) {
        discard;
    }
    float falloff = pow(max(0.0, 1.0 - d), 3.0) * 0.22;
    fragColor = vec4(ColorModulator.rgb * falloff, falloff);
}
