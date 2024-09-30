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
	final static boolean DEBUG = false;
	
	
	public final static int NO_CMD = 0;
	public final static int CMD_DRAW_ARRAYS = 1;
	public final static int CMD_DRAW_INDEXED = 2;
	public final static int CMD_BEGIN_RECORD = 3;
	public final static int CMD_END_RECORD = 4;
	public final static int CMD_KILL = 5;

	public final static int STATE_INACTIVE = 0;
	public final static int STATE_SLEEPING = 1;
	public final static int STATE_RUNNING = 2;
	public final static int STATE_ENTERING_SLEEP = 3;
	public final static int STATE_KILLED = 4;
	public final static int STATE_WAKING = 5;
	
	
	// CURRENT BUGS:
	// Let's say we're calling endCommands:
	// - (1) executing cmd
	// - (0) Set cmd id
	// - (1) Oh look! A new command to execute.
	// - (1) Done, let's go to sleep
	// - (0) wakeThread (we're under the assumption that the thread hasn't done the work yet)
	// - (1) Woke up, but wait! There isn't any work for me to do!
	// The solution? Not sure yet.
	
	
	
	// To avoid clashing from the main thread accessing the front of the queue while the
	// other thread is accessing the end of the queue, best solution is to make this big
	// enough lol.
	private final static int MAX_QUEUE_LENGTH = 50000;
	
	private VulkanSystem system;
	private VKSetup vkbase;
	private int myID = 0;
	
	private VkCommandBuffer[] cmdbuffers;
	private AtomicInteger currentFrame = new AtomicInteger(0);
	private AtomicInteger currentImage = new AtomicInteger(0);
	private long commandPool;

	private AtomicInteger threadState = new AtomicInteger(STATE_INACTIVE);
	private AtomicBoolean openCmdBuffer = new AtomicBoolean(false);
	
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
	        		  int id = cmdID.getAndSet(index, 0);
	        		  
	        		  // TODO: get correct frame thingiemajig.
	        		  VkCommandBuffer cmdbuffer = cmdbuffers[currentFrame.get()];
	        		  
