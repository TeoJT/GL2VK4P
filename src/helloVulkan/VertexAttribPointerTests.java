package helloVulkan;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

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
	
	String code2 = 
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
	void test01() {
		assertEquals(0, getDescription1().get(0).binding());
		assertEquals(0, getDescription1().get(1).binding());
	}


	@Test
	void test02() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription1();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
	}

	@Test
	void test03() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription1();
		assertEquals(0, descriptions.get(0).offset());
		assertEquals(2*4, descriptions.get(1).offset());
	}
	
	@Test
	void test04() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription2();
		assertEquals(0, descriptions.get(0).binding());
		assertEquals(1, descriptions.get(1).binding());
	}
	
	// Just a neat lil bunch of tests to have

	@Test
	void test05() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription3();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
	}
	

	@Test
	void test06() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription3();
		assertEquals(0, descriptions.get(0).offset());
		assertEquals(2*4, descriptions.get(1).offset());
	}

	@Test
	void test07() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription3();
		assertEquals(VK_FORMAT_R32G32_SFLOAT, descriptions.get(0).format());
		assertEquals(VK_FORMAT_R32G32B32_SFLOAT, descriptions.get(1).format());
	}
	
	// Binding description tests
	@Test
	void test08() {
		VkVertexInputBindingDescription.Buffer bindings = getBindings1();
		
		assertEquals(0, bindings.get(0).binding());
		assertEquals(1, bindings.capacity());
	}

	@Test
	void test09() {
		VkVertexInputBindingDescription.Buffer bindings = getBindings2();
		
		assertEquals(0, bindings.get(0).binding());
		assertEquals(1, bindings.get(1).binding());
		assertEquals(2, bindings.capacity());
	}
	
	// TODO: test using actual openGL functions.
	// TODO: test using crazyAttribsCode.
}
