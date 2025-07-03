package io.github.sceneview.utils

import android.os.Looper
import android.view.MotionEvent
import com.google.android.filament.Renderer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Ray
import io.github.sceneview.Entity
import io.github.sceneview.math.Position
import io.github.sceneview.math.toFloat3
import io.github.sceneview.node.Node

/**
 * View扩展工具类 - 3D图形学坐标变换和拾取功能
 *
 * 这个文件包含了3D图形学中的核心概念和算法实现：
 *
 * 1. 坐标系统变换（Coordinate System Transformations）：
 *    - 屏幕坐标系 ↔ 世界坐标系
 *    - 支持Android触摸事件的坐标转换
 *    - 处理不同坐标系之间的Y轴翻转
 *
 * 2. 射线投射（Ray Casting）：
 *    - 从2D屏幕点生成3D射线
 *    - 用于3D对象拾取和碰撞检测
 *    - 支持透视投影的射线计算
 *
 * 3. 拾取系统（Picking System）：
 *    - 基于深度缓冲区的高效拾取
 *    - 异步拾取查询避免阻塞主线程
 *    - 支持多点触控和批量拾取
 *    - 提供Entity和Node两个层次的拾取接口
 *
 * 4. 视口管理（Viewport Management）：
 *    - 视口尺寸计算和坐标标准化
 *    - 支持不同屏幕分辨率的适配
 *
 * 图形学核心概念：
 * - 渲染管线：世界坐标 → 视图坐标 → 投影坐标 → 屏幕坐标
 * - 逆变换：屏幕坐标 → 世界坐标（用于交互）
 * - 深度缓冲区：Z-Buffer用于可见性判断和拾取
 * - 标准化设备坐标（NDC）：设备无关的坐标系统
 */

/**
 * Get a world space position from a screen space position
 * 从屏幕空间位置获取世界空间位置
 *
 * Screen space is in Android device screen coordinates
 * 屏幕空间使用Android设备屏幕坐标系
 *
 * 图形学原理：
 * 这个函数实现了从2D屏幕坐标到3D世界坐标的逆变换。在计算机图形学中，渲染管线通常包含以下变换：
 * 世界坐标 -> 视图坐标 -> 投影坐标 -> 屏幕坐标
 * 此函数执行逆过程：屏幕坐标 -> 视图坐标 -> 世界坐标
 *
 * 坐标系转换说明：
 * - 屏幕坐标系：原点在左上角，X轴向右，Y轴向下
 * - 视图坐标系：原点在左下角，X轴向右，Y轴向上（OpenGL标准）
 * - 世界坐标系：3D空间中的绝对坐标系
 *
 * @param xPx Horizontal screen coordinate in pixels where you want the world position.
 *  水平屏幕坐标（像素），表示你想要获取世界位置的点
 * (0 = left, View Width = right) (0 = 左边, View Width = 右边)
 *
 * The x value is negative when the point is left of the [View.getViewport], between 0 and the width
 * of the [View.getViewport] width when the point is within the viewport, and greater than the width
 * when the point is to the right of the viewport.
 *
 * 当点位于[View.getViewport]左侧时x值为负，在视口内时x值在0到视口宽度之间，在视口右侧时x值大于视口宽度
 *
 *
 * @param yPx Vertical screen coordinate in pixels where you want the world position.
 *  垂直屏幕坐标（像素），表示你想要获取世界位置的点
 * (0 = top, View Height = bottom)(0 = 顶部, View Height = 底部)
 *
 * The y value is negative when the point is above the [View.getViewport], between 0 and the height
 * of the [View.getViewport] height when the point is within the viewport, and greater than the
 * height when the point is below the viewport.
 *
 *  当点位于[View.getViewport]上方时y值为负，在视口内时y值在0到视口高度之间，
 *  在视口下方时y值大于视口高度
 *
 * @param z Z is used for the depth between 1 and 0 深度值，范围在1到0之间
 *  (1 = near, 0 = infinity).(1 = 近平面, 0 = 无穷远)
 *  在透视投影中，z值控制点在摄像机前方的距离
 *
 * @return The world position of the point 该点的世界位置
 */
