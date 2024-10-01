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
	public final static int CMD_BUFFER_DATA = 6;

	public final static int STATE_INACTIVE = 0;
	public final static int STATE_SLEEPING = 1;
	public final static int STATE_RUNNING = 2;
	public final static int STATE_ENTERING_SLEEP = 3;
	public final static int STATE_KILLED = 4;
	public final static int STATE_WAKING = 5;
	public final static int STATE_SLEEPING_INTERRUPTED = 6;
	public final static int STATE_NEXT_CMD = 7;
	
	
	// CURRENT BUGS:
	// BUG WARNING signalled out of sleep with no work available.
	// Let's say we're calling endCommands:
	// - (1) executing cmd
	// - (0) Set cmd id
	// - (1) Oh look! A new command to execute.
	// - (1) Done, let's go to sleep
	// - (0) wakeThread (we're under the assumption that the thread hasn't done the work yet)
	// - (1) Woke up, but wait! There isn't any work for me to do!
	// Solution: check cmd is set to 0 or not
	
	// Other bug: 
	// looplock'd waiting for a thread that won't respond (state 1)
	// 
	
	// To avoid clashing from the main thread accessing the front of the queue while the
	// other thread is accessing the end of the queue, best solution is to make this big
	// enough lol.
	private final static int MAX_QUEUE_LENGTH = 50000;
	
	private VulkanSystem system;
	private VKSetup vkbase;
	private int myID = 0;
	
	private VkCommandBuffer[] cmdbuffers;
	
	// NOT to be set by main thread
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
	private AtomicLongArray cmdLongArg1 = new AtomicLongArray(MAX_QUEUE_LENGTH);
	private AtomicLongArray cmdLongArg2 = new AtomicLongArray(MAX_QUEUE_LENGTH);
	private AtomicIntegerArray cmdIntArg1 = new AtomicIntegerArray(MAX_QUEUE_LENGTH);
	private AtomicIntegerArray cmdIntArg2 = new AtomicIntegerArray(MAX_QUEUE_LENGTH);
	
	

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

        		  long sleepTime = 0L;
        		  long runTime = 0L;
	        	  
	        	  // Loop until receive KILL_THREAD cmd
	        	  while (true) {
    				  long runbefore = System.nanoTime();
	        		  boolean goToSleepMode = false;
	        		  boolean kill = false;

	        		  int index = (myIndex++)%MAX_QUEUE_LENGTH;
	        		  
	        		  VkCommandBuffer cmdbuffer = cmdbuffers[currentFrame.get()];
	        		  
	        		  
//	        		  if (threadState.get() == STATE_WAKING) {
//	        			  if (id == NO_CMD) System.err.println(myID+" NO_CMD warning");
//        				  threadState.set(STATE_RUNNING);
//	        		  }
	        		  

	        		  // ======================
	        		  // CMD EXECUTOR
	        		  // ======================
	        		  

	        		  // As soon as we're at this point we need to tell the main thread that this is a 
	        		  // time-sensitive point where we are in the process of getting the next
	        		  // cmd, and in between that verrrry small timeframe, it could be set to something
	        		  // different. The next command will most definitely be 0 -> something else, so
	        		  // our thread may end up reading an outdated version (i.e. 0, or NO_CMD). 
	        		  // If that happens, our main thread needs to see the state is STATE_NEXT_CMD, and
	        		  // then call wakeThread which will make sure our thread's got the right value.
	        		  
        			  threadState.set(STATE_NEXT_CMD);
	        		  
	        		  switch (cmdID.getAndSet(index, 0)) {
	        		  case NO_CMD:
	        			  threadState.set(STATE_ENTERING_SLEEP);
	        			  goToSleepMode = true;
	        			  myIndex--;
	        			  break;
	        		  case CMD_DRAW_ARRAYS:
	        			  threadState.set(STATE_RUNNING);
	        			  println("CMD_DRAW_ARRAYS (index "+index+")");
	        			  system.drawArraysImpl(cmdbuffer, cmdLongArg1.get(index), cmdIntArg1.get(index), cmdIntArg2.get(index));
	        			  break;
	        			  
	        			  // Probably the most important command
	        		  case CMD_BEGIN_RECORD:
	        			  	threadState.set(STATE_RUNNING);
	        			  	sleepTime = 0;
	        			  	runTime = 0;
	        			  	println("CMD_BEGIN_RECORD");

        			  		currentImage.set(cmdIntArg1.get(index));
        			  		currentFrame.set(cmdIntArg2.get(index));
        			  		cmdbuffer = cmdbuffers[currentFrame.get()];
	        			  	
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
	        			  	threadState.set(STATE_RUNNING);
	        			  	println("CMD_END_RECORD (index "+index+")");

	        			  	if (openCmdBuffer.get() == true) {
							    if(vkEndCommandBuffer(cmdbuffer) != VK_SUCCESS) {
							        throw new RuntimeException("Failed to record command buffer");
							    }
	        			  	}
	        			  	else System.err.println("("+myID+") Attempt to close an already closed command buffer."); 

	        	            openCmdBuffer.set(false);
	        	            // We should also really go into sleep mode now
	        	            threadState.set(STATE_ENTERING_SLEEP);
	        	            goToSleepMode = true;
	        	            
//	        	            System.out.println("("+myID+") Sleep time "+(sleepTime/1000L)+"us  Run time "+(runTime/1000L)+"us");
	        	            
						    break;
	        		  case CMD_KILL:
	        			  threadState.set(STATE_RUNNING);
	        			  goToSleepMode = false;
	        			  kill = true;
	        			  break;
	        		  case CMD_BUFFER_DATA:
	        			  threadState.set(STATE_RUNNING);
	        			  println("CMD_BUFFER_DATA (index "+index+")");
	        			  println(""+cmdLongArg1.get(index));
	        			  
	        			  
	        			  system.copyBufferFast(cmdbuffer, cmdLongArg1.get(index), cmdLongArg2.get(index), cmdIntArg1.get(index));
	        			  break;
	        		  }
	        		  
	        		  // ======================
	        		  
	        		  
	        		  if (kill) {
	        			  threadState.set(STATE_KILLED);
	        			  // Kills the thread
	        			  break;
	        		  }

	        		  runTime += System.nanoTime()-runbefore;
	        		  // No more tasks to do? Take a lil nap.
	        		  if (goToSleepMode) {
	        			  println("NOW SLEEPING");
        				  long before = System.nanoTime();
	        			  try {
	        				  // Sleep for an indefinite amount of time
	        				  // (we gonna interrupt the thread later)
	        				  threadState.set(STATE_SLEEPING);
	        				  println("State "+threadState.get());
	        				  Thread.sleep(999999);
	        			  }
	        			  catch (InterruptedException e) {
	        				  threadState.set(STATE_WAKING);
	        				  println("WAKEUP");
	        			  }
        				  sleepTime += System.nanoTime()-before;
	        		  }
	        	  }
	        	  threadState.set(STATE_KILLED);
	        	  
	          }
		}
		);
		thread.start();
	}
	int stalltime = 0;
	
	private int getNextCMDIndex() {
		int ret = (cmdindex)%MAX_QUEUE_LENGTH;
		while (cmdID.get(ret) != NO_CMD) {
			// We're forced to wait until the thread has caught up with some of the queue
//			try {
//				Thread.sleep(0);
//			} catch (InterruptedException e) {
//			}
//			println("WARNING  queue clash, cmdid is "+ret);
		}
		cmdindex++;
		return ret;
	}
	
	
	private void wakeThread(int cmdindex) {
		// There's a bug if we just call wakethread after setting cmdIndex.
		// I'm going to copy+paste it here:
		// Let's say we're calling endCommands:
		// - (1) executing cmd
		// - (0) Set cmd id
		// - (1) Oh look! A new command to execute.
		// - (1) Done, let's go to sleep
		// - (0) wakeThread (we're under the assumption that the thread hasn't done the work yet)
		// - (1) Woke up, but wait! There isn't any work for me to do!
		// Solution: Check cmdid is non-zero, because thread sets it to 0 as soon as it executes
		// the command
		if (cmdID.get(cmdindex) == NO_CMD) {
			return;
		}
		
		
		// Only need to interrupt if sleeping.
		// We call it here because if wakeThread is called, then a command was called, and
		// when a command was called, that means we should definitely not be asleep
		// (avoids concurrency issues with await()
		// If it's on STATE_NEXT_CMD, it means that it might have an outdated cmdid, which we can fix
		// by simply interrupting it as soon as it eventually goes into sleep mode
		if (threadState.get() == STATE_ENTERING_SLEEP || threadState.get() == STATE_NEXT_CMD) {
			// Uhoh, unlucky. This means we just gotta wait until we're entering sleep state then wake up.
			while (threadState.get() != STATE_SLEEPING) {
				try {
					Thread.sleep(0, 1000);
				} catch (InterruptedException e) {
				}
			}
			println("INTERRUPT");

			threadState.set(STATE_SLEEPING_INTERRUPTED);
			  println("rrupt State "+threadState.get());
			thread.interrupt();
		}
		if (threadState.get() == STATE_SLEEPING) {
			println("INTERRUPT");

			// We need to set status for only one interrupt otherwise we will keep calling
			// interrupt interrupt interrupt interrupt interrupt interrupt interrupt interrupt 
			// and it seems to be stored in some sort of queue. That means, when the thread tries
			// to go back to sleep, it immediately wakes up because those interrupts are still in
			// the queue. We tell it "it's been interrupted once, don't bother it any further."
			threadState.set(STATE_SLEEPING_INTERRUPTED);
			  println("rrupt State "+threadState.get());
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
		cmdLongArg1.set(index, id);
        cmdIntArg1.set(index, size);
        cmdIntArg2.set(index, first);
        // Remember, last thing we should set is cmdID, set it before and
        // our thread may begin executing drawArrays without all the commands
        // being properly set.
        cmdID.set(index, CMD_DRAW_ARRAYS);
        wakeThread(index);
    }
    
    
    public void bufferData(long srcBuffer, long dstBuffer, int size) {
        int index = getNextCMDIndex();
		println("call CMD_BUFFER_DATA (index "+index+")");
		cmdLongArg1.set(index, srcBuffer);
        cmdLongArg2.set(index, dstBuffer);
        cmdIntArg1.set(index, size);
        // Remember, last thing we should set is cmdID, set it before and
        // our thread may begin executing drawArrays without all the commands
        // being properly set.
        cmdID.set(index, CMD_BUFFER_DATA);
        wakeThread(index);
    }

    
	public void beginRecord(int currentFrame, int currentImage) {
		println("call begin record");
        int index = getNextCMDIndex();
        cmdIntArg1.set(index, currentImage);
        cmdIntArg2.set(index, currentFrame);
        cmdID.set(index, CMD_BEGIN_RECORD);
        
        wakeThread(index);
	}
	
	public void endRecord() {
        int index = getNextCMDIndex();
        cmdID.set(index, CMD_END_RECORD);
		println("call CMD_END_RECORD (index "+index+")");
        // No arguments
        wakeThread(index);
	}

	public void kill() {
		println("kill thread");
        int index = getNextCMDIndex();
        cmdID.set(index, CMD_KILL);
        // No arguments
        wakeThread(index);
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