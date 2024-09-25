package helloVulkan;

import java.util.Collection;
import java.util.List;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Pointer;

public class Util {
    public static final int UINT32_MAX = 0xFFFFFFFF;
    public static final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL;
    
    public static PointerBuffer asPointerBuffer(MemoryStack stack, Collection<String> collection) {

        PointerBuffer buffer = stack.mallocPointer(collection.size());

        collection.stream()
                .map(stack::UTF8)
                .forEach(buffer::put);

        return buffer.rewind();
    }

    public static PointerBuffer asPointerBuffer(MemoryStack stack, List<? extends Pointer> list) {

        PointerBuffer buffer = stack.mallocPointer(list.size());

        list.forEach(buffer::put);

        return buffer.rewind();
    }

    public static int clamp(int min, int max, int value) {
        return Math.max(min, Math.min(max, value));
    }

    
	// Lil debugging tools here
	long tmrnbefore = 0L;
	public void beginTmr() {
		tmrnbefore = System.nanoTime();
	}
	public void endTmr(String name) {
		long us = ((System.nanoTime()-tmrnbefore)/1000L);
		System.out.println(
				name+": "+us+"us"+
				(us > 1000 ? " ("+(us/1000L)+"ms)" : "") +
				(us > 1000000 ? " ("+(us/1000000L)+"s)" : "")
		);
	}
}
