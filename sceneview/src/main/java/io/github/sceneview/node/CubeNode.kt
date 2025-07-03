package io.github.sceneview.node

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import io.github.sceneview.geometries.Cube
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size

/**
 * CubeNode 是一个用于表示立方体节点的类。
 * 它继承自 GeometryNode，专门用于渲染立方体几何体。
 * 
 * 图形学原理：
 * - 立方体是一种基本的三维几何体，由6个矩形面组成。
 * - 每个面由4个顶点定义，这些顶点通过三角形网格连接。
 * - 通过调整立方体的尺寸（Size）和中心位置（Position），可以改变其在场景中的表现。
 */
open class CubeNode private constructor(
    engine: Engine,
    override val geometry: Cube, // 立方体的几何数据
    materialInstances: List<MaterialInstance?>, // 材质实例列表，用于定义表面属性
    primitivesOffsets: List<IntRange> = geometry.primitivesOffsets, // 原始图元的偏移范围
    builderApply: RenderableManager.Builder.() -> Unit = {} // 自定义渲染器构建逻辑
) : GeometryNode(
    engine = engine,
    geometry = geometry,
    materialInstances = materialInstances,
    primitivesOffsets = primitivesOffsets,
    builderApply = builderApply
) {

    /**
     * 构造函数：创建一个带有单个材质实例的立方体节点。
     *
     * 参数：
     * - engine: Filament 引擎实例，用于管理渲染资源。
     * - geometry: 立方体的几何数据。
     * - materialInstance: 单一材质实例，用于定义立方体的外观。
     * - builderApply: 可选的自定义渲染器构建逻辑。
     */
    constructor(
        engine: Engine,
        geometry: Cube,
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
     * 构造函数：根据指定的尺寸和中心位置创建立方体节点。
     *
     * 参数：
     * - engine: Filament 引擎实例。
     * - size: 立方体的尺寸（宽度、高度、深度）。
     * - center: 立方体的中心位置。
     * - materialInstances: 材质实例列表。
     * - builderApply: 自定义渲染器构建逻辑。
     */
    constructor(
        engine: Engine,
        size: Size = Cube.DEFAULT_SIZE,
        center: Position = Cube.DEFAULT_CENTER,
        materialInstances: List<MaterialInstance?>,
        builderApply: RenderableManager.Builder.() -> Unit = {}
    ) : this(
        engine = engine,
        geometry = Cube.Builder()
            .size(size)
            .center(center)
            .build(engine), // 使用 Cube.Builder 创建几何体
        materialInstances = materialInstances,
        builderApply = builderApply
    )

    /**
     * 构造函数：简化版构造函数，支持单个材质实例。
     *
     * 参数：
     * - engine: Filament 引擎实例。
     * - size: 立方体的尺寸。
     * - center: 立方体的中心位置。
     * - materialInstance: 单一材质实例。
     * - builderApply: 自定义渲染器构建逻辑。
     */
    constructor(
        engine: Engine,
        size: Size = Cube.DEFAULT_SIZE,
        center: Position = Cube.DEFAULT_CENTER,
        materialInstance: MaterialInstance? = null,
        builderApply: RenderableManager.Builder.() -> Unit = {}
    ) : this(
        engine = engine,
        geometry = Cube.Builder()
            .size(size)
            .center(center)
            .build(engine), // 使用 Cube.Builder 创建几何体
        materialInstance = materialInstance,
        builderApply = builderApply
    )

    /**
     * 更新立方体的几何数据。
     *
     * 参数：
     * - center: 新的中心位置，默认使用当前几何体的中心。
     * - size: 新的尺寸，默认使用当前几何体的尺寸。
     *
     * 返回值：
     * - 返回更新后的几何体对象。
     */
    fun updateGeometry(
        center: Position = geometry.center,
        size: Size = geometry.size
    ) = setGeometry(geometry.update(engine, center, size)) // 调用几何体的更新方法
}