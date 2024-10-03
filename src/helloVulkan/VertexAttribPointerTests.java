package helloVulkan;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

import java.nio.IntBuffer;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import helloVulkan.VertexAttribsBinding;

class VertexAttribPointerTests {

	String code1 = 
			"""
#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inColor;

layout(location = 0) out vec3 fragColor;

void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;
}
			""";
	
	String weirdCode = 
			"""
#version 450



void main() {
layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inColor;
}
			""";
	

String crazyAttribsCode = 
			"""
#version 450

layout(location = 0) in vec2 group1attrib1;
layout(location = 1) in vec3 group1attrib2;

layout(location = 2) in vec3 group2attrib1;
layout(location = 3) in float group2attrib2;
layout(location = 4) in vec4 group2attrib3;
layout(location = 5) in float group2attrib4;

layout(location = 6) in float group3attrib1;

layout(location = 0) out vec3 fragColor;

void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;
}
		""";


	
	private VkVertexInputAttributeDescription.Buffer getDescription1() {

		GL2VKPipeline pipeline = new GL2VKPipeline();
		pipeline.compileVertex(code1);
		pipeline.bind(1);
		pipeline.vertexAttribPointer(0, 2*4, 0*4, 5*4);
		pipeline.vertexAttribPointer(1, 3*4, 2*4, 5*4);
		
		return pipeline.getAttributeDescriptions();
	}
	

	private VkVertexInputAttributeDescription.Buffer getDescription2() {
		GL2VKPipeline pipeline = new GL2VKPipeline();
		pipeline.compileVertex(code1);
		pipeline.bind(1);
		pipeline.vertexAttribPointer(0, 2*4, 0*4, 5*4);
		pipeline.bind(2);
		pipeline.vertexAttribPointer(1, 3*4, 2*4, 5*4);
		
		return pipeline.getAttributeDescriptions();
	}

	private VkVertexInputAttributeDescription.Buffer getDescription3() {
		GL2VKPipeline pipeline = new GL2VKPipeline();
		pipeline.compileVertex(code1);
		pipeline.bind(1);
		pipeline.vertexAttribPointer(0);
		pipeline.vertexAttribPointer(1);
		
		return pipeline.getAttributeDescriptions();
	}

	private VkVertexInputBindingDescription.Buffer getBindings1() {

		GL2VKPipeline pipeline = new GL2VKPipeline();
		pipeline.compileVertex(code1);
		pipeline.bind(1);
		pipeline.vertexAttribPointer(0, 2*4, 0*4, 5*4);
		pipeline.vertexAttribPointer(1, 3*4, 2*4, 5*4);
		
		return pipeline.getBindingDescriptions();
	}

	private VkVertexInputBindingDescription.Buffer getBindings2() {

		GL2VKPipeline pipeline = new GL2VKPipeline();
		pipeline.compileVertex(code1);
		pipeline.bind(1);
		pipeline.vertexAttribPointer(0, 2*4, 0*4, 5*4);
		pipeline.bind(2);
		pipeline.vertexAttribPointer(1, 3*4, 2*4, 5*4);
		
		return pipeline.getBindingDescriptions();
	}
	
	
	
	
	
	
	
	@Test
	public void interleaved_binding() {
		assertEquals(0, getDescription1().get(0).binding());
		assertEquals(0, getDescription1().get(1).binding());
	}