//	        		  println(""+id);
	        		  
	        		  // ======================
	        		  // CMD EXECUTOR
	        		  // ======================
	        		  
	        		  
	        		  switch (id) {
	        		  case NO_CMD:
	        			  threadState.set(STATE_ENTERING_SLEEP);
	        			  goToSleepMode = true;
	        			  myIndex--;
	        			  break;
	        		  case CMD_DRAW_ARRAYS:
	        			  println("CMD_DRAW_ARRAYS (index "+index+")");
	        			  system.drawArraysImpl(cmdbuffer, cmdBufferid.get(index), cmdSize.get(index), cmdFirst.get(index));
	        			  break;
	        			  
	        			  // Probably the most important command
	        		  case CMD_BEGIN_RECORD:
	        			  	println("CMD_BEGIN_RECORD");
	        			  	
	        			  	if (openCmdBuffer.get() == false) {
	        			  		vkResetCommandBuffer(cmdbuffer, 0);
		        		      	// Begin recording
		        		      	
		        		      	// In case you're wondering, beginInfo index is currentImage, not
		        		      	// the currentFrame, because it holds which framebuffer (image) we're
		        		      	// using (confusing i know)
		        	            if(vkBeginCommandBuffer(cmdbuffer, beginInfos[currentImage.get()]) != VK_SUCCESS) {
		        	                throw new RuntimeException("Failed to begin recording command buffer");
		        	            }
	        			  	}
	        			  	// Bug detected
	        			  	else System.err.println("("+myID+") Attempt to begin an already open command buffer."); 
	        			  	
	        	            openCmdBuffer.set(true);
	        	            vkCmdBindPipeline(cmdbuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, system.graphicsPipeline);
	        	            break;
	        		  case CMD_END_RECORD:
	        			  	println("CMD_END_RECORD (index "+index+")");

	        			  	if (openCmdBuffer.get() == true) {
							    if(vkEndCommandBuffer(cmdbuffer) != VK_SUCCESS) {
							        throw new RuntimeException("Failed to record command buffer");
							    }
		        	            openCmdBuffer.set(false);
	        			  	}
	        			  	else System.err.println("("+myID+") Attempt to close an already closed command buffer."); 
	        	            
	        	            // We should also really go into sleep mode now
	        	            threadState.set(STATE_ENTERING_SLEEP);
	        	            goToSleepMode = true;
	        	            
						    break;
	        		  case CMD_KILL:
	        			  goToSleepMode = false;
	        			  kill = true;
	        			  break;
	        		  }
	        		  // ======================
	        		  
	        		  
	        		  if (kill) {
	        			  threadState.set(STATE_KILLED);
	        			  // Kills the thread
	        			  break;
	        		  }
	        		  
	        		  // No more tasks to do? Take a lil nap.
	        		  if (goToSleepMode) {
	        			  println("NOW SLEEPING");
	        			  try {
	        				  // Sleep for an indefinite amount of time
	        				  // (we gonna interrupt the thread later)
	        				  threadState.lazySet(STATE_SLEEPING);
	        				  Thread.sleep(9999999);
	        			  }
	        			  catch (InterruptedException e) {
	        				  // When interrupted, this means we continue down the loop.
	        				  // As a bug-safe safety percaution, loop through our cmd list until we find
	        				  // a value (even though technically speaking, we should always have a next
	        				  // item to execute at myIndex, but this is threads so we never know.
	        				  threadState.set(STATE_WAKING);
	        				  int count = 0;
	        				  while (count < MAX_QUEUE_LENGTH) {
	        					  if (cmdID.get((myIndex)%MAX_QUEUE_LENGTH) == NO_CMD) {
	        						  myIndex++;
	        					  }
	        					  else break;
	        					  count++;
	        				  }
	        				  if (count >= MAX_QUEUE_LENGTH) {
	        					  System.err.println("("+myID+") BUG WARNING signalled out of sleep with no work available.");
	        				  }
	        				  else if (count >= 1) {
	        					  System.err.println("("+myID+") BUG WARNING had to escape our way out of blank queue ("+count+")");
	        				  }
	        				  threadState.set(STATE_RUNNING);
	        			  }
	        		  }
	        	  }
	        	  threadState.set(STATE_KILLED);
	        	  
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
		if (threadState.get() == STATE_ENTERING_SLEEP) {
			// Uhoh, unlucky. This means we just gotta wait until we're entering sleep state then wake up.
			while (threadState.get() != STATE_SLEEPING) {
				try {
					Thread.sleep(0, 1000);
				} catch (InterruptedException e) {
				}
			}
			thread.interrupt();
		}
		if (threadState.get() == STATE_SLEEPING) {
			thread.interrupt();
		}
		
		// We also need to consider the case for when a thread is ABOUT to enter sleep mode.
		// Cus we can call interrupt() all we want, it's not going to stop the thread from
		// entering sleep mode.
		
//		sleeping.set(false);
	}


    public void drawArrays(long id, int size, int first) {
        int index = getNextCMDIndex();
		println("call CMD_DRAW_ARRAYS (index "+index+")");
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
        int index = getNextCMDIndex();
		println("call CMD_END_RECORD (index "+index+")");
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
		// We wait until it has finished its commands
		
		// In order for the thread to be properly done its work it must:
		// - be in sleep mode
		// - its cmd buffer must be closed
		while (
				!(threadState.get() == STATE_SLEEPING &&
				openCmdBuffer.get() == false)
				) {
			
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
			}
			if (count == 500) {
				System.err.println("("+this.myID+") BUG WARNING  looplock'd waiting for a thread that won't respond (state "+threadState.get()+")");
			}
			count++;
		}
	}
}