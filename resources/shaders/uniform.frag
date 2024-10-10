#version 450
#extension GL_ARB_separate_shader_objects : enable

layout( push_constant ) uniform u_pos_struct 
{ 
  layout(offset=32) float extra_red;  
  float u_brightness;  
} uni;

layout(location = 0) in vec3 fragColor;

layout(location = 0) out vec4 outColor;

void main() {
    outColor = vec4(fragColor+vec3(uni.u_brightness+uni.extra_red, uni.u_brightness, uni.u_brightness), 1.0);
}