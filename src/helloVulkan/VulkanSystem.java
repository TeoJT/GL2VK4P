package helloVulkan;

import helloVulkan.ShaderSPIRVUtils.SPIRV;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static helloVulkan.ShaderSPIRVUtils.ShaderKind.FRAGMENT_SHADER;
import static helloVulkan.ShaderSPIRVUtils.ShaderKind.VERTEX_SHADER;
import static helloVulkan.ShaderSPIRVUtils.compileShaderFile;
import static helloVulkan.ShaderSPIRVUtils.getVertexAttribPointers;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.Configuration.DEBUG;
import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;
import org.joml.Vector3fc;
import org.joml.Vector3f;
import org.joml.Vector2fc;
import org.joml.Vector2f;




// Poptential useful extensions:

// VK_KHR_dynamic_rendering - 
// Create a single render pass without requiring different render passes
// per pipeline or something

// enableVertexAttribArray is important because it tells opengl what
// vertex buffers to include for drawing with in commands like drawArrays.


public class VulkanSystem {



	public static final int MAX_FRAMES_IN_FLIGHT = 2;

    

    // ======= FIELDS ======= //

    public VkDevice device;
    



    public long renderPass;
    private long pipelineLayout;
    public long graphicsPipeline;

    private List<VkCommandBuffer> commandBuffers;
    public List<Long> swapChainFramebuffers;

    private List<Frame> inFlightFrames;
    private Map<Integer, Frame> imagesInFlight;
    private int currentFrame;

    boolean framebufferResize;
    
    public VKSetup vkbase;

	private VertexAttribsBinding vertexAttribs = null;
    
	private int selectedNode = 0;
	private ThreadNode[] threadNodes = new ThreadNode[8];
    

    // ======= METHODS ======= //

    public void run() {
        initVulkan();
    }



    public void initVulkan() {
    	vkbase = new VKSetup();
    	vkbase.initBase();
    	device = vkbase.device;
    	
        createRenderPass();
        createGraphicsPipeline();
        createFramebuffers();
        createCommandPool();
        createCommandBuffers();
        createSyncObjects();
        createThreadNodes();
    }
    
    public boolean shouldClose() {
        glfwPollEvents();
    	return glfwWindowShouldClose(vkbase.window);
    }

    
    
    public void cleanup() {
    	
    	for (ThreadNode n : threadNodes) {
    		n.kill();
    	}
    	

        cleanupSwapChain();

        inFlightFrames.forEach(frame -> {

            vkDestroySemaphore(device, frame.renderFinishedSemaphore(), null);
            vkDestroySemaphore(device, frame.imageAvailableSemaphore(), null);
            vkDestroyFence(device, frame.fence(), null);
        });
        inFlightFrames.clear();

        vkDestroyCommandPool(device, vkbase.commandPool, null);

        vkDestroyDevice(device, null);

        if(VKSetup.ENABLE_VALIDATION_LAYERS) {
            VKSetup.destroyDebugUtilsMessengerEXT(vkbase.instance, vkbase.debugMessenger, null);
        }

        vkDestroySurfaceKHR(vkbase.instance, vkbase.vkwindow.surface, null);

        vkDestroyInstance(vkbase.instance, null);

        glfwDestroyWindow(vkbase.window);

        glfwTerminate();
    }
    
    
    private void createThreadNodes() {
    	for (int i = 0; i < threadNodes.length; i++) {
    		threadNodes[i] = new ThreadNode(this, i);
    	}
    }
    


