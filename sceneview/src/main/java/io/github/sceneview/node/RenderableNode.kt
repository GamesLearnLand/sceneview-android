package io.github.sceneview.node

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import io.github.sceneview.Entity
import io.github.sceneview.FilamentEntity
import io.github.sceneview.SceneView
import io.github.sceneview.components.RenderableComponent
import io.github.sceneview.math.toVector3Box
import io.github.sceneview.safeDestroyRenderable

/**
 * RenderableNode 是场景图层次结构中的一个节点，表示一个变换。
 *
 * 该节点包含渲染引擎要渲染的可渲染模型。
 *
 * 每个节点可以有任意数量的子节点和一个父节点。父节点可以是另一个节点，也可以是 [SceneView]。
 */
open class RenderableNode(
    engine: Engine, // 渲染引擎实例
    @FilamentEntity entity: Entity = EntityManager.get().create(), // Filament 实体，默认创建一个新的实体
) : Node(engine, entity), RenderableComponent {

    /**
     * 构造函数，用于创建带有可渲染组件的节点。
     *
     * @param primitiveCount 将提供给构建器的原始图元数量
     * @param boundingBox 可渲染对象的边界框
     * @param materialInstances 材质实例列表，每个材质实例对应一个图元
     * @param builder 自定义 RenderableManager.Builder 的扩展函数
     */
    constructor(
        engine: Engine,
        @FilamentEntity entity: Entity = EntityManager.get().create(),
        primitiveCount: Int,
        boundingBox: Box,
        materialInstances: List<MaterialInstance?> = listOf(),
        builder: RenderableManager.Builder.() -> Unit,
    ) : this(engine, entity) {
        RenderableManager.Builder(primitiveCount)
            .boundingBox(boundingBox)
            .apply {
                materialInstances.forEachIndexed { index, materialInstance ->
                    materialInstance?.let { material(index, materialInstance) }
                }
            }.apply(builder)
            .build(engine, entity)
        updateCollisionShape()
    }

    /**
     * 更新节点的可见性状态，并同步到渲染层。
     */
    override fun updateVisibility() {
        super.updateVisibility()

        setLayerVisible(isVisible)
    }

    /**
     * 更新碰撞形状，基于当前的轴对齐包围盒计算新的碰撞形状。
     */
    fun updateCollisionShape() {
        collisionShape = axisAlignedBoundingBox.toVector3Box()
    }

    /**
     * 销毁节点及其关联的资源。
     */
    override fun destroy() {
        super.destroy()
        engine.safeDestroyRenderable(entity)
    }
}