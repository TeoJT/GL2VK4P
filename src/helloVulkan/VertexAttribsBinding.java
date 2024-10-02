package helloVulkan;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_VERTEX_INPUT_RATE_VERTEX;

import java.util.HashMap;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import static org.lwjgl.vulkan.VK10.*;


// ABOUT BINDINGS:
// Bindings are just like glBindBuffer before calling vertexAttribPointer.
// Bindings are indexes to those buffers.
// For example 
// Binding 0: vertexBuffer[0]
// Binding 1: vertexBuffer[1]
// Remember that each of those bindings are tied to the attributes
// For example, attribute (location=0) can be attached to binding 0
// Then attribute (location=1) can be attached to binding 1
// And
// When you want interleaved, it's like this
// Attribute (location=0) attached to binding 0
// Attribute (location=1) attached to binding 0
// Both attached to binding 0.
// Of course, if you want separate buffers per attribute, you'll need to
// assign them different buffers each.

// TODO: bindingSize should only be as big as the number of attribs we assign
// to the binding.
// TODO: Allow us to construct our attribs-binding through vertexAttribPointer.

// In our main program, here's what's happening surface level v underneath the hood:

// EXAMPLE 1
// Surface: 
// glbindBuffer(PGL.ARRAY_BUFFER, vertexVboId);
// glvertexAttribPointer(vertLoc, VERT_CMP_COUNT, PGL.FLOAT, false, vertexStride, vertexOffset);
// glbindBuffer(PGL.ARRAY_BUFFER, colorVboId);
// glvertexAttribPointer(colorLoc, CLR_CMP_COUNT, PGL.FLOAT, false, colorStride, colorOffset);

// Under the hood:
// - New buffer bound, create new VertexAttribsBinding object
// - Set VertexAttribsBinding object's attribs.
// - New buffer bound, create new VertexAttribsBinding object
// - Set VertexAttribsBinding object's attribs.
// - When pipeline gets created, we go through each VertexAttribsBinding object
//   and join the vertexAttributeDescriptions and combine the vertexbindings of each object

// EXAMPLE 2
// Surface:
// glbufferData(PGL.ARRAY_BUFFER, Float.BYTES * attribs.length, attribBuffer, PGL.DYNAMIC_DRAW);
// glvertexAttribPointer(vertLoc, VERT_CMP_COUNT, PGL.FLOAT, false, stride, vertexOffset);
// glvertexAttribPointer(colorLoc, CLR_CMP_COUNT, PGL.FLOAT, false, stride, colorOffset);


// Under the hood:
// - New buffer bound, create new VertexAttribsBinding object
// - Set VertexAttribsBinding object's attribs.
// - Set VertexAttribsBinding object's attribs.
// - When pipeline gets created, we go through each VertexAttribsBinding object
//   and join the vertexAttributeDescriptions and combine the vertexbindings of each object



public class VertexAttribsBinding {
	private int myBinding = 0;
	private int bindingStride = 0;
	private ShaderAttribInfo attribInfo;
	
	private int stateHash = 0;
	
	// use classic arrays instead of ArrayLists because we care about garbage
	// collection overhead.
	private int[] usedLocations = new int[512];
	private int usedLocationsIndex = 0;
	
	public VertexAttribsBinding(int binding, ShaderAttribInfo attribInfo) {
		this.myBinding = binding;
		this.attribInfo = attribInfo;
		this.bindingStride = attribInfo.bindingSize;
	}
	
	
	

	// As ugly as it is to modify values straight from attribInfo, realistically these
	// values will not be changed to anything different if the program uses vertexAttribPointer 
	// correctly.
	// NOTE: Passing the return value from glGetAttribLocation will NOT work because how it works
	// in gl:
	// Shader 0:
	// 1: attrib 0
	// 2: attrib 1
	// Shader 1:
	// 3: attrib 0
	// 4: attrib 1
	// 5: attrib 2
	// Instead you will need to pass the attrib number belonging to the shader, i.e. attrib 1,2,3
	// OpenGL is truly a tangled mess.
	public void vertexAttribPointer(int location, int size, int offset, int stride) {
		attribInfo.locationToAttrib[location].size = size;
		attribInfo.locationToAttrib[location].offset = offset;
		bindingStride = stride;
		
		// We're using those attribs
		usedLocations[usedLocationsIndex++] = location;
		
		// What's set by this function determines the state of the pipeline (if
		// it changes at any point, we need to recreate the pipeline with new
		// vertex bindings)
		stateHash += (location+1L)*usedLocationsIndex*100L + size*2 + offset*3;
	}
	
	public int getHashState() {
		return stateHash;
	}
	
	// Because vertexAttribPointer is called every frame, we need to reset, just simply means
	// that we re-record the statehash
	public void reset() {
		usedLocationsIndex = 0;
		stateHash = 0;
	}
	

	
	// The actual vulkan stuff
	public VkVertexInputBindingDescription.Buffer getBindingDescription(MemoryStack stack) {
		VkVertexInputBindingDescription.Buffer bindingDescription =
		VkVertexInputBindingDescription.calloc(1, stack);
		
		bindingDescription.binding(myBinding);
		bindingDescription.stride(bindingStride);
		bindingDescription.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
		return bindingDescription;
	}
	
	public VkVertexInputAttributeDescription.Buffer getAttributeDescriptions(MemoryStack stack) {
		VkVertexInputAttributeDescription.Buffer attributeDescriptions =
		VkVertexInputAttributeDescription.calloc(attribInfo.nameToLocation.size());
		
		int i = 0;
		for (Integer loc : attribInfo.nameToLocation.values()) {
			VkVertexInputAttributeDescription description = attributeDescriptions.get(i++);
			description.binding(myBinding);
			description.location(loc);
			description.format(attribInfo.locationToAttrib[loc].format);
			description.offset(attribInfo.locationToAttrib[loc].offset);
		}
		
		return attributeDescriptions.rewind();
	}
	
}