    private void createRenderPass() {

        try(MemoryStack stack = stackPush()) {

            VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.calloc(1, stack);
            colorAttachment.format(vkbase.swapChainImageFormat);
            colorAttachment.samples(VK_SAMPLE_COUNT_1_BIT);
            colorAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR);
            colorAttachment.storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            colorAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
            colorAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
            colorAttachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            colorAttachment.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            VkAttachmentReference.Buffer colorAttachmentRef = VkAttachmentReference.calloc(1, stack);
            colorAttachmentRef.attachment(0);
            colorAttachmentRef.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
            subpass.colorAttachmentCount(1);
            subpass.pColorAttachments(colorAttachmentRef);

            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack);
            dependency.srcSubpass(VK_SUBPASS_EXTERNAL);
            dependency.dstSubpass(0);
            dependency.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            dependency.srcAccessMask(0);
            dependency.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            dependency.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack);
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
            renderPassInfo.pAttachments(colorAttachment);
            renderPassInfo.pSubpasses(subpass);
            renderPassInfo.pDependencies(dependency);

            LongBuffer pRenderPass = stack.mallocLong(1);

            if(vkCreateRenderPass(device, renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create render pass");
            }

            renderPass = pRenderPass.get(0);
        }
    }

    private void createGraphicsPipeline() {

        try(MemoryStack stack = stackPush()) {

            // Let's compile the GLSL shaders into SPIR-V at runtime using the shaderc library
            // Check ShaderSPIRVUtils class to see how it can be done
            SPIRV vertShaderSPIRV = compileShaderFile("resources/shaders/09_shader_base.vert", VERTEX_SHADER);
            SPIRV fragShaderSPIRV = compileShaderFile("resources/shaders/09_shader_base.frag", FRAGMENT_SHADER);
            
            vertexAttribs = getVertexAttribPointers("resources/shaders/09_shader_base.vert", 0);
            
            long vertShaderModule = createShaderModule(vertShaderSPIRV.bytecode());
            long fragShaderModule = createShaderModule(fragShaderSPIRV.bytecode());

            ByteBuffer entryPoint = stack.UTF8("main");

            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);

            VkPipelineShaderStageCreateInfo vertShaderStageInfo = shaderStages.get(0);

            vertShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
            vertShaderStageInfo.stage(VK_SHADER_STAGE_VERTEX_BIT);
            vertShaderStageInfo.module(vertShaderModule);
            vertShaderStageInfo.pName(entryPoint);

            VkPipelineShaderStageCreateInfo fragShaderStageInfo = shaderStages.get(1);

            fragShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
            fragShaderStageInfo.stage(VK_SHADER_STAGE_FRAGMENT_BIT);
            fragShaderStageInfo.module(fragShaderModule);
            fragShaderStageInfo.pName(entryPoint);

            // ===> VERTEX STAGE <===

            VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack);
            vertexInputInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
            vertexInputInfo.pVertexBindingDescriptions(vertexAttribs.getBindingDescription(stack));
            vertexInputInfo.pVertexAttributeDescriptions(vertexAttribs.getAttributeDescriptions(stack));

            // ===> ASSEMBLY STAGE <===

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack);
            inputAssembly.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
            inputAssembly.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            inputAssembly.primitiveRestartEnable(false);

            // ===> VIEWPORT & SCISSOR

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.x(0.0f);
            viewport.y(0.0f);
            viewport.width(vkbase.swapChainExtent.width());
            viewport.height(vkbase.swapChainExtent.height());
            viewport.minDepth(0.0f);
            viewport.maxDepth(1.0f);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.offset(VkOffset2D.calloc(stack).set(0, 0));
            scissor.extent(vkbase.swapChainExtent);

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack);
            viewportState.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
            viewportState.pViewports(viewport);
            viewportState.pScissors(scissor);

            // ===> RASTERIZATION STAGE <===

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack);
            rasterizer.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
            rasterizer.depthClampEnable(false);
            rasterizer.rasterizerDiscardEnable(false);
            rasterizer.polygonMode(VK_POLYGON_MODE_FILL);
            rasterizer.lineWidth(1.0f);
            rasterizer.cullMode(VK_CULL_MODE_BACK_BIT);
            rasterizer.frontFace(VK_FRONT_FACE_CLOCKWISE);
            rasterizer.depthBiasEnable(false);

            // ===> MULTISAMPLING <===

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack);
            multisampling.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
            multisampling.sampleShadingEnable(false);
            multisampling.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            // ===> COLOR BLENDING <===

            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
            colorBlendAttachment.colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
            colorBlendAttachment.blendEnable(false);

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack);
            colorBlending.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
            colorBlending.logicOpEnable(false);
            colorBlending.logicOp(VK_LOGIC_OP_COPY);
            colorBlending.pAttachments(colorBlendAttachment);
            colorBlending.blendConstants(stack.floats(0.0f, 0.0f, 0.0f, 0.0f));

            // ===> PIPELINE LAYOUT CREATION <===

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack);
            pipelineLayoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);

            LongBuffer pPipelineLayout = stack.longs(VK_NULL_HANDLE);

            if(vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create pipeline layout");
            }

            pipelineLayout = pPipelineLayout.get(0);

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO);
            pipelineInfo.pStages(shaderStages);
            pipelineInfo.pVertexInputState(vertexInputInfo);
            pipelineInfo.pInputAssemblyState(inputAssembly);
            pipelineInfo.pViewportState(viewportState);
            pipelineInfo.pRasterizationState(rasterizer);
            pipelineInfo.pMultisampleState(multisampling);
            pipelineInfo.pColorBlendState(colorBlending);
            pipelineInfo.layout(pipelineLayout);
            pipelineInfo.renderPass(renderPass);
            pipelineInfo.subpass(0);
            pipelineInfo.basePipelineHandle(VK_NULL_HANDLE);
            pipelineInfo.basePipelineIndex(-1);

            LongBuffer pGraphicsPipeline = stack.mallocLong(1);

            if(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pGraphicsPipeline) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create graphics pipeline");
            }

            graphicsPipeline = pGraphicsPipeline.get(0);

            // ===> RELEASE RESOURCES <===

            vkDestroyShaderModule(device, vertShaderModule, null);
            vkDestroyShaderModule(device, fragShaderModule, null);

            vertShaderSPIRV.free();
            fragShaderSPIRV.free();
        }
    }
    
    private void createSwapChainObjects() {
        vkbase.createSwapChain();
        vkbase.createImageViews();
        createRenderPass();
        createGraphicsPipeline();
        createFramebuffers();
        createCommandBuffers();
    }

    public void recreateSwapChain() {

        try(MemoryStack stack = stackPush()) {

            IntBuffer width = stack.ints(0);
            IntBuffer height = stack.ints(0);

            while(width.get(0) == 0 && height.get(0) == 0) {
                glfwGetFramebufferSize(vkbase.window, width, height);
                glfwWaitEvents();
            }
        }

        vkDeviceWaitIdle(device);

        cleanupSwapChain();

        createSwapChainObjects();
    }

    private void createFramebuffers() {

        swapChainFramebuffers = new ArrayList<>(vkbase.swapChainImageViews.size());

        try(MemoryStack stack = stackPush()) {

            LongBuffer attachments = stack.mallocLong(1);
            LongBuffer pFramebuffer = stack.mallocLong(1);

            // Lets allocate the create info struct once and just update the pAttachments field each iteration
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack);
            framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
            framebufferInfo.renderPass(renderPass);
            framebufferInfo.width(vkbase.swapChainExtent.width());
            framebufferInfo.height(vkbase.swapChainExtent.height());
            framebufferInfo.layers(1);

            for(long imageView : vkbase.swapChainImageViews) {

                attachments.put(0, imageView);

                framebufferInfo.pAttachments(attachments);

                if(vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create framebuffer");
                }

                swapChainFramebuffers.add(pFramebuffer.get(0));
            }
        }
    }



    private void createCommandBuffers() {

        final int commandBuffersCount = swapChainFramebuffers.size();

        commandBuffers = new ArrayList<>(commandBuffersCount);

        try(MemoryStack stack = stackPush()) {

            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(vkbase.commandPool);
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
            allocInfo.commandBufferCount(commandBuffersCount);

            PointerBuffer pCommandBuffers = stack.mallocPointer(commandBuffersCount);

            if(vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffers");
            }

            for(int i = 0;i < commandBuffersCount;i++) {
                commandBuffers.add(new VkCommandBuffer(pCommandBuffers.get(i), device));
            }
        }
    }
    
    

    private void createSyncObjects() {

        inFlightFrames = new ArrayList<>(MAX_FRAMES_IN_FLIGHT);
        imagesInFlight = new HashMap<>(vkbase.swapChainImages.size());

        try(MemoryStack stack = stackPush()) {

            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack);
            semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pImageAvailableSemaphore = stack.mallocLong(1);
            LongBuffer pRenderFinishedSemaphore = stack.mallocLong(1);
            LongBuffer pFence = stack.mallocLong(1);

            for(int i = 0;i < MAX_FRAMES_IN_FLIGHT;i++) {

                if(vkCreateSemaphore(device, semaphoreInfo, null, pImageAvailableSemaphore) != VK_SUCCESS
                || vkCreateSemaphore(device, semaphoreInfo, null, pRenderFinishedSemaphore) != VK_SUCCESS
                || vkCreateFence(device, fenceInfo, null, pFence) != VK_SUCCESS) {

                    throw new RuntimeException("Failed to create synchronization objects for the frame " + i);
                }

                inFlightFrames.add(new Frame(pImageAvailableSemaphore.get(0), pRenderFinishedSemaphore.get(0), pFence.get(0)));
            }

        }
    }
    
    private void createCommandPool() {
    	vkbase.createCommandPoolWithTransfer();
    }
    
    
    
    
    
    
    private VkCommandBuffer currentCommandBuffer = null;
    private IntBuffer currentImageIndex = null;

    public void beginRecord() {
    	// All the stuff that was before recordCommandBuffer()
        try(MemoryStack stack = stackPush()) {
        	
        	// Frames in flight stuff
            Frame thisFrame = inFlightFrames.get(currentFrame);

            vkWaitForFences(device, thisFrame.pFence(), true, Util.UINT64_MAX);

            // Aquire next image.
            currentImageIndex = stack.mallocInt(1);

            int vkResult = vkAcquireNextImageKHR(device, vkbase.swapChain, Util.UINT64_MAX,
                    thisFrame.imageAvailableSemaphore(), VK_NULL_HANDLE, currentImageIndex);

            // Window resizing
            if(vkResult == VK_ERROR_OUT_OF_DATE_KHR) {
                recreateSwapChain();
                return;
            } else if(vkResult != VK_SUCCESS) {
                throw new RuntimeException("Cannot get image");
            }
            
            final int imageIndex = currentImageIndex.get(0);

        	
            // Fence wait for images in flight.
            if(imagesInFlight.containsKey(imageIndex)) {
                vkWaitForFences(device, imagesInFlight.get(imageIndex).fence(), true, Util.UINT64_MAX);
            }

            imagesInFlight.put(imageIndex, thisFrame);

        }
        
        currentCommandBuffer = commandBuffers.get(currentFrame);
    	
    	// Now to the command buffer stuff.
        vkResetCommandBuffer(currentCommandBuffer, 0);
        final int imageIndex = currentImageIndex.get(0);
        
        try(MemoryStack stack = stackPush()) {
            
	        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack);
	        beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
	
	        VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack);
	        renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
	
	        renderPassInfo.renderPass(renderPass);
	
	        VkRect2D renderArea = VkRect2D.calloc(stack);
	        renderArea.offset(VkOffset2D.calloc(stack).set(0, 0));
	        renderArea.extent(vkbase.swapChainExtent);
	        renderPassInfo.renderArea(renderArea);
	
	        VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
	        clearValues.color().float32(stack.floats(0.0f, 0.0f, 0.0f, 1.0f));
	        renderPassInfo.pClearValues(clearValues);

            if(vkBeginCommandBuffer(currentCommandBuffer, beginInfo) != VK_SUCCESS) {
                throw new RuntimeException("Failed to begin recording command buffer");
            }


            renderPassInfo.framebuffer(swapChainFramebuffers.get(imageIndex));

            vkCmdBeginRenderPass(currentCommandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_SECONDARY_COMMAND_BUFFERS);
            
