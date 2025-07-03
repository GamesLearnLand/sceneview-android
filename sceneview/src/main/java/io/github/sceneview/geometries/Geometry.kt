package io.github.sceneview.geometries

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.RenderableManager
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.VertexBuffer
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.max
import dev.romainguy.kotlin.math.min
import io.github.sceneview.EntityInstance
import io.github.sceneview.math.Box
import io.github.sceneview.math.Color
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.normalToTangent
import java.nio.FloatBuffer
import java.nio.IntBuffer

typealias UvCoordinate = Float2
typealias UvScale = Float2

private const val kPositionSize = 3 // x, y, z
private const val kTangentSize = 4 // Quaternion: x, y, z, w
private const val kUVSize = 2 // x, y
private const val kColorSize = 4 // r, g, b, a

/**
 * 几何体参数，用于构建和更新可渲染对象（Renderable）
 *
 * 可渲染对象由多个原始图元（Primitive）组成。
 * 您可以声明一个图元，让几何体的所有部分使用相同的材质，
 * 或者为每个三角形索引声明一个不同的图元，使用不同的材质。
 * 我们可以为每个面声明n个图元，并为每个图元分配不同的材质实例，
 * 并设置不同的参数。
 *
 * @see Cube
 * @see Cylinder
 * @see Plane
 * @see Sphere
 */
