package helloVulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_SECONDARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkCreateCommandPool;

import java.nio.LongBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;

import helloVulkan.VKSetup.QueueFamilyIndices;

public class ThreadNode {
	public final static int CMD_DRAW_ARRAYS = 1;
	public final static int CMD_DRAW_INDEXED = 2;
	
	// To avoid clashing from the main thread accessing the front of the queue while the
	// other thread is accessing the end of the queue, best solution is to make this big
	// enough lol.
	private final static int MAX_QUEUE_LENGTH = 512;
	
	private VulkanSystem system;
	private VKSetup vkbase;
	
	private VkCommandBuffer[] cmdbuffers;
	private AtomicBoolean sleeping = new AtomicBoolean(false);
	private long commandPool;
	
	
	// There are two seperate indexes, one for our thread (main thread) and one for this thread.
	// We add item to queue (cmdindex = 0 -> 1) and then eventually thread updates its own index
	// as it works on cmd   (myIndex  = 0 -> 1)
	private int cmdindex = 0;
	private Thread thread;
	
	// Accessed by 2 threads so volatile (i was told that volatile avoids outdated caching issues)
	// (source: the internet, the most truthful place, i think)
	private volatile CMD[] cmdqueue;
	
	
	// INNER COMMAND TYPES
	// Originally was gonna create some classes which extend this class,
	// but this would mean garbage collection for each command we call.
	// The other option is to put all arguments from every command into
	// the one cmd class, which isn't the most readable or memory efficient,
	// but we care about going FAST.
	private class CMD {
		// All
		public int cmdid = 0;
		
		// Vertex
		public long bufferid = 0;
		public int size = 0;
		public int first = 0;
	}
	
	

	public ThreadNode(VulkanSystem vk) {
		system = vk;
		vkbase = vk.vkbase;
		cmdqueue = new CMD[MAX_QUEUE_LENGTH];
		for (int i = 0; i < MAX_QUEUE_LENGTH; i++) {
			cmdqueue[i] = new CMD();
		}
		
		createObjects();
		startThread();
	}

	private void createObjects() {
		// Create command pool
        try(MemoryStack stack = stackPush()) {
	        VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack);
	        poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
	        poolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
	        poolInfo.queueFamilyIndex(vkbase.queueIndicies.graphicsFamily);
	        
	        // create our command pool vk
	        LongBuffer pCommandPool = stack.mallocLong(1);
	        if (vkCreateCommandPool(vkbase.device, poolInfo, null, pCommandPool) != VK_SUCCESS) {
	            throw new RuntimeException("Failed to create command pool");
	        };
	        commandPool = pCommandPool.get(0);
        }
        
        // Create secondary command buffer
        try(MemoryStack stack = stackPush()) {
        	final int commandBuffersCount = vkbase.swapChainFramebuffers.size();

            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(commandPool);
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_SECONDARY);
            allocInfo.commandBufferCount(commandBuffersCount);

            PointerBuffer pCommandBuffers = stack.mallocPointer(commandBuffersCount);

            if(vkAllocateCommandBuffers(vkbase.device, allocInfo, pCommandBuffers) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffers");
            }

            for(int i = 0; i < commandBuffersCount; i++) {
            	cmdbuffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), vkbase.device);
            }
        }
	}
	
	private void startThread() {
		// Inside of thread, we run logic which checks for items in the queue
		// and then executes the vk commands that correspond to the int
		thread = new Thread(new Runnable() {
	          public void run() {
	        	  int myIndex = 0;
	        	  // Look forever
	        	  while (true) {
	        		  boolean goToSleepMode = false;
	        		  // Set it to 0 because why not (prevents hard-to-solve bugs probably)
	        		  CMD cmd = cmdqueue[(myIndex++)%MAX_QUEUE_LENGTH];
	        		  
	        		  // TODO: get correct frame thingiemajig.
	        		  VkCommandBuffer cmdbuffer = cmdbuffers[0];
	        		  
	        		  
	        		  
	        		  // ======================
	        		  // CMD EXECUTOR
	        		  // ======================
	        		  switch (cmd.cmdid) {
	        		  case 0:
	        			  goToSleepMode = true;
	        			  break;
	        		  case CMD_DRAW_ARRAYS:
	        			  system.drawArrays(cmdbuffer, cmd.bufferid, cmd.size, cmd.first);
	        			  break;
	        		  }
	        		  // ======================
	        		  
	        		  // Reset to zero for safety purposes.
	        		  cmd.cmdid = 0;
	        		  
	        		  // No more tasks to do? Take a lil nap.
	        		  if (goToSleepMode) {
	        			  sleeping.set(true);
	        			  try {
	        				  // Sleep for an indefinite amount of time
	        				  // (we gonna interrupt the thread later)
	        				  Thread.sleep(999999999);
	        			  }
	        			  catch (InterruptedException e) {
	        				  // When interrupted, this means we continue down the loop.
	        				  sleeping.set(false);
	        				  // As a bug-safe safety percaution, loop through our cmd list until we find
	        				  // a value (even though technically speaking, we should always have a next
	        				  // item to execute at myIndex, but this is threads so we never know.
	        				  int count = 0;
	        				  while (count < MAX_QUEUE_LENGTH) {
	        					  if (cmdqueue[(myIndex)%MAX_QUEUE_LENGTH].cmdid == 0) {
	        						  myIndex++;
	        					  }
	        					  else break;
	        					  count++;
	        				  }
	        				  if (count >= MAX_QUEUE_LENGTH) {
	        					  System.err.println("BUG WARNING signalled out of sleep with no work available.");
	        				  }
	        				  // TODO: Add terminate signal detection code here.
	        			  }
	        		  }
	        	  }
	        	  
	          }
		}
		);
	}
	
	private CMD getNextCMD() {
		return cmdqueue[(cmdindex++)%MAX_QUEUE_LENGTH];
	}
	
	private void wakeThread() {
		// Only need to interrupt if sleeping.
		if (sleeping.get() == true) {
			thread.interrupt();
		}
	}

    public void drawArrays(long id, int size, int first) {
        CMD cmd = getNextCMD();
        cmd.cmdid = CMD_DRAW_ARRAYS;
        cmd.bufferid = id;
        cmd.size = size;
        cmd.first = first;
        wakeThread();
    }
}
