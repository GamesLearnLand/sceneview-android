package com.volc.llm.editor_demo

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.SphereNode
import kotlinx.coroutines.launch

class BlankFragment : Fragment(R.layout.fragment_blank) {

    private lateinit var sceneView: SceneView
    private lateinit var loadingView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sceneView = view.findViewById(R.id.sceneView)
        loadingView = view.findViewById(R.id.loadingView)

        viewLifecycleOwner.lifecycleScope.launch {
            setupCoordinateSystem()
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
            center = Position(0.0f, 0.0f, 0.0f),
            materialInstance = originPointMaterial
        )

        // 创建X轴（红色）
        val xAxisMaterial = materialLoader.createColorInstance(colorOf(1.0f, 0.0f, 0.0f, 1.0f))
        val xAxis = CylinderNode(
            engine = engine,
            radius = 0.01f,
            height = 2.0f,
            center = Position(0.0f, 1.0f, 0.0f),
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
            center = Position(0.0f, 1.0f, 0.0f),
            materialInstance = yAxisMaterial
        )
        // Y轴默认就是垂直的，不需要旋转

        // 创建Z轴（蓝色）
        val zAxisMaterial = materialLoader.createColorInstance(colorOf(0.0f, 0.0f, 1.0f, 1.0f))
        val zAxis = CylinderNode(
            engine = engine,
            radius = 0.01f,
            height = 2.0f,
            center = Position(0.0f, 1.0f, 0.0f),
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
}