fun View.screenToWorld(xPx: Float, yPx: Float, z: Float = 1.0f): Position =
    camera!!.viewToWorld(
        viewPosition = Float2(
            x = xPx / viewport.width,
            // Invert Y because screen Y points down and ViewPort Y points up.
            y = 1.0f - (yPx / viewport.height)
        ),
        z = z
    )

/**
 * Get a world space position from a screen space position  从屏幕空间位置获取世界空间位置
 *
 * Screen space is in Android device screen coordinates  屏幕空间使用Android设备屏幕坐标系
 *
 * 图形学原理：
 * 这是screenToWorld函数的便捷重载版本，专门处理Android触摸事件。
 * 它从MotionEvent中提取触摸点的屏幕坐标，然后执行相同的屏幕到世界坐标转换。
 * 这在处理用户交互（如点击、拖拽）时特别有用，可以直接将触摸位置映射到3D场景中的世界坐标。
 *
 * @param motionEvent The motion event where you want the world position. 你想要获取世界位置的触摸事件
 *
 * @return The world position of the point 该点的世界位置
 */
fun View.motionEventToWorld(motionEvent: MotionEvent): Position = screenToWorld(
    xPx = motionEvent.getX(motionEvent.actionIndex),
    yPx = motionEvent.getY(motionEvent.actionIndex)
)

/**
 * Get a screen space position from a world position.
 * 从世界位置获取屏幕空间位置
 *
 * The device coordinate space is unaffected by the orientation of the device.
 * 设备坐标空间不受设备方向影响
 *
 * 图形学原理：
 * 这个函数执行正向的图形渲染管线变换：世界坐标 -> 视图坐标 -> 屏幕坐标
 * 这是screenToWorld的逆操作。在3D图形学中，这种变换被称为"投影变换"，
 * 它将3D世界中的点投影到2D屏幕平面上。
 * 变换过程：
 * 1. 世界坐标通过视图矩阵转换为视图坐标（相对于摄像机）
 * 2. 视图坐标通过投影矩阵转换为标准化设备坐标（NDC）
 * 3. NDC坐标通过视口变换转换为屏幕像素坐标
 *
 * 应用场景：
 * - 在屏幕上显示3D对象的2D标签或UI元素
 * - 计算3D对象在屏幕上的可见性
 * - 实现屏幕空间的碰撞检测
 *
 * @param worldPosition The world position to convert. 要转换的世界位置
 *
 * @return Screen coordinate in pixels where the world position is.
 * (0 = left, View Width = right)
 * (0 = top, View Height = bottom)
 * Screen space is in Android device screen coordinates
 *
 * @return 世界位置在屏幕上的坐标（像素）
 * (0 = 左边, View Width = 右边)
 * (0 = 顶部, View Height = 底部)
 * 屏幕空间使用Android设备屏幕坐标系
 */
fun View.worldToScreen(worldPosition: Position): Float2 =
    camera!!.worldToView(worldPosition).apply {
        x *= viewport.width
        // Invert Y because screen Y points down and ViewPort Y points up.
        y = (1.0f - y) * viewport.height
    }

