package helloVulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_SECONDARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCreateCommandPool;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkResetCommandBuffer;


import java.nio.LongBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandBufferInheritanceInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;

//import helloVulkan.VKSetup.QueueFamilyIndices;

public class ThreadNode {
	final static boolean DEBUG = true;
	
	
	public final static int NO_CMD = 0;
	public final static int CMD_DRAW_ARRAYS = 1;
	public final static int CMD_DRAW_INDEXED = 2;
	public final static int CMD_BEGIN_RECORD = 3;
	public final static int CMD_END_RECORD = 4;
	public final static int CMD_KILL = 5;
	
	// To avoid clashing from the main thread accessing the front of the queue while the
	// other thread is accessing the end of the queue, best solution is to make this big
	// enough lol.
	private final static int MAX_QUEUE_LENGTH = 1000;
	
	private VulkanSystem system;
	private VKSetup vkbase;
	private int myID = 0;
	
	private VkCommandBuffer[] cmdbuffers;
	private AtomicBoolean sleeping = new AtomicBoolean(false);
	private AtomicInteger currentFrame = new AtomicInteger(0);
	private AtomicInteger currentImage = new AtomicInteger(0);
	private long commandPool;
	
	// Read-only begin info for beginning our recording of commands
	// (what am i even typing i need sleep)
	// One for each frames in flight
	private VkCommandBufferBeginInfo[] beginInfos;
	// Just to keep it from being garbage collected or something
	private VkCommandBufferInheritanceInfo[] inheritanceInfos;
	
	
	// There are two seperate indexes, one for our thread (main thread) and one for this thread.
	// We add item to queue (cmdindex = 0 -> 1) and then eventually thread updates its own index
	// as it works on cmd   (myIndex  = 0 -> 1)
	private int cmdindex = 0;  // Start at one because thread will have already consumed index 0
	private Thread thread;
	
	// Accessed by 2 threads so volatile (i was told that volatile avoids outdated caching issues)
	// (source: the internet, the most truthful place, i think)
//	private volatile CMD[] cmdqueue;

	
	// INNER COMMAND TYPES
	// Originally was gonna create some classes which extend this class,
	// but this would mean garbage collection for each command we call.
	// The other option is to put all arguments from every command into
	// the one cmd class, which isn't the most readable or memory efficient,
	// but we care about going FAST.
	private AtomicIntegerArray cmdID = new AtomicIntegerArray(MAX_QUEUE_LENGTH);
	private AtomicLongArray cmdBufferid = new AtomicLongArray(MAX_QUEUE_LENGTH);
	private AtomicIntegerArray cmdSize = new AtomicIntegerArray(MAX_QUEUE_LENGTH);
	private AtomicIntegerArray cmdFirst = new AtomicIntegerArray(MAX_QUEUE_LENGTH);
	
	

	public ThreadNode(VulkanSystem vk, int id) {
		// Variables setup
		system = vk;
		vkbase = vk.vkbase;
		myID = id;
		
		// Initialise cmdqueue and all its objects
		for (int i = 0; i < MAX_QUEUE_LENGTH; i++) {
			cmdID.set(i, 0);
		}
		
		createObjects();
		startThread();
	}
	