	@Test
	public void basic_location() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription1();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
	}

	@Test
	public void basic_offset() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription1();
		assertEquals(0, descriptions.get(0).offset());
		assertEquals(2*4, descriptions.get(1).offset());
	}
	
	@Test
	public void separate_binding() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription2();
		assertEquals(0, descriptions.get(0).binding());
		assertEquals(1, descriptions.get(1).binding());
	}
	
	// Just a neat lil bunch of tests to have

	@Test
	public void simple_location() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription3();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
	}
	

	@Test
	public void simple_offset() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription3();
		assertEquals(0, descriptions.get(0).offset());
		assertEquals(2*4, descriptions.get(1).offset());
	}

	@Test
	public void simple_format() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription3();
		assertEquals(VK_FORMAT_R32G32_SFLOAT, descriptions.get(0).format());
		assertEquals(VK_FORMAT_R32G32B32_SFLOAT, descriptions.get(1).format());
	}
	
	// Binding description tests
	@Test
	public void real_binding_test_1() {
		VkVertexInputBindingDescription.Buffer bindings = getBindings1();
		
		assertEquals(0, bindings.get(0).binding());
		assertEquals(1, bindings.capacity());
	}

	@Test
	public void real_binding_test_2() {
		VkVertexInputBindingDescription.Buffer bindings = getBindings2();
		
		assertEquals(0, bindings.get(0).binding());
		assertEquals(1, bindings.get(1).binding());
		assertEquals(2, bindings.capacity());
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

String vertSource1 = """
#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inColor;

layout(location = 0) out vec3 fragColor;

void main() {
    gl_Position = vec4(inPosition, 0.0, 1.0);
    fragColor = inColor;
}
		""";

String fragSource1 = """
#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(location = 0) in vec3 fragColor;

layout(location = 0) out vec4 outColor;

void main() {
    outColor = vec4(fragColor, 1.0);
}
		""";

String vertSource2 = """
#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inNormals;
layout(location = 2) in vec3 inColor;
layout(location = 3) in float inBrightness;

layout(location = 0) out vec3 fragColor;

void main() {
// An awful program but we're just testing.
    gl_Position = vec4(inPosition*inNormals.xy, 0.0, 1.0);
    fragColor = inColor*vec3(inBrightness);
}
		""";

String fragSource2 = """
#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(location = 0) in vec3 fragColor;

layout(location = 0) out vec4 outColor;

void main() {
    outColor = vec4(fragColor, 1.0);
}
		""";

	
	// And now
	// For the openGL tests
	int glProgram1 = -1;
	int failShader = -1;
	int vertShader1 = -1;
	int fragShader1 = -1;
	
	private GL2VK glProgram1() {
		return glProgram1(false);
	}
	
	private GL2VK glProgram1(boolean extraBinding) {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		glProgram1 = gl.glCreateProgram();
		vertShader1 = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		fragShader1 = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		
		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	int buffer1 = out.get(0);
    	int buffer2 = out.get(1);
		
		gl.glShaderSource(vertShader1, vertSource1);
		gl.glShaderSource(fragShader1, fragSource1);
		
		gl.glCompileShader(vertShader1);
		gl.glCompileShader(fragShader1);
		
		gl.glAttachShader(glProgram1, vertShader1);
		gl.glAttachShader(glProgram1, fragShader1);

		int position = gl.glGetAttribLocation(glProgram1, "inPosition");
		int color = gl.glGetAttribLocation(glProgram1, "inColor");
		
		gl.glBindBuffer(0, buffer1);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 5*4, 0);
		if (extraBinding) gl.glBindBuffer(0, buffer2);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);
		
		return gl;
	}
	
	// Intentional shader compilation fail
	private GL2VK glProgram2() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		glProgram1 = gl.glCreateProgram();
		failShader = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		
		gl.glShaderSource(failShader, weirdCode);
		
		gl.glCompileShader(failShader);
		
		return gl;
	}
	
	
	private VkVertexInputAttributeDescription.Buffer glProgram1Description() {
		// Don't care that I'm using 1 here.
		return glProgram1().getPipeline(glProgram1).getAttributeDescriptions();
	}
	
	private VkVertexInputBindingDescription.Buffer glProgram1Bindings1() {
		return glProgram1().getPipeline(glProgram1).getBindingDescriptions();
	}
	
	private VkVertexInputBindingDescription.Buffer glProgram1Bindings2() {
		return glProgram1(true).getPipeline(glProgram1).getBindingDescriptions();
	}
	

	@Test
	public void glprogram_location_description() {
		VkVertexInputAttributeDescription.Buffer descriptions = glProgram1Description();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
	}

	@Test
	public void glprogram_location_exists() {
		GL2VK gl = glProgram1();
		assertEquals(1, gl.glGetAttribLocation(glProgram1, "inColor"));
		assertEquals(2, gl.glGetAttribLocation(glProgram1, "inPosition"));
	}

	@Test
	public void glprogram_offset_description() {
		VkVertexInputAttributeDescription.Buffer descriptions = glProgram1Description();
		assertEquals(0, descriptions.get(0).offset());
		assertEquals(8, descriptions.get(1).offset());
	}

	// Binding descriptions
	@Test
	public void glprogram_binding_description_interleaved() {
		VkVertexInputBindingDescription.Buffer descriptions = glProgram1Bindings1();
		assertEquals(0, descriptions.get(0).binding());
	}

	// Binding descriptions
	@Test
	public void glprogram_binding_description_separate() {
		VkVertexInputBindingDescription.Buffer descriptions = glProgram1Bindings2();
		assertEquals(0, descriptions.get(0).binding());
		assertEquals(1, descriptions.get(1).binding());
	}

	// Compile failure expected
	@Test
	public void compile_fail() {
		GL2VK gl = glProgram2();
		IntBuffer out = IntBuffer.allocate(1);
		gl.glGetShaderiv(failShader, GL2VK.GL_COMPILE_STATUS, out);
		assertEquals(GL2VK.GL_FALSE, out.get(0));
	}

	// Compile pass expected
	@Test
	public void compile_pass() {
		GL2VK gl = glProgram1();
		
		IntBuffer out = IntBuffer.allocate(1);
		gl.glGetShaderiv(vertShader1, GL2VK.GL_COMPILE_STATUS, out);
		assertEquals(GL2VK.GL_TRUE, out.get(0));

		gl.glGetShaderiv(fragShader1, GL2VK.GL_COMPILE_STATUS, out);
		assertEquals(GL2VK.GL_TRUE, out.get(0));
	}

	// Compile pass expected
	@Test
	public void compile_fail_info() {
		GL2VK gl = glProgram2();
		
		String err = gl.glGetShaderInfoLog(failShader);
		System.out.println("Ready for a shader compile error?");
		System.out.println(err);
		assertTrue(err.length() > 0);
	}
	
	@Test
	public void check_shadercode2() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		glProgram1 = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		
    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource2);
		gl.glShaderSource(fragShaderX, fragSource2);
		
		gl.glCompileShader(vertShaderX);
		gl.glCompileShader(fragShaderX);

		IntBuffer out1 = IntBuffer.allocate(1);
		gl.glGetShaderiv(vertShaderX, GL2VK.GL_COMPILE_STATUS, out1);
		IntBuffer out2 = IntBuffer.allocate(1);
		gl.glGetShaderiv(fragShaderX, GL2VK.GL_COMPILE_STATUS, out2);
		
		assertEquals(1, out1.get(0));
		assertEquals(1, out2.get(0));
	}
	

	@Test
	public void glprogram_multiple_shaders_step() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		
		// Buffers
		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	int buffer1 = out.get(0);
    	int buffer2 = out.get(1);
		
    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(fragShaderX, fragSource1);
		
		// Compile shaders X
		gl.glCompileShader(vertShaderX);
		gl.glCompileShader(fragShaderX);
		
		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programX, fragShaderX);
		
		// Vertex attribs X
		int position = gl.glGetAttribLocation(programX, "inPosition");
		int color = gl.glGetAttribLocation(programX, "inColor");
		gl.glBindBuffer(0, buffer1);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);

		// Test X
		VkVertexInputAttributeDescription.Buffer descriptions = gl.getPipeline(programX).getAttributeDescriptions();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());

		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

    	// Pass source code
		gl.glShaderSource(vertShaderY, vertSource2);
		gl.glShaderSource(fragShaderY, fragSource2);
		
		// Compile shaders X
		gl.glCompileShader(vertShaderY);
		gl.glCompileShader(fragShaderY);
		
		// attach to program X
		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programY, fragShaderY);

		//layout(location = 0) in vec2 inPosition;
		//layout(location = 1) in vec3 inNormals;
		//layout(location = 2) in vec3 inColor;
		//layout(location = 3) in float inBrightness;

		// Vertex attribs X
		int positionY = gl.glGetAttribLocation(programY, "inPosition");
		int normals = gl.glGetAttribLocation(programY, "inNormals");
		int colorY = gl.glGetAttribLocation(programY, "inColor");
		int brightness = gl.glGetAttribLocation(programY, "inBrightness");
		
		gl.glBindBuffer(0, buffer2);
		gl.glVertexAttribPointer(positionY, 2*4, 0, false, 9*4, 0);
		gl.glVertexAttribPointer(normals, 3*4, 0, false, 9*4, 2*4);
		gl.glVertexAttribPointer(colorY, 3*4, 0, false, 9*4, 5*4);
		gl.glVertexAttribPointer(brightness, 1*4, 0, false, 9*4, 8*4);

		descriptions = gl.getPipeline(programY).getAttributeDescriptions();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
		assertEquals(2, descriptions.get(2).location());
		assertEquals(3, descriptions.get(3).location());
	}
	
	

	// Let's do some variations to make the ordering weird and try to catch out bugs.
	// I just wanna test it differently in different ways now lmao
	@Test
	public void glprogram_multiple_shaders_after() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		
		// Buffers
		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	int buffer1 = out.get(0);
    	int buffer2 = out.get(1);
		
    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(fragShaderX, fragSource1);
		
		// Compile shaders X
		gl.glCompileShader(vertShaderX);
		gl.glCompileShader(fragShaderX);
		
		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programX, fragShaderX);
		
		// Vertex attribs X
		int position = gl.glGetAttribLocation(programX, "inPosition");
		int color = gl.glGetAttribLocation(programX, "inColor");
		gl.glBindBuffer(0, buffer1);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);

		// Test X
		VkVertexInputAttributeDescription.Buffer descriptions = gl.getPipeline(programX).getAttributeDescriptions();
		

		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

    	// Pass source code
		gl.glShaderSource(vertShaderY, vertSource2);
		gl.glShaderSource(fragShaderY, fragSource2);
		
		// Compile shaders X
		gl.glCompileShader(vertShaderY);
		gl.glCompileShader(fragShaderY);
		
		// attach to program X
		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programY, fragShaderY);

		//layout(location = 0) in vec2 inPosition;
		//layout(location = 1) in vec3 inNormals;
		//layout(location = 2) in vec3 inColor;
		//layout(location = 3) in float inBrightness;

		// Vertex attribs X
		int positionY = gl.glGetAttribLocation(programY, "inPosition");
		int normals = gl.glGetAttribLocation(programY, "inNormals");
		int colorY = gl.glGetAttribLocation(programY, "inColor");
		int brightness = gl.glGetAttribLocation(programY, "inBrightness");
		
		gl.glBindBuffer(0, buffer2);
		gl.glVertexAttribPointer(positionY, 2*4, 0, false, 9*4, 0);
		gl.glVertexAttribPointer(normals, 3*4, 0, false, 9*4, 2*4);
		gl.glVertexAttribPointer(colorY, 3*4, 0, false, 9*4, 5*4);
		gl.glVertexAttribPointer(brightness, 1*4, 0, false, 9*4, 8*4);

		descriptions = gl.getPipeline(programY).getAttributeDescriptions();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
		assertEquals(2, descriptions.get(2).location());
		assertEquals(3, descriptions.get(3).location());
	}
	
	
	
	@Test
	public void glprogram_multiple_shaders_merged() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		
		// Buffers
		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	int buffer1 = out.get(0);
    	int buffer2 = out.get(1);
		
    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(vertShaderY, vertSource2);
		gl.glShaderSource(fragShaderX, fragSource1);
		gl.glShaderSource(fragShaderY, fragSource2);
		
		// Compile shaders X
		gl.glCompileShader(vertShaderX);
		gl.glCompileShader(vertShaderY);
		gl.glCompileShader(fragShaderX);
		gl.glCompileShader(fragShaderY);
		
		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programX, fragShaderX);
		gl.glAttachShader(programY, fragShaderY);
		
		// Vertex attribs X
		int position = gl.glGetAttribLocation(programX, "inPosition");
		int color = gl.glGetAttribLocation(programX, "inColor");
		int positionY = gl.glGetAttribLocation(programY, "inPosition");
		int normals = gl.glGetAttribLocation(programY, "inNormals");
		int colorY = gl.glGetAttribLocation(programY, "inColor");
		int brightness = gl.glGetAttribLocation(programY, "inBrightness");
		gl.glBindBuffer(0, buffer1);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);
		
		gl.glBindBuffer(0, buffer2);
		gl.glVertexAttribPointer(positionY, 2*4, 0, false, 9*4, 0);
		gl.glVertexAttribPointer(normals, 3*4, 0, false, 9*4, 2*4);
		gl.glVertexAttribPointer(colorY, 3*4, 0, false, 9*4, 5*4);
		gl.glVertexAttribPointer(brightness, 1*4, 0, false, 9*4, 8*4);

		// Test X
		VkVertexInputAttributeDescription.Buffer descriptions = gl.getPipeline(programX).getAttributeDescriptions();

		descriptions = gl.getPipeline(programX).getAttributeDescriptions();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
		
		descriptions = gl.getPipeline(programY).getAttributeDescriptions();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
		assertEquals(2, descriptions.get(2).location());
		assertEquals(3, descriptions.get(3).location());
	}
	
	

	@Test
	public void glprogram_multiple_shaders_merged_index() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		
		// Buffers
		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	int buffer1 = out.get(0);
    	int buffer2 = out.get(1);
		
    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(vertShaderY, vertSource2);
		gl.glShaderSource(fragShaderX, fragSource1);
		gl.glShaderSource(fragShaderY, fragSource2);
		
		// Compile shaders X
		gl.glCompileShader(vertShaderX);
		gl.glCompileShader(vertShaderY);
		gl.glCompileShader(fragShaderX);
		gl.glCompileShader(fragShaderY);
		
		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programX, fragShaderX);
		gl.glAttachShader(programY, fragShaderY);
		
		// Vertex attribs X
		int position = gl.glGetAttribLocation(programX, "inPosition");
		int color = gl.glGetAttribLocation(programX, "inColor");
		int positionY = gl.glGetAttribLocation(programY, "inPosition");
		int normals = gl.glGetAttribLocation(programY, "inNormals");
		int colorY = gl.glGetAttribLocation(programY, "inColor");
		int brightness = gl.glGetAttribLocation(programY, "inBrightness");

		assertEquals(1, color);
		assertEquals(2, position);
		assertEquals(3, brightness);
		assertEquals(4, colorY);
		assertEquals(5, positionY);
		assertEquals(6, normals);
	}
	
	
	
	public void glprogram_multiple_shaders_step_index() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		
		// Buffers
		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	int buffer1 = out.get(0);
    	int buffer2 = out.get(1);
		
    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(fragShaderX, fragSource1);
		
		// Compile shaders X
		gl.glCompileShader(vertShaderX);
		gl.glCompileShader(fragShaderX);
		
		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programX, fragShaderX);
		
		// Vertex attribs X
		int position = gl.glGetAttribLocation(programX, "inPosition");
		int color = gl.glGetAttribLocation(programX, "inColor");
		gl.glBindBuffer(0, buffer1);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);

		// Test X
		VkVertexInputAttributeDescription.Buffer descriptions = gl.getPipeline(programX).getAttributeDescriptions();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());

		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

    	// Pass source code
		gl.glShaderSource(vertShaderY, vertSource2);
		gl.glShaderSource(fragShaderY, fragSource2);
		
		// Compile shaders X
		gl.glCompileShader(vertShaderY);
		gl.glCompileShader(fragShaderY);
		
		// attach to program X
		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programY, fragShaderY);

		//layout(location = 0) in vec2 inPosition;
		//layout(location = 1) in vec3 inNormals;
		//layout(location = 2) in vec3 inColor;
		//layout(location = 3) in float inBrightness;

		// Vertex attribs X
		int positionY = gl.glGetAttribLocation(programY, "inPosition");
		int normals = gl.glGetAttribLocation(programY, "inNormals");
		int colorY = gl.glGetAttribLocation(programY, "inColor");
		int brightness = gl.glGetAttribLocation(programY, "inBrightness");

		assertEquals(1, color);
		assertEquals(2, position);
		assertEquals(3, brightness);
		assertEquals(4, colorY);
		assertEquals(5, positionY);
		assertEquals(6, normals);
	}

	
	
	@Test
	public void glprogram_multiple_shaders_merged_offset() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		
		// Buffers
		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	int buffer1 = out.get(0);
    	int buffer2 = out.get(1);
		
    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(vertShaderY, vertSource2);
		gl.glShaderSource(fragShaderX, fragSource1);
		gl.glShaderSource(fragShaderY, fragSource2);
		
		// Compile shaders X
		gl.glCompileShader(vertShaderX);
		gl.glCompileShader(vertShaderY);
		gl.glCompileShader(fragShaderX);
		gl.glCompileShader(fragShaderY);
		
		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programX, fragShaderX);

		// Vertex attribs X
		int position = gl.glGetAttribLocation(programX, "inPosition");
		int color = gl.glGetAttribLocation(programX, "inColor");
		gl.glBindBuffer(0, buffer1);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);
		
		
		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programY, fragShaderY);
		
		int positionY = gl.glGetAttribLocation(programY, "inPosition");
		int normals = gl.glGetAttribLocation(programY, "inNormals");
		int colorY = gl.glGetAttribLocation(programY, "inColor");
		int brightness = gl.glGetAttribLocation(programY, "inBrightness");
		