open class Geometry internal constructor(
    /**
     * 图元类型，定义了如何解释顶点索引
     * 例如：TRIANGLES、TRIANGLE_STRIP 等
     */
    val primitiveType: PrimitiveType,
    
    /**
     * 顶点列表，包含位置、法线、纹理坐标和颜色等信息
     */
    vertices: List<Vertex>,
    
    /**
     * 顶点缓冲区，存储所有顶点数据
     */
    val vertexBuffer: VertexBuffer,
    
    /**
     * 原始图元的索引列表，每个子网格对应一组索引
     */
    primitivesIndices: List<List<Int>>,
    
    /**
     * 索引缓冲区，存储顶点索引数据
     */
    val indexBuffer: IndexBuffer,
    
    /**
     * 各个原始图元在索引缓冲区中的偏移范围
     */
    var primitivesOffsets: List<IntRange>,
    
    /**
     * 几何体的包围盒，用于快速碰撞检测和视锥体裁剪
     */
    var boundingBox: Box
) {
    /**
     * 代表几何体的一个顶点
     *
     * @property position 顶点的位置坐标 (x, y, z)
     * @property normal 法线向量，用于光照计算，默认为null
     * @property uvCoordinate 纹理坐标，用于贴图映射，默认为null
     * @property color 顶点颜色，默认为null
     */
    data class Vertex(
        val position: Position = Position(),
        val normal: Direction? = null,
        val uvCoordinate: UvCoordinate? = null,
        val color: Color? = null
    )

//    /**
//     * 代表几何体的一个子网格
//     *
//     * 每个几何体可能有多个子网格
//     */
//    data class PrimitiveIndices(val indices: List<Int>) {
//        constructor(vararg indices: Int) : this(indices.toList())
//    }

    open class Builder(val primitiveType: PrimitiveType = PrimitiveType.TRIANGLES) {
        protected val vertexBuilder = VertexBuffer.Builder()
        protected val indexBuilder = IndexBuffer.Builder()

        protected var vertices: List<Vertex> = listOf()
        protected var indices: List<List<Int>> = listOf()

        /**
         * 设置顶点数据
         *
         * @param vertices 顶点列表
         */
        fun vertices(vertices: List<Vertex>) = apply {
            vertexBuilder.bufferCount(
                1 + // 位置始终存在
                        (if (vertices.hasNormals) 1 else 0) +
                        (if (vertices.hasUvCoordinates) 1 else 0) +
                        (if (vertices.hasColors) 1 else 0)
            )
            vertexBuilder.vertexCount(vertices.size)

            // 位置属性
            var bufferIndex = 0
            vertexBuilder.attribute(
                VertexBuffer.VertexAttribute.POSITION,
                bufferIndex,
                VertexBuffer.AttributeType.FLOAT3,
                0,
                kPositionSize * Float.SIZE_BYTES
            )
            
            // 切线属性（基于法线计算）
            if (vertices.hasNormals) {
                bufferIndex++
                vertexBuilder.attribute(
                    VertexBuffer.VertexAttribute.TANGENTS,
                    bufferIndex,
                    VertexBuffer.AttributeType.FLOAT4,
                    0,
                    kTangentSize * Float.SIZE_BYTES
                )
                vertexBuilder.normalized(VertexBuffer.VertexAttribute.TANGENTS)
            }
            
            // 纹理坐标属性
            if (vertices.hasUvCoordinates) {
                bufferIndex++
                vertexBuilder.attribute(
                    VertexBuffer.VertexAttribute.UV0,
                    bufferIndex,
                    VertexBuffer.AttributeType.FLOAT2,
                    0,
                    kUVSize * Float.SIZE_BYTES
                )
            }
            
            // 颜色属性
            if (vertices.hasColors) {
                bufferIndex++
                vertexBuilder.attribute(
                    VertexBuffer.VertexAttribute.COLOR,
                    bufferIndex,
                    VertexBuffer.AttributeType.FLOAT4,
                    0,
                    kColorSize * Float.SIZE_BYTES
                )
                vertexBuilder.normalized(VertexBuffer.VertexAttribute.COLOR)
            }
            this.vertices = vertices
        }

        /**
         * 设置原始图元的索引
         *
         * @param indices 索引列表
         */
        fun primitivesIndices(indices: List<List<Int>>) = apply {
            indexBuilder.indexCount(indices.sumOf { it.size })
                .bufferType(IndexBuffer.Builder.IndexType.UINT)
            this.indices = indices
        }

        /**
         * 设置单个原始图元的索引
         *
         * @param indices 索引列表
         */
        fun indices(indices: List<Int>) = primitivesIndices(listOf(indices))

        /**
         * 构建几何体
         *
         * @param engine 渲染引擎
         * @param constructor 几何体构造函数
         * @return 构建的几何体实例
         */
        fun <T : Geometry> build(
            engine: Engine,
            constructor: (
                vertexBuffer: VertexBuffer, indexBuffer: IndexBuffer,
                offsets: List<IntRange>, boundingBox: Box
            ) -> T
        ): T {
            val vertexBuffer = vertexBuilder.build(engine)
            val boundingBox = vertexBuffer.setVertices(engine, vertices)
            val indexBuffer = indexBuilder.build(engine).apply {
                setIndices(engine, indices.flatten())
            }
            return constructor(
                vertexBuffer,
                indexBuffer,
                indices.getOffsets(),
                boundingBox
            )
        }

        /**
         * 默认构建方法
         *
         * @param engine 渲染引擎
         * @return 构建的几何体实例
         */
        open fun build(engine: Engine) =
            build(engine) { vertexBuffer, indexBuffer, offsets, boundingBox ->
                Geometry(
                    primitiveType, vertices, vertexBuffer, indices, indexBuffer,
                    offsets, boundingBox
                )
            }
    }

    var vertices: List<Vertex> = vertices
        private set

    var primitivesIndices: List<List<Int>> = primitivesIndices
        private set

    val indices: List<Int>
        get() = primitivesIndices.flatten()

    /**
     * 更新顶点数据
     *
     * @param engine 渲染引擎
     * @param vertices 新的顶点列表
     */
    fun setVertices(engine: Engine, vertices: List<Vertex>) {
        this.vertices = vertices
        boundingBox = vertexBuffer.setVertices(engine, vertices)
    }

    /**
     * 更新原始图元的索引
     *
     * @param engine 渲染引擎
     * @param primitivesIndices 新的原始图元索引列表
     */
    fun setPrimitivesIndices(engine: Engine, primitivesIndices: List<List<Int>>) {
        this.primitivesIndices = primitivesIndices
        primitivesOffsets = primitivesIndices.getOffsets()
        indexBuffer.setIndices(engine, primitivesIndices.flatMap { it.indices })
    }

    /**
     * 更新几何体数据
     *
     * @param engine 渲染引擎
     * @param vertices 新的顶点列表
     * @param primitivesIndices 新的原始图元索引列表
     */
    fun update(
        engine: Engine,
        vertices: List<Vertex> = this.vertices,
        primitivesIndices: List<List<Int>> = this.primitivesIndices
    ) = apply {
        if (this.vertices != vertices) {
            setVertices(engine, vertices)
        }
        if (this.primitivesIndices != primitivesIndices) {
            setPrimitivesIndices(engine, primitivesIndices)
        }
    }
}

/**
 * 判断顶点列表是否包含法线信息
 */
val List<Geometry.Vertex>.hasNormals get() = any { it.normal != null }

/**
 * 判断顶点列表是否包含纹理坐标信息
 */
val List<Geometry.Vertex>.hasUvCoordinates get() = any { it.uvCoordinate != null }

/**
 * 判断顶点列表是否包含颜色信息
 */
val List<Geometry.Vertex>.hasColors get() = any { it.color != null }

