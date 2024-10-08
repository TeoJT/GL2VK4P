#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inColor;

layout(location = 0) out vec3 fragColor;

layout( push_constant ) uniform u_pos_struct 
{ 
  vec2 u_pos;
  vec2 u_pos_secondary;
  float u_r;
  float u_g;
  float u_b;
} uni;

void main() {
    gl_Position = vec4(inPosition+uni.u_pos+uni.u_pos_secondary, 0.0, 1.0);
    fragColor = inColor*vec3(uni.u_r, uni.u_g, uni.u_b);
}