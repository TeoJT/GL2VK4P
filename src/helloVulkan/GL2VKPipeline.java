package helloVulkan;

import static helloVulkan.ShaderSPIRVUtils.compileShaderFile;
import static helloVulkan.ShaderSPIRVUtils.ShaderKind.FRAGMENT_SHADER;
import static helloVulkan.ShaderSPIRVUtils.ShaderKind.VERTEX_SHADER;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_A_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_B_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_G_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_R_BIT;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_BACK_BIT;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_CLOCKWISE;
import static org.lwjgl.vulkan.VK10.VK_LOGIC_OP_COPY;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL;
import static org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkOffset2D;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkViewport;

import helloVulkan.ShaderSPIRVUtils.SPIRV;


// Buffer bindings in opengl look like this
// buffer 6
// buffer 7
// buffer 8
// Let's say we call
//  bindBuffer(6)
//  vertexAttribPointer(...)
//  bindBuffer(5)
//  vertexAttribPointer(...)
//  bindBuffer(8)
//  vertexAttribPointer(...)
// The question is, what would vulkan's bindings be?
// ...
// Whenever we bind a buffer, we need to add it to a list along
// with the actual buffer so we can later use it to vkcmdBindVertexArrays()
// so
// bindBuffer(6)
// enableVertexArrays()
// vertexAttribPointer(...)
// - vkbuffer[0] = buffer(6)
// - Create VertexAttribsBinding with binding 0
// 
// bindBuffer(5)
// vertexAttribPointer(...)
// - vkbuffer[1] = buffer(5)
// - Create VertexAttribsBinding with binding 1
//
// bindBuffer(8)
// vertexAttribPointer(...)
// - vkbuffer[2] = buffer(8)
// - Create VertexAttribsBinding with binding 2
// 
// We don't care what our vkbindings are, as long as it's in the correct order
// as we're passing the lists in when we call vkCmdBindVertexArrays().

// TODO: Properly fix up compileshaders
// TODO: Turn gl2vkBinding to buffer pointers so that we can use it in vkCmdBindVertexBuffers.
// It's very simple, when you call drawArrays in GL2VK, simply pass a list of buffers[].bufferID
// to the command.
// Maybe not so simple, because challenge to overcome: we need to somehow pass that big ol array
// through atomicLongs.

public class GL2VKPipeline {

	private VulkanSystem system;
	private VKSetup vkbase;
	
	private SPIRV vertShaderSPIRV = null;
	private SPIRV fragShaderSPIRV = null;

    private long pipelineLayout;
    public long graphicsPipeline;
    
	private ShaderAttribInfo attribInfo = null;
	
	private HashMap<Integer, VertexAttribsBinding> gl2vkBinding = new HashMap<Integer, VertexAttribsBinding>();
	private int boundBinding = 0;
	private int totalVertexAttribsBindings = 0;
	
	
	
	public GL2VKPipeline(VulkanSystem system) {
    	this.system = system;
    	this.vkbase = system.vkbase;
	}
	
	// Only use this constructor for testing purposes
	public GL2VKPipeline() {
	}

