package io.github.sceneview.sample.modeltest

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.view.MotionEvent
import io.github.sceneview.SceneView
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.sample.modeltest.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import io.github.sceneview.utils.screenToRay

private const val TAG = "MainActivity2"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    private lateinit var sceneView: SceneView
    
    // 球的位置坐标常量
    private val centerPosition = Position(0f, 0f, -50f)
    private val leftBottomFrontPosition = Position(-40f, -40f, -10f)
    private val rightBottomFrontPosition = Position(40f, -40f, -10f)
    private val leftTopFrontPosition = Position(-40f, 40f, -10f)
    private val rightTopFrontPosition = Position(40f, 40f, -10f)
    private val leftBottomBackPosition = Position(-40f, -40f, -90f)
    private val rightBottomBackPosition = Position(40f, -40f, -90f)
    private val leftTopBackPosition = Position(-40f, 40f, -90f)
    private val rightTopBackPosition = Position(40f, 40f, -90f)

    // 为球添加拖动手势支持 - 在相机投影平面上移动
    private fun setupBallDragGesture(ballNode: ModelNode) {
        // 设置触摸和编辑属性
        ballNode.isTouchable = true
        ballNode.isEditable = true
        ballNode.isPositionEditable = true
        
        // 为小球创建碰撞检测形状
        ballNode.collisionShape = io.github.sceneview.collision.Sphere(1.5f)
        
        var isDragging = false
        var initialDepth = 0f
        
        // 在onTouch中直接处理拖动逻辑
        ballNode.onTouch = { e, hitResult ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    Log.d(TAG, "Drag started for ${ballNode.name}")
                    isDragging = true
                    // 记录球距离相机的初始深度
                    val cameraPosition = sceneView.cameraNode.worldPosition
                    val cameraForward = sceneView.cameraNode.forwardDirection
                    val ballPosition = ballNode.worldPosition
                    val cameraToball = ballPosition - cameraPosition
                    // 计算dot product: cameraToball . cameraForward
                    initialDepth = cameraToball.x * cameraForward.x + 
                                  cameraToball.y * cameraForward.y + 
                                  cameraToball.z * cameraForward.z
                    Log.d(TAG, "Initial depth: $initialDepth")
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        Log.d(TAG, "Dragging ${ballNode.name} to screen pos: (${e.x}, ${e.y})")
                        // 将屏幕坐标转换为射线
                        val ray = sceneView.view.screenToRay(e.x, e.y)
                        val cameraPosition = sceneView.cameraNode.worldPosition
                        val cameraForward = sceneView.cameraNode.forwardDirection
                        
                        // 计算射线与初始深度平面的交点
                        val planeOrigin = cameraPosition + cameraForward * initialDepth
                        val denominator = ray.direction.x * cameraForward.x + 
                                         ray.direction.y * cameraForward.y + 
                                         ray.direction.z * cameraForward.z
                        
                        if (kotlin.math.abs(denominator) > 1e-6f) {
                            val diff = planeOrigin - ray.origin
                            val numerator = diff.x * cameraForward.x + 
                                           diff.y * cameraForward.y + 
                                           diff.z * cameraForward.z
                            val t = numerator / denominator
                            if (t >= 0) {
                                val newPosition = ray.origin + ray.direction * t
                                ballNode.worldPosition = newPosition
                                Log.d(TAG, "Ball moved to: $newPosition")
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    Log.d(TAG, "Drag ended for ${ballNode.name}")
                    isDragging = false
                }
            }
            true // 返回true阻止事件传播到相机手势处理器
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()

        sceneView = binding.sceneView

        lifecycleScope.launch {
            
            val cameraPosition = Position(x = 0f, y = 0f, z = 150f)
            val targetPosition = Position(x = 0f, y = 0f, z = -50f)

            sceneView.cameraManipulator = CameraGestureDetector.DefaultCameraManipulator(
                orbitHomePosition = cameraPosition,
                targetPosition = targetPosition
            )

            sceneView.cameraNode.apply {
                position = cameraPosition
                lookAt(targetPosition)
            }

            val boxModelInstance = sceneView.modelLoader.createModelInstance("models/box.glb")
            val boxModelNode = ModelNode(
                modelInstance = boxModelInstance,
            )
            boxModelNode.isTouchable = false
            sceneView.addChildNode(boxModelNode)

            
            // 中心球
            val ballModelInstance1 = sceneView.modelLoader.createModelInstance("models/ball.glb")
            val ballModelNode1 = ModelNode(
                modelInstance = ballModelInstance1,
            )
            ballModelNode1.scale = Scale(4f)
            ballModelNode1.position = centerPosition
            ballModelNode1.name = "CenterBall"
            sceneView.addChildNode(ballModelNode1)
            setupBallDragGesture(ballModelNode1)
            
            // 调试信息
            Log.d(TAG, "Center ball setup complete:")
            Log.d(TAG, "  - Position: ${ballModelNode1.worldPosition}")
            Log.d(TAG, "  - Touchable: ${ballModelNode1.isTouchable}")
            Log.d(TAG, "  - Editable: ${ballModelNode1.isEditable}")
            Log.d(TAG, "  - Position editable: ${ballModelNode1.isPositionEditable}")
            Log.d(TAG, "  - Collision shape: ${ballModelNode1.collisionShape}")
            Log.d(TAG, "  - Scale: ${ballModelNode1.scale}")

            // 左下前角球
            val ballModelInstance2 = sceneView.modelLoader.createModelInstance("models/ball.glb")
            val ballModelNode2 = ModelNode(
                modelInstance = ballModelInstance2,
            )
            ballModelNode2.scale = Scale(4f)
            ballModelNode2.position = leftBottomFrontPosition
            sceneView.addChildNode(ballModelNode2)
            setupBallDragGesture(ballModelNode2)

            // 右下前角球
            val ballModelInstance3 = sceneView.modelLoader.createModelInstance("models/ball.glb")
            val ballModelNode3 = ModelNode(
                modelInstance = ballModelInstance3,
            )
            ballModelNode3.scale = Scale(4f)
            ballModelNode3.position = rightBottomFrontPosition
            sceneView.addChildNode(ballModelNode3)
            setupBallDragGesture(ballModelNode3)

            // 左上前角球
            val ballModelInstance4 = sceneView.modelLoader.createModelInstance("models/ball.glb")
            val ballModelNode4 = ModelNode(
                modelInstance = ballModelInstance4,
            )
            ballModelNode4.scale = Scale(4f)
            ballModelNode4.position = leftTopFrontPosition
            sceneView.addChildNode(ballModelNode4)
            setupBallDragGesture(ballModelNode4)

            // 右上前角球
            val ballModelInstance5 = sceneView.modelLoader.createModelInstance("models/ball.glb")
            val ballModelNode5 = ModelNode(
                modelInstance = ballModelInstance5,
            )
            ballModelNode5.scale = Scale(4f)
            ballModelNode5.position = rightTopFrontPosition
            sceneView.addChildNode(ballModelNode5)
            setupBallDragGesture(ballModelNode5)

            // 左下后角球
            val ballModelInstance6 = sceneView.modelLoader.createModelInstance("models/ball.glb")
            val ballModelNode6 = ModelNode(
                modelInstance = ballModelInstance6,
            )
            ballModelNode6.scale = Scale(4f)
            ballModelNode6.position = leftBottomBackPosition
            sceneView.addChildNode(ballModelNode6)
            setupBallDragGesture(ballModelNode6)

            // 右下后角球
            val ballModelInstance7 = sceneView.modelLoader.createModelInstance("models/ball.glb")
            val ballModelNode7 = ModelNode(
                modelInstance = ballModelInstance7,
            )
            ballModelNode7.scale = Scale(4f)
            ballModelNode7.position = rightBottomBackPosition
            sceneView.addChildNode(ballModelNode7)
            setupBallDragGesture(ballModelNode7)

            // 左上后角球
            val ballModelInstance8 = sceneView.modelLoader.createModelInstance("models/ball.glb")
            val ballModelNode8 = ModelNode(
                modelInstance = ballModelInstance8,
            )
            ballModelNode8.scale = Scale(4f)
            ballModelNode8.position = leftTopBackPosition
            sceneView.addChildNode(ballModelNode8)
            setupBallDragGesture(ballModelNode8)

            // 右上后角球
            val ballModelInstance9 = sceneView.modelLoader.createModelInstance("models/ball.glb")
            val ballModelNode9 = ModelNode(
                modelInstance = ballModelInstance9,
            )
            ballModelNode9.scale = Scale(4f)
            ballModelNode9.position = rightTopBackPosition
            sceneView.addChildNode(ballModelNode9)
            setupBallDragGesture(ballModelNode9)
            
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}