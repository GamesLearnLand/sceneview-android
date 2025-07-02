package io.github.sceneview.node

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.geometries.geometry
import io.github.sceneview.managers.materials
import io.github.sceneview.safeDestroyGeometry

/**
 * Mesh是原始图元的集合，每个图元都有自己的几何体和材质。
 *
 * 特定可渲染对象中的所有图元共享一组渲染属性，例如是否投射阴影或使用顶点蒙皮。
 * Kotlin使用示例：
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
 * 要修改现有可渲染对象的状态，客户端应首先使用RenderableManager获取一个临时句柄，称为“实例”。
 * 然后可以使用该实例来获取或设置可渲染对象的状态。
 * 请注意，实例是短暂的；客户端应存储实体，而不是实例。
 *
 * @see Geometry
 */
open class GeometryNode(
    engine: Engine,
    open val geometry: Geometry,
    materialInstances: List<MaterialInstance?>,
    primitivesOffsets: List<IntRange> = geometry.primitivesOffsets,
    builderApply: RenderableManager.Builder.() -> Unit = {}
) : RenderableNode(
    engine = engine,
    primitiveCount = primitivesOffsets.size,
    boundingBox = geometry.boundingBox,
    materialInstances = materialInstances,
    builder = {
        geometry(geometry, primitivesOffsets)
        materials(materialInstances)
        apply(builderApply)
    }) {

    constructor(
        engine: Engine,
        geometry: Geometry,
        materialInstance: MaterialInstance? = null,
        builderApply: RenderableManager.Builder.() -> Unit = {}
    ) : this(
        engine = engine,
        geometry = geometry,
        materialInstances = listOf(materialInstance),
        primitivesOffsets = listOf(0..geometry.primitivesOffsets.last().last),
        builderApply = builderApply
    )

    /**
     * 更新几何体的顶点和索引数据。
     *
     * @param vertices 顶点列表，默认值为当前几何体的顶点。
     * @param indices 原始图元的索引列表，默认值为当前几何体的索引。
     */
    fun updateGeometry(
        vertices: List<Geometry.Vertex> = geometry.vertices,
        indices: List<List<Int>> = geometry.primitivesIndices
    ) = setGeometry(geometry.update(engine, vertices, indices))

    override fun destroy() {
        super.destroy()
        engine.safeDestroyGeometry(geometry)
    }
}