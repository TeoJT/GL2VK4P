package helloVulkan;

import java.nio.IntBuffer;

import helloVulkan.ShaderSPIRVUtils.SPIRV;
import helloVulkan.ShaderSPIRVUtils.ShaderKind;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static helloVulkan.ShaderSPIRVUtils.compileShaderFile;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;

import java.nio.ByteBuffer;

public class GL2VK {
	
	public static final int GL_VERTEX_BUFFER = 1;
	public static final int GL_INDEX_BUFFER = 2;
	
	public static final int GL_VERTEX_SHADER = 1;
	public static final int GL_FRAGMENT_SHADER = 2;
	
	public static final int GL_COMPILE_STATUS = 1;

	// Shaders aren't actually anything significant, they're really temporary data structures
	// to create a vulkan pipeline.
	private class GLShader {
		public String source = "";
		public boolean successfulCompile = false;
		public int type;
		public SPIRV spirv = null;
		
		// Use for vertex shaders only. See notes in glCompileShader
		// for why we're oddly putting this here.
		public ShaderAttribInfo attribInfo = null;
		
		public GLShader(int type) {
			this.type = type;
		}
	}
	
	// Attrib pointers are the most stupid thing ever that
	// we need an entire class to do it.
	private class GLAttribPointer {
		public GL2VKPipeline program = null; 
		
		public GLAttribPointer(GL2VKPipeline program) {
			this.program = program;
		}
	}
	
	private VulkanSystem system;
	
	// TODO: Change these to arrayLists?
	private GraphicsBuffer[] buffers = new GraphicsBuffer[4096];
	private GL2VKPipeline[] programs = new GL2VKPipeline[1024];
	private GLShader[] shaders = new GLShader[1024];
	
	// Vulkan locations != OpenGL attrib locations
	// Attribs are universally unique, meaning that any 2 programs will never have any
	// attribute locations that are the same. (e.g. Program 1 has 1 2 3, Program 2 has 4 5 6)
	// In vulkan, 2 different programs can have different attribute locations
	// (Program 1 has 1 2 3, Program 2 has 1 2 3 4)
	// Because of
	private GLAttribPointer[] glAttribs = new GLAttribPointer[4096];
	
	// Buffering with secondary command buffers (in high-performance threadnodes) results
	// in the validation layers giving an error related to not being allowed to call vkCmdCopyData
	// while the renderpass is enabled (and disabling it temporarily is not really an option).
	// But, it still seems to perform as normal. Remember validations guarentees that your application
	// will run without error on all hardware. So... what if we were to ignore the warnings for the 
	// sake of performance? That's what dangerMode does.
	private boolean dangerMode = false;
	
	private int bufferIndex = 1;
	private int programIndex = 1;
	private int shaderIndex = 1;
	private int attribIndex = 1;
	
	private int boundBuffer = 0;
	private int boundProgram = 0;
	
	
	
	// Constructor
	public GL2VK() {
		system = new VulkanSystem();
		system.initVulkan();
		
	}
	
	public void glGenBuffers(int count, IntBuffer out) {
		for (int i = 0; i < count; i++) {
			// Create new buffer object
			buffers[bufferIndex] = new GraphicsBuffer(system);
			// Put it into the int array so we get back our
			// ids to our allocated buffers.
			out.put(i, bufferIndex++);
		}
		out.rewind();
	}
	
	
	public void glBindBuffer(int type, int vbo) {
		boundBuffer = vbo;
	}
	
	public void glBufferData(int target, int size, ByteBuffer data, int usage) {
		// Get VK usage
		int vkusage = 0;
		switch (target) {
		case GL_VERTEX_BUFFER:
			vkusage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
			break;
		case GL_INDEX_BUFFER:
			vkusage = VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
			break;
		}
		
		
		// Note: target is for specifying vertex_array, indicies_array
		// which we'll likely need. Usage, I have no idea what it does.
		
		// Create buffer if not exist or currentSize != size.
		buffers[boundBuffer].createBufferAuto(size, vkusage);
		
		buffers[boundBuffer].bufferData(data, size, dangerMode);
	}
	
	public void glDrawArrays(int mode, int first, int count) {
		// TODO: *5 is just a placeholder for a fixed shader.
		system.nodeDrawArrays(buffers[boundBuffer].bufferID, count*5, 0);
	}
	