/**
 * Calculates a ray in world space going from the near-plane of the camera and through a point in
 * view space.
 *
 * 计算从摄像机近平面出发并穿过视图空间中某点的世界空间射线
 *
 * Screen space is in Android device screen coordinates: TopLeft = (0, 0),  BottomRight =
 * (Screen Width, Screen Height).
 * The device coordinate space is unaffected by the orientation of the device.
 *
 * 屏幕空间使用Android设备屏幕坐标：左上角 = (0, 0)，右下角 = (屏幕宽度, 屏幕高度)
 * 设备坐标空间不受设备方向影响
 *
 * 图形学原理：
 * 射线投射（Ray Casting）是3D图形学中的基础技术，用于：
 * 1. 拾取检测（Picking）：确定用户点击了哪个3D对象
 * 2. 碰撞检测：检测射线与3D几何体的交点
 * 3. 光线追踪：模拟光线在场景中的传播
 *
 * 射线的数学表示：
 * Ray(t) = Origin + t * Direction
 * 其中：
 * - Origin：射线起点（摄像机位置）
 * - Direction：射线方向（从摄像机指向屏幕点的单位向量）
 * - t：参数，t >= 0表示射线上的点
 *
 * 这个函数将2D屏幕坐标转换为3D射线，射线从摄像机近平面开始，
 * 穿过对应的屏幕像素点，延伸到无穷远处。
 *
 * @param xPx Horizontal screen coordinate in pixels where you want the world position.
 * (0 = left, View Width = right)
 * The x value is negative when the point is left of the [View.getViewport], between 0 and the width
 * of the [View.getViewport] width when the point is within the viewport, and greater than the width
 * when the point is to the right of the viewport.
 * @param yPx Vertical screen coordinate in pixels where you want the world position.
 * (0 = top, View Height = bottom)
 * The y value is negative when the point is above the [View.getViewport], between 0 and the height
 * of the [View.getViewport] height when the point is within the viewport, and greater than the
 * height when the point is below the viewport.
 *
 * @return A Ray from the camera near to far / infinity
 *
 * @param xPx 水平屏幕坐标（像素），表示你想要获取世界位置的点
 * (0 = 左边, View Width = 右边)
 * 当点位于[View.getViewport]左侧时x值为负，在视口内时x值在0到视口宽度之间，
 * 在视口右侧时x值大于视口宽度
 * @param yPx 垂直屏幕坐标（像素），表示你想要获取世界位置的点
 * (0 = 顶部, View Height = 底部)
 * 当点位于[View.getViewport]上方时y值为负，在视口内时y值在0到视口高度之间，
 * 在视口下方时y值大于视口高度
 *
 * @return 从摄像机近平面到远平面/无穷远的射线
 */
fun View.screenToRay(xPx: Float, yPx: Float): Ray = camera!!.viewToRay(
    Float2(
        x = xPx / viewport.width,
        // Invert Y because screen Y points down and ViewPort Y points up.
        y = 1.0f - (yPx / viewport.height)
    )
)

/**
 * Calculates a ray in world space going from the near-plane of the camera and going through a point
 * in screen space.
 *
 * Screen space is in Android device screen coordinates:
 * TopLeft = (0, 0),  BottomRight = (Screen Width, Screen Height).
 * The device coordinate space is unaffected by the orientation of the device.
 *
 * @param motionEvent The motion event where you want the world position.
 *
 * @return A Ray from the camera to far / infinity
 *
 * 计算从摄像机近平面出发并穿过屏幕空间中某点的世界空间射线
 *
 * 屏幕空间使用Android设备屏幕坐标：
 * 左上角 = (0, 0)，右下角 = (屏幕宽度, 屏幕高度)
 * 设备坐标空间不受设备方向影响
 *
 * 图形学原理：
 * 这是screenToRay函数的便捷重载版本，专门处理Android触摸事件。
 * 在交互式3D应用中，用户通过触摸屏幕来选择或操作3D对象。
 * 这个函数将触摸点转换为3D射线，使得可以进行射线-几何体相交测试，
 * 从而实现精确的3D对象拾取。
 *
 * 应用场景：
 * - 触摸选择3D模型
 * - 拖拽3D对象
 * - 在3D场景中放置新对象
 * - 测量3D空间中的距离
 *
 * @param motionEvent 你想要获取世界位置的触摸事件
 *
 * @return 从摄像机到远平面/无穷远的射线
 */
fun View.motionEventToRay(motionEvent: MotionEvent): Ray =
    screenToRay(xPx = motionEvent.x, yPx = motionEvent.y)

