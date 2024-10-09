package gl2vk4p;

public class GLUniform {
	
	public static final int VERTEX = 1;
	public static final int FRAGMENT = 1;
	
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
}
