package io.github.sceneview.node

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.GestureDetector
import android.view.GestureDetector.OnContextClickListener
import android.view.GestureDetector.OnDoubleTapListener
import android.view.MotionEvent
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Scene
import com.google.android.filament.TransformManager
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.degrees
import dev.romainguy.kotlin.math.inverse
import dev.romainguy.kotlin.math.lookAt
import dev.romainguy.kotlin.math.lookTowards
import io.github.sceneview.Entity
import io.github.sceneview.EntityInstance
import io.github.sceneview.FilamentEntity
import io.github.sceneview.SceneView
import io.github.sceneview.animation.NodeAnimator
import io.github.sceneview.collision.Collider
import io.github.sceneview.collision.CollisionShape
import io.github.sceneview.collision.CollisionSystem
import io.github.sceneview.collision.HitResult
import io.github.sceneview.collision.Matrix
import io.github.sceneview.collision.TransformProvider
import io.github.sceneview.gesture.MoveGestureDetector
import io.github.sceneview.gesture.RotateGestureDetector
import io.github.sceneview.gesture.ScaleGestureDetector
import io.github.sceneview.managers.getParentOrNull
import io.github.sceneview.managers.getTransform
import io.github.sceneview.managers.getWorldTransform
import io.github.sceneview.managers.setTransform
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Transform
import io.github.sceneview.math.equals
import io.github.sceneview.math.quaternion
import io.github.sceneview.math.slerp
import io.github.sceneview.math.times
import io.github.sceneview.math.toMatrix
import io.github.sceneview.math.toQuaternion
import io.github.sceneview.safeDestroyEntity
import io.github.sceneview.safeDestroyTransformable
import io.github.sceneview.utils.intervalSeconds
import kotlin.reflect.KProperty1

/**
 * A Node represents a transformation within the scene graph's hierarchy.
 * Node 表示场景图层次结构中的一个变换节点。
 *
 * It can contain a renderable for the rendering engine to render.
 * 它可以包含一个可渲染对象供渲染引擎进行渲染。
 *
 * Each node can have an arbitrary number of child nodes and one parent. The parent may be
 * another node, or the scene.
 * 每个节点可以有任意数量的子节点和一个父节点。父节点可以是另一个节点或场景本身。
 *
 * 图形学原理：
 * 场景图（Scene Graph）是计算机图形学中用于组织和管理3D场景对象的树状数据结构。
 * 每个节点代表一个变换矩阵，子节点的变换是相对于父节点的局部变换。
 * 最终的世界变换是从根节点到当前节点路径上所有变换矩阵的乘积。
 * 这种层次结构使得复杂场景的管理变得简单，支持对象的组合、继承变换等特性。
 *
 * 坐标系说明（右手坐标系）：
 * ------- +y ----- -z
 *
 * ---------|----/----
 *
 * ---------|--/------
 *
 * -x - - - 0 - - - +x
 *
 * ------/--|---------
 *
 * ----/----|---------
 *
 * +z ---- -y --------
 */
