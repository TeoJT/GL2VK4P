#version 450

#ifdef GL_ES
precision mediump float;
precision mediump int;
#endif

layout(location = 0) in vec4 vertColor;

layout(location = 0) out vec4 gl2vkFragColor;

void main() {
  gl2vkFragColor = vertColor;
}