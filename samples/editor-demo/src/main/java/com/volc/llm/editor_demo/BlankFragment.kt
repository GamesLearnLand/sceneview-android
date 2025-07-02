package com.volc.llm.editor_demo

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.collision.HitResult
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.SphereNode
import io.github.sceneview.utils.motionEventToRay
import dev.romainguy.kotlin.math.normalize
import dev.romainguy.kotlin.math.distance
import kotlinx.coroutines.launch

class BlankFragment : Fragment(R.layout.fragment_blank) {

    private val TAG = "BlankFragment"

    private lateinit var sceneView: SceneView
    private lateinit var loadingView: View
    private var selectedCube: CubeNode? = null
    private var cubeInitialDistance: Float = 0f
    private var isDragging: Boolean = false
    private lateinit var cubeNode: CubeNode

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sceneView = view.findViewById(R.id.sceneView)
        loadingView = view.findViewById(R.id.loadingView)

        viewLifecycleOwner.lifecycleScope.launch {
            setupCoordinateSystem()
            setupInteractiveCube()
            setupTouchHandling()
            loadingView.isGone = true
        }
    }

    private fun setupCoordinateSystem() {
        val engine = sceneView.engine
        val materialLoader = sceneView.materialLoader

        // 原点
        val originPointMaterial =
            materialLoader.createColorInstance(colorOf(0.5f, 0.5f, 0.5f, 1.0f))
        val originPoint = SphereNode(
            engine = engine,
            radius = 0.02f,
            center = Float3(0.0f, 0.0f, 0.0f),
            materialInstance = originPointMaterial
        )

        // 创建X轴（红色）
        val xAxisMaterial = materialLoader.createColorInstance(colorOf(1.0f, 0.0f, 0.0f, 1.0f))
        val xAxis = CylinderNode(
            engine = engine,
            radius = 0.01f,
            height = 2.0f,
            center = Float3(0.0f, 1.0f, 0.0f),
            materialInstance = xAxisMaterial
        )
        // 旋转X轴使其沿X方向：绕Z轴旋转-90度，使圆柱体从默认的Y轴方向朝向X轴方向
        xAxis.rotation = Float3(0.0f, 0.0f, -90.0f)

        // 创建Y轴（绿色）
        val yAxisMaterial = materialLoader.createColorInstance(colorOf(0.0f, 1.0f, 0.0f, 1.0f))
        val yAxis = CylinderNode(
            engine = engine,
            radius = 0.01f,
            height = 2.0f,
            center = Float3(0.0f, 1.0f, 0.0f),
            materialInstance = yAxisMaterial
        )
        // Y轴默认就是垂直的，不需要旋转

        // 创建Z轴（蓝色）
        val zAxisMaterial = materialLoader.createColorInstance(colorOf(0.0f, 0.0f, 1.0f, 1.0f))
        val zAxis = CylinderNode(
            engine = engine,
            radius = 0.01f,
            height = 2.0f,
            center = Float3(0.0f, 1.0f, 0.0f),
            materialInstance = zAxisMaterial
        )
        // 旋转Z轴使其沿Z方向
        zAxis.rotation = Float3(90.0f, 0.0f, 0.0f)

        // 将坐标轴添加到场景中
        sceneView.addChildNode(originPoint)
        sceneView.addChildNode(xAxis)
        sceneView.addChildNode(yAxis)
        sceneView.addChildNode(zAxis)
    }

    private fun setupInteractiveCube() {
        // 创建立方体
        cubeNode = CubeNode(
            engine = sceneView.engine,
            size = Float3(0.1f, 0.1f, 0.1f),
            center = Float3(0.0f, 0.0f, 0.0f),
            materialInstance = sceneView.materialLoader.createColorInstance(
                colorOf(1.0f, 0.5f, 0.0f, 1.0f)// 橙色
            )
        )

        // 设置立方体位置
        cubeNode.worldPosition = Float3(0.5f, 0.5f, 0.5f)
        
        // 使立方体可触摸
        cubeNode.isTouchable = true

        // 将立方体添加到场景中
        sceneView.addChildNode(cubeNode)
    }
    
    private fun setupTouchHandling() {
        sceneView.onTouchEvent = { motionEvent: MotionEvent, hitResult: HitResult? ->
            handleTouchEvent(motionEvent, hitResult)
        }
    }
    
    private fun handleTouchEvent(motionEvent: MotionEvent, hitResult: HitResult?): Boolean {
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                // 检查是否触摸到立方体
                if (hitResult?.node == cubeNode) {
                    selectedCube = cubeNode
                    isDragging = true
                    // 记录立方体当前与相机的距离，用于后续拖拽时保持相同距离
                    val cameraPosition = sceneView.cameraNode.worldPosition
                    val cubePosition = cubeNode.worldPosition
                    cubeInitialDistance = distance(cameraPosition, cubePosition)
                    return true
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && selectedCube != null) {
                    // 从相机位置发出射线到屏幕坐标
                    val ray = sceneView.view.motionEventToRay(motionEvent)
                    val cameraPosition = sceneView.cameraNode.worldPosition
                    
                    // 计算射线方向并归一化
                    val rayDirection = normalize(ray.direction)
                    
                    // 根据初始距离计算新的世界坐标位置
                    val newWorldPosition = cameraPosition + rayDirection * cubeInitialDistance
                    
                    // 更新立方体位置，保持与相机的距离不变
                    selectedCube?.worldPosition = newWorldPosition
                    return true
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    selectedCube = null
                    return true
                }
            }
        }
        return false
    }
}