/**
 * Creates a picking query. Multiple queries can be created (e.g.: multi-touch).
 *
 * Picking queries are all executed when [Renderer.render] is called on this View.
 * The provided callback is guaranteed to be called at some point in the future.
 *
 * Typically it takes a couple frames to receive the result of a picking query.
 *
 * @param xPx Horizontal screen coordinate in pixels where you want to pick.
 * (0 = left, View Width = right)
 * The x value is negative when the point is left of the [View.getViewport], between 0 and the width
 * of the [View.getViewport] width when the point is within the viewport, and greater than the width
 * when the point is to the right of the viewport.
 * @param yPx Vertical screen coordinate in pixels where you want to pick.
 * (0 = top, View Height = bottom)
 * The y value is negative when the point is above the [View.getViewport], between 0 and the height
 * of the [View.getViewport] height when the point is within the viewport, and greater than the
 * height when the point is below the viewport.
 * @param handler An [java.util.concurrent.Executor].
 * On Android this can also be a [android.os.Handler].
 * @param onCompleted User callback executed by `handler` when the picking query result is
 * available.
 *
 * 创建拾取查询。可以创建多个查询（例如：多点触控）
 *
 * 当在此View上调用[Renderer.render]时，所有拾取查询都会被执行。
 * 保证提供的回调会在将来的某个时刻被调用。
 *
 * 通常需要几帧才能收到拾取查询的结果。
 *
 * 图形学原理：
 * 拾取（Picking）是3D图形学中的重要技术，用于确定用户在屏幕上点击的位置对应的3D对象。
 * 拾取过程通常包括以下步骤：
 *
 * 1. 射线投射法（Ray Casting）：
 *    - 从摄像机位置发射一条射线，穿过屏幕上的点击位置
 *    - 计算射线与场景中所有几何体的交点
 *    - 选择距离摄像机最近的交点
 *
 * 2. 深度缓冲区查询（Depth Buffer Query）：
 *    - 直接查询渲染后的深度缓冲区（Z-Buffer）
 *    - 获取指定像素位置的深度值和对象ID
 *    - 这种方法更高效，因为利用了GPU已经计算好的深度信息
 *
 * 3. 异步处理：
 *    - 拾取查询通常是异步的，因为需要等待GPU完成渲染
 *    - 结果在几帧后通过回调返回
 *    - 这避免了阻塞主线程，保持应用的流畅性
 *
 * @param xPx 你想要拾取的水平屏幕坐标（像素）
 * (0 = 左边, View Width = 右边)
 * 当点位于[View.getViewport]左侧时x值为负，在视口内时x值在0到视口宽度之间，
 * 在视口右侧时x值大于视口宽度
 * @param yPx 你想要拾取的垂直屏幕坐标（像素）
 * (0 = 顶部, View Height = 底部)
 * 当点位于[View.getViewport]上方时y值为负，在视口内时y值在0到视口高度之间，
 * 在视口下方时y值大于视口高度
 * @param handler 一个[java.util.concurrent.Executor]
 * 在Android上也可以是[android.os.Handler]
 * @param onCompleted 当拾取查询结果可用时由`handler`执行的用户回调
 */
fun View.pick(
    xPx: Float,
    yPx: Float,
    handler: Any = Looper.getMainLooper(),
    onCompleted: (
        /** The Renderable Entity at the picking query location  */
        renderable: Entity,
        /** The value of the depth buffer at the picking query location  */
        depth: Float,
        /** The fragment coordinate in GL convention at the picking query location  */
        fragCoords: Float3
    ) -> Unit
) =
// Wrap in a try/catch to handle the case where we receive a pick event as the screen is
// being swiped to emulate a back button press. In that case, the view will be destroyed but
// there is still a possibility of this method being called after the fact but before the
// sceneview is fully destroyed which would trigger an IllegalStateException and crash the app
    runCatching {
        pick(
            xPx.toInt(),
            // Invert the y coordinate since its origin must be at the bottom
            (viewport.height - yPx).toInt(),
            handler
        ) { result ->
            onCompleted(
                result.renderable,
                result.depth,
                result.fragCoords.toFloat3()
            )
        }
    }

