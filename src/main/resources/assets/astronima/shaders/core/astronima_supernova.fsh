#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 localPos;

out vec4 fragColor;

// A supernova (design/sky.md §3's own "supernova" row): a new point of light where there was
// none - real in kind (a naked-eye supernova genuinely is an intensely bright point, not a shape
// or a disc). Colour is not chosen here at all: AsteroidSkyRenderer#drawSupernova computes it on
// the CPU from BlackBody.rgb(SupernovaSpectrum.temperatureKAt(elapsed)) - the exact same real
// temperature /astronima magic spectrum supernova reports through Spectrum.peakWavelengthNm, so
// the colour on screen and the colour the instrument reads are one real number, not two
// independently tuned ones. This shader only shapes that colour into a glowing point.
//
// Invented: the exact core/glow falloff shape and the twinkle rate, tuned for a readable, lively
// point rather than measured from a real point-spread function.
void main() {
    float d = length(localPos);
    if (d > 1.0) {
        discard;
    }

    float time = ModelOffset.x;
    float growth = clamp(ModelOffset.y, 0.0, 1.0);

    // Real stars twinkle from atmospheric turbulence, which this airless body has none of - kept
    // anyway (as StarField's own stars already are, "kept, liked") purely so a single bright new
    // point does not read as flat and lifeless against its already-twinkling neighbours.
    // Amplitude raised 10% -> 25% (design/sky.md §5.7's S7e): a star that has just exploded may
    // flicker hard, and the smaller figure read as barely distinguishable from the ordinary
    // background starfield's own twinkle.
    float twinkle = 0.75 + 0.25 * sin(time * 5.0 + d * 20.0);

    float core = smoothstep(0.14, 0.0, d) * 1.4;
    float halo = smoothstep(0.55, 0.0, d) * 0.45;
    float brightness = (core + halo) * twinkle * growth;

    vec3 color = ColorModulator.rgb * brightness;
    float alpha = clamp(brightness, 0.0, 1.0);
    fragColor = vec4(color, alpha);
}
