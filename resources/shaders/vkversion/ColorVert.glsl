#version 450

layout( push_constant ) uniform gltovkuniforms_struct
{
  mat4 transformMatrix;
} gltovkuniforms;


layout(location = 0) in vec4 position;
layout(location = 1) in vec4 color;

layout(location = 0) out vec4 vertColor;

void main() {
  gl_Position = gltovkuniforms.transformMatrix * position;
    
  vertColor = color;
}