/**
 * 设置顶点缓冲区的数据
 *
 * @param engine 渲染引擎
 * @param vertices 顶点列表
 * @return 计算得到的包围盒
 */
fun VertexBuffer.setVertices(engine: Engine, vertices: List<Geometry.Vertex>): Box {
    var bufferIndex = 0

    // 创建位置缓冲区
    setBufferAt(
        engine, bufferIndex,
        FloatBuffer.allocate(vertices.size * kPositionSize).apply {
            vertices.forEach { put(it.position.toFloatArray()) }
            flip()
        }, 0,
        vertices.size * kPositionSize
    )

    // 创建切线缓冲区
    if (vertices.hasNormals) {
        bufferIndex++
        setBufferAt(
            engine, bufferIndex,
            FloatBuffer.allocate(vertices.size * kTangentSize).apply {
                vertices.forEach { put(normalToTangent(it.normal!!).toFloatArray()) }
                flip()
            }, 0,
            vertices.size * kTangentSize
        )
    }

    // 创建纹理坐标缓冲区
    if (vertices.hasUvCoordinates) {
        bufferIndex++
        setBufferAt(
            engine, bufferIndex,
            FloatBuffer.allocate(vertices.size * kUVSize).apply {
                vertices.forEach { put(it.uvCoordinate!!.toFloatArray()) }
                rewind()
            }, 0,
            vertices.size * kUVSize
        )
    }

    // 创建颜色缓冲区
    if (vertices.hasColors) {
        bufferIndex++
        setBufferAt(
            engine, bufferIndex,
            FloatBuffer.allocate(vertices.size * kColorSize).apply {
                vertices.forEach { put(it.color!!.toFloatArray()) }
                rewind()
            }, 0,
            vertices.size * kColorSize
        )
    }

    // 计算轴对齐边界框（AABB）
    var minPosition = Position(vertices.first().position)
    var maxPosition = Position(vertices.first().position)
    vertices.forEach { vertex ->
        minPosition = min(minPosition, vertex.position)
        maxPosition = max(maxPosition, vertex.position)
    }

    val halfExtent = (maxPosition - minPosition) / 2.0f
    val center = minPosition + halfExtent
    return Box(center, halfExtent)
}

/**
 * 设置索引缓冲区的数据
 *
 * @param engine 渲染引擎
 * @param indices 索引列表
 */
fun IndexBuffer.setIndices(
    engine: Engine,
    indices: List<Int>
) {
    // 填充索引缓冲区
    setBuffer(engine,
        IntBuffer.allocate(indices.size).apply {
            indices.forEach { put(it) }
            flip()
        })
}


/**
 * 获取原始图元的偏移范围
 *
 * @return 偏移范围列表
 */
fun List<List<Int>>.getOffsets(): List<IntRange> {
    var indexStart = 0
    return map { primitiveIndices ->
        (indexStart until indexStart + primitiveIndices.size).also {
            indexStart += primitiveIndices.size
        }
    }
}

/**
 * 为可渲染对象指定几何体数据
 *
 * Filament 的原始图元必须关联一个 [VertexBuffer] 和 [IndexBuffer]。
 * 通常，每个原始图元通过一对链式调用来指定：
 * [geometry] 和 [RenderableManager.Builder.material]。
 * @see Geometry
 * @see Plane
 * @see Cube
 * @see Sphere
 * @see Cylinder
 * @see RenderableManager.Builder.geometry
 */
fun RenderableManager.Builder.geometry(
    geometry: Geometry,
    offsets: List<IntRange> = geometry.primitivesOffsets
) = apply {
    offsets.forEachIndexed { primitiveIndex, offset ->
        geometry(
            primitiveIndex,
            geometry.primitiveType,
            geometry.vertexBuffer,
            geometry.indexBuffer,
            offset.first,
            offset.count()
        )
    }
    // 整体包围盒
    boundingBox(geometry.boundingBox)
}

/**
 * 更改给定可渲染实例的几何体
 *
 * @see Geometry
 * @see Plane
 * @see Cube
 * @see Sphere
 * @see Cylinder
 * @see RenderableManager.Builder.geometry
 */
fun RenderableManager.setGeometry(
    instance: EntityInstance,
    geometry: Geometry,
    offsets: List<IntRange> = geometry.primitivesOffsets
) {
    offsets.forEachIndexed { primitiveIndex, offset ->
        setGeometryAt(
            instance,
            primitiveIndex,
            geometry.primitiveType,
            geometry.vertexBuffer,
            geometry.indexBuffer,
            offset.first,
            offset.count()
        )
    }
    // 整体包围盒
    setAxisAlignedBoundingBox(instance, geometry.boundingBox)
}