	private void println(String message) {
		if (DEBUG) {
			System.out.println("("+myID+") "+message);
		}
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

//    	final int commandBuffersCount = system.swapChainFramebuffers.size();
        final int commandBuffersCount = VulkanSystem.MAX_FRAMES_IN_FLIGHT;
    	
        // Create secondary command buffer
        try(MemoryStack stack = stackPush()) {

            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(commandPool);
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_SECONDARY);
            allocInfo.commandBufferCount(commandBuffersCount);

            PointerBuffer pCommandBuffers = stack.mallocPointer(commandBuffersCount);

            if(vkAllocateCommandBuffers(vkbase.device, allocInfo, pCommandBuffers) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffers");
            }

            cmdbuffers = new VkCommandBuffer[commandBuffersCount];
            for(int i = 0; i < commandBuffersCount; i++) {
            	cmdbuffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), vkbase.device);
            }
        }
        
        int imagesSize = system.swapChainFramebuffers.size();
        // Create readonly beginInfo structs.
        beginInfos = new VkCommandBufferBeginInfo[imagesSize];
        inheritanceInfos = new VkCommandBufferInheritanceInfo[imagesSize];
        for(int i = 0; i < imagesSize; i++) {
//        	 Inheritance because for some reason we need that
	        inheritanceInfos[i] = VkCommandBufferInheritanceInfo.create();
	        inheritanceInfos[i].sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO);
			inheritanceInfos[i].renderPass(system.renderPass);
			// Secondary command buffer also use the currently active framebuffer
			inheritanceInfos[i].framebuffer(system.swapChainFramebuffers.get(i));
			
			beginInfos[i] = VkCommandBufferBeginInfo.create();
			beginInfos[i].sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
			beginInfos[i].flags(VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT);
			beginInfos[i].pInheritanceInfo(inheritanceInfos[i]);
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
	        		  boolean kill = false;
	        		  // Set it to 0 because why not (prevents hard-to-solve bugs probably)
	        		  int index = (myIndex++)%MAX_QUEUE_LENGTH;
	        		  int id = cmdID.get(index);
	        		  
	        		  // TODO: get correct frame thingiemajig.
	        		  VkCommandBuffer cmdbuffer = cmdbuffers[currentFrame.get()];
	        		  
	        		  println(""+id);
	        		  
	        		  // ======================
	        		  // CMD EXECUTOR
	        		  // ======================
	        		  switch (id) {
	        		  case NO_CMD:
	        			  sleeping.set(true);
	        			  goToSleepMode = true;
	        			  myIndex--;
	        			  break;
	        		  case CMD_DRAW_ARRAYS:
	        			  println("CMD_DRAW_ARRAYS");
	        			  system.drawArraysImpl(cmdbuffer, cmdBufferid.get(index), cmdSize.get(index), cmdFirst.get(index));
	        			  break;
	        			  
	        			  // Probably the most important command
	        		  case CMD_BEGIN_RECORD:
	        			  	println("CMD_BEGIN_RECORD");
	        		      	vkResetCommandBuffer(cmdbuffer, 0);
	        		      	// Begin recording
	        		      	
	        		      	// In case you're wondering, beginInfo index is currentImage, not
	        		      	// the currentFrame, because it holds which framebuffer (image) we're
	        		      	// using (confusing i know)
	        	            if(vkBeginCommandBuffer(cmdbuffer, beginInfos[currentImage.get()]) != VK_SUCCESS) {
	        	                throw new RuntimeException("Failed to begin recording command buffer");
	        	            }
	        	            vkCmdBindPipeline(cmdbuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, system.graphicsPipeline);
	        	            break;
	        		  case CMD_END_RECORD:
	        			  	println("CMD_END_RECORD");
						    if(vkEndCommandBuffer(cmdbuffer) != VK_SUCCESS) {
						        throw new RuntimeException("Failed to record command buffer");
						    }
						    break;
	        		  case CMD_KILL:
	        			  goToSleepMode = false;
	        			  kill = true;
	        			  break;
	        		  }
	        		  // ======================
	        		  
	        		  // Reset to zero for safety purposes.
	        		  cmdID.set(index, NO_CMD);
	        		  
	        		  if (kill) {
	        			  sleeping.set(false);
	        			  // Kills the thread
	        			  break;
	        		  }
	        		  
	        		  // No more tasks to do? Take a lil nap.
	        		  if (goToSleepMode) {
	        			  println("NOW SLEEPING");
	        			  try {
	        				  // Sleep for an indefinite amount of time
	        				  // (we gonna interrupt the thread later)
	        				  Thread.sleep(9999999);
	        			  }
	        			  catch (InterruptedException e) {
	        				  // When interrupted, this means we continue down the loop.
	        				  // As a bug-safe safety percaution, loop through our cmd list until we find
	        				  // a value (even though technically speaking, we should always have a next
	        				  // item to execute at myIndex, but this is threads so we never know.
	        				  int count = 0;
	        				  while (count < MAX_QUEUE_LENGTH) {
	        					  if (cmdID.get((myIndex)%MAX_QUEUE_LENGTH) == NO_CMD) {
	        						  myIndex++;
	        					  }
	        					  else break;
	        					  count++;
	        				  }
	        				  if (count >= MAX_QUEUE_LENGTH) {
	        					  System.err.println("BUG WARNING signalled out of sleep with no work available.");
	        				  }
	        				  else if (count >= 1) {
	        					  System.err.println("BUG WARNING had to escape our way out of blank queue ("+count+")");
	        				  }
	        				  sleeping.set(false);
	        			  }
	        		  }
	        	  }
	        	  
	          }
		}
		);
		thread.start();
	}
	
	
	private int getNextCMDIndex() {
		int ret = (cmdindex)%MAX_QUEUE_LENGTH;
		while (cmdID.get(ret) != NO_CMD) {
			// We're forced to wait until the thread has caught up with some of the queue
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
			}
//			println("WARNING  queue clash, cmdid is "+ret.cmdid);
		}
		cmdindex++;
		return ret;
	}
	
	
	private void wakeThread() {
		// Only need to interrupt if sleeping.
		// We call it here because if wakeThread is called, then a command was called, and
		// when a command was called, that means we should definitely not be asleep
		// (avoids concurrency issues with await()
		if (sleeping.get() == true) {
			thread.interrupt();
		}
		sleeping.set(false);
	}


    public void drawArrays(long id, int size, int first) {
		println("call draw arrays");
        int index = getNextCMDIndex();
        cmdID.set(index, CMD_DRAW_ARRAYS);
        cmdBufferid.set(index, id);
        cmdSize.set(index, size);
        cmdFirst.set(index, first);
        wakeThread();
    }

	public void beginRecord(int currentFrame, int currentImage) {
		println("call begin record");
		this.currentFrame.set(currentFrame);
		this.currentImage.set(currentImage);
        int index = getNextCMDIndex();
        cmdID.set(index, CMD_BEGIN_RECORD);
        // No arguments
        wakeThread();
	}
	
	public void endRecord() {
		println("call end record");
        int index = getNextCMDIndex();
        cmdID.set(index, CMD_END_RECORD);
        // No arguments
        wakeThread();
	}

	public void kill() {
		println("kill thread");
        int index = getNextCMDIndex();
        cmdID.set(index, CMD_KILL);
        // No arguments
        wakeThread();
	}
	
	public VkCommandBuffer getBuffer() {
		return cmdbuffers[currentFrame.get()];
	}
	
	public void await() {
		int count = 0;
		while (sleeping.get() == false) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
			}
			if (count == 500) {
				System.err.println("BUG WARNING  looplock'd waiting for a thread that won't respond");
			}
			count++;
		}
	}
}