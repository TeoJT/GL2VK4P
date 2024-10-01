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

// TODO: isolate loadShaderAttribs from class since we don't want it running
// for each binding (waste of performance)
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
	
	// Each attrib has a type, size, and location
	public class AttribInfo {
		public int location = 0;
		public int format = 0;
		public int size = 0;
		public int offset = 0;
		
		public AttribInfo(int l, int f, int s, int off) {
			location = l;
			format = f;
			size = s;
			offset = off;
		}
	}
	
	// Stupid redundant info for gl backward compat
	private int bindingSize = 0;
	public AttribInfo[] locationToAttrib = new AttribInfo[1024];
	public HashMap<String, Integer> nameToLocation = new HashMap<String, Integer>();
	
	public VertexAttribsBinding(int binding, String vertexShader) {
		myBinding = binding;
//		bindings++;
		loadShaderAttribs(vertexShader);
	}
	
	private void loadShaderAttribs(String vertexShader) {
		String[] lines = vertexShader.split("\n");
		bindingSize = 0;
		int currOffset = 0;
		
		int bracketDepth = 0;
		for (String s : lines) {
			bracketDepth += countChars(s, "{");
			bracketDepth -= countChars(s, "}");
			
			// Make sure we're not in any methods (like main)
			// and search for attribs (keyword "in")
			if (
					bracketDepth == 0 &&
					s.contains(" in ")
			) {
				// Get location
				String[] elements = s.split(" ");
				String line = s.replaceAll(" ", "");
				int index = line.indexOf("layout(location=");
				if (index == -1) continue;
				index += "layout(location=".length();
				int endIndex = line.indexOf(")", index);
				
				int location = Integer.parseInt(line.substring(index, endIndex));
//				System.out.println("Location: "+location);
				
				// Get type
				String type = "";
				String attribName = "";
				try {
					for (int i = 0; i < elements.length; i++) {
						if (elements[i].equals("in")) {
							type = elements[i+1];
							attribName = elements[i+2];
							// Remove ; at the end
							if (attribName.charAt(attribName.length()-1) == ';') attribName = attribName.substring(0, attribName.length()-1);
//							System.out.println("type: "+type+"  attribName: "+attribName);
							break;
						}
					}
				}
				catch (IndexOutOfBoundsException e) {
//					System.err.println("Woops");
				}
				
				int size = typeToSize(type);
				int format = typeToFormat(type);
				
				locationToAttrib[location] =  
					new AttribInfo(location, format, size, currOffset);
				
				bindingSize += size;
				currOffset += size;
				
				nameToLocation.put(attribName, location);
			}
		}
	}
	
	private int countChars(String line, String c) {
		return line.length() - line.replace(c, "").length();
	}
	
	private int typeToFormat(String val) {
		if (val.equals("float")) return VK_FORMAT_R32_SFLOAT;
		else if (val.equals("vec2")) return VK_FORMAT_R32G32_SFLOAT;
		else if (val.equals("vec3")) return VK_FORMAT_R32G32B32_SFLOAT;
		else if (val.equals("vec4")) return VK_FORMAT_R32G32B32A32_SFLOAT;
		else if (val.equals("int")) return VK_FORMAT_R32_SINT;
		else if (val.equals("ivec2")) return VK_FORMAT_R32G32_SINT;
		else if (val.equals("ivec3")) return VK_FORMAT_R32G32B32_SINT;
		else if (val.equals("ivec4")) return VK_FORMAT_R32G32B32A32_SINT;
		else if (val.equals("uint")) return VK_FORMAT_R32_UINT;
		else if (val.equals("uvec2")) return VK_FORMAT_R32G32_UINT;
		else if (val.equals("uvec3")) return VK_FORMAT_R32G32B32_UINT;
		else if (val.equals("uvec4")) return VK_FORMAT_R32G32B32A32_UINT;
		else if (val.equals("bool")) return VK_FORMAT_R8_UINT;
		else if (val.equals("bvec2")) return VK_FORMAT_R8G8_UINT;
		else if (val.equals("bvec3")) return VK_FORMAT_R8G8B8_UINT;
		else if (val.equals("bvec4")) return VK_FORMAT_R8G8B8A8_UINT;
		else if (val.equals("mat2")) return VK_FORMAT_R32G32_SFLOAT;
		else if (val.equals("mat3")) return VK_FORMAT_R32G32B32_SFLOAT;
		else if (val.equals("mat4")) return VK_FORMAT_R32G32B32A32_SFLOAT;
		else return -1;
	}
	
	private int typeToSize(String val) {
		if (val.equals("float")) return 1 * Float.BYTES;
		else if (val.equals("vec2")) return 2 * Float.BYTES;
		else if (val.equals("vec3")) return 3 * Float.BYTES;
		else if (val.equals("vec4")) return 4 * Float.BYTES;
		else if (val.equals("int")) return 1 * Integer.BYTES;
		else if (val.equals("ivec2")) return 2 * Integer.BYTES;
		else if (val.equals("ivec3")) return 3 * Integer.BYTES;
		else if (val.equals("ivec4")) return 4 * Integer.BYTES;
		else if (val.equals("uint")) return 1 * Integer.BYTES;
		else if (val.equals("uvec2")) return 2 * Integer.BYTES;
		else if (val.equals("uvec3")) return 3 * Integer.BYTES;
		else if (val.equals("uvec4")) return 4 * Integer.BYTES;
		else if (val.equals("bool")) return 1;
		else if (val.equals("bvec2")) return 2;
		else if (val.equals("bvec3")) return 3;
		else if (val.equals("bvec4")) return 4;
		else if (val.equals("mat2")) return 2 * 2 * Float.BYTES;
		else if (val.equals("mat3")) return 3 * 3 * Float.BYTES;
		else if (val.equals("mat4")) return 4 * 4 * Float.BYTES;
		else return -1;
	}
	
	
	// The actual vulkan stuff
	public VkVertexInputBindingDescription.Buffer getBindingDescription(MemoryStack stack) {
		VkVertexInputBindingDescription.Buffer bindingDescription =
		VkVertexInputBindingDescription.calloc(1, stack);
		
		bindingDescription.binding(myBinding);
		bindingDescription.stride(bindingSize);
		bindingDescription.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
		return bindingDescription;
	}
	
	// For use of opengl layers
//	public int getAttribLocation(String name) {
//		if (nameToLocation.containsKey(name)) return nameToLocation.get(name);
//		else {
//			System.err.println("getAttribLocation: no attrib "+name);
//			return -1;
//		}
//	}
	
	public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset) {
		locationToAttrib[index].size = size;
//		locationToAttrib[index].format = type;
		locationToAttrib[index].offset = offset;
	}
	
	public int getSize() {
		return bindingSize;
	}
	
	public VkVertexInputAttributeDescription.Buffer getAttributeDescriptions(MemoryStack stack) {
		VkVertexInputAttributeDescription.Buffer attributeDescriptions =
		VkVertexInputAttributeDescription.calloc(nameToLocation.size());
		
		int i = 0;
		for (Integer loc : nameToLocation.values()) {
			VkVertexInputAttributeDescription description = attributeDescriptions.get(i++);
			description.binding(myBinding);
			description.location(loc);
			description.format(locationToAttrib[loc].format);
			description.offset(locationToAttrib[loc].offset);
		}
		
		return attributeDescriptions.rewind();
	}
	
}
