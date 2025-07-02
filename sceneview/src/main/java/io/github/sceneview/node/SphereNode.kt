package io.github.sceneview.node

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import io.github.sceneview.geometries.Sphere
import io.github.sceneview.math.Position

/**
 * SphereNode 是一个表示球体的节点类，继承自 GeometryNode。
 * 它允许通过不同的构造函数创建具有不同属性的球体，并提供更新几何形状的方法。
 */
open class SphereNode private constructor(
    engine: Engine, // Filament 引擎实例
    override val geometry: Sphere, // 球体的几何形状
    materialInstances: List<MaterialInstance?>, // 材质实例列表
    primitivesOffsets: List<IntRange> = geometry.primitivesOffsets, // 原始数据偏移范围
    builderApply: RenderableManager.Builder.() -> Unit = {} // 可选的渲染器构建器配置
) : GeometryNode(
    engine = engine,
    geometry = geometry,
    materialInstances = materialInstances,
    primitivesOffsets = primitivesOffsets,
    builderApply = builderApply
) {

    /**
     * 构造函数，用于创建一个带有单个材质实例的球体节点。
     *
     * @param engine Filament 引擎实例
     * @param geometry 球体的几何形状
     * @param materialInstance 单个材质实例，默认为 null
     * @param builderApply 可选的渲染器构建器配置
     */
    constructor(
        engine: Engine,
        geometry: Sphere,
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
     * 构造函数，用于创建一个带有指定半径、中心位置、堆栈数和切片数的球体节点。
     *
     * @param engine Filament 引擎实例
     * @param radius 球体的半径，默认值为 Sphere.DEFAULT_RADIUS
     * @param center 球体的中心位置，默认值为 Sphere.DEFAULT_CENTER
     * @param stacks 球体的堆栈数，默认值为 Sphere.DEFAULT_STACKS
     * @param slices 球体的切片数，默认值为 Sphere.DEFAULT_SLICES
     * @param materialInstances 材质实例列表
     * @param builderApply 可选的渲染器构建器配置
     */
    constructor(
        engine: Engine,
        radius: Float = Sphere.DEFAULT_RADIUS,
        center: Position = Sphere.DEFAULT_CENTER,
        stacks: Int = Sphere.DEFAULT_STACKS,
        slices: Int = Sphere.DEFAULT_SLICES,
        materialInstances: List<MaterialInstance?>,
        builderApply: RenderableManager.Builder.() -> Unit = {}
    ) : this(
        engine = engine,
        geometry = Sphere.Builder()
            .radius(radius)
            .center(center)
            .stacks(stacks)
            .slices(slices)
            .build(engine),
        materialInstances = materialInstances,
        builderApply = builderApply
    )

    /**
     * 构造函数，用于创建一个带有单个材质实例的球体节点，并指定半径、中心位置、堆栈数和切片数。
     *
     * @param engine Filament 引擎实例
     * @param radius 球体的半径，默认值为 Sphere.DEFAULT_RADIUS
     * @param center 球体的中心位置，默认值为 Sphere.DEFAULT_CENTER
     * @param stacks 球体的堆栈数，默认值为 Sphere.DEFAULT_STACKS
     * @param slices 球体的切片数，默认值为 Sphere.DEFAULT_SLICES
     * @param materialInstance 单个材质实例，默认为 null
     * @param builderApply 可选的渲染器构建器配置
     */
    constructor(
        engine: Engine,
        radius: Float = Sphere.DEFAULT_RADIUS,
        center: Position = Sphere.DEFAULT_CENTER,
        stacks: Int = Sphere.DEFAULT_STACKS,
        slices: Int = Sphere.DEFAULT_SLICES,
        materialInstance: MaterialInstance? = null,
        builderApply: RenderableManager.Builder.() -> Unit = {}
    ) : this(
        engine = engine,
        geometry = Sphere.Builder()
            .radius(radius)
            .center(center)
            .stacks(stacks)
            .slices(slices)
            .build(engine),
        materialInstance = materialInstance,
        builderApply = builderApply
    )

    /**
     * 更新球体的几何形状。
     *
     * @param radius 球体的新半径，默认值为当前几何形状的半径
     * @param center 球体的新中心位置，默认值为当前几何形状的中心位置
     * @param stacks 球体的新堆栈数，默认值为当前几何形状的堆栈数
     * @param slices 球体的新切片数，默认值为当前几何形状的切片数
     * @return 返回更新后的几何形状
     */
    fun updateGeometry(
        radius: Float = geometry.radius,
        center: Position = geometry.center,
        stacks: Int = geometry.stacks,
        slices: Int = geometry.slices
    ) = setGeometry(geometry.update(engine, radius, center, stacks, slices))
}