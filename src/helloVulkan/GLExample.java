package helloVulkan;

import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class GLExample {
	GL2VK gl;

    // ======= CLASSES ======= //
    private static class Vertex {
        private static final int SIZEOF = (2 + 3) * Float.BYTES;

    	private Vector2fc pos;
    	private Vector3fc color;
    	
    	public Vertex(Vector2fc pos, Vector3fc color) {
    		this.pos = pos;
    		this.color = color;
    	}
    	
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
        for(Vertex vertex : vertices) {
            buffer.putFloat(vertex.pos.x());
            buffer.putFloat(vertex.pos.y());

            buffer.putFloat(vertex.color.x());
            buffer.putFloat(vertex.color.y());
            buffer.putFloat(vertex.color.z());
        }
    }
	
	
	public void run() {
		try {
			gl = new GL2VK();
//			triangles();
			throttleTest();
		}
		catch (Exception e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
	}
	
	
	
	
	public void triangles() {
		vertices = new Vertex[10000];
    	createVertices(vertices);
    	
    	// Gen buffers
    	IntBuffer out = IntBuffer.allocate(1);
    	gl.glGenBuffers(1, out);
    	int vertexBuffer = out.get(0);
    	
    	

    	int size = vertices.length*Vertex.SIZEOF;
    	ByteBuffer buff = ByteBuffer.allocate(size);
    	
    	// Buffer data
    	createVertices(vertices);
    	buff.rewind();
		memcpy(buff, vertices);
    	gl.glBindBuffer(GL2VK.GL_VERTEX_BUFFER, vertexBuffer);
    	gl.glBufferData(GL2VK.GL_VERTEX_BUFFER, size, buff, 0);

    	boolean multithreaded = true;
    	int threadIndex = 0;
    	
    	while (!gl.shouldClose()) {

    		if (multithreaded) gl.selectNode((int)(threadIndex++)%gl.getNodesCount());
    		else gl.selectNode(0);
        	

    		gl.beginRecord();
    		gl.glDrawArrays(vertexBuffer, 0, vertices.length);
    		gl.endRecord();
    		System.out.println(threadIndex);
    		
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
        	for (int i = 0; i < 25000; i++) {
        		if (multithreaded) gl.selectNode((int)(i/1000)%gl.getNodesCount());
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
