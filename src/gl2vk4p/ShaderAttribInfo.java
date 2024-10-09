package gl2vk4p;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_UINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_UINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_UINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32_UINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8_UINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8_UINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8_UINT;

import java.util.ArrayList;
import java.util.HashMap;


public class ShaderAttribInfo {

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
	
	public ShaderAttribInfo(String source) {
		loadShaderAttribs(source);
	}
	
	private void newAttribInfo(int l, int f, int s, int off) {
		locationToAttrib[l] = new AttribInfo(l,f,s,off);
	}
	
	// Stupid redundant info for gl backward compat
	public int bindingSize = 0;
	public AttribInfo[] locationToAttrib = new AttribInfo[1024];
	public HashMap<String, Integer> nameToLocation = new HashMap<String, Integer>();
	

	private void loadShaderAttribs(String shader) {
		
		// Split into array of lines
		String[] lines = shader.split("\n");
		int currAttribOffset = 0;
		
		
		int bracketDepth = 0;
		for (String s : lines) {
			
			// Bracket depth so we only get attribs from outside bodies.
			// You'll never see attribs/uniforms in brackets for example.
			bracketDepth += countChars(s, "{");
			bracketDepth -= countChars(s, "}");
			
			
			// Make sure we're not in any methods (like main)
			// and search for attribs (keyword "in")
			if (
					bracketDepth == 0 &&
					s.contains(" in ")
			) {
				// Lil filtering
				// Elements makes it easier to get each individual token.
				String[] elements = s.split(" ");
				// No spaces allowed.
				String line = s.replaceAll(" ", "");

				// Get location
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
					// Search for the element "in".
					// It is equivalant to "attrib" in OpenGL.
					for (int i = 0; i < elements.length; i++) {
						if (elements[i].equals("in")) {
							// Once we find in, remember what follows:
							// e.g. in vec3 variableName;
							type = elements[i+1];
							attribName = elements[i+2];
							// Remove ; at the end
							// And get the attribname
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
				
				newAttribInfo(location, format, size, currAttribOffset);
				
				bindingSize += size;
				currAttribOffset += size;
				
				nameToLocation.put(attribName, location);
				
				// Shouldn't be anything else for us to scan at this point.
				continue;
			}
		}
	}
	
	
	
	public static ArrayList<GLUniform> parseUniforms(String shaderSource) {

		// Split into array of lines
		String[] lines = shaderSource.split("\n");
		int currUniformOffset = 0;
		
		ArrayList<GLUniform> uniforms = new ArrayList<GLUniform>();
		
		// Set to true while we're inside of a uniform struct.
		// While in this state, any variables (i.e. items that start with
		// vec3, float, mat4 etc) will be added as uniforms with their names.
		boolean uniformStruct = false;
		
		int bracketDepth = 0;
		for (String s : lines) {
			
			// Here we're going to do a comparison to see if we're exiting struct
			int beforeBracketDepth = bracketDepth;
			
			// Bracket depth so we only get attribs from outside bodies.
			// You'll never see attribs/uniforms in brackets for example.
			bracketDepth += countChars(s, "{");
			bracketDepth -= countChars(s, "}");
			
			// If there's a change in bracketDepth to 0 and we're in uniformStruct
			// state, it means we're at the end of the struct block.
			if (
					bracketDepth != beforeBracketDepth && 
					bracketDepth == 0 &&
					uniformStruct
				) {
				uniformStruct = false;
			}
			
			// If we're in the state of looking for uniforms
			if (uniformStruct) {
				// Lil filtering
				// Elements makes it easier to get each individual token..replaceAll("\t", "")
				String[] elements = s.replaceAll("\t", "").trim().split(" ");
				
				
				// Try/catch block because there's probably cases where it will crash
				// but it seriously doesn't matter if it breaks.
				try {
					// I wish there was a break statement for if conditions
					if (elements.length >= 2) {
						
						// First element should be type
						int size = typeToSize(elements[0]);
						// If it's anything but -1 it means it's a type and hence a valid stuct value.
						// Also lil safety check and make sure elements[1] exists
						if (size != -1) {
							
							// Next element should be name
							String uniformName = elements[1];
							
							// Remove ';' at the end
							if (uniformName.charAt(uniformName.length()-1) == ';') uniformName = uniformName.substring(0, uniformName.length()-1);
							
							uniforms.add(new GLUniform(uniformName, size, currUniformOffset));
							
							
							currUniformOffset += size;
						}
					}
					continue;
				}
				catch (RuntimeException e) {
					
				}
			}
			

			// Uniforms
			if (
					bracketDepth == 0 &&
					s.contains(" uniform ")
			) {
				// Lil filtering
				// Elements makes it easier to get each individual token.
				String[] elements = s.split(" ");
				// No spaces allowed.
				String line = s.replaceAll(" ", "");
				
				
				// Get the type of uniform (push constant or descriptor)
				int pushConstantIndex = line.indexOf("layout(push_constant)");
				
				// TODO: Descriptor layouts.
				// Impossible (always false) condition here.
				int descriptorIndex = line.indexOf("(([amongus]))");
				
				// PUSH CONSTANT PATH
				if (pushConstantIndex != -1) {
					
					// Prolly don't need that.
					String structName = "";
					
					// Scroll until we find (uniform)
					try {
						// Search for the element "in".
						for (int i = 0; i < elements.length; i++) {
							if (elements[i].equals("uniform")) {
								// The element after that should be the struct name.
								structName = elements[i+1];
								
								// We shall start to traverse the struct adding any variable we come
								// across as uniforms
								uniformStruct = true;
								break;
							}
						}
					}
					catch (IndexOutOfBoundsException e) {
//						System.err.println("Woops");
					}
					continue;
				}
				// Descriptor path
				else if (descriptorIndex != -1) {
					continue;
				}
				// Unknown uniform type.
				else {
					// No continue because the line might have something else for us to scan.
				}
			}
		}
		return uniforms;
	}
	
	private static int countChars(String line, String c) {
		return line.length() - line.replace(c, "").length();
	}
	
	private static int typeToFormat(String val) {
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
	
	private static int typeToSize(String val) {
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
	
	
}