//            vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
        }

        // And then begin our thread nodes (secondary command buffers)
        // TODO: other thread nodes
    	for (ThreadNode n : threadNodes) {
    		n.beginRecord(currentFrame, imageIndex);
    	}
        
    }
    
    
    public void endRecord() {
    	// Before we can end recording, we need to think about our secondary command buffers

    	for (ThreadNode n : threadNodes) {
	    	n.endRecord();
    	}
    	for (ThreadNode n : threadNodes) {
    		n.await();
    	}
    	
    	// TODO: TEST NODE
    	try(MemoryStack stack = stackPush()) {
    		// TODO: avoid garbage collection by making it assign list only once.
	    	List<VkCommandBuffer> cmdbuffers = new ArrayList<>();

	    	for (ThreadNode n : threadNodes) {
	    		cmdbuffers.add(n.getBuffer());
	    	}
	    	
	    	vkCmdExecuteCommands(currentCommandBuffer, Util.asPointerBuffer(stack, cmdbuffers));
    	}
    	
        vkCmdEndRenderPass(currentCommandBuffer);

        if(vkEndCommandBuffer(currentCommandBuffer) != VK_SUCCESS) {
            throw new RuntimeException("Failed to record command buffer");
        }
        
        submitAndPresent();
    }
    
    public void drawArrays(long id, int size, int first) {
//    	System.out.println(selectedNode);
    	threadNodes[selectedNode].drawArrays(id, size, first);
    }

    // NOT thread safe!
    public void drawArraysImpl(long id, int size, int first) {
    	drawArraysImpl(currentCommandBuffer, id, size, first);
    }

    // TODO: take in list of longs
    // This method is thread safe
    public void drawArraysImpl(VkCommandBuffer cmdbuffer, long id, int size, int first) {
        try(MemoryStack stack = stackPush()) {
	        LongBuffer vertexBuffers = stack.longs(id);
	        LongBuffer offsets = stack.longs(0);
	        vkCmdBindVertexBuffers(cmdbuffer, 0, vertexBuffers, offsets);
	
	        vkCmdDraw(cmdbuffer, size, 1, first, 0);
        }
    }
    
    public void selectNode(int node) {
    	selectedNode = node;
    }
    
    public int getNodesCount() {
    	return threadNodes.length;
    }
    
    public void submitAndPresent() {
        try(MemoryStack stack = stackPush()) {

            Frame thisFrame = inFlightFrames.get(currentFrame);
        	
	    	// Queue submission (to be carried out after recording a command buffer)
	        VkSubmitInfo submitInfo = VkSubmitInfo.callocStack(stack);
	        submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
	
	        submitInfo.waitSemaphoreCount(1);
	        submitInfo.pWaitSemaphores(thisFrame.pImageAvailableSemaphore());
	        submitInfo.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT));
	
	        submitInfo.pSignalSemaphores(thisFrame.pRenderFinishedSemaphore());
	
	        submitInfo.pCommandBuffers(stack.pointers(commandBuffers.get(currentFrame)));
	
	        vkResetFences(device, thisFrame.pFence());
	
	        int vkResult = 0;
	        if((vkResult = vkQueueSubmit(vkbase.graphicsQueue, submitInfo, thisFrame.fence())) != VK_SUCCESS) {
	            vkResetFences(device, thisFrame.pFence());
	            throw new RuntimeException("Failed to submit draw command buffer: " + vkResult);
	        }
	
	        // Presenting image from swapchain (to be done after submission)
	        // and also some waiting.
	        VkPresentInfoKHR presentInfo = VkPresentInfoKHR.callocStack(stack);
	        presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
	
	        presentInfo.pWaitSemaphores(thisFrame.pRenderFinishedSemaphore());
	
	        presentInfo.swapchainCount(1);
	        presentInfo.pSwapchains(stack.longs(vkbase.swapChain));
	
	        presentInfo.pImageIndices(currentImageIndex);
	
	        vkResult = vkQueuePresentKHR(vkbase.presentQueue, presentInfo);
	
	        // Window resizing
	        if(vkResult == VK_ERROR_OUT_OF_DATE_KHR || vkResult == VK_SUBOPTIMAL_KHR || framebufferResize) {
	            framebufferResize = false;
	            recreateSwapChain();
	        } else if(vkResult != VK_SUCCESS) {
	            throw new RuntimeException("Failed to present swap chain image");
	        }
	
	        // update current frame.
	        currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
        }
    }
    

    private long createShaderModule(ByteBuffer spirvCode) {

        try(MemoryStack stack = stackPush()) {

            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack);

            createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            createInfo.pCode(spirvCode);

            LongBuffer pShaderModule = stack.mallocLong(1);

            if(vkCreateShaderModule(device, createInfo, null, pShaderModule) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create shader module");
            }

            return pShaderModule.get(0);
        }
    }


    public void cleanupSwapChain() {

        swapChainFramebuffers.forEach(framebuffer -> vkDestroyFramebuffer(device, framebuffer, null));

        try(MemoryStack stack = stackPush()) {vkFreeCommandBuffers(device, vkbase.commandPool, Util.asPointerBuffer(stack, commandBuffers));}

        vkDestroyPipeline(device, graphicsPipeline, null);

        vkDestroyPipelineLayout(device, pipelineLayout, null);

        vkDestroyRenderPass(device, renderPass, null);

        vkbase.swapChainImageViews.forEach(imageView -> vkDestroyImageView(device, imageView, null));

        vkDestroySwapchainKHR(device, vkbase.swapChain, null);
        
    }



}