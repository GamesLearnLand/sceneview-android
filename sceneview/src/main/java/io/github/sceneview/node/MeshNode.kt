package io.github.sceneview.node

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.VertexBuffer
import io.github.sceneview.geometries.Geometry

/**
 * Mesh是原始图形的集合，每个原始图形都有自己的几何体和材质。
 *
 * 特定可渲染对象中的所有原始图形共享一组渲染属性，例如是否投射阴影或使用顶点蒙皮。Kotlin使用示例：
 *
 * ```
 * val entity = EntityManager.get().create()
 *
 * RenderableManager.Builder(1)
 *         .boundingBox(Box(0.0f, 0.0f, 0.0f, 9000.0f, 9000.0f, 9000.0f))
 *         .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
 *         .material(0, material)
 *         .build(engine, entity)
 *
 * scene.addEntity(renderable)
 * ```
 *
 * 要修改现有可渲染对象的状态，客户端应首先使用RenderableManager获取一个临时句柄，称为“实例”。然后可以使用该实例来获取或设置可渲染对象的状态。请注意，实例是短暂的；客户端应存储实体，而不是实例。
 *
 * @see Geometry
 */
open class MeshNode(
    engine: Engine,
    primitiveType: PrimitiveType,
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val boundingBox: Box? = null,
    /**
     * 绑定材质实例。
     *
     * 如果未指定材质，Filament将回退到基本默认材质。
     */
    materialInstance: MaterialInstance? = null,
    builder: RenderableManager.Builder.() -> Unit = {}
) : RenderableNode(engine) {

    init {
        RenderableManager.Builder(1)
            .geometry(
                0,
                primitiveType,
                vertexBuffer,
                indexBuffer
            )
            .apply {
                boundingBox?.let { boundingBox(it) }
                culling(boundingBox != null)
                materialInstance?.let { materialInstance ->
                    material(0, materialInstance)
                }
            }.apply(builder)
            .build(engine, entity)
        updateCollisionShape()
    }
}