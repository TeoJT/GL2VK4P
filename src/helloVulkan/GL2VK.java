package helloVulkan;

import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;

import java.nio.ByteBuffer;

public class GL2VK {
	
	public static final int GL_VERTEX_BUFFER = 1;
	public static final int GL_INDEX_BUFFER = 2;
	
	private class GLPrograms {
	}
	
	private VulkanSystem system;
	
	private GraphicsBuffer[] buffers = new GraphicsBuffer[1024];
	private GLPrograms[] programs = new GLPrograms[1024];
	
	private int bufferIndex = 1;
	private int boundBuffer = 0;
	
	
	
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
	
	public void glGetAttribLocation(int program, String attribName) {
		
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
		
		buffers[boundBuffer].bufferData(data, size);
	}
	
	public void glDrawArrays(int mode, int first, int count) {
		// TODO: *5 is just a placeholder for a fixed shader.
		system.drawArrays(buffers[boundBuffer].bufferID, count*5, 0);
	}
	
	
	//     Placeholders for testing
	public boolean shouldClose() {
		return system.shouldClose();
	}
	
	public void beginRecord() {
		system.beginRecord();
	}

	public void endRecord() {
		system.endRecord();
	}
}