//		gl.glBindBuffer(0, buffer2);
		gl.glVertexAttribPointer(positionY, 2*4, 0, false, 9*4, 5*4);
		gl.glVertexAttribPointer(normals, 3*4, 0, false, 9*4, 7*4);
		gl.glVertexAttribPointer(colorY, 3*4, 0, false, 9*4, 10*4);
		gl.glVertexAttribPointer(brightness, 1*4, 0, false, 9*4, 11*4);

		// Test X
		VkVertexInputAttributeDescription.Buffer descriptions = gl.getPipeline(programX).getAttributeDescriptions();

		descriptions = gl.getPipeline(programX).getAttributeDescriptions();
		assertEquals(0, descriptions.get(0).offset());
		assertEquals(2*4, descriptions.get(1).offset());
		
		descriptions = gl.getPipeline(programY).getAttributeDescriptions();
		assertEquals(5*4, descriptions.get(0).offset());
		assertEquals(7*4, descriptions.get(1).offset());
		assertEquals(10*4, descriptions.get(2).offset());
		assertEquals(11*4, descriptions.get(3).offset());
	}
	
	
	
	@Test
	public void glprogram_multiple_shaders_merged_bindings() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		
		// Buffers
		IntBuffer out = IntBuffer.allocate(4);
    	gl.glGenBuffers(4, out);
    	int buffer1 = out.get(0);
    	int buffer2 = out.get(1);
    	int buffer3 = out.get(2);
    	int buffer4 = out.get(3);
		
    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(vertShaderY, vertSource2);
		gl.glShaderSource(fragShaderX, fragSource1);
		gl.glShaderSource(fragShaderY, fragSource2);
		
		// Compile shaders X
		gl.glCompileShader(vertShaderX);
		gl.glCompileShader(vertShaderY);
		gl.glCompileShader(fragShaderX);
		gl.glCompileShader(fragShaderY);
		
		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programX, fragShaderX);

		// Vertex attribs X
		int position = gl.glGetAttribLocation(programX, "inPosition");
		int color = gl.glGetAttribLocation(programX, "inColor");
		gl.glBindBuffer(0, buffer1);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);
		
		
		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programY, fragShaderY);
		
		int positionY = gl.glGetAttribLocation(programY, "inPosition");
		int normals = gl.glGetAttribLocation(programY, "inNormals");
		int colorY = gl.glGetAttribLocation(programY, "inColor");
		int brightness = gl.glGetAttribLocation(programY, "inBrightness");
		
		gl.glBindBuffer(0, buffer2);
		gl.glVertexAttribPointer(positionY, 2*4, 0, false, 9*4, 5*4);
		gl.glVertexAttribPointer(normals, 3*4, 0, false, 9*4, 7*4);
		gl.glBindBuffer(0, buffer3);
		gl.glVertexAttribPointer(colorY, 3*4, 0, false, 9*4, 10*4);
		gl.glBindBuffer(0, buffer4);
		gl.glVertexAttribPointer(brightness, 1*4, 0, false, 9*4, 11*4);

		// Test X
		VkVertexInputBindingDescription.Buffer descriptions = gl.getPipeline(programX).getBindingDescriptions();

		descriptions = gl.getPipeline(programX).getBindingDescriptions();
		assertEquals(0, descriptions.get(0).binding());
		
		descriptions = gl.getPipeline(programY).getBindingDescriptions();
		assertEquals(0, descriptions.get(0).binding());
		assertEquals(1, descriptions.get(1).binding());
		assertEquals(2, descriptions.get(2).binding());
	}
	
	// TODO: test using crazyAttribsCode.
}