    private long createShaderModule(ByteBuffer spirvCode) {

        try(MemoryStack stack = stackPush()) {

            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack);

            createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            createInfo.pCode(spirvCode);

            LongBuffer pShaderModule = stack.mallocLong(1);

            if(vkCreateShaderModule(vkbase.device, createInfo, null, pShaderModule) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create shader module");
            }

            return pShaderModule.get(0);
        }
    }
    
    
    
    

    private void createGraphicsPipeline() {

        try(MemoryStack stack = stackPush()) {

            // Let's compile the GLSL shaders into SPIR-V at runtime using the shaderc library
            // Check ShaderSPIRVUtils class to see how it can be done
        	if (vertShaderSPIRV == null || fragShaderSPIRV == null) {
        		throw new RuntimeException("Shaders must be compiled before calling createGraphicsPipeline()");
        	}
            
            
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
            vertexInputInfo.pVertexBindingDescriptions(getBindingDescriptions());
            vertexInputInfo.pVertexAttributeDescriptions(getAttributeDescriptions());

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

            if(vkCreatePipelineLayout(vkbase.device, pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
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
            pipelineInfo.renderPass(system.renderPass);
            pipelineInfo.subpass(0);
            pipelineInfo.basePipelineHandle(VK_NULL_HANDLE);
            pipelineInfo.basePipelineIndex(-1);

            LongBuffer pGraphicsPipeline = stack.mallocLong(1);

            if(vkCreateGraphicsPipelines(vkbase.device, VK_NULL_HANDLE, pipelineInfo, null, pGraphicsPipeline) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create graphics pipeline");
            }

            graphicsPipeline = pGraphicsPipeline.get(0);

            // ===> RELEASE RESOURCES <===

            vkDestroyShaderModule(vkbase.device, vertShaderModule, null);
            vkDestroyShaderModule(vkbase.device, fragShaderModule, null);

            vertShaderSPIRV.free();
            fragShaderSPIRV.free();
        }
    }
    
    public VkVertexInputBindingDescription.Buffer getBindingDescriptions() {
		VkVertexInputBindingDescription.Buffer bindingDescriptions =
		VkVertexInputBindingDescription.calloc(gl2vkBinding.size());
		
		int i = 0;
    	for (VertexAttribsBinding vab : gl2vkBinding.values()) {
    		vab.updateBindingDescription(bindingDescriptions.get(i++));
    	}
    	
    	return bindingDescriptions.rewind();
    }
    
    public VkVertexInputAttributeDescription.Buffer getAttributeDescriptions() {
		VkVertexInputAttributeDescription.Buffer attributeDescriptions =
		VkVertexInputAttributeDescription.calloc(attribInfo.nameToLocation.size());
		
		int i = 0;
    	for (VertexAttribsBinding vab : gl2vkBinding.values()) {
    		vab.updateAttributeDescriptions(attributeDescriptions, i);
    		i += vab.getSize();
    	}
		
		return attributeDescriptions;
    }
    
    // Used when glBindBuffer is called, so that we know to create a new binding
    // for our vertexattribs vulkan pipeline. This should be called in glVertexAttribPointer
    // function.
    public void bind(int glIndex) {
    	boundBinding = glIndex;
    	// Automatically allocate new binding and increate binding count by one.
    	if (!gl2vkBinding.containsKey(boundBinding)) {
        	gl2vkBinding.put(glIndex, new VertexAttribsBinding(totalVertexAttribsBindings++, attribInfo));
    	}
    	// It all flowssss. In a very complicated, spaghetti'd way.
    	// Tell me a better way to do it though.
    }
    
    public void vertexAttribPointer(int location, int size, int offset, int stride) {
    	// Remember, a gl buffer binding of 0 means no bound buffer,
    	// and by default in this class, means bind() hasn't been called.
    	if (boundBinding == 0) {
    		System.err.println("BUG WARNING  vertexAttribPointer called with no bound buffer.");
    		return;
    	}
    	gl2vkBinding.get(boundBinding).vertexAttribPointer(location, size, offset, stride);
    }
    
    // Not actually used but cool to have
    public void vertexAttribPointer(int location) {
    	gl2vkBinding.get(boundBinding).vertexAttribPointer(location);
    }

    
    public void compileVertex(String source) {
//        vertShaderSPIRV = compileShaderFile("resources/shaders/09_shader_base.vert", VERTEX_SHADER);
        attribInfo = new ShaderAttribInfo(source);
    }

    
    public void compileFragment(String source) {
//        fragShaderSPIRV = compileShaderFile("resources/shaders/09_shader_base.frag", FRAGMENT_SHADER);
    }
    
    public void clean() {
        vkDestroyPipeline(system.device, graphicsPipeline, null);
        vkDestroyPipelineLayout(system.device, pipelineLayout, null);
    }
}
