package helloVulkan;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;

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

	@Test
	void test01() {
		VertexAttribsBinding vap = new VertexAttribsBinding(0, code1);
		assert(vap.nameToLocation.containsKey("inPosition"));
		assert(vap.nameToLocation.containsKey("inColor"));
	}

//	@Test
//	void test02() {
//		VertexAttribsBinding vap = new VertexAttribsBinding(0, code1);
//		assertEquals(0, vap.getAttribLocation("inPosition"));
//		assertEquals(1, vap.getAttribLocation("inColor"));
//	}

	// Test weird code where attrib is inside main
	@Test
	void test03() {
		VertexAttribsBinding vap = new VertexAttribsBinding(0, code2);
		assertEquals(null, vap.nameToLocation.get("inPosition"));
		assertEquals(null, vap.nameToLocation.get("inColor"));
	}
	
	// Testing correct types
	@Test
	void test04() {
		VertexAttribsBinding vap = new VertexAttribsBinding(0, code1);
		assertEquals(VK_FORMAT_R32G32_SFLOAT, vap.locationToAttrib[0].format);
		assertEquals(VK_FORMAT_R32G32B32_SFLOAT, vap.locationToAttrib[1].format);
	}

	@Test
	void test05() {
		VertexAttribsBinding vap = new VertexAttribsBinding(0, code1);
		assertEquals(8, vap.locationToAttrib[0].size);
		assertEquals(12, vap.locationToAttrib[1].size);
	}

	@Test
	void test06() {
		VertexAttribsBinding vap = new VertexAttribsBinding(0, code1);

        try(MemoryStack stack = stackPush()) {
        	assertEquals(20, vap.getBindingDescription(stack).stride());
        }
	}

}
