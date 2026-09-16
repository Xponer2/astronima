#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;

out vec2 localPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // -1..1 across the quad, so the fragment shader can compute a true radial distance
    // regardless of how the quad itself is foreshortened on screen by perspective - a flat
    // tinted quad with no gradient looked exactly like what it was (reported back as "a
    // square"); a shape computed per-fragment in this local space is a real circle always.
    localPos = UV0 * 2.0 - 1.0;
}