/**
 * Creates a picking query. Multiple queries can be created (e.g.: multi-touch).
 *
 * Picking queries are all executed when [Renderer.render] is called on this View.
 * The provided callback is guaranteed to be called at some point in the future.
 *
 * Typically it takes a couple frames to receive the result of a picking query.
 *
 * @param xPx Horizontal screen coordinate in pixels where you want to pick.
 * (0 = left, View Width = right)
 * The x value is negative when the point is left of the [View.getViewport], between 0 and the width
 * of the [View.getViewport] width when the point is within the viewport, and greater than the width
 * when the point is to the right of the viewport.
 * @param yPx Vertical screen coordinate in pixels where you want to pick.
 * (0 = top, View Height = bottom)
 * The y value is negative when the point is above the [View.getViewport], between 0 and the height
 * of the [View.getViewport] height when the point is within the viewport, and greater than the
 * height when the point is below the viewport.
 * @param nodes List of nodes you want the pick to be made on.
 * @param handler An [java.util.concurrent.Executor].
 * On Android this can also be a [android.os.Handler].
 * @param onCompleted User callback executed by `handler` when the picking query result is
 * available.
 *
 * 创建拾取查询。可以创建多个查询（例如：多点触控）
 *
 * 当在此View上调用[Renderer.render]时，所有拾取查询都会被执行。
 * 保证提供的回调会在将来的某个时刻被调用。
 *
 * 通常需要几帧才能收到拾取查询的结果。
 *
 * 图形学原理：
 * 这是针对特定节点列表的拾取查询版本。与通用拾取不同，这个函数：
 * 1. 首先执行标准的拾取查询获取渲染实体
 * 2. 然后在提供的节点列表中查找匹配的节点
 * 3. 返回匹配的Node对象而不是底层的Entity
 *
 * 这种方法的优势：
 * - 提供了更高级的抽象，直接返回应用层的Node对象
 * - 可以限制拾取范围到特定的节点集合
 * - 避免了手动将Entity映射回Node的复杂性
 * - 支持场景图（Scene Graph）中的层次化拾取
 *
 * @param xPx 你想要拾取的水平屏幕坐标（像素）
 * (0 = 左边, View Width = 右边)
 * 当点位于[View.getViewport]左侧时x值为负，在视口内时x值在0到视口宽度之间，
 * 在视口右侧时x值大于视口宽度
 * @param yPx 你想要拾取的垂直屏幕坐标（像素）
 * (0 = 顶部, View Height = 底部)
 * 当点位于[View.getViewport]上方时y值为负，在视口内时y值在0到视口高度之间，
 * 在视口下方时y值大于视口高度
 * @param nodes 你想要进行拾取的节点列表
 * @param handler 一个[java.util.concurrent.Executor]
 * 在Android上也可以是[android.os.Handler]
 * @param onCompleted 当拾取查询结果可用时由`handler`执行的用户回调
 */
fun View.pickNode(
    xPx: Float,
    yPx: Float,
    nodes: List<Node>,
    handler: Any = Looper.getMainLooper(),
    onCompleted: (
        /** The Renderable Node at the picking query location  */
        node: Node?,
        /** The value of the depth buffer at the picking query location  */
        depth: Float,
        /** The fragment coordinate in GL convention at the picking query location  */
        fragCoords: Float3
    ) -> Unit
) = pick(xPx = xPx, yPx = yPx, handler = handler) { renderable, depth, fragCoords ->
    onCompleted(nodes.firstOrNull { it.entity == renderable }, depth, fragCoords)
}

/**
 * Creates a picking query. Multiple queries can be created (e.g.: multi-touch).
 *
 * Picking queries are all executed when [Renderer.render] is called on this View.
 * The provided callback is guaranteed to be called at some point in the future.
 *
 * Typically it takes a couple frames to receive the result of a picking query.
 *
 * @param motionEvent The motion event where you want to pick.
 * @param nodes List of nodes you want the pick to be made on.
 * @param handler An [java.util.concurrent.Executor].
 * On Android this can also be a [android.os.Handler].
 * @param onCompleted User callback executed by `handler` when the picking query result is
 * available.
 *
 * 创建拾取查询。可以创建多个查询（例如：多点触控）
 *
 * 当在此View上调用[Renderer.render]时，所有拾取查询都会被执行。
 * 保证提供的回调会在将来的某个时刻被调用。
 *
 * 通常需要几帧才能收到拾取查询的结果。
 *
 * 图形学原理：
 * 这是pickNode函数的便捷重载版本，专门处理Android触摸事件。
 * 它结合了触摸事件处理和3D拾取技术，使得在移动设备上实现3D交互变得简单。
 *
 * 在移动3D应用中的典型用法：
 * - 触摸选择3D模型进行编辑
 * - 实现3D对象的拖拽操作
 * - 在AR应用中选择虚拟对象
 * - 3D游戏中的对象交互
 *
 * @param motionEvent 你想要进行拾取的触摸事件
 * @param nodes 你想要进行拾取的节点列表
 * @param handler 一个[java.util.concurrent.Executor]
 * 在Android上也可以是[android.os.Handler]
 * @param onCompleted 当拾取查询结果可用时由`handler`执行的用户回调
 */
