#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <astronima:noise.glsl>

in vec2 localPos;

out vec4 fragColor;

float fbm(vec2 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amp * noise(p);
        p *= 2.13;
        amp *= 0.5;
    }
    return value;
}

// A real per-fragment lunar phase, not one of vanilla's 8 baked sprite textures - the shape is
// computed here, not looked up. AsteroidSkyRenderer synthesises a light direction from the real,
// independently-varying MOON_PHASE attribute (0 = full moon, 4 = new moon, 45 degrees apart) and
// hands it over through ModelOffset.xyz, exactly the way the sun shaders smuggle a float through
// ModelOffset.x. It is not the Sun's own real direction projected geometrically - that was the
// first version, and it was wrong, because this engine's own day timeline holds MOON_ANGLE at a
// constant 180-degree offset from SUN_ANGLE always (see AsteroidSkyRenderer#drawMoonOccluder's
// own comment, and /astronima sky, for how that was actually found rather than guessed at).
// Reconstructing a hemisphere's worth of surface normal from a flat disc
// (x, y, sqrt(1-x^2-y^2)) is the standard billboard-sphere trick either way: cheap, and correct
// for a body seen from far enough away that its own parallax does not matter.
void main() {
    float d = length(localPos);
    if (d > 1.0) {
        discard;
    }
    vec3 normal = vec3(localPos.x, localPos.y, sqrt(max(0.0, 1.0 - d * d)));
    vec3 lightDir = normalize(ModelOffset.xyz);
    float ndotl = dot(normal, lightDir);
    // Soft, not a hard sprite cutout - the actual point of doing this per-fragment instead of
    // picking one of 8 baked textures.
    float terminator = smoothstep(-0.15, 0.15, ndotl);

    // Regolith texture: invented (no real crater map), standing in for the mod's own "more
    // detail" bar rather than a flat grey circle. Two scales, the same reasoning as everywhere
    // else this session - one octave reads as a smudge, not a surface.
    float craters = fbm(localPos * 6.0);
    float fineCraters = fbm(localPos * 17.0 + vec2(50.0, 12.0));
    float regolith = 0.82 + 0.12 * craters + 0.06 * fineCraters;

    // A real airless body's own photometry is close to flat (Lommel-Seeliger scattering, not
    // atmospheric limb darkening) - kept deliberately subtle rather than reusing the Sun's own
    // strong limb law, which would be physically wrong here.
    float limb = mix(0.88, 1.0, normal.z);

    // The dark side is not pure black: a small ambient floor keeps the terminator readable
    // against the void rather than vanishing into it - invented for legibility, not a claim
    // about earthshine (this body has no primary to reflect it).
    float brightness = mix(0.035, 1.0, terminator) * regolith * limb;

    // The Moon's own signature, and unlike the Sun's breathing or the nebula's pulse, this one is
    // entirely real: the opposition surge (Seeliger effect) - real regolith brightens sharply and
    // disproportionately right at full opposition, because every visible grain's own shadow hides
    // directly behind itself only when the light source sits exactly behind the observer.
    // `lightDir.z` reaches exactly 1 only at phaseAngle = 0, i.e. only during the real
    // MOON_PHASE.FULL_MOON phase - so this fires precisely when a real Moon's own surge would,
    // gated by `terminator` so it only ever brightens ground already lit.
    float oppositionSurge = smoothstep(0.9, 1.0, lightDir.z) * 0.5 * terminator;
    brightness += oppositionSurge;

    vec3 color = ColorModulator.rgb * brightness;

    float alpha = clamp((1.0 - d) * 10.0, 0.0, 1.0);
    fragColor = vec4(color, alpha);
}