	// Probably not going to fully implement glEnableVertexAttribArray or glDisableVertexAttribArray
	// because chances are, when we use glVertexAttribPointer, we're being pretty clear that we do,
	// indeed, want to use the vertexAttrib. And it's not like glDisableVertexAttribArray is going to
	// have any effect, you can't disable vertex attribs in a pipeline that's already been created.
	public void glVertexAttribPointer(int glindex, int size, int type, boolean normalized, int stride, int offset) {
		// Convert from gl index to vk location
		// We also need the program to see what we're doing
		GL2VKPipeline program = glAttribs[glindex].program;
		// It's such a mess, I'm so sorry
		int vkLocation = program.getVKAttribLocation(glindex);
		
		program.bind(boundBuffer);
		program.vertexAttribPointer(vkLocation, size, offset, stride);
	}
	
	public int glCreateProgram() {
		int ret = programIndex;
		programs[programIndex++] = new GL2VKPipeline();
		return ret;
	}
	
	public int glGetAttribLocation(int program, String name) {
		if (programs[program] == null) return -1;
		
		return programs[program].getGLAttribLocation(name);
	}
	
	
	public int glCreateShader(int type) {
		int ret = shaderIndex;
		shaders[shaderIndex++] = new GLShader(type);
		return ret;
	}
	
	// TODO: add minor error checking.
	public void glShaderSource(int shader, String source) {
		// TODO: convert from opengl-style glsl to vulkan glsl
		shaders[shader].source = source;
	}

	public String glGetShaderSource(int shader) {
		return shaders[shader].source;
	}
	
	public void glDeleteShader(int shader) {
		shaders[shader] = null;
	}
	
	// This is simple stuff to understand (i hope)
	public void glCompileShader(int shader) {
		GLShader sh = shaders[shader];
		ShaderKind shaderKind;
		if (sh.type == GL_VERTEX_SHADER) {
			shaderKind = ShaderKind.VERTEX_SHADER;
		}
		else if (sh.type == GL_FRAGMENT_SHADER) {
			shaderKind = ShaderKind.FRAGMENT_SHADER;
		}
		else shaderKind = ShaderKind.VERTEX_SHADER;
		
		sh.spirv = compileShaderFile(sh.source, shaderKind);
		sh.successfulCompile = true;
		// TODO: failed compiles just throws an exception. Actually check to see if it compiled successfully
		// or not.
		
		if (sh.successfulCompile && sh.type == GL_VERTEX_SHADER) {
			// Here, we must get attribute information.
			//
			// "Why not do it when we create our pipeline or when we
			// attach the shader?"
			//
			// Because as soon as we compile the shader, the program could
			// call glGetAttribLocation before attaching the shader or 
			// linking the program.
			// Thanks, opengl, you're so messy that we have to do things weirdly.
			sh.attribInfo = new ShaderAttribInfo(sh.source);
		}
	}
	
	
	// This function populates the glAttribs array which contains information about
	// the vulkan program, and nothing else.
	// It literally just loops over or something
	private void addGLAttribs(GL2VKPipeline program) {
		int l = program.attribInfo.nameToLocation.size();
		for (int i = 0; i < l; i++) {
			glAttribs[attribIndex++] = new GLAttribPointer(program);
		}
	}
	
	
	
	public void glAttachShader(int program, int shader) {
		GLShader sh = shaders[shader];
		if (sh.type == GL_VERTEX_SHADER) {
			programs[program].vertShaderSPIRV = shaders[shader].spirv;
			// Of course we'll need the attrib info to our pipeline.
			// This function has been modified so that instead of vk locations, we get
			// back gl locations.
			// Welcome to the absolute unhinged nature of opengl.
			int count = programs[program].addAttribInfo(sh.attribInfo, attribIndex);
			attribIndex += count;
			// And then
			// We'll need gl attribs since 
			// gl attrib locations != vulkan attrib locations.
			addGLAttribs(programs[program]);
		}
		else if (sh.type == GL_FRAGMENT_SHADER) {
			programs[program].fragShaderSPIRV = shaders[shader].spirv;
		}
	}
	
	// Mainly just used for getting shader compilation status.
	public void glGetShaderiv(int shader, int pname, IntBuffer params) {
		if (pname == GL_COMPILE_STATUS) {
			int status = 0;
			if (shaders[shader].successfulCompile == true) {
				status = 1;
			}
			params.put(0, status);
		}
	}
	
	// Because we create our pipeline on draw command calls, this effectively does nothing.
	public void glLinkProgram(int program) {
		
	}
	
	
	
	
	
	
	
	//     Placeholders for testing
	public boolean shouldClose() {
		return system.shouldClose();
	}
	
	public void close() {
		system.cleanup();
	}
	
	public void beginRecord() {
		system.beginRecord();
	}

	public void endRecord() {
		system.endRecord();
	}
	
	public void selectNode(int node) {
		system.selectNode(node);
	}
	
	public int getNodesCount() {
		return system.getNodesCount();
	}
	
	public void setDangerMode(boolean mode) {
		dangerMode = mode;
	}
}
