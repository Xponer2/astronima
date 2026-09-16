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
        p *= 2.11;
        amp *= 0.5;
    }
    return value;
}

// A passing companion body (design/sky.md §3, "belt density; scale"): real content - a small
// body drifting past close enough to see, visibly tumbling as real irregular asteroids do (they
// are not spherical, and do not present the same face to an observer as they turn). No relative-
// geometry phase shading attempted here the way the Moon gets - a body visible for well under a
// minute reads fine as a simply-lit rocky silhouette, and building the Moon's own full sun-
// relative lighting for something gone this quickly would be effort spent where nobody looks.
void main() {
    float d = length(localPos);
    if (d > 1.0) {
        discard;
    }
    float time = ModelOffset.x;

    // Tumbling: the surface noise rotates independently of the body's own motion across the sky -
    // real in kind (small irregular bodies really do tumble, often chaotically), invented in its
    // exact rate, which is tuned for visibility across one brief pass rather than measured.
    float tumbleAngle = time * 1.3;
    float cosT = cos(tumbleAngle);
    float sinT = sin(tumbleAngle);
    vec2 rotated = vec2(localPos.x * cosT - localPos.y * sinT, localPos.x * sinT + localPos.y * cosT);

    float craters = fbm(rotated * 5.0);
    float fineCraters = fbm(rotated * 13.0 + vec2(20.0, 7.0));
    float regolith = 0.55 + 0.25 * craters + 0.15 * fineCraters;

    // A simple fixed lit/shadowed side rather than the Moon's own real sun-relative geometry -
    // real in kind (a rock lit by a real sun does have a bright and a dark side), simplified
    // because this event's own brevity does not reward the extra precision.
    float mu = sqrt(max(0.0, 1.0 - d * d));
    float lit = 0.28 + 0.72 * smoothstep(-0.3, 0.6, rotated.x) * mu;

    vec3 color = ColorModulator.rgb * regolith * lit;
    float alpha = clamp((1.0 - d) * 8.0, 0.0, 1.0);
    fragColor = vec4(color, alpha);
}
