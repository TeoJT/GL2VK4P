# OpenGL To Vulkan layer For Processing
(A.K.A. GL2VK4P)
Part of the "Vulkan in Processing" project, this component is the crucial part of the Processing Vulkan framework which translate the OpenGL commands into Vulkan commands, and ultimately, is the Vulkan framework, as it directly executes the commands.

## Features:
- Most OpenGL functions (will be) supported.
- Thread-safe multithreading (up to 70% CPU usage on my machine), with use of the selectNode() function.
- OpenGL glsl to Vulkan glsl formatter (no need to precompile or port shaders, it can be done at runtime) (still WIP).
- Runtime shader compiler

## Design
![updated-design-1](https://github.com/user-attachments/assets/5ace518e-7f4a-4add-8a76-e2a9d9eec6ac)


## Major TODO's
- Uniforms
- OpenGL glsl to Vulkan glsl formatter
- Integrate GL2VK into a Processing sketch and start testing
- Depth buffer
- Textures

## Future
*(Not necessary but nice features I'll implement if I have time)*
- Pipeline caching (for cases where GL program tries to change pipeline during runtime)
- Use descriptor sets for vertex buffers
- Use descriptor sets for shaders with large uniform data
- Multiple framebuffers (for use with PGraphics)