open class Node(
    val engine: Engine,
    @FilamentEntity val entity: Entity = EntityManager.get().create(),
) : GestureDetector.OnGestureListener,
    OnDoubleTapListener,
    OnContextClickListener,
    MoveGestureDetector.OnMoveListener,
    RotateGestureDetector.OnRotateListener,
    ScaleGestureDetector.OnScaleListener,
    TransformProvider {

    var isHittable: Boolean = true

    /**
     * ### Define your own custom name
     * ### 定义自定义名称
     */
    open var name: String? = null

    /**
     * The node can be selected when a touch event happened.
     * 当触摸事件发生时，节点是否可以被选中。
     *
     * If a not touchable child [Node] is touched, we check the parent hierarchy to find the
     * closest touchable parent. In this case, the first selectable parent will be the one to have
     * its [isTouchable] value to `true`.
     * 如果触摸了一个不可触摸的子节点，我们会检查父节点层次结构以找到最近的可触摸父节点。
     * 在这种情况下，第一个可选择的父节点将是其 [isTouchable] 值为 `true` 的节点。
     */
    open var isTouchable: Boolean = true
    open var isEditable: Boolean = false
    open var isPositionEditable: Boolean = false
        get() = isEditable && field
    open var isRotationEditable: Boolean = true
        get() = isEditable && field
    open var isScaleEditable: Boolean = true
        get() = isEditable && field

    var editableScaleRange = 0.1f..10.0f

    /**
     * The visible state of this node.
     * 此节点的可见状态。
     *
     * Note that a Node may be visible but still not rendered if its parent is not visible or if it
     * isn't part of the scene.
     * 注意：如果父节点不可见或节点不是场景的一部分，即使节点可见也可能不会被渲染。
     */
    open var isVisible = true
        get() = field && parent?.isVisible != false
        set(value) {
            if (field != value) {
                field = value
                updateVisibility()
            }
        }

    /**
     * 是否启用平滑变换。
     * 图形学原理：平滑变换通过插值算法（如线性插值LERP或球面线性插值SLERP）
     * 在多个帧之间平滑地过渡变换状态，避免突兀的跳跃，提供更好的视觉体验。
     */
    var isSmoothTransformEnabled = false

    /**
     * The smooth position, rotation and scale speed.
     * 平滑位置、旋转和缩放的速度。
     *
     * This value is used by [smoothTransform]
     * 此值由 [smoothTransform] 使用
     */
    var smoothTransformSpeed = 5.0f

    /**
     * Position to locate within the coordinate system the parent.
     * 在父坐标系中定位的位置。
     *
     * Default is `Position(x = 0.0f, y = 0.0f, z = 0.0f)`, indicating that the component is placed
     * at the origin of the parent component's coordinate system.
     * 默认值为 `Position(x = 0.0f, y = 0.0f, z = 0.0f)`，表示组件位于父组件坐标系的原点。
     *
     * **Horizontal (X):**
     * **水平方向 (X)：**
     * - left: x < 0.0f
     * - 左侧: x < 0.0f
     * - center horizontal: x = 0.0f
     * - 水平居中: x = 0.0f
     * - right: x > 0.0f
     * - 右侧: x > 0.0f
     *
     * **Vertical (Y):**
     * **垂直方向 (Y)：**
     * - top: y > 0.0f
     * - 顶部: y > 0.0f
     * - center vertical : y = 0.0f
     * - 垂直居中: y = 0.0f
     * - bottom: y < 0.0f
     * - 底部: y < 0.0f
     *
     * **Depth (Z):**
     * **深度方向 (Z)：**
     * - forward: z < 0.0f
     * - 向前: z < 0.0f
     * - origin/camera position: z = 0.0f
     * - 原点/相机位置: z = 0.0f
     * - backward: z > 0.0f
     * - 向后: z > 0.0f
     *
     * 图形学原理：
     * 位置变换是仿射变换的一种，通过平移矩阵实现。在齐次坐标系中，
     * 平移变换矩阵为：
     * [1 0 0 tx]
     * [0 1 0 ty]
     * [0 0 1 tz]
     * [0 0 0 1 ]
     * 其中 (tx, ty, tz) 为平移向量。
     *
     * ------- +y ----- -z
     *
     * ---------|----/----
     *
     * ---------|--/------
     *
     * -x - - - 0 - - - +x
     *
     * ------/--|---------
     *
     * ----/----|---------
     *
     * +z ---- -y --------
     *
     * @see transform
     */
    open var position: Position
        get() = transform.position
        set(value) {
            transform = Transform(value, quaternion, scale)
        }

    /**
     * World-space position.
     * 世界空间位置。
     *
     * The world position of this component (i.e. relative to the [SceneView]).
     * This is the composition of this component's local position with its parent's world position.
     * 此组件的世界位置（即相对于 [SceneView]）。
     * 这是此组件的局部位置与其父节点世界位置的组合。
     *
     * 图形学原理：
     * 世界位置通过变换矩阵链的乘积计算得出：
     * WorldPosition = ParentWorldMatrix * LocalPosition
     * 这种层次变换允许复杂的对象组合和相对定位。
     *
     * @see worldTransform
     */
    open var worldPosition: Position
        get() = worldTransform.position
        set(value) {
            position = parent?.getLocalPosition(value) ?: value
        }

    /**
     * Quaternion rotation.
     * 四元数旋转。
     *
     * 图形学原理：
     * 四元数是表示3D旋转的数学工具，由一个标量部分w和一个向量部分(x,y,z)组成。
     * 四元数避免了欧拉角的万向锁问题，提供了平滑的旋转插值。
     * 四元数乘法对应旋转的组合，且满足结合律。
     * 单位四元数 q = w + xi + yj + zk，其中 w² + x² + y² + z² = 1
     *
     * @see transform
     */
    open var quaternion: Quaternion
        get() = transform.quaternion
        set(value) {
            transform = Transform(position, value, scale)
        }

    /**
     * The world-space quaternion.
     * 世界空间四元数。
     *
     * The world quaternion of this component (i.e. relative to the [SceneView]).
     * This is the composition of this component's local quaternion with its parent's world
     * quaternion.
     * 此组件的世界四元数（即相对于 [SceneView]）。
     * 这是此组件的局部四元数与其父节点世界四元数的组合。
     *
     * 图形学原理：
     * 世界旋转通过四元数乘法链计算：
     * WorldQuaternion = ParentWorldQuaternion * LocalQuaternion
     * 四元数乘法不满足交换律，顺序很重要。
     *
     * @see worldTransform
     */
    open var worldQuaternion: Quaternion
        get() = worldTransform.toQuaternion()
        set(value) {
            quaternion = parent?.getLocalQuaternion(value) ?: value
        }

    /**
     * 欧拉角表示的朝向，每个轴的角度范围为 `0.0f` 到 `360.0f`。
     *
     * 三元素旋转向量以角度指定旋转轴的方向。
     * 旋转是相对于组件的原点属性进行的。
     *
     * 默认值为 `Rotation(x = 0.0f, y = 0.0f, z = 0.0f)`，表示无旋转。
     *
     * 注意：修改返回的旋转值的单个分量不会产生任何效果。
     *
     * @see transform
     */
    open var rotation: Rotation
        get() = quaternion.toEulerAngles()
        set(value) {
            quaternion = Quaternion.fromEuler(value)
        }

    /**
     * World-space rotation.
     * 世界空间旋转。
     *
     * The world rotation of this component (i.e. relative to the [SceneView]).
     * This is the composition of this component's local rotation with its parent's world rotation.
     * 此组件的世界旋转（即相对于 [SceneView]）。
     * 这是此组件的局部旋转与其父节点世界旋转的组合。
     *
     * @see worldTransform
     */
    open var worldRotation: Rotation
        get() = worldTransform.rotation
        set(value) {
            worldQuaternion = Quaternion.fromEuler(value)
        }

    /**
     * Scale on each axis.
     * 各轴上的缩放。
     *
     * Reduce (`scale < 1.0f`) / Increase (`scale > 1.0f`).
     * 缩小 (`scale < 1.0f`) / 放大 (`scale > 1.0f`)。
     *
     * 图形学原理：
     * 缩放变换通过缩放矩阵实现：
     * [sx 0  0  0]
     * [0  sy 0  0]
     * [0  0  sz 0]
     * [0  0  0  1]
     * 其中 (sx, sy, sz) 为各轴的缩放因子。
     * 非均匀缩放可能导致对象变形。
     *
     * @see transform
     */
    open var scale: Scale
        get() = transform.scale
        set(value) {
            transform = Transform(position, quaternion, value)
        }

    /**
     * World-space scale.
     * 世界空间缩放。
     *
     * The world scale of this component (i.e. relative to the [SceneView]).
     * This is the composition of this component's local scale with its parent's world scale.
     * 此组件的世界缩放（即相对于 [SceneView]）。
     * 这是此组件的局部缩放与其父节点世界缩放的组合。
     *
     * 图形学原理：
     * 世界缩放是父节点缩放与局部缩放的乘积。
     * 注意：缩放变换不满足交换律，与旋转组合时顺序很重要。
     *
     * @see worldTransform
     */
    open var worldScale: Scale
        get() = worldTransform.scale
        set(value) {
            scale = parent?.getLocalScale(value) ?: value
        }

    /**
     * Local transform of the transform component (i.e. relative to the parent).
     * 变换组件的局部变换（即相对于父节点）。
     *
     * 图形学原理：
     * 局部变换矩阵包含了位置、旋转和缩放的组合变换。
     * 变换矩阵的标准形式为：
     * T = T(translation) * R(rotation) * S(scale)
     * 在齐次坐标系中，这是一个4x4矩阵，支持仿射变换。
     *
     * @see TransformManager.getTransform
     * @see TransformManager.setTransform
     */
    open var transform: Transform
        get() = transformManager.getTransform(transformInstance)
        set(value) {
            transformManager.setTransform(transformInstance, value)
            onTransformChanged()
        }

    /**
     * World transform of a transform component (i.e. relative to the root).
     * 变换组件的世界变换（即相对于根节点）。
     *
     * 图形学原理：
     * 世界变换矩阵是从根节点到当前节点路径上所有变换矩阵的乘积：
     * WorldTransform = RootTransform * ... * ParentTransform * LocalTransform
     * 这种层次变换是场景图的核心概念。
     *
     * @see TransformManager.getWorldTransform
     */
    var worldTransform: Transform
        get() = transformManager.getWorldTransform(transformInstance)
        set(value) {
            transform = parent?.getLocalTransform(value) ?: value
        }

    var smoothTransform: Transform? = null

    var parentEntity: Entity?
        get() = transformManager.getParentOrNull(transformInstance)
        set(value) {
            if (parentEntity != value) {
                parentInstance = value?.let { transformManager.getInstance(it) }
            }
        }

    var parentInstance: EntityInstance?
        get() = parentEntity?.let { transformManager.getInstance(it) }
        set(value) {
            if (parentInstance != value) {
                transformManager.setParent(transformInstance, value ?: 0)
            }
        }

    /**
     * Changes the parent node.
     * 更改父节点。
     *
     * If set to null, this node will be detached.
     * 如果设置为 null，此节点将被分离。
     *
     * The local position, rotation, and scale of this node will remain the same.
     * Therefore, the world position, rotation, and scale of this node may be different after the
     * parent changes.
     * 此节点的局部位置、旋转和缩放将保持不变。
     * 因此，父节点更改后，此节点的世界位置、旋转和缩放可能会有所不同。
     *
     * In addition to setting this field, it will also do the following things:
     * 除了设置此字段外，还将执行以下操作：
     * - Remove this node from its previous parent's children.
     * - 从其先前父节点的子节点中移除此节点。
     * - Add this node to its new parent's children.
     * - 将此节点添加到其新父节点的子节点中。
     * - Recursively update the node's transformation to reflect the change in parent.
     * - 递归更新节点的变换以反映父节点的更改。
     * - Recursively update the scene field to match the new parent's scene field.
     * - 递归更新场景字段以匹配新父节点的场景字段。
     *
     * 图形学原理：
     * 场景图中的父子关系决定了变换的继承。当父节点变换时，
     * 所有子节点的世界变换都会相应更新，这是通过矩阵乘法链实现的。
     */
    open var parent: Node? = null
        set(value) {
            if (field != value) {
                val oldParent = field
                field = value
                oldParent?.let { it.childNodes = it.childNodes - this }
                value?.let { it.childNodes = it.childNodes + this }
                parentEntity = value?.entity
            }
        }

    var childNodes = setOf<Node>()
        set(value) {
            if (field != value) {
                val removedNodes = field - value
                val addedNodes = value - field
                field = value
                removedNodes.forEach { child ->
                    if (child.parent == this@Node) {
                        child.parent = null
                    }
                    onChildRemoved.forEach { it(child) }
                }
                addedNodes.forEach { child ->
                    if (child.parent != this@Node) {
                        child.parent = this@Node
                    }
                    onChildAdded.forEach { it(child) }
                }
                onTransformChanged()
            }
        }

    var collisionSystem: CollisionSystem? = null
        set(value) {
            if (field != value) {
                field = value
                collider?.setAttachedCollisionSystem(value)
            }
        }

    var editingTransforms = setOf<KProperty1<Node, Any>>()
        set(value) {
            if (field != value) {
                field = value
                onEditingChanged?.invoke(value)
            }
        }

    /**
     * Transform from the world coordinate system to the coordinate system of this node.
     * 从世界坐标系到此节点坐标系的变换。
     *
     * 图形学原理：
     * 世界到局部的变换矩阵是世界变换矩阵的逆矩阵。
     * 这个变换用于将世界空间中的点或向量转换到节点的局部空间。
     * LocalPoint = inverse(WorldTransform) * WorldPoint
     */
    val worldToLocal: Transform get() = inverse(worldTransform)

    var onFrame: ((frameTimeNanos: Long) -> Unit)? = null
    var onSmoothEnd: ((node: Node) -> Unit)? = null
    var onAddedToScene: ((scene: Scene) -> Unit)? = null
    var onRemovedFromScene: ((scene: Scene) -> Unit)? = null

    var onTouch: ((e: MotionEvent, hitResult: HitResult) -> Boolean)? = null
    var onDown: ((e: MotionEvent) -> Boolean)? = null
    var onShowPress: ((e: MotionEvent) -> Unit)? = null
    var onSingleTapUp: ((e: MotionEvent) -> Boolean)? = null
    var onScroll: ((e1: MotionEvent?, e2: MotionEvent, distance: Float2) -> Boolean)? = null
    var onLongPress: ((e: MotionEvent) -> Unit)? = null
    var onFling: ((e1: MotionEvent?, e2: MotionEvent, velocity: Float2) -> Boolean)? = null
    var onSingleTapConfirmed: ((e: MotionEvent) -> Boolean)? = null
    var onDoubleTap: ((e: MotionEvent) -> Boolean)? = null
    var onDoubleTapEvent: ((e: MotionEvent) -> Boolean)? = null
    var onContextClick: ((e: MotionEvent) -> Boolean)? = null
    var onMoveBegin: ((detector: MoveGestureDetector, e: MotionEvent) -> Boolean)? = null
    var onMove: ((detector: MoveGestureDetector, e: MotionEvent, worldPosition: Position) -> Boolean)? =
        null
    var onMoveEnd: ((detector: MoveGestureDetector, e: MotionEvent) -> Unit)? = null
    var onRotateBegin: ((detector: RotateGestureDetector, e: MotionEvent) -> Boolean)? = null
    var onRotate: ((detector: RotateGestureDetector, e: MotionEvent, rotationDelta: Quaternion) -> Boolean)? =
        null
    var onRotateEnd: ((detector: RotateGestureDetector, e: MotionEvent) -> Unit)? = null
    var onScaleBegin: ((detector: ScaleGestureDetector, e: MotionEvent) -> Boolean)? = null
    var onScale: ((detector: ScaleGestureDetector, e: MotionEvent, scaleFactor: Float) -> Boolean)? =
        null
    var onScaleEnd: ((detector: ScaleGestureDetector, e: MotionEvent) -> Unit)? = null

    var onEditingChanged: ((editingTransforms: Set<KProperty1<Node, Any>?>) -> Unit)? =
        null

    var collider: Collider? = null
        set(value) {
            if (field != value) {
                field?.let { collisionSystem?.removeCollider(it) }
                field = value
                value?.let { collisionSystem?.addCollider(it) }
            }
        }

    /**
     * The shape to used to detect collisions for this [Node].
     * 用于检测此 [Node] 碰撞的形状。
     *
     * If the shape is not set and renderable is set, then [Collider.setShape] is used to detect
     * collisions for this [Node].
     * 如果未设置形状但设置了可渲染对象，则使用 [Collider.setShape] 来检测此 [Node] 的碰撞。
     *
     * [CollisionShape] represents a geometric shape, i.e. sphere, box, convex hull.
     * If null, this node's current collision shape will be removed.
     * [CollisionShape] 表示几何形状，例如球体、盒子、凸包。
     * 如果为 null，将移除此节点当前的碰撞形状。
     *
     * 图形学原理：
     * 碰撞检测是3D图形学中的重要概念，用于确定两个或多个对象是否相交。
     * 常用的碰撞形状包括：
     * - 球体：计算简单，适用于圆形对象
     * - 轴对齐包围盒(AABB)：计算效率高，适用于规则形状
     * - 有向包围盒(OBB)：更精确但计算复杂
     * - 凸包：最精确但计算最复杂
     */
    var collisionShape: CollisionShape? = null
        get() = collider?.shape
        set(value) {
            field = value
            if (value != null) {
                val collider = collider ?: Collider(
                    this
                ).also { collider = it }
                collider.shape = value
            } else {
                collider = null
            }
            // Refresh the collider to ensure it is using the correct collision shape now
            // that the renderable has changed.
            onTransformChanged()
        }

    val transformManager get() = engine.transformManager
    val transformInstance get() = transformManager.getInstance(entity)

    internal open val sceneEntities = listOf(entity)
    internal val onChildAdded = mutableListOf<(child: Node) -> Unit>()
    internal val onChildRemoved = mutableListOf<(child: Node) -> Unit>()

    private var lastFrameTimeNanos: Long? = null

    init {
        if (!transformManager.hasComponent(entity)) {
            transformManager.create(entity)
        }
    }

    /**
     * Converts a position in the world-space to a local-space of this node.
     * 将世界空间中的位置转换为此节点的局部空间。
     *
     * @param worldPosition the position in world-space to convert.
     * @param worldPosition 要转换的世界空间位置。
     * @return a new position that represents the world position in local-space.
     * @return 表示世界位置在局部空间中的新位置。
     *
     * 图形学原理：
     * 使用世界到局部变换矩阵的逆变换：
     * LocalPosition = inverse(WorldTransform) * WorldPosition
     * 这在射线投射、碰撞检测等场景中非常有用。
     */
    fun getLocalPosition(worldPosition: Position) = worldToLocal * worldPosition

    /**
     * Converts a position in the local-space of this node to world-space.
     * 将此节点局部空间中的位置转换为世界空间。
     *
     * @param localPosition the position in local-space to convert.
     * @param localPosition 要转换的局部空间位置。
     * @return a new position that represents the local position in world-space.
     * @return 表示局部位置在世界空间中的新位置。
     *
     * 图形学原理：
     * 使用世界变换矩阵进行变换：
     * WorldPosition = WorldTransform * LocalPosition
     * 这是渲染管线中的标准操作。
     */
    fun getWorldPosition(localPosition: Position) = worldTransform * localPosition

    /**
     * Converts a quaternion in the world-space to a local-space of this node.
     * 将世界空间中的四元数转换为此节点的局部空间。
     *
     * @param worldQuaternion the quaternion in world-space to convert.
     * @param worldQuaternion 要转换的世界空间四元数。
     * @return a new quaternion that represents the world quaternion in local-space.
     * @return 表示世界四元数在局部空间中的新四元数。
     *
     * 图形学原理：
     * 四元数的空间转换通过四元数乘法实现：
     * LocalQuaternion = inverse(WorldQuaternion) * TargetQuaternion
     * 四元数乘法不满足交换律，顺序很重要。
     */
    fun getLocalQuaternion(worldQuaternion: Quaternion) =
        worldToLocal.toQuaternion() * worldQuaternion

    /**
     * Converts a quaternion in the local-space of this node to world-space.
     * 将此节点局部空间中的四元数转换为世界空间。
     *
     * @param quaternion the quaternion in local-space to convert.
     * @param quaternion 要转换的局部空间四元数。
     * @return a new quaternion that represents the local quaternion in world-space.
     * @return 表示局部四元数在世界空间中的新四元数。
     *
     * 图形学原理：
     * 局部到世界的四元数转换：
     * WorldQuaternion = WorldTransformQuaternion * LocalQuaternion
     * 这保持了旋转的层次继承关系。
     */
    fun getWorldQuaternion(quaternion: Quaternion) = worldTransform.toQuaternion() * quaternion

    /**
     * Converts a rotation in the world-space to a local-space of this node.
     *
     * @param worldRotation the rotation in world-space to convert.
     * @return a new rotation that represents the world rotation in local-space.
     */
    fun getLocalRotation(worldRotation: Rotation) =
        getLocalQuaternion(Quaternion.fromEuler(worldRotation)).toEulerAngles()

    /**
     * Converts a rotation in the local-space of this node to world-space.
     *
     * @param rotation the rotation in local-space to convert.
     * @return a new rotation that represents the local rotation in world-space.
     */
    fun getWorldRotation(rotation: Rotation) =
        getWorldQuaternion(Quaternion.fromEuler(rotation)).toEulerAngles()

    /**
     * Converts a scale in the world-space to a local-space of this node.
     *
     * @param worldScale the transform in world-space to convert.
     * @return a new scale that represents the world scale in local-space.
     */
//    fun getLocalScale(worldScale: Scale) = scale(worldToLocal) * worldScale
//    fun getLocalScale(worldScale: Scale) = (worldToLocal * scale(worldScale)).scale
    fun getLocalScale(worldScale: Scale) = worldToLocal * worldScale

    /**
     * Converts a scale in the local-space of this node to world-space.
     *
     * @param scale the scale in local-space to convert.
     * @return a new scale that represents the local scale in world-space.
     */
//    fun getWorldScale(scale: Scale) = (worldTransform * scale(scale)).scale
//    fun getWorldScale(scale: Scale) = scale(worldTransform) * scale
    fun getWorldScale(scale: Scale) = worldTransform * scale

    /**
     * Converts a node transform in the world-space to a local-space of this node.
     *
     * @param node the node in world-space to convert.
     * @return a new transform that represents the world transform in local-space.
     */
    fun getLocalTransform(node: Node) = getLocalTransform(node.worldTransform)

    /**
     * Converts a transform in the world-space to a local-space of this node.
     *
     * @param worldTransform the transform in world-space to convert.
     * @return a new transform that represents the world transform in local-space.
     */
    fun getLocalTransform(worldTransform: Transform) = worldToLocal * worldTransform

    /**
     * Converts a node transform in the local-space of this node to world-space.
     *
     * @param node the node in local-space to convert.
     * @return a new transform that represents the local transform in world-space.
     */
    fun getWorldTransform(node: Node) = getWorldTransform(node.transform)

    /**
     * Converts a transform in the local-space of this node to world-space.
     *
     * @param localTransform the transform in local-space to convert.
     * @return a new transform that represents the local transform in world-space.
     */
    fun getWorldTransform(localTransform: Transform) = worldTransform * localTransform

    /**
     * The node scale.
     *
     * - reduce size: scale < 1.0f
     * - same size: scale = 1.0f
     * - increase size: scale > 1.0f
     */
    fun setScale(scale: Float) {
        this.scale.xyz = Scale(scale)
    }

    /**
     * Change the node transform.
     */
    open fun transform(
        transform: Transform,
        smooth: Boolean = isSmoothTransformEnabled,
        smoothSpeed: Float = smoothTransformSpeed
    ) = apply {
        if (smooth) {
            this.smoothTransformSpeed = smoothSpeed
            this.smoothTransform = transform
        } else {
            this.smoothTransform = null
            this.transform = transform
        }
    }

    /**
     * Change the node transform.
     *
     * @see position
     * @see quaternion
     * @see scale
     */
    fun transform(
        position: Position = this.position,
        quaternion: Quaternion = this.quaternion,
        scale: Scale = this.scale,
        smooth: Boolean = isSmoothTransformEnabled,
        smoothSpeed: Float = smoothTransformSpeed
    ) = transform(Transform(position, quaternion, scale), smooth, smoothSpeed)

    /**
     * Change the node transform.
     *
     * @see position
     * @see rotation
     * @see scale
     */
    fun transform(
        position: Position = this.position,
        rotation: Rotation,
        scale: Scale = this.scale,
        smooth: Boolean = isSmoothTransformEnabled,
        smoothSpeed: Float = smoothTransformSpeed
    ) = transform(position, rotation.toQuaternion(), scale, smooth, smoothSpeed)

    /**
     * Change the node world transform.
     */
    open fun worldTransform(
        worldTransform: Transform,
        smooth: Boolean = isSmoothTransformEnabled,
        smoothSpeed: Float = smoothTransformSpeed
    ) = transform(parent?.getLocalTransform(worldTransform) ?: worldTransform, smooth, smoothSpeed)

    /**
     * Change the node world transform.
     *
     * @see position
     * @see quaternion
     * @see scale
     */
    fun worldTransform(
        position: Position = this.worldPosition,
        quaternion: Quaternion = this.worldQuaternion,
        scale: Scale = this.worldScale,
        smooth: Boolean = isSmoothTransformEnabled,
        smoothSpeed: Float = smoothTransformSpeed
    ) = worldTransform(Transform(position, quaternion, scale), smooth, smoothSpeed)

    /**
     * Change the node world transform.
     *
     * @see position
     * @see rotation
     * @see scale
     */
    fun worldTransform(
        position: Position = this.worldPosition,
        rotation: Rotation,
        scale: Scale = this.worldScale,
        smooth: Boolean = isSmoothTransformEnabled,
        smoothSpeed: Float = smoothTransformSpeed
    ) = worldTransform(Transform(position, rotation.toQuaternion(), scale), smooth, smoothSpeed)

    /**
     * Rotates the node to face another node.
     * 旋转节点以面向另一个节点。
     *
     * @param targetNode The target node to look at
     * @param targetNode 要看向的目标节点
     * @param upDirection The up direction will determine the orientation of the node around the direction
     * @param upDirection 上方向将决定节点围绕方向的朝向
     * @param smooth Whether the rotation should happen smoothly
     * @param smooth 旋转是否应该平滑进行
     *
     * 图形学原理：
     * LookAt变换是计算机图形学中的基础概念，用于计算从一个点看向另一个点的旋转。
     * 它构建了一个观察矩阵，定义了局部坐标系的三个轴：
     * - Forward轴：从眼睛指向目标的方向
     * - Right轴：Forward与Up的叉积
     * - Up轴：Right与Forward的叉积（重新计算以确保正交）
     */
    fun lookAt(
        targetNode: Node,
        upDirection: Direction = Direction(y = 1.0f),
        smooth: Boolean = isSmoothTransformEnabled,
        smoothSpeed: Float = smoothTransformSpeed
    ) = lookAt(
        targetWorldPosition = targetNode.worldPosition,
        upDirection = upDirection,
        smooth = smooth,
        smoothSpeed = smoothSpeed
    )

    /**
     * Rotates the node to face a point in world-space.
     * 旋转节点以面向世界空间中的一个点。
     *
     * @param targetWorldPosition The target position to look at in world space
     * @param targetWorldPosition 在世界空间中要看向的目标位置
     * @param upDirection The up direction will determine the orientation of the node around the direction
     * @param upDirection 上方向将决定节点围绕方向的朝向
     * @param smooth Whether the rotation should happen smoothly
     * @param smooth 旋转是否应该平滑进行
     *
     * 图形学原理：
     * LookAt矩阵的构建过程：
     * 1. Forward = normalize(target - eye)
     * 2. Right = normalize(cross(forward, up))
     * 3. Up = cross(right, forward)
     * 4. 构建旋转矩阵并转换为四元数
     */
    fun lookAt(
        targetWorldPosition: Position,
        upDirection: Direction = Direction(y = 1.0f),
        smooth: Boolean = isSmoothTransformEnabled,
        smoothSpeed: Float = smoothTransformSpeed
    ) = worldTransform(
        quaternion = lookAt(
            eye = worldPosition,
            target = targetWorldPosition,
            up = upDirection
        ).toQuaternion(),
        smooth = smooth,
        smoothSpeed = smoothSpeed
    )

    /**
     * Rotates the node to face a direction in world-space.
     * 旋转节点以面向世界空间中的一个方向。
     *
     * The look direction and up direction cannot be coincident (parallel) or the orientation will
     * be invalid.
     * 观察方向和上方向不能重合（平行），否则朝向将无效。
     *
     * @param lookDirection The desired look direction in world-space.
     * @param lookDirection 在世界空间中期望的观察方向。
     * @param upDirection The up direction will determine the orientation of the node around the
     * look direction.
     * @param upDirection 上方向将决定节点围绕观察方向的朝向。
     * @param smooth Whether the rotation should happen smoothly.
     * @param smooth 旋转是否应该平滑进行。
     *
     * 图形学原理：
     * LookTowards与LookAt的区别在于，LookTowards直接使用方向向量而不是目标点。
     * 这种方法更适合于定义物体的朝向，特别是当我们知道期望的方向但不关心具体目标位置时。
     * 方向向量必须是单位向量，且与上向量不能平行，以确保能构建有效的正交坐标系。
     * 算法步骤：
     * 1. Forward = normalize(lookDirection)
     * 2. Right = normalize(cross(forward, up))
     * 3. Up = cross(right, forward)
     * 4. 构建旋转矩阵并转换为四元数
     */
    fun lookTowards(
        lookDirection: Direction,
        upDirection: Direction = Direction(y = 1.0f),
        smooth: Boolean = isSmoothTransformEnabled,
        smoothSpeed: Float = smoothTransformSpeed
    ) = worldTransform(
        quaternion = lookTowards(
            eye = worldPosition,
//            forward = -lookDirection,
            forward = lookDirection,
            up = upDirection
        ).toQuaternion(),
        smooth = smooth,
        smoothSpeed = smoothSpeed
    )

    /**
     * Add a node as a child of this node.
     * 将一个节点添加为此节点的子节点。
     *
     * The child node is added to the end of the list of children.
     * 子节点被添加到子节点列表的末尾。
     * If the child already has a parent, it is removed from its old parent.
     * 如果子节点已经有父节点，它会从旧父节点中移除。
     *
     * @param node the node to add as a child
     * @param node 要添加为子节点的节点
     *
     * 图形学原理：
     * 场景图中的父子关系建立了变换的层次结构。
     * 子节点的世界变换 = 父节点的世界变换 × 子节点的局部变换
     * 这种层次结构使得复杂场景的管理变得简单，支持组合变换和批量操作。
     */
    fun addChildNode(node: Node) = apply { childNodes += node }

    /**
     * Add multiple nodes as children of this node.
     * 将多个节点添加为此节点的子节点。
     *
     * @param nodes the set of nodes to add as children
     * @param nodes 要添加为子节点的节点集合
     */
    fun addChildNodes(nodes: Set<Node>) = apply { childNodes += nodes }

    /**
     * Remove a child node from this node.
     * 从此节点中移除一个子节点。
     *
     * @param node the child node to remove
     * @param node 要移除的子节点
     */
    fun removeChildNode(node: Node) = apply { childNodes -= node }

    /**
     * Remove multiple child nodes from this node.
     * 从此节点中移除多个子节点。
     *
     * @param nodes the set of child nodes to remove
     * @param nodes 要移除的子节点集合
     */
    fun removeChildNodes(nodes: Set<Node>) = apply { childNodes = childNodes - nodes }

    /**
     * Remove all child nodes from this node.
     * 移除此节点的所有子节点。
     *
     * This will detach all children but not destroy them.
     * 这将分离所有子节点但不会销毁它们。
     */
    fun clearChildNodes() = apply { childNodes = setOf() }

    /**
     * Animate a [Node] position property value.
     * 动画化节点的位置属性值。
     *
     * @param positions Values that the position property will be animated to.
     * @param positions 位置属性将要动画到的值。
     *
     * 图形学原理：
     * 位置动画通过在关键帧之间插值实现平滑的位移效果。
     * 常用插值方法包括：
     * - 线性插值：P(t) = P0 + t * (P1 - P0)
     * - 贝塞尔曲线插值：提供更自然的运动轨迹
     * - 样条插值：确保速度和加速度的连续性
     */
    fun animatePositions(vararg positions: Position): ObjectAnimator =
        NodeAnimator.ofPosition(this, *positions)

    /**
     * Animate a [Node] quaternion property value.
     * 动画化节点的四元数属性值。
     *
     * @param quaternions Values that the quaternion property will be animated to.
     * @param quaternions 四元数属性将要动画到的值。
     *
     * 图形学原理：
     * 四元数动画使用球面线性插值(SLERP)来实现平滑的旋转过渡。
     * SLERP公式：q(t) = (sin((1-t)θ)/sinθ) * q0 + (sin(tθ)/sinθ) * q1
     * 其中θ是两个四元数之间的角度。这确保了旋转路径是最短的，
     * 避免了欧拉角插值可能产生的万向锁和不自然的旋转路径。
     */
    fun animateQuaternions(vararg quaternions: Quaternion): ObjectAnimator =
        NodeAnimator.ofQuaternion(this, *quaternions)

    /**
     * Animate a [Node] rotation property value.
     * 动画化节点的旋转属性值。
     *
     * @param rotations Values that the rotation property will be animated to.
     * @param rotations 旋转属性将要动画到的值。
     *
     * 图形学原理：
     * 欧拉角旋转动画需要注意万向锁问题。当某个轴的旋转角度接近±90度时，
     * 可能会失去一个自由度，导致不期望的旋转行为。
     * 为了避免这个问题，内部实现通常会转换为四元数进行插值，
     * 然后再转换回欧拉角。
     */
    fun animateRotations(vararg rotations: Rotation): ObjectAnimator =
        NodeAnimator.ofRotation(this, *rotations)

    /**
     * Animate a [Node] scale property value.
     * 动画化节点的缩放属性值。
     *
     * @param scales Values that the scale property will be animated to.
     * @param scales 缩放属性将要动画到的值。
     *
     * 图形学原理：
     * 缩放动画通过线性插值各轴的缩放因子实现。
     * 需要注意的是，缩放为0可能导致除零错误或渲染问题，
     * 负缩放值会导致对象翻转。非均匀缩放（各轴缩放因子不同）
     * 会改变对象的形状比例。
     */
    fun animateScales(vararg scales: Scale): ObjectAnimator =
        NodeAnimator.ofScale(this, *scales)

    /**
     * Animate a [Node] transform property value.
     * 动画化节点的变换属性值。
     *
     * @param transforms Values that the transform property will be animated to.
     * @param transforms 变换属性将要动画到的值。
     *
     * 图形学原理：
     * 变换动画是位置、旋转和缩放动画的组合。为了确保平滑的过渡，
     * 每个组件都使用适当的插值方法：
     * - 位置：线性插值
     * - 旋转：球面线性插值(SLERP)
     * - 缩放：线性插值
     * 这种组合动画能够创建复杂的运动效果，如物体沿曲线移动的同时旋转和缩放。
     */
    fun animateTransforms(vararg transforms: Transform): AnimatorSet =
        NodeAnimator.ofTransform(this, *transforms)

    /**
     * Tests to see if this node collision shape overlaps the collision shape of any other nodes in
     * the scene using [Node.collisionShape].
     * 测试此节点的碰撞形状是否与场景中其他节点的碰撞形状重叠，使用[Node.collisionShape]。
     *
     * @return A node that is overlapping the test node. If no node is overlapping the test node,
     * then this is null. If multiple nodes are overlapping the test node, then this could be any of
     * them.
     * @return 与测试节点重叠的节点。如果没有节点与测试节点重叠，则为null。
     * 如果多个节点与测试节点重叠，则可能是其中任何一个。
     *
     * 图形学原理：
     * 碰撞检测是3D图形学中的核心技术，用于判断两个几何体是否相交。
     * 常用算法包括：
     * - 包围盒检测：AABB（轴对齐包围盒）和OBB（有向包围盒）
     * - 分离轴定理(SAT)：用于凸多面体的精确碰撞检测
     * - GJK算法：用于凸形状的距离和碰撞检测
     * 通常采用层次化检测：粗检测（包围盒）+ 精检测（几何形状）。
     */
    fun overlapTest() = collisionSystem!!.intersects(collider!!)?.node

    /**
     * Tests to see if a node is overlapping any other nodes within the scene using
     * [Node.collisionShape].
     * 测试节点是否与场景中的其他节点重叠，使用[Node.collisionShape]。
     *
     * @return A list of all nodes that are overlapping this node. If no node is overlapping the
     * test node, then the list is empty.
     * @return 与此节点重叠的所有节点列表。如果没有节点与测试节点重叠，则列表为空。
     *
     * 图形学原理：
     * 多对象碰撞检测需要优化性能，常用技术包括：
     * - 空间分割：八叉树、四叉树、BSP树等数据结构
     * - 扫描线算法：对于2D或简化的3D场景
     * - 时间相干性：利用前一帧的结果优化当前帧检测
     * - 早期退出：一旦找到足够的碰撞就停止检测
     */
    fun overlapTestAll() = buildList {
        collisionSystem!!.intersectsAll(collider!!) {
            add(it.node)
        }
    }

    /**
     * Called once per frame to update the node's state.
     * 每帧调用一次以更新节点状态。
     *
     * @param frameTimeNanos The current frame time in nanoseconds.
     * @param frameTimeNanos 当前帧时间（纳秒）。
     *
     * 图形学原理：
     * 平滑变换使用球面线性插值(SLERP)实现，这是一种在两个变换之间创建平滑过渡的技术。
     * SLERP确保旋转沿着最短路径进行，并保持匀速运动。
     * 对于完整变换矩阵，通常分解为：
     * - 位置：线性插值
     * - 旋转：四元数SLERP
     * - 缩放：线性插值
     * 然后重新组合成一个变换矩阵。
     */
    open fun onFrame(frameTimeNanos: Long) {
        smoothTransform?.let { smoothTransform ->
            if (smoothTransform != transform) {
                val slerpTransform = slerp(
                    start = transform,
                    end = smoothTransform,
                    deltaSeconds = frameTimeNanos.intervalSeconds(lastFrameTimeNanos),
                    speed = smoothTransformSpeed
                )
                if (!slerpTransform.equals(this.transform, delta = 0.001f)) {
                    this.transform = slerpTransform
                } else {
                    this.transform = smoothTransform
                    this.smoothTransform = null
                    onSmoothEnd?.invoke(this)
                }
            } else {
                this.smoothTransform = null
            }
        }
        childNodes.forEach { it.onFrame(frameTimeNanos) }

        onFrame?.invoke(frameTimeNanos)

        lastFrameTimeNanos = frameTimeNanos
    }

    /**
     * The transformation (position, rotation or scale) of the [Node] has changed.
     * [Node]的变换（位置、旋转或缩放）已更改。
     *
     * If node's position is changed, then that will trigger [onWorldTransformChanged] to be called
     * for all of it's descendants.
     * 如果节点的位置发生变化，将触发对其所有后代调用[onWorldTransformChanged]。
     *
     * 图形学原理：
     * 当节点的局部变换发生变化时，需要更新其世界变换和所有子节点的世界变换。
     * 这是场景图层次结构中的重要事件，确保变换的正确传播。
     */
    open fun onTransformChanged() {
        onWorldTransformChanged()
    }

    /**
     * Called when this node is added to a scene.
     * 当此节点添加到场景时调用。
     *
     * @param scene The scene this node was added to.
     * @param scene 此节点被添加到的场景。
     */
    open fun onAddedToScene(scene: Scene) {
        onAddedToScene?.invoke(scene)
    }

    /**
     * Called when this node is removed from a scene.
     * 当此节点从场景中移除时调用。
     *
     * @param scene The scene this node was removed from.
     * @param scene 此节点被移除的场景。
     */
    open fun onRemovedFromScene(scene: Scene) {
        onRemovedFromScene?.invoke(scene)
    }

    /**
     * The transformation (position, rotation or scale) of the [Node] has changed.
     * [Node]的变换（位置、旋转或缩放）已更改。
     *
     * If node's position is changed, then that will trigger [onWorldTransformChanged] to be called
     * for all of it's descendants.
     * 如果节点的位置发生变化，将触发对其所有后代调用[onWorldTransformChanged]。
     *
     * 图形学原理：
     * 世界变换更新是场景图渲染的关键步骤。当节点的世界变换更新时：
     * 1. 需要重新计算碰撞形状的世界空间表示
     * 2. 所有子节点的世界变换也需要递归更新
     * 3. 这确保了整个场景图中的变换一致性
     */
    open fun onWorldTransformChanged() {
        collider?.markWorldShapeDirty()
        childNodes.forEach { it.onWorldTransformChanged() }
    }


    open fun onTouchEvent(e: MotionEvent, hitResult: HitResult) =
        onTouch?.invoke(e, hitResult) ?: false

    override fun onDown(e: MotionEvent) = onDown?.invoke(e) ?: false
    override fun onShowPress(e: MotionEvent) {
        onShowPress?.invoke(e)
    }

    override fun onSingleTapUp(e: MotionEvent) = onSingleTapUp?.invoke(e) ?: false
    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ) = onScroll?.invoke(e1, e2, Float2(distanceX, distanceY)) ?: false

    override fun onLongPress(e: MotionEvent) {
        onLongPress?.invoke(e)
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ) = onFling?.invoke(e1, e2, Float2(velocityX, velocityY)) ?: false

    override fun onSingleTapConfirmed(e: MotionEvent) = onSingleTapConfirmed?.invoke(e) ?: false
    override fun onDoubleTap(e: MotionEvent) = onDoubleTap?.invoke(e) ?: false
    override fun onDoubleTapEvent(e: MotionEvent) = onDoubleTapEvent?.invoke(e) ?: false
    override fun onContextClick(e: MotionEvent) = onContextClick?.invoke(e) ?: false

    override fun onMoveBegin(detector: MoveGestureDetector, e: MotionEvent): Boolean {
        return if (isPositionEditable && onMoveBegin?.invoke(detector, e) != false) {
            editingTransforms = editingTransforms + Node::position
            true
        } else {
            parent?.onMoveBegin(detector, e) ?: false
        }
    }

    override fun onMove(detector: MoveGestureDetector, e: MotionEvent): Boolean {
        return if (isPositionEditable) {
            // Find the hit test location in the parent to place the child at the
            // corresponding location
            collisionSystem?.hitTest(e)?.firstOrNull { it.node == parent }?.let {
                onMove(detector, e, it.worldPosition)
            } ?: false
        } else {
            parent?.onMove(detector, e) ?: false
        }
    }

    open fun onMove(
        detector: MoveGestureDetector,
        e: MotionEvent,
        worldPosition: Position
    ): Boolean {
        return if (onMove?.invoke(detector, e, worldPosition) != false) {
            this.worldPosition = worldPosition
            true
        } else {
            false
        }
    }

    override fun onMoveEnd(detector: MoveGestureDetector, e: MotionEvent) {
        if (isPositionEditable) {
            editingTransforms = editingTransforms - Node::position
        } else {
            parent?.onMoveEnd(detector, e)
        }
    }

    override fun onRotateBegin(detector: RotateGestureDetector, e: MotionEvent): Boolean {
        return if (isRotationEditable && onRotateBegin?.invoke(detector, e) != false) {
            editingTransforms = editingTransforms + Node::quaternion
            true
        } else {
            parent?.onRotateBegin(detector, e) ?: false
        }
    }

    override fun onRotate(detector: RotateGestureDetector, e: MotionEvent): Boolean {
        return if (isRotationEditable) {
            val deltaRadians = detector.currentAngle - detector.lastAngle
            onRotate(
                detector, e,
                rotationDelta = Quaternion.fromAxisAngle(Float3(y = 1.0f), degrees(-deltaRadians))
            )
        } else {
            parent?.onRotate(detector, e) ?: false
        }
    }

    open fun onRotate(
        detector: RotateGestureDetector,
        e: MotionEvent,
        rotationDelta: Quaternion
    ): Boolean {
        return if (onRotate?.invoke(detector, e, rotationDelta) != false) {
            quaternion *= rotationDelta
            true
        } else {
            false
        }
    }

    override fun onRotateEnd(detector: RotateGestureDetector, e: MotionEvent) {
        if (isRotationEditable) {
            editingTransforms = editingTransforms - Node::quaternion
        } else {
            parent?.onRotateEnd(detector, e)
        }
    }

    override fun onScaleBegin(detector: ScaleGestureDetector, e: MotionEvent): Boolean {
        return if (isScaleEditable && onScaleBegin?.invoke(detector, e) != false) {
            true
        } else {
            parent?.onScaleBegin(detector, e) ?: false
        }
    }

    override fun onScale(detector: ScaleGestureDetector, e: MotionEvent): Boolean {
        return if (isScaleEditable) {
            editingTransforms = editingTransforms + Node::scale
            onScale(detector, e, detector.scaleFactor)
        } else {
            parent?.onScale(detector, e) ?: false
        }
    }

    open fun onScale(detector: ScaleGestureDetector, e: MotionEvent, scaleFactor: Float): Boolean {
        return if (onScale?.invoke(detector, e, scaleFactor) != false) {
            val newScale = scale * scaleFactor
            if (newScale.x in editableScaleRange &&
                newScale.y in editableScaleRange &&
                newScale.z in editableScaleRange
            ) {
                scale = newScale
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    override fun onScaleEnd(detector: ScaleGestureDetector, e: MotionEvent) {
        if (isScaleEditable) {
            editingTransforms = editingTransforms - Node::scale
        } else {
            parent?.onScaleEnd(detector, e)
        }
    }

    /**
     * Updates the children visibility
     * 更新子节点的可见性
     *
     * @see RenderableNode.updateVisibility
     *
     * 图形学原理：
     * 可见性传播是场景图中的重要概念。当父节点不可见时，子节点通常也应该不可见，
     * 这种层次化的可见性管理可以优化渲染性能，避免渲染不可见的对象。
     * 这通常通过视锥体剔除(Frustum Culling)和遮挡剔除(Occlusion Culling)等技术实现。
     */
    protected open fun updateVisibility() {
        childNodes.forEach { childNode ->
            childNode.updateVisibility()
        }
    }

    /**
     * Returns the transformation matrix for this node.
     * 返回此节点的变换矩阵。
     *
     * 图形学原理：
     * 变换矩阵是4x4矩阵，包含了位置、旋转和缩放信息。
     * 在渲染管线中，顶点坐标通过与此矩阵相乘来转换到世界空间。
     */
    override fun getTransformationMatrix(): Matrix {
        return worldTransform.toMatrix()
    }

    /**
     * Detach and destroy the node and all its children.
     * 分离并销毁节点及其所有子节点。
     *
     * 图形学原理：
     * 资源管理是3D图形应用的关键部分。正确销毁不再需要的节点和相关资源
     * 可以防止内存泄漏和资源浪费。这包括：
     * - 从场景图中移除节点
     * - 释放GPU资源（如纹理、网格数据）
     * - 清理相关的系统资源
     */
    open fun destroy() {
        runCatching { parent = null }
        engine.safeDestroyTransformable(entity)
        engine.safeDestroyEntity(entity)
    }
}

interface OnNodeGestureListener : GestureDetector.OnGestureListener,
    OnDoubleTapListener,
    OnContextClickListener,
    MoveGestureDetector.OnMoveListener,
    RotateGestureDetector.OnRotateListener,
    ScaleGestureDetector.OnScaleListener

open class SimpleOnNodeGestureListener : GestureDetector.SimpleOnGestureListener(),
    MoveGestureDetector.SimpleOnMoveListener,
    RotateGestureDetector.SimpleOnRotateListener,
    ScaleGestureDetector.SimpleOnScaleListener,
    OnNodeGestureListener