fun View.pickNode(
    motionEvent: MotionEvent,
    nodes: List<Node>,
    handler: Any = Looper.getMainLooper(),
    onCompleted: (
        /** The Renderable Node at the picking query location  */
        node: Node?,
        /** The value of the depth buffer at the picking query location  */
        depth: Float,
        /** The fragment coordinate in GL convention at the picking query location  */
        fragCoords: Float3
    ) -> Unit
) = pickNode(motionEvent.x, motionEvent.y, nodes, handler, onCompleted)

/**
 * Creates a picking query. Multiple queries can be created (e.g.: multi-touch).
 *
 * Picking queries are all executed when [Renderer.render] is called on this View.
 * The provided callback is guaranteed to be called at some point in the future.
 *
 * Typically it takes a couple frames to receive the result of a picking query.
 *
 * @param viewPosition normalized view coordinate
 * x = (0 = left, 0.5 = center, 1 = right)
 * y = (0 = bottom, 0.5 = center, 1 = top)
 * @param nodes List of nodes you want the pick to be made on.
 * @param handler An [java.util.concurrent.Executor].
 * On Android this can also be a [android.os.Handler].
 * @param onCompleted User callback executed by `handler` when the picking query result is
 * available.
 *
 * 创建拾取查询。可以创建多个查询（例如：多点触控）
 *
 * 当在此View上调用[Renderer.render]时，所有拾取查询都会被执行。
 * 保证提供的回调会在将来的某个时刻被调用。
 *
 * 通常需要几帧才能收到拾取查询的结果。
 *
 * 图形学原理：
 * 这个版本使用标准化视图坐标（Normalized View Coordinates），这是计算机图形学中的标准坐标系统。
 * 标准化坐标的优势：
 * 1. 设备无关性：不依赖于具体的屏幕分辨率
 * 2. 数学简洁性：坐标范围固定在[0,1]之间
 * 3. 易于计算：便于进行比例计算和插值
 *
 * 坐标系说明：
 * - (0, 0) = 左下角（遵循OpenGL标准）
 * - (1, 1) = 右上角
 * - (0.5, 0.5) = 视图中心
 *
 * 这种坐标系统在以下场景中特别有用：
 * - 程序化拾取（非用户交互）
 * - 自动化测试
 * - 基于百分比的UI布局
 * - 跨设备的一致性交互
 *
 * @param viewPosition 标准化视图坐标
 * x = (0 = 左边, 0.5 = 中心, 1 = 右边)
 * y = (0 = 底部, 0.5 = 中心, 1 = 顶部)
 * @param nodes 你想要进行拾取的节点列表
 * @param handler 一个[java.util.concurrent.Executor]
 * 在Android上也可以是[android.os.Handler]
 * @param onCompleted 当拾取查询结果可用时由`handler`执行的用户回调
 */
fun View.pickNode(
    viewPosition: Float2,
    nodes: List<Node>,
    handler: Any = Looper.getMainLooper(),
    onCompleted: (
        /** The Renderable Node at the picking query location  */
        node: Node?,
        /** The value of the depth buffer at the picking query location  */
        depth: Float,
        /** The fragment coordinate in GL convention at the picking query location  */
        fragCoords: Float3
    ) -> Unit
) = pickNode(
    xPx = viewPosition.x * viewport.width,
    // Invert Y because screen Y points down and Filament Y points up.
    yPx = (1.0f - viewPosition.y) * viewport.height,
    nodes = nodes,
    handler = handler,
    onCompleted = onCompleted
)

/**
 * 获取视口的尺寸作为Float2向量
 *
 * 图形学原理：
 * 视口（Viewport）是3D图形渲染管线中的重要概念，它定义了3D场景在屏幕上的渲染区域。
 * 视口变换是渲染管线的最后一步，将标准化设备坐标（NDC）转换为屏幕坐标。
 *
 * 视口变换公式：
 * screen_x = (ndc_x + 1) * viewport_width / 2 + viewport_x
 * screen_y = (ndc_y + 1) * viewport_height / 2 + viewport_y
 *
 * 这个扩展属性提供了便捷的方式来获取视口尺寸，常用于：
 * - 计算屏幕坐标和标准化坐标之间的转换
 * - 确定渲染区域的宽高比
 * - 进行基于视口大小的UI布局计算
 * - 实现自适应的3D场景渲染
 *
 * @return 包含视口宽度和高度的Float2向量
 */
val Viewport.size get() = Float2(width.toFloat(), height.toFloat())
