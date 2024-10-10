package gl2vk4p;

import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;

public class GLUniform {
	
	public static final int VERTEX = 1;
	public static final int FRAGMENT = 2;
	
	public String name;
	public int size = 0;
	public int offset = 0;
	// Vertex or fragment
	public int vertexFragment = 0;
	
	public GLUniform(String name, int size, int offset) {
		this.name = name;
		this.size = size;
		this.offset = offset;
	}

	// Pain
	public GLUniform(String name, int size) {
		this.name = name;
		this.size = size;
	}
	
	public int getVkType() {
        if (vertexFragment == VERTEX) {
		  return VK_SHADER_STAGE_VERTEX_BIT;
	  	}
	  	else if (vertexFragment == FRAGMENT) {
	  		return VK_SHADER_STAGE_FRAGMENT_BIT;
	  	}
	  	else {
	  		return VK_SHADER_STAGE_VERTEX_BIT;
	  	}
	}
}
