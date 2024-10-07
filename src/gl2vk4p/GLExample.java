package gl2vk4p;

import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public class GLExample {
	GL2VK gl;

    // ======= CLASSES ======= //
    private static class Vertex {
        private static final int SIZEOF = (2 + 3) * Float.BYTES;

    	private Vector2fc pos;
    	private Vector3fc color;
    	
    	public Vertex(float x, float y, float r, float g, float b) {
    		this.pos = new Vector2f(x, y);
    		this.color = new Vector3f(r,g,b);
    	}

    	// NOTES FOR VERTEX BINDING:
    	// in OpenGL, bindBuffer specifies which buffer index to bind.
    	// This index can be used in vulkan's bindingDescription.binding(index)
    	// If we want an interleaved buffer: then
    	// bindBuffer(1);
    	// vertexAttribPointer("pos" ... )
    	// bindBuffer(1)
    	// vertexAttribPointer("color" ... )
    	//
    	// If we want a separate buffer:
    	// bindBuffer(1);
    	// vertexAttribPointer("pos" ... )
    	// bindBuffer(2)
    	// vertexAttribPointer("color" ... )
    	// 
    	// All we do is create a new bindingdescription whenever a new
    	// buffer is bound when vertexAttribPointer is called.
	}
	
	public void run() {
		try {
			gl = new GL2VK();
//			triangles();
//			trianglesSeparate();
//			throttleTest();
//			indices();
			indicesUniform();

		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	
	private void createIndicesSquare(ByteBuffer vertexBuffer, ByteBuffer colorBuffer, ByteBuffer indexBuffer) {

    	vertexBuffer.order(ByteOrder.LITTLE_ENDIAN);
    	colorBuffer.order(ByteOrder.LITTLE_ENDIAN);
    	indexBuffer.order(ByteOrder.LITTLE_ENDIAN);
    	
//        {{-0.5f, -0.5f}, {1.0f, 0.0f, 0.0f}},
//        {{0.5f, -0.5f}, {0.0f, 1.0f, 0.0f}},
//        {{0.5f, 0.5f}, {0.0f, 0.0f, 1.0f}},
//        {{-0.5f, 0.5f}, {1.0f, 1.0f, 1.0f}}
    	vertexBuffer.putFloat(-0.5f);
    	vertexBuffer.putFloat(-0.5f);
    	colorBuffer.putFloat(1f);
    	colorBuffer.putFloat(0f);
    	colorBuffer.putFloat(0f);

    	vertexBuffer.putFloat(0.5f);
    	vertexBuffer.putFloat(-0.5f);
    	colorBuffer.putFloat(0f);
    	colorBuffer.putFloat(1f);
    	colorBuffer.putFloat(0f);

    	vertexBuffer.putFloat(0.5f);
    	vertexBuffer.putFloat(0.5f);
    	colorBuffer.putFloat(0f);
    	colorBuffer.putFloat(0f);
    	colorBuffer.putFloat(1f);

    	vertexBuffer.putFloat(-0.5f);
    	vertexBuffer.putFloat(0.5f);
    	colorBuffer.putFloat(1f);
    	colorBuffer.putFloat(0f);
    	colorBuffer.putFloat(1f);
    	
    	indexBuffer.putShort((short)0);
    	indexBuffer.putShort((short)1);
    	indexBuffer.putShort((short)2);
    	indexBuffer.putShort((short)2);
    	indexBuffer.putShort((short)3);
    	indexBuffer.putShort((short)0);
    	
    	vertexBuffer.rewind();
    	colorBuffer.rewind();
    	indexBuffer.rewind();
	}
	
	
	
	public void indicesUniform() {

		// Create the data
    	ByteBuffer vertexBuffer = ByteBuffer.allocate(Float.BYTES * 6 * 2);
    	ByteBuffer colorBuffer = ByteBuffer.allocate(Float.BYTES * 6 * 3);
    	ByteBuffer indexBuffer = ByteBuffer.allocate(Short.BYTES * 6);
    	createIndicesSquare(vertexBuffer, colorBuffer, indexBuffer);
    	
    	// Gen buffers
    	IntBuffer out = IntBuffer.allocate(3);
    	gl.glGenBuffers(3, out);
    	int glVertBuff = out.get(0);
    	int glColBuff = out.get(1);
    	int glIndexBuff = out.get(2);
    	
    	
    	// Create our gpu program
    	int program = gl.glCreateProgram();
    	int vertShader = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
    	int fragShader = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
    	
    	// Shader source
    	gl.glShaderSource(vertShader, Util.readFile("resources/shaders/uniform.vert"));
    	gl.glShaderSource(fragShader, Util.readFile("resources/shaders/uniform.frag"));
    	// Compile the shaders
    	gl.glCompileShader(vertShader);
    	gl.glCompileShader(fragShader);
    	// Check shaders
		IntBuffer compileStatus = IntBuffer.allocate(1);
		gl.glGetShaderiv(vertShader, GL2VK.GL_COMPILE_STATUS, compileStatus);
		if (compileStatus.get(0) == GL2VK.GL_FALSE) {
			System.out.println(gl.glGetShaderInfoLog(vertShader));
			System.exit(1);
		}
		gl.glGetShaderiv(fragShader, GL2VK.GL_COMPILE_STATUS, compileStatus);
		if (compileStatus.get(0) == GL2VK.GL_FALSE) {
			System.out.println(gl.glGetShaderInfoLog(fragShader));
			System.exit(1);
		}
    	// Attach the shaders
    	gl.glAttachShader(program, vertShader);
    	gl.glAttachShader(program, fragShader);
    	// Don't need em anymore
    	gl.glDeleteShader(vertShader);
    	gl.glDeleteShader(fragShader);
    	
    	gl.glLinkProgram(program);

    	
		// Setup up attribs
		int position = gl.glGetAttribLocation(program, "inPosition");
		int color = gl.glGetAttribLocation(program, "inColor");
		
    	gl.glBindBuffer(GL2VK.GL_VERTEX_BUFFER, glVertBuff);
    	gl.glBufferData(GL2VK.GL_VERTEX_BUFFER, vertexBuffer.capacity(), vertexBuffer, 0);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 2*4, 0);
    	gl.glBindBuffer(GL2VK.GL_VERTEX_BUFFER, glColBuff);
    	gl.glBufferData(GL2VK.GL_VERTEX_BUFFER, colorBuffer.capacity(), colorBuffer, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 3*4, 0);

    	gl.glBindBuffer(GL2VK.GL_INDEX_BUFFER, glIndexBuff);
    	gl.glBufferData(GL2VK.GL_INDEX_BUFFER, indexBuffer.capacity(), indexBuffer, 0);
    	
    	
		
		
		gl.useProgram(program);
		
		boolean multithreaded = true;
		int threadIndex = 0;
		
		double qtime = 0d;

    	while (!gl.shouldClose()) {
    		gl.beginRecord();

    		if (multithreaded) gl.selectNode((int)(threadIndex++)%gl.getNodesCount());
    		else gl.selectNode(0);

    		qtime += 0.1d;
    		
        	gl.glBindBuffer(GL2VK.GL_INDEX_BUFFER, glIndexBuff);
    		gl.glUniform2f(0, (float)Math.sin(qtime)*0.5f, (float)Math.cos(qtime)*0.5f);
    		gl.glDrawElements(0, 6, GL2VK.GL_UNSIGNED_SHORT, 0);
    		gl.endRecord();
    		
//    		frameWait();
    	}
    	gl.close();
	}
	
	
	
	// Draw a square with indicies
	public void indices() {

		// Create the data
    	ByteBuffer vertexBuffer = ByteBuffer.allocate(Float.BYTES * 6 * 2);
    	ByteBuffer colorBuffer = ByteBuffer.allocate(Float.BYTES * 6 * 3);
    	ByteBuffer indexBuffer = ByteBuffer.allocate(Short.BYTES * 6);
    	createIndicesSquare(vertexBuffer, colorBuffer, indexBuffer);
    	
    	// Gen buffers
    	IntBuffer out = IntBuffer.allocate(3);
    	gl.glGenBuffers(3, out);
    	int glVertBuff = out.get(0);
    	int glColBuff = out.get(1);
    	int glIndexBuff = out.get(2);
    	
    	
    	// Create our gpu program
    	int program = gl.glCreateProgram();
    	int vertShader = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
    	int fragShader = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
    	
    	// Shader source
    	gl.glShaderSource(vertShader, Util.readFile("resources/shaders/shader.vert"));
    	gl.glShaderSource(fragShader, Util.readFile("resources/shaders/shader.frag"));
    	// Compile the shaders
    	gl.glCompileShader(vertShader);
    	gl.glCompileShader(fragShader);
    	// Attach the shaders
    	gl.glAttachShader(program, vertShader);
    	gl.glAttachShader(program, fragShader);
    	// Don't need em anymore
    	gl.glDeleteShader(vertShader);
    	gl.glDeleteShader(fragShader);
    	
    	gl.glLinkProgram(program);

    	
		// Setup up attribs
		int position = gl.glGetAttribLocation(program, "inPosition");
		int color = gl.glGetAttribLocation(program, "inColor");
		
    	gl.glBindBuffer(GL2VK.GL_VERTEX_BUFFER, glVertBuff);
    	gl.glBufferData(GL2VK.GL_VERTEX_BUFFER, vertexBuffer.capacity(), vertexBuffer, 0);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 2*4, 0);
    	gl.glBindBuffer(GL2VK.GL_VERTEX_BUFFER, glColBuff);
    	gl.glBufferData(GL2VK.GL_VERTEX_BUFFER, colorBuffer.capacity(), colorBuffer, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 3*4, 0);

    	gl.glBindBuffer(GL2VK.GL_INDEX_BUFFER, glIndexBuff);
    	gl.glBufferData(GL2VK.GL_INDEX_BUFFER, indexBuffer.capacity(), indexBuffer, 0);
    	
    	
		
		
		gl.useProgram(program);
		
		boolean multithreaded = true;
		int threadIndex = 0;

    	while (!gl.shouldClose()) {
    		gl.beginRecord();

    		if (multithreaded) gl.selectNode((int)(threadIndex++)%gl.getNodesCount());
    		else gl.selectNode(0);

        	gl.glBindBuffer(GL2VK.GL_INDEX_BUFFER, glIndexBuff);
    		gl.glDrawElements(0, 6, GL2VK.GL_UNSIGNED_SHORT, 0);
    		gl.endRecord();
    		
    		frameWait();
    	}
    	gl.close();
	}
	

	private Vertex[] vertices;
	
	
	private void createVertices(Vertex[] buffer) {
		
		final float TRIANGLE_SIZE = 0.04f;
		
		int l = buffer.length;
		for (int i = 0; i < l; i+=3) {
			float r = (float)Math.random();
			float g = (float)Math.random();
			float b = (float)Math.random();
			
			float x1 = (float)Math.random()*2f-1f;
			float y1 = (float)Math.random()*2f-1f;
			float x2 = x1+TRIANGLE_SIZE;
			float y2 = y1;
			float x3 = x1+TRIANGLE_SIZE/2f;
			float y3 = y1+TRIANGLE_SIZE;
			buffer[i] = new Vertex(x1,y1,r,g,b);
			if (i+1 < l) buffer[i+1] = new Vertex(x2,y2,r,g,b);
			if (i+2 < l) buffer[i+2] = new Vertex(x3,y3,r,g,b);
		}
	}

    private void memcpy(ByteBuffer buffer, Vertex[] vertices) {
    	buffer.order(ByteOrder.LITTLE_ENDIAN);
        for(Vertex vertex : vertices) {
            buffer.putFloat(vertex.pos.x());
            buffer.putFloat(vertex.pos.y());

            buffer.putFloat(vertex.color.x());
            buffer.putFloat(vertex.color.y());
            buffer.putFloat(vertex.color.z());
        }
    }
    
    
    private void copyVertex(ByteBuffer buffer, Vertex[] vertices) {
    	buffer.order(ByteOrder.LITTLE_ENDIAN);
        for(Vertex vertex : vertices) {
            buffer.putFloat(vertex.pos.x());
            buffer.putFloat(vertex.pos.y());
        }
    }
    

    private void copyColor(ByteBuffer buffer, Vertex[] vertices) {
    	buffer.order(ByteOrder.LITTLE_ENDIAN);
        for(Vertex vertex : vertices) {
            buffer.putFloat(vertex.color.x());
            buffer.putFloat(vertex.color.y());
            buffer.putFloat(vertex.color.z());
        }
    }
	
	
	public void triangles() {
		vertices = new Vertex[1000];
    	createVertices(vertices);
    	
    	// Gen buffers
    	IntBuffer out = IntBuffer.allocate(1);
    	gl.glGenBuffers(1, out);
    	int vertexBuffer = out.get(0);
    	
    	// Create our gpu program
    	int program = gl.glCreateProgram();
    	int vertShader = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
    	int fragShader = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
    	
    	// Shader source
    	gl.glShaderSource(vertShader, Util.readFile("resources/shaders/shader.vert"));
    	gl.glShaderSource(fragShader, Util.readFile("resources/shaders/shader.frag"));
    	// Compile the shaders
    	gl.glCompileShader(vertShader);
    	gl.glCompileShader(fragShader);
    	// Attach the shaders
    	gl.glAttachShader(program, vertShader);
    	gl.glAttachShader(program, fragShader);
    	// Don't need em anymore
    	gl.glDeleteShader(vertShader);
    	gl.glDeleteShader(fragShader);
    	
    	gl.glLinkProgram(program);
    	
    	
		
    	int size = vertices.length*Vertex.SIZEOF;
    	ByteBuffer buff = ByteBuffer.allocate(size);

    	gl.glBindBuffer(GL2VK.GL_VERTEX_BUFFER, vertexBuffer);
    	
		// Setup up attribs
		int position = gl.glGetAttribLocation(program, "inPosition");
		int color = gl.glGetAttribLocation(program, "inColor");
		gl.glVertexAttribPointer(position, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);
		
		gl.useProgram(program);
    	

    	boolean multithreaded = false;
    	int threadIndex = 0;
    	
    	while (!gl.shouldClose()) {
        	// Buffer data
        	createVertices(vertices);
        	buff.rewind();
    		memcpy(buff, vertices);
        	gl.glBindBuffer(GL2VK.GL_VERTEX_BUFFER, vertexBuffer);
        	gl.glBufferData(GL2VK.GL_VERTEX_BUFFER, size, buff, 0);

    		gl.beginRecord();

    		if (multithreaded) gl.selectNode((int)(threadIndex++)%gl.getNodesCount());
    		else gl.selectNode(0);
    		
    		gl.glDrawArrays(0, 0, vertices.length);
    		gl.endRecord();
    		
    		frameWait();
    	}
    	gl.close();
	}
	
	

	public void trianglesSeparate() {
		vertices = new Vertex[1000];
    	createVertices(vertices);
    	
    	// Gen buffers
    	IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	int vertexBuffer = out.get(0);
    	int colorBuffer = out.get(1);
    	
    	// Create our gpu program
    	int program = gl.glCreateProgram();
    	int vertShader = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
    	int fragShader = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
    	
    	// Shader source
    	gl.glShaderSource(vertShader, Util.readFile("resources/shaders/shader.vert"));
    	gl.glShaderSource(fragShader, Util.readFile("resources/shaders/shader.frag"));
    	// Compile the shaders
    	gl.glCompileShader(vertShader);
    	gl.glCompileShader(fragShader);
    	// Attach the shaders
    	gl.glAttachShader(program, vertShader);
    	gl.glAttachShader(program, fragShader);
    	// Don't need em anymore
    	gl.glDeleteShader(vertShader);
    	gl.glDeleteShader(fragShader);
    	
    	gl.glLinkProgram(program);
    	
    	
		
    	ByteBuffer vertexBuff = ByteBuffer.allocate(2 * Float.BYTES * vertices.length);
    	ByteBuffer colorBuff = ByteBuffer.allocate(3 * Float.BYTES * vertices.length);

    	
		// Setup up attribs
		int position = gl.glGetAttribLocation(program, "inPosition");
		int color = gl.glGetAttribLocation(program, "inColor");

    	gl.glBindBuffer(GL2VK.GL_VERTEX_BUFFER, vertexBuffer);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 2*4, 0);
    	gl.glBindBuffer(GL2VK.GL_VERTEX_BUFFER, colorBuffer);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 3*4, 0);
		
		gl.useProgram(program);
    	

    	boolean multithreaded = false;
    	int threadIndex = 0;
    	
    	while (!gl.shouldClose()) {
        	// Buffer vertices
        	createVertices(vertices);
        	vertexBuff.rewind();
        	copyVertex(vertexBuff, vertices);
        	gl.glBindBuffer(GL2VK.GL_VERTEX_BUFFER, vertexBuffer);
        	gl.glBufferData(GL2VK.GL_VERTEX_BUFFER, 2 * Float.BYTES * vertices.length, vertexBuff, 0);

        	colorBuff.rewind();
        	copyColor(colorBuff, vertices);
        	gl.glBindBuffer(GL2VK.GL_VERTEX_BUFFER, colorBuffer);
        	gl.glBufferData(GL2VK.GL_VERTEX_BUFFER, 3 * Float.BYTES * vertices.length, colorBuff, 0);
//
//        	vertexBuff.rewind();
//        	
//        	while (vertexBuff.hasRemaining()) {
//        		System.out.println(vertexBuff.getFloat());
//        	}

    		gl.beginRecord();

    		if (multithreaded) gl.selectNode((int)(threadIndex++)%gl.getNodesCount());
    		else gl.selectNode(0);
    		
    		gl.glDrawArrays(0, 0, vertices.length);
    		gl.endRecord();
    		
    		frameWait();
    	}
    	gl.close();
	}
	
	
	
	private void frameWait() {
		try {
			Thread.sleep(16);
		} catch (InterruptedException e) {
		}
	}
	
	
	public void throttleTest() {
		final int PARTS = 5;
		vertices = new Vertex[3];
		int[] vertexBuffer = new int[PARTS];
    	createVertices(vertices);
    	
    	// Gen buffers
    	IntBuffer out = IntBuffer.allocate(PARTS);
    	gl.glGenBuffers(PARTS, out);
    	vertexBuffer = out.array();
    	
    	int buffindex = 0;

    	int size = vertices.length*Vertex.SIZEOF;
    	ByteBuffer buff = ByteBuffer.allocate(size);
    	
    	for (int i = 0; i < PARTS; i++) {
	    	// Buffer data
	    	createVertices(vertices);
	    	buff.rewind();
			memcpy(buff, vertices);
			
	    	gl.glBindBuffer(GL2VK.GL_VERTEX_BUFFER, vertexBuffer[i]);
	    	gl.glBufferData(GL2VK.GL_VERTEX_BUFFER, size, buff, 0);
    	}
		
    	boolean multithreaded = true;
  	
    	while (!gl.shouldClose()) {
    		
    		gl.beginRecord();
    		
    		// Throttle
        	for (int i = 0; i < 10000; i++) {
        		if (multithreaded) gl.selectNode((int)(i/10)%gl.getNodesCount());
        		else gl.selectNode(0);
        		
        		gl.glBindBuffer(GL2VK.GL_VERTEX_BUFFER, vertexBuffer[buffindex]);
	    		gl.glDrawArrays(0, 0, vertices.length);
        	}
        	
    		buffindex++;
    		if (buffindex >= PARTS) buffindex = 0;
    		
    		
    		gl.endRecord();
//    		frameWait();
    	}
    	gl.close();
	}
	
}
