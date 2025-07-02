package io.github.sceneview

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.opengl.EGLContext
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.google.android.filament.ColorGrading
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.Fence
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.View.AntiAliasing
import com.google.android.filament.View.BlendMode
import com.google.android.filament.View.QualityLevel
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.Utils
import dev.romainguy.kotlin.math.Float2
import io.github.sceneview.collision.CollisionSystem
import io.github.sceneview.collision.HitResult
import io.github.sceneview.environment.Environment
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.gesture.GestureDetector
import io.github.sceneview.gesture.MoveGestureDetector
import io.github.sceneview.gesture.RotateGestureDetector
import io.github.sceneview.gesture.ScaleGestureDetector
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.managers.color
import io.github.sceneview.math.Position
import io.github.sceneview.math.Transform
import io.github.sceneview.math.colorOf
import io.github.sceneview.math.toColor
import io.github.sceneview.model.Model
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.ViewNode2
import io.github.sceneview.utils.OpenGL
import io.github.sceneview.utils.SurfaceMirrorer
import io.github.sceneview.utils.intervalSeconds
import io.github.sceneview.utils.readBuffer
import io.github.sceneview.utils.setKeepScreenOn

// 类型别名定义
typealias Entity = Int
typealias EntityInstance = Int
typealias FilamentEntity = com.google.android.filament.Entity
typealias FilamentEntityInstance = com.google.android.filament.EntityInstance

/**
 * A SurfaceView that manages rendering and interactions with the 3D scene.
 * 管理3D场景渲染和交互的SurfaceView。
 *
 * Maintains the scene graph, a hierarchical organization of a scene's content.
 * 维护场景图，这是场景内容的层次化组织结构。
 * A scene can have zero or more child nodes and each node can have zero or more child nodes.
 * 一个场景可以有零个或多个子节点，每个节点也可以有零个或多个子节点。
 * The Scene also provides hit testing, a way to detect which node is touched by a MotionEvent or
 * Ray.
 * 场景还提供碰撞检测功能，这是一种检测哪个节点被MotionEvent或射线触碰的方法。
 */
open class SceneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
    /**
     * Provide your own instance if you want to share Filament resources between multiple views.
     * 如果您想在多个视图之间共享Filament资源，请提供您自己的实例。
     */
    sharedEngine: Engine? = null,
    /**
     * Consumes a blob of glTF 2.0 content (either JSON or GLB) and produces a [Model] object, which is
     * a bundle of Filament textures, vertex buffers, index buffers, etc.
     * 消费glTF 2.0内容块（JSON或GLB格式）并生成[Model]对象，该对象是Filament纹理、顶点缓冲区、索引缓冲区等的集合。
     *
     * A [Model] is composed of 1 or more [ModelInstance] objects which contain entities and components.
     * [Model]由一个或多个包含实体和组件的[ModelInstance]对象组成。
     */
    sharedModelLoader: ModelLoader? = null,
    /**
     * A Filament Material defines the visual appearance of an object.
     * Filament材质定义了对象的视觉外观。
     *
     * Materials function as a templates from which [MaterialInstance]s can be spawned.
     * 材质作为模板，可以从中生成[MaterialInstance]实例。
     */
    sharedMaterialLoader: MaterialLoader? = null,
    /**
     * Utility for decoding an HDR file or consuming KTX1 files and producing Filament textures,
     * IBLs, and sky boxes.
     * 用于解码HDR文件或处理KTX1文件并生成Filament纹理、IBL和天空盒的工具。
     *
     * KTX is a simple container format that makes it easy to bundle miplevels and cubemap faces
     * into a single file.
     * KTX是一种简单的容器格式，可以轻松地将mip级别和立方体贴图面打包到单个文件中。
     */
    sharedEnvironmentLoader: EnvironmentLoader? = null,
    /**
     * Provide your own instance if you want to share [Node]s' scene between multiple views.
     * 如果您想在多个视图之间共享[Node]的场景，请提供您自己的实例。
     */
    sharedScene: Scene? = null,
    /**
     * Encompasses all the state needed for rendering a {@link Scene}.
     * 包含渲染{@link Scene}所需的所有状态。
     *
     * [View] instances are heavy objects that internally cache a lot of data needed for
     * rendering. It is not advised for an application to use many View objects.
     * [View]实例是重量级对象，内部缓存了大量渲染所需的数据。不建议应用程序使用太多View对象。
     *
     * For example, in a game, a [View] could be used for the main scene and another one for the
     * game's user interface. More <code>View</code> instances could be used for creating special
     * effects (e.g. a [View] is akin to a rendering pass).
     * 例如，在游戏中，一个[View]可以用于主场景，另一个用于游戏用户界面。更多的<code>View</code>实例可以用于创建特殊效果（例如，[View]类似于渲染通道）。
     */
    sharedView: View? = null,
    /**
     * A [Renderer] instance represents an operating system's window.
     * [Renderer]实例代表操作系统的窗口。
     *
     * Typically, applications create a [Renderer] per window. The [Renderer] generates drawing
     * commands for the render thread and manages frame latency.
     * 通常，应用程序为每个窗口创建一个[Renderer]。[Renderer]为渲染线程生成绘制命令并管理帧延迟。
     */
    sharedRenderer: Renderer? = null,
    /**
     * Represents a virtual camera, which determines the perspective through which the scene is
     * viewed.
     * 代表虚拟摄像机，决定观察场景的视角。
     *
     * All other functionality in Node is supported. You can access the position and rotation of the
     * camera, assign a collision shape to it, or add children to it.
     * 支持Node中的所有其他功能。您可以访问摄像机的位置和旋转，为其分配碰撞形状，或向其添加子节点。
     */
    sharedCameraNode: CameraNode? = null,
    /**
     * Always add a direct light source since it is required for shadowing.
     * 始终添加直接光源，因为阴影渲染需要它。
     *
     * We highly recommend adding an [IndirectLight] as well.
     * 我们强烈建议同时添加[IndirectLight]。
     */
    sharedMainLightNode: LightNode? = null,
    /**
     * Defines the lighting environment and the skybox of the scene.
     * 定义场景的光照环境和天空盒。
     *
     * Environments are usually captured as high-resolution HDR equirectangular images and processed
     * by the cmgen tool to generate the data needed by IndirectLight.
     * 环境通常以高分辨率HDR等距柱状投影图像的形式捕获，并通过cmgen工具处理以生成IndirectLight所需的数据。
     *
     * You can also process an hdr at runtime but this is more consuming.
     * 您也可以在运行时处理hdr，但这会消耗更多资源。
     *
     * - Currently IndirectLight is intended to be used for "distant probes", that is, to represent
     * global illumination from a distant (i.e. at infinity) environment, such as the sky or distant
     * mountains.
     * - 目前IndirectLight旨在用于"远距离探针"，即表示来自远距离（即无限远）环境的全局照明，如天空或远山。
     * Only a single IndirectLight can be used in a Scene. This limitation will be lifted in the
     * future.
     * 一个场景中只能使用一个IndirectLight。这个限制将在未来解除。
     *
     * - When added to a Scene, the Skybox fills all untouched pixels.
     * - 当添加到场景中时，天空盒会填充所有未被几何体触及的像素。
     *
     * @see [EnvironmentLoader]
     */
    sharedEnvironment: Environment? = null,
    /**
     * Controls whether the render target (SurfaceView) is opaque or not.
     * 控制渲染目标（SurfaceView）是否不透明。
     * The render target is considered opaque by default.
     * 渲染目标默认被认为是不透明的。
     */
    isOpaque: Boolean = true,
    /**
     * Physics system to handle collision between nodes, hit testing on a nodes,...
     * 物理系统，用于处理节点之间的碰撞、节点的碰撞检测等。
     */
    sharedCollisionSystem: CollisionSystem? = null,
    /**
     * Helper that enables camera interaction similar to sketchfab or Google Maps.
     * 启用类似于sketchfab或Google Maps的摄像机交互的辅助工具。
     *
     * Needs to be a callable function because it can be reinitialized in case of viewport change
     * or camera node manual position changed.
     * 需要是一个可调用函数，因为在视口变化或摄像机节点手动位置改变时可以重新初始化。
     *
     * The first onTouch event will make the first manipulator build. So you can change the camera
     * position before any user gesture.
     * 第一个onTouch事件将构建第一个操作器。因此您可以在任何用户手势之前更改摄像机位置。
     *
     * Clients notify the camera manipulator of various mouse or touch events, then periodically
     * call its getLookAt() method so that they can adjust their camera(s). Three modes are
     * supported: ORBIT, MAP, and FREE_FLIGHT. To construct a manipulator instance, the desired mode
     * is passed into the create method.
     * 客户端通知摄像机操作器各种鼠标或触摸事件，然后定期调用其getLookAt()方法以便调整摄像机。支持三种模式：ORBIT、MAP和FREE_FLIGHT。要构造操作器实例，需要将所需模式传递给create方法。
     */
    cameraManipulator: CameraGestureDetector.CameraManipulator? =
        createDefaultCameraManipulator(sharedCameraNode?.worldPosition),
    /**
     * Used for Node's that can display an Android [View]
     * 用于可以显示Android [View]的Node。
     *
     * Manages a [FrameLayout] that is attached directly to a [WindowManager] that other views can be
     * added and removed from.
     * 管理直接附加到[WindowManager]的[FrameLayout]，其他视图可以从中添加和移除。
     *
     * To render a [View], the [View] must be attached to a [WindowManager] so that it can be properly
     * drawn. This class encapsulates a [FrameLayout] that is attached to a [WindowManager] that other
     * views can be added to as children. This allows us to safely and correctly draw the [View]
     * associated with a [RenderableManager] [Entity] and a [MaterialInstance] while keeping them
     * isolated from the rest of the activities View hierarchy.
     * 要渲染[View]，必须将[View]附加到[WindowManager]以便正确绘制。此类封装了附加到[WindowManager]的[FrameLayout]，其他视图可以作为子视图添加到其中。这使我们能够安全正确地绘制与[RenderableManager] [Entity]和[MaterialInstance]关联的[View]，同时将它们与活动的其余View层次结构隔离。
     *
     * Additionally, this manages the lifecycle of the window to help ensure that the window is
     * added/removed from the WindowManager at the appropriate times.
     * 此外，这还管理窗口的生命周期，以帮助确保在适当的时间从WindowManager添加/移除窗口。
     */
    var viewNodeWindowManager: ViewNode2.WindowManager? = null,
    /**
     * The listener invoked for all the gesture detector callbacks.
     * 为所有手势检测器回调调用的监听器。
     *
     * Responds to Android touch events with listeners.
     * 通过监听器响应Android触摸事件。
     */
    onGestureListener: GestureDetector.OnGestureListener? = null,
    var onTouchEvent: ((e: MotionEvent, hitResult: HitResult?) -> Boolean)? = null,
    sharedActivity: ComponentActivity? = null,
    sharedLifecycle: Lifecycle? = null,
) : SurfaceView(context, attrs, defStyleAttr, defStyleRes) {

    /** ## Deprecated: Use [CameraGestureDetector.DefaultCameraManipulator]
     * ## 已弃用：使用[CameraGestureDetector.DefaultCameraManipulator]
     *
     * Replace `manipulator = Manipulator.Builder().build()` with
     * `cameraManipulator = CameraGestureDetector.DefaultCameraManipulator(manipulator =
     * Manipulator.Builder().build())`
     * 将`manipulator = Manipulator.Builder().build()`替换为
     * `cameraManipulator = CameraGestureDetector.DefaultCameraManipulator(manipulator =
     * Manipulator.Builder().build())`
     */
    @Deprecated("Use CameraGestureDetector.DefaultCameraManipulator")
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        defStyleRes: Int = 0,
        sharedEngine: Engine? = null,
        sharedModelLoader: ModelLoader? = null,
        sharedMaterialLoader: MaterialLoader? = null,
        sharedEnvironmentLoader: EnvironmentLoader? = null,
        sharedScene: Scene? = null,
        sharedView: View? = null,
        sharedRenderer: Renderer? = null,
        sharedCameraNode: CameraNode? = null,
        sharedMainLightNode: LightNode? = null,
        sharedEnvironment: Environment? = null,
        isOpaque: Boolean = true,
        sharedCollisionSystem: CollisionSystem? = null,
        manipulator: Manipulator,
        viewNodeWindowManager: ViewNode2.WindowManager? = null,
        onGestureListener: GestureDetector.OnGestureListener? = null,
        onTouchEvent: ((e: MotionEvent, hitResult: HitResult?) -> Boolean)? = null,
        sharedActivity: ComponentActivity? = null,
        sharedLifecycle: Lifecycle? = null,
    ) : this(
        context = context,
        attrs = attrs,
        defStyleAttr = defStyleAttr,
        defStyleRes = defStyleRes,
        sharedEngine = sharedEngine,
        sharedModelLoader = sharedModelLoader,
        sharedMaterialLoader = sharedMaterialLoader,
        sharedEnvironmentLoader = sharedEnvironmentLoader,
        sharedScene = sharedScene,
        sharedView = sharedView,
        sharedRenderer = sharedRenderer,
        sharedCameraNode = sharedCameraNode,
        sharedMainLightNode = sharedMainLightNode,
        sharedEnvironment = sharedEnvironment,
        isOpaque = isOpaque,
        sharedCollisionSystem = sharedCollisionSystem,
        cameraManipulator = CameraGestureDetector.createDefaultCameraManipulator(manipulator),
        viewNodeWindowManager = viewNodeWindowManager,
        onGestureListener = onGestureListener,
        onTouchEvent = onTouchEvent,
        sharedActivity = sharedActivity,
        sharedLifecycle = sharedLifecycle,
    )

    val engine = sharedEngine ?: createEglContext().let {
        defaultEglContext = it
        createEngine(it).also { defaultEngine = it }
    }

    val modelLoader = sharedModelLoader ?: createModelLoader(engine, context).also {
        defaultModelLoader = it
    }
    val materialLoader = sharedMaterialLoader ?: createMaterialLoader(engine, context).also {
        defaultMaterialLoader = it
    }

    /**
     * Utility for decoding an HDR file or consuming KTX1 files and producing Filament textures,
     * IBLs, and sky boxes.
     * 用于解码HDR文件或处理KTX1文件并生成Filament纹理、IBL和天空盒的工具。
     *
     * KTX is a simple container format that makes it easy to bundle miplevels and cubemap faces
     * into a single file.
     * KTX是一种简单的容器格式，可以轻松地将mip级别和立方体贴图面打包到单个文件中。
     */
    val environmentLoader = sharedEnvironmentLoader
        ?: createEnvironmentLoader(engine, context).also { defaultEnvironmentLoader = it }

    /**
     * Defines the lighting environment and the skybox of the scene.
     * 定义场景的光照环境和天空盒。
     *
     * Environments are usually captured as high-resolution HDR equirectangular images and processed
     * by the cmgen tool to generate the data needed by IndirectLight.
     * 环境通常以高分辨率HDR等距柱状投影图像的形式捕获，并通过cmgen工具处理以生成IndirectLight所需的数据。
     *
     * You can also process an hdr at runtime but this is more consuming.
     * 您也可以在运行时处理hdr，但这会消耗更多资源。
     *
     * - Currently IndirectLight is intended to be used for "distant probes", that is, to represent
     * global illumination from a distant (i.e. at infinity) environment, such as the sky or distant
     * mountains.
     * - 目前IndirectLight旨在用于"远距离探针"，即表示来自远距离（即无限远）环境的全局照明，如天空或远山。
     * Only a single IndirectLight can be used in a Scene. This limitation will be lifted in the
     * future.
     * 一个场景中只能使用一个IndirectLight。这个限制将在未来解除。
     *
     * - When added to a Scene, the Skybox fills all untouched pixels.
     * - 当添加到场景中时，天空盒会填充所有未被几何体触及的像素。
     *
     * @see [EnvironmentLoader]
     */
    var environment = sharedEnvironment ?: createEnvironment(environmentLoader, isOpaque)
        set(value) {
            if (field != value) {
                field = value
                indirectLight = environment.indirectLight
                skybox = environment.skybox
            }
        }

    val view = (sharedView ?: createView(engine).also { defaultView = it }).also { view ->
//        setOpaque(isOpaque)
        view.blendMode = if (isOpaque) BlendMode.OPAQUE else BlendMode.TRANSLUCENT
        view.scene = (sharedScene ?: createScene(engine).also { defaultScene = it }).also { scene ->
            scene.indirectLight = environment.indirectLight
            scene.skybox = environment.skybox
        }
    }

    // 场景属性，获取和设置当前视图的场景
    var scene
        get() = view.scene!!
        set(value) {
            if (view.scene != value) {
                view.scene = value
            }
        }

    // 渲染器，负责生成绘制命令和管理帧延迟
    val renderer =
        (sharedRenderer ?: createRenderer(engine).also { defaultRenderer = it }).also { renderer ->
            if (!isOpaque) {
                // clear the swapchain with transparent pixels
                // 用透明像素清除交换链
                renderer.clearOptions = renderer.clearOptions.apply {
                    clear = !isOpaque
                }
            }
        }

    // UI辅助工具，管理Surface的生命周期和渲染回调
    val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).also { uiHelper ->
        uiHelper.renderCallback = SurfaceCallback()
        uiHelper.isOpaque = isOpaque
        // Make the render target transparent
        // 使渲染目标透明
        uiHelper.attachTo(this@SceneView)
    }

    protected var _cameraNode: CameraNode? = null

    /**
     * Represents a virtual camera, which determines the perspective through which the scene is
     * viewed.
     * 代表虚拟摄像机，决定观察场景的视角。
     *
     * All other functionality in Node is supported. You can access the position and rotation of the
     * camera, assign a collision shape to it, or add children to it. Disabling the camera turns off
     * rendering.
     * 支持Node中的所有其他功能。您可以访问摄像机的位置和旋转，为其分配碰撞形状，或向其添加子节点。禁用摄像机会关闭渲染。
     */
    open val cameraNode: CameraNode get() = _cameraNode!!

    private var _mainLightNode: LightNode? =
        (sharedMainLightNode ?: createMainLightNode(engine).also { defaultMainLight = it })

    /**
     * Always add a direct light source since it is required for shadowing.
     * 始终添加直接光源，因为阴影渲染需要它。
     *
     * We highly recommend adding an [IndirectLight] as well.
     * 我们强烈建议同时添加[IndirectLight]。
     */
    open var mainLightNode: LightNode?
        get() = _mainLightNode
        set(value) {
            if (_mainLightNode != value) {
                _mainLightNode?.let { removeNode(it) }
                _mainLightNode = value
                value?.let { addNode(it) }
            }
        }

    /**
     * IndirectLight is used to simulate environment lighting.
     * IndirectLight用于模拟环境光照。
     *
     * Environment lighting has a two components:
     * 环境光照有两个组件：
     * - irradiance
     * - 辐照度
     * - reflections (specular component)
     * - 反射（镜面反射组件）
     *
     * @see IndirectLight.Builder
     * @see EnvironmentLoader
     */
    open var indirectLight: IndirectLight?
        get() = scene.indirectLight
        set(value) {
            if (scene.indirectLight != value) {
                scene.indirectLight = value
            }
        }

    /**
     * The Skybox is drawn last and covers all pixels not touched by geometry.
     * 天空盒最后绘制，覆盖所有未被几何体触及的像素。
     *
     * When added to a [SceneView], the `Skybox` fills all untouched pixels.
     * 当添加到[SceneView]时，`Skybox`填充所有未触及的像素。
     *
     * The Skybox to use to fill untouched pixels, or null to unset the Skybox.
     * 用于填充未触及像素的天空盒，或null来取消设置天空盒。
     *
     * @see Skybox.Builder
     * @see ModelLoader
     */
    var skybox: Skybox?
        get() = scene.skybox
        set(value) {
            if (scene.skybox != value) {
                scene.skybox = value
            }
        }

    /**
     * A list of child nodes directly attached to this SceneView.
     * 直接附加到此SceneView的子节点列表。
     *
     * Each node can have an arbitrary number of child nodes and one parent. The parent may be
     * another node, or the [SceneView].
     * 每个节点可以有任意数量的子节点和一个父节点。父节点可以是另一个节点，或者是[SceneView]。
     */
    var childNodes = listOf<Node>()
        set(value) {
            val removedNodes = (field - value.toSet())
            val addedNodes = (value - field.toSet())
            field = value.toList()
            removedNodes.forEach {
                removeNode(it)
            }
            addedNodes.forEach {
                addNode(it)
            }
        }

    /**
     * Inverts winding for front face rendering.
     * 反转正面渲染的缠绕顺序。
     *
     * Inverts the winding order of front faces. By default front faces use a counter-clockwise
     * winding order. When the winding order is inverted, front faces are faces with a clockwise
     * winding order.
     * 反转正面的缠绕顺序。默认情况下，正面使用逆时针缠绕顺序。当缠绕顺序反转时，正面是具有顺时针缠绕顺序的面。
     *
     * Changing the winding order will directly affect the culling mode in materials
     * (see [com.google.android.filament.Material.getCullingMode]).
     * 改变缠绕顺序将直接影响材质中的剔除模式（参见[com.google.android.filament.Material.getCullingMode]）。
     *
     * Inverting the winding order of front faces is useful when rendering mirrored reflections
     * (water, mirror surfaces, front camera in AR, etc.).
     * 反转正面的缠绕顺序在渲染镜像反射（水面、镜面、AR中的前置摄像头等）时很有用。
     *
     * `true` to invert front faces, false otherwise.
     * `true`表示反转正面，否则为false。
     */
    var isFrontFaceWindingInverted: Boolean
        get() = view.isFrontFaceWindingInverted
        set(value) {
            view.isFrontFaceWindingInverted = value
        }

    /**
     * Physics system to handle collision between nodes, hit testing on a nodes,...
     * 物理系统，用于处理节点间的碰撞、节点的命中测试等。
     */
    val collisionSystem = (sharedCollisionSystem ?: createCollisionSystem(view).also {
        defaultCollisionSystem = it
    })

    /**
     * Invoked when an frame is processed.
     * 当处理帧时调用。
     *
     * Registers a callback to be invoked when a valid Frame is processing.
     * 注册一个回调，在处理有效帧时调用。
     *
     * The callback to be invoked once per frame **immediately before the scene is updated.
     * 每帧调用一次的回调，**在场景更新之前立即调用。
     *
     * The callback will only be invoked if the Frame is considered as valid.
     * 只有当帧被认为是有效的时才会调用回调。
     */
    var onFrame: ((frameTimeNanos: Long) -> Unit)? = null

    /**
     * Detects various gestures and events.
     * 检测各种手势和事件。
     *
     * The gesture listener callback will notify users when a particular motion event has occurred.
     * 手势监听器回调将在特定运动事件发生时通知用户。
     * Responds to Android touch events with listeners.
     * 通过监听器响应Android触摸事件。
     */
    var gestureDetector: GestureDetector? =
        GestureDetector(context = context, listener = onGestureListener)
        private set

    /**
     * The listener invoked for all the gesture detector callbacks.
     * 为所有手势检测器回调调用的监听器。
     *
     * Responds to Android touch events with listeners.
     * 通过监听器响应Android触摸事件。
     */
    var onGestureListener: GestureDetector.OnGestureListener?
        get() = gestureDetector?.listener
        set(value) {
            gestureDetector?.listener = value
        }

    var cameraGestureDetector: CameraGestureDetector? =
        CameraGestureDetector(viewHeight = ::getHeight, cameraManipulator = cameraManipulator)
        private set

    /**
     * Helper that enables camera interaction similar to sketchfab or Google Maps.
     * 启用类似于sketchfab或Google Maps的摄像机交互的辅助工具。
     *
     * Needs to be a callable function because it can be reinitialized in case of viewport change
     * or camera node manual position changed.
     * 需要是一个可调用函数，因为在视口更改或摄像机节点手动位置更改时可以重新初始化。
     *
     * The first onTouch event will make the first manipulator build. So you can change the camera
     * position before any user gesture.
     * 第一个onTouch事件将构建第一个操作器。因此您可以在任何用户手势之前更改摄像机位置。
     *
     * Clients notify the camera manipulator of various mouse or touch events, then periodically
     * call its getLookAt() method so that they can adjust their camera(s). Three modes are
     * supported: ORBIT, MAP, and FREE_FLIGHT. To construct a manipulator instance, the desired mode
     * is passed into the create method.
     * 客户端通知摄像机操作器各种鼠标或触摸事件，然后定期调用其getLookAt()方法以便调整摄像机。
     * 支持三种模式：ORBIT、MAP和FREE_FLIGHT。要构造操作器实例，需要将所需模式传递给create方法。
     */
    var cameraManipulator: CameraGestureDetector.CameraManipulator?
        get() = cameraGestureDetector?.cameraManipulator
        set(value) {
            cameraGestureDetector?.cameraManipulator = value
        }

    protected open val activity: ComponentActivity? = sharedActivity
        get() = field ?: try {
            findFragment<Fragment>().requireActivity()
        } catch (e: Exception) {
            context as? ComponentActivity
        }

    open var lifecycle: Lifecycle? = sharedLifecycle
        set(value) {
            field?.removeObserver(lifecycleObserver)
            field = value
            value?.addObserver(lifecycleObserver)
        }

    protected var isDestroyed = false

    private val displayHelper = DisplayHelper(context)
    private var swapChain: SwapChain? = null
    private val lifecycleObserver = LifeCycleObserver()
    private val frameCallback = FrameCallback()

    private var lastTouchEvent: MotionEvent? = null
    private var surfaceMirrorer: SurfaceMirrorer? = null
    private var lastFrameTimeNanos: Long? = null

    private var defaultEglContext: EGLContext? = null
    private var defaultEngine: Engine? = null
    private var defaultScene: Scene? = null
    private var defaultView: View? = null
    private var defaultRenderer: Renderer? = null
    private var defaultModelLoader: ModelLoader? = null
    private var defaultMaterialLoader: MaterialLoader? = null
    private var defaultEnvironmentLoader: EnvironmentLoader? = null
    private var defaultCollisionSystem: CollisionSystem? = null
    private var defaultCameraNode: CameraNode? = null
    private var defaultMainLight: LightNode? = null

    init {
        _mainLightNode?.let { addNode(it) }

        setCameraNode(sharedCameraNode ?: createCameraNode(engine).also {
            defaultCameraNode = it
        })

        lifecycle?.addObserver(lifecycleObserver)
    }

    /**
     * Sets this View's Camera.
     * 设置此视图的摄像机。
     *
     * This method associates the specified Camera with this View. A Camera can be associated with
     * several View instances. To remove an existing association, simply pass null.
     * 此方法将指定的摄像机与此视图关联。一个摄像机可以与多个视图实例关联。要移除现有关联，只需传递null。
     *
     * The View does not take ownership of the Scene pointer. Before destroying a Camera, be sure
     * to remove it from all associated Views.
     * 视图不拥有场景指针的所有权。在销毁摄像机之前，请确保将其从所有关联的视图中移除。
     */
    fun setCameraNode(cameraNode: CameraNode) {
        if (_cameraNode != cameraNode) {
            _cameraNode?.collisionSystem = null
            _cameraNode = cameraNode
            cameraNode.collisionSystem = collisionSystem
            cameraNode.setView(view)
            view.camera = cameraNode.camera
        }
    }

    /**
     * Add a node to the [Scene] as a direct child.
     * 将节点作为直接子节点添加到[Scene]中。
     *
     * If the node is already in the scene, no change is made.
     * 如果节点已经在场景中，则不做任何更改。
     *
     * @param node the node to add as a child
     * @param node 要添加为子节点的节点
     * @throws IllegalArgumentException if the child is the same object as the parent, or if the
     * parent is a descendant of the child
     * @throws IllegalArgumentException 如果子节点与父节点是同一个对象，或者父节点是子节点的后代
     */
    fun addChildNode(node: Node) {
        childNodes = childNodes + node
    }

    /**
     * Add multiple nodes to the [Scene] as a direct child.
     * 将多个节点作为直接子节点添加到[Scene]中。
     *
     * If the nodes are already in the scene, no change is made.
     * 如果节点已经在场景中，则不做任何更改。
     *
     * @param nodes the nodes to add as children
     * @param nodes 要添加为子节点的节点列表
     * @throws IllegalArgumentException if the child is the same object as the parent, or if the
     * parent is a descendant of the child
     * @throws IllegalArgumentException 如果子节点与父节点是同一个对象，或者父节点是子节点的后代
     */
    fun addChildNodes(nodes: List<Node>) {
        childNodes = childNodes + nodes
    }

    /**
     * Removes a node from the children of this [Scene].
     * 从此[Scene]的子节点中移除一个节点。
     *
     * If the node is not in the scene, no change is made.
     * 如果节点不在场景中，则不做任何更改。
     *
     * @param node the node to remove from the children
     * @param node 要从子节点中移除的节点
     */
    fun removeChildNode(node: Node) {
        childNodes = childNodes - node
    }

    /**
     * Removes multiple nodes from the children of this [Scene].
     * 从此[Scene]的子节点中移除多个节点。
     *
     * If the nodes are not in the scene, no change is made.
     * 如果节点不在场景中，则不做任何更改。
     *
     * @param nodes the nodes to remove from the children
     * @param nodes 要从子节点中移除的节点列表
     */
    fun removeChildNodes(nodes: List<Node>) {
        childNodes = childNodes - nodes
    }

    /**
     * Removes all nodes from the children of this [Scene].
     * 从此[Scene]的子节点中移除所有节点。
     */
    fun clearChildNodes() {
        childNodes = listOf()
    }

    fun startMirroring(
        surface: Surface,
        left: Int = 0,
        bottom: Int = 0,
        width: Int = this.width,
        height: Int = this.height
    ) {
        if (surfaceMirrorer == null) {
            surfaceMirrorer = SurfaceMirrorer()
        }
        surfaceMirrorer?.startMirroring(this, surface, left, bottom, width, height)
    }

    fun stopMirroring(surface: Surface) {
        surfaceMirrorer?.stopMirroring(this, surface)
        surfaceMirrorer = null
    }

    fun startRecording(mediaRecorder: MediaRecorder) {
        mediaRecorder.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
        }
        mediaRecorder.prepare()
        mediaRecorder.start()
        startMirroring(mediaRecorder.surface)
    }

    fun stopRecording(mediaRecorder: MediaRecorder) {
        stopMirroring(mediaRecorder.surface)
        mediaRecorder.stop()
        mediaRecorder.reset()
        mediaRecorder.surface.release()
    }

    /**
     * Force destroy.
     * 强制销毁。
     *
     * You don't have to call this method because everything is already lifecycle aware.
     * 您不必调用此方法，因为所有内容都已具有生命周期感知能力。
     * Meaning that they are already self destroyed when they receive the `onDestroy()` callback.
     * 这意味着当它们收到`onDestroy()`回调时，它们已经自我销毁了。
     */
    open fun destroy() {
        if (!isDestroyed) {
            lifecycle = null
            Choreographer.getInstance().removeFrameCallback(frameCallback)

            runCatching { uiHelper.detach() }

            defaultCameraNode?.destroy()
            defaultMainLight?.destroy()

//        runCatching { ResourceManager.getInstance().destroyAllResources() }

            defaultRenderer?.let { engine.safeDestroyRenderer(it) }
            defaultView?.let { engine.safeDestroyView(it) }
            defaultScene?.let { engine.safeDestroyScene(it) }
            defaultEnvironmentLoader?.destroy()
            defaultMaterialLoader?.let { engine.safeDestroyMaterialLoader(it) }
            defaultModelLoader?.let { engine.safeDestroyModelLoader(it) }

            defaultEngine?.let { it.safeDestroy() }
            defaultEglContext?.let { OpenGL.destroyEglContext(it) }
            isDestroyed = true
        }
    }

    /**
     * Callback that occurs for each display frame. Updates the scene and reposts itself to be
     * called by the choreographer on the next frame.
     * 每个显示帧发生的回调。更新场景并重新发布自己，以便在下一帧由编舞者调用。
     *
     * @param frameTimeNanos time in nanoseconds when the frame started being rendered,
     * Typically comes from [Choreographer.FrameCallback]
     * @param frameTimeNanos 帧开始渲染时的纳秒时间，通常来自[Choreographer.FrameCallback]
     */
    protected open fun onFrame(frameTimeNanos: Long) {
        modelLoader.updateLoad()

        childNodes.forEach { it.onFrame(frameTimeNanos) }

        if (uiHelper.isReadyToRender) {
//            transformManager.openLocalTransformTransaction()

            // Only update the camera manipulator if a touch has been made
            cameraManipulator?.let { manipulator ->
                if (lastTouchEvent != null) {
                    manipulator.update(frameTimeNanos.intervalSeconds(lastFrameTimeNanos).toFloat())
                    // Extract the camera basis from the helper and push it to the Filament camera.
                    cameraNode.transform = manipulator.getTransform()
                }
            }

            onFrame?.invoke(frameTimeNanos)

//            transformManager.commitLocalTransformTransaction()

            // Render the scene, unless the renderer wants to skip the frame.
            if (renderer.beginFrame(swapChain!!, frameTimeNanos)) {
                renderer.render(view)
                surfaceMirrorer?.onFrame(this)
                renderer.endFrame()
            }
        }

        lastFrameTimeNanos = frameTimeNanos
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (lifecycle == null) {
            lifecycle = runCatching { findViewTreeLifecycleOwner()?.lifecycle }.getOrNull()
        }
    }

    override fun onDetachedFromWindow() {
        if (!isDestroyed) {
            destroy()
        }
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 确保视图的onTouchListener被调用
        if (!super.onTouchEvent(event)) {
            lastTouchEvent = event

            // 执行碰撞检测，找到被触摸的节点
            val hitResult = collisionSystem.hitTest(event).firstOrNull {
                it.node.isTouchable // 只选择可触摸的节点
            }

            // 如果全局触摸事件监听器或节点自身的触摸事件处理没有消费该事件
            if (onTouchEvent?.invoke(event, hitResult) != true &&
                hitResult?.node?.onTouchEvent(event, hitResult) != true
            ) {
                // 则交由手势检测器和相机手势检测器处理
                gestureDetector?.onTouchEvent(event, hitResult)
                cameraGestureDetector?.onTouchEvent(event)
            }

            return true // 表示事件已被处理
        }
        return false // 事件未被处理，交由父类或其他视图处理
    }

    protected open fun onResized(width: Int, height: Int) {
        view.viewport = Viewport(0, 0, width, height)
        cameraManipulator?.setViewport(width, height)
        cameraNode.updateProjection()
    }

    internal fun addNode(node: Node) {
        node.collisionSystem = collisionSystem
        if (node.sceneEntities.isNotEmpty()) {
            scene.addEntities(node.sceneEntities.toIntArray())
        }
        node.onChildAdded += ::addNode
        node.onChildRemoved += ::removeNode
        node.onAddedToScene(scene)
        node.childNodes.forEach { addNode(it) }
    }

    internal fun removeNode(node: Node) {
        node.collisionSystem = null
        if (node.sceneEntities.isNotEmpty()) {
            scene.removeEntities(node.sceneEntities.toIntArray())
        }
        node.onChildAdded -= ::addNode
        node.onChildRemoved -= ::removeNode
        node.onRemovedFromScene(scene)
        node.childNodes.forEach { removeNode(it) }
    }

    internal fun replaceNode(oldNode: Node?, newNode: Node?) {
        oldNode?.let { removeNode(it) }
        newNode?.let { addNode(it) }
    }

    fun setOnGestureListener(
        onDown: (e: MotionEvent, node: Node?) -> Unit = { _, _ -> },
        onShowPress: (e: MotionEvent, node: Node?) -> Unit = { _, _ -> },
        onSingleTapUp: (e: MotionEvent, node: Node?) -> Unit = { _, _ -> },
        onScroll: (e1: MotionEvent?, e2: MotionEvent, node: Node?, distance: Float2) -> Unit = { _, _, _, _ -> },
        onLongPress: (e: MotionEvent, node: Node?) -> Unit = { _, _ -> },
        onFling: (e1: MotionEvent?, e2: MotionEvent, node: Node?, velocity: Float2) -> Unit = { _, _, _, _ -> },
        onSingleTapConfirmed: (e: MotionEvent, node: Node?) -> Unit = { _, _ -> },
        onDoubleTap: (e: MotionEvent, node: Node?) -> Unit = { _, _ -> },
        onDoubleTapEvent: (e: MotionEvent, node: Node?) -> Unit = { _, _ -> },
        onContextClick: (e: MotionEvent, node: Node?) -> Unit = { _, _ -> },
        onMoveBegin: (detector: MoveGestureDetector, e: MotionEvent, node: Node?) -> Unit = { _, _, _ -> },
        onMove: (detector: MoveGestureDetector, e: MotionEvent, node: Node?) -> Unit = { _, _, _ -> },
        onMoveEnd: (detector: MoveGestureDetector, e: MotionEvent, node: Node?) -> Unit = { _, _, _ -> },
        onRotateBegin: (detector: RotateGestureDetector, e: MotionEvent, node: Node?) -> Unit = { _, _, _ -> },
        onRotate: (detector: RotateGestureDetector, e: MotionEvent, node: Node?) -> Unit = { _, _, _ -> },
        onRotateEnd: (detector: RotateGestureDetector, e: MotionEvent, node: Node?) -> Unit = { _, _, _ -> },
        onScaleBegin: (detector: ScaleGestureDetector, e: MotionEvent, node: Node?) -> Unit = { _, _, _ -> },
        onScale: (detector: ScaleGestureDetector, e: MotionEvent, node: Node?) -> Unit = { _, _, _ -> },
        onScaleEnd: (detector: ScaleGestureDetector, e: MotionEvent, node: Node?) -> Unit = { _, _, _ -> }
    ) {
        onGestureListener = object : GestureDetector.OnGestureListener {
            override fun onDown(e: MotionEvent, node: Node?) = onDown(e, node)
            override fun onShowPress(e: MotionEvent, node: Node?) = onShowPress(e, node)
            override fun onSingleTapUp(e: MotionEvent, node: Node?) = onSingleTapUp(e, node)
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                node: Node?,
                distance: Float2
            ) = onScroll(e1, e2, node, distance)

            override fun onLongPress(e: MotionEvent, node: Node?) = onLongPress(e, node)
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, node: Node?, velocity: Float2) =
                onFling(e1, e2, node, velocity)

            override fun onSingleTapConfirmed(e: MotionEvent, node: Node?) =
                onSingleTapConfirmed(e, node)

            override fun onDoubleTap(e: MotionEvent, node: Node?) = onDoubleTap(e, node)
            override fun onDoubleTapEvent(e: MotionEvent, node: Node?) = onDoubleTapEvent(e, node)
            override fun onContextClick(e: MotionEvent, node: Node?) = onContextClick(e, node)
            override fun onMoveBegin(detector: MoveGestureDetector, e: MotionEvent, node: Node?) =
                onMoveBegin(detector, e, node)

            override fun onMove(detector: MoveGestureDetector, e: MotionEvent, node: Node?) =
                onMove(detector, e, node)

            override fun onMoveEnd(detector: MoveGestureDetector, e: MotionEvent, node: Node?) =
                onMoveEnd(detector, e, node)

            override fun onRotateBegin(
                detector: RotateGestureDetector,
                e: MotionEvent,
                node: Node?
            ) = onRotateBegin(detector, e, node)

            override fun onRotate(detector: RotateGestureDetector, e: MotionEvent, node: Node?) =
                onRotate(detector, e, node)

            override fun onRotateEnd(detector: RotateGestureDetector, e: MotionEvent, node: Node?) =
                onRotateEnd(detector, e, node)

            override fun onScaleBegin(detector: ScaleGestureDetector, e: MotionEvent, node: Node?) =
                onScaleBegin(detector, e, node)

            override fun onScale(detector: ScaleGestureDetector, e: MotionEvent, node: Node?) =
                onScale(detector, e, node)

            override fun onScaleEnd(detector: ScaleGestureDetector, e: MotionEvent, node: Node?) =
                onScaleEnd(detector, e, node)
        }
    }

    private inner class LifeCycleObserver : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {

            viewNodeWindowManager?.resume(this@SceneView)

            // Start the drawing when the renderer is resumed.  Remove and re-add the callback
            // to avoid getting called twice.
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            Choreographer.getInstance().postFrameCallback(frameCallback)

            activity?.setKeepScreenOn(true)
        }

        override fun onPause(owner: LifecycleOwner) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)

            viewNodeWindowManager?.pause()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            viewNodeWindowManager?.destroy()
            destroy()
        }
    }

    private inner class FrameCallback : Choreographer.FrameCallback {
        override fun doFrame(timestamp: Long) {
            // Always post the callback for the next frame.
            Choreographer.getInstance().postFrameCallback(this)

            onFrame(timestamp)
        }
    }

    private inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChain?.let { runCatching { engine.destroySwapChain(it) } }
            swapChain = engine.createSwapChain(surface)
            displayHelper.attach(renderer, display)
        }

        override fun onDetachedFromSurface() {
            displayHelper.detach()
            swapChain?.let {
                runCatching { engine.destroySwapChain(it) }
                engine.flushAndWait()
                swapChain = null
            }
        }

        override fun onResized(width: Int, height: Int) {
            this@SceneView.onResized(width, height)

            // Wait for all pending frames to be processed before returning. This is to avoid a race
            // between the surface being resized before pending frames are rendered into it.
            engine.createFence().apply {
                wait(Fence.Mode.FLUSH, Fence.WAIT_FOR_EVER)
                engine.destroyFence(this)
            }
        }
    }

    class DefaultCameraNode(engine: Engine) : CameraNode(engine) {
        init {
            transform = Transform(position = Position(0.0f, 0.0f, 1.0f))
            // Set the exposure on the camera, this exposure follows the sunny f/16 rule
            // Since we define a light that has the same intensity as the sun, it guarantees a
            // proper exposure
            setExposure(16.0f, 1.0f / 125.0f, 100.0f)
        }
    }

    class DefaultLightNode(engine: Engine) : LightNode(
        engine = engine,
        type = LightManager.Type.DIRECTIONAL,
        apply = {
            color(DEFAULT_MAIN_LIGHT_COLOR)
            intensity(DEFAULT_MAIN_LIGHT_COLOR_INTENSITY)
            direction(0.0f, -1.0f, 0.0f)
            castShadows(true)
        })

    companion object {

        init {
            Gltfio.init()
            Filament.init()
            Utils.init()
        }

        const val DEFAULT_MAIN_LIGHT_COLOR_TEMPERATURE = 6_500.0f
        const val DEFAULT_MAIN_LIGHT_COLOR_INTENSITY = 100_000.0f

        val DEFAULT_MAIN_LIGHT_COLOR = Colors.cct(DEFAULT_MAIN_LIGHT_COLOR_TEMPERATURE).toColor()
        val DEFAULT_MAIN_LIGHT_INTENSITY = DEFAULT_MAIN_LIGHT_COLOR_INTENSITY

        val DEFAULT_OBJECT_POSITION = Position(0.0f, 0.0f, -4.0f)

        fun createEglContext() = OpenGL.createEglContext()
        fun createEngine(eglContext: EGLContext) = Engine.create(eglContext)

        fun createScene(engine: Engine) = engine.createScene()

        fun createView(engine: Engine) =
            engine.createView().apply {
                // On mobile, better use lower quality color buffer
                renderQuality = renderQuality.apply {
                    hdrColorBuffer = QualityLevel.MEDIUM
                }
                // Dynamic resolution often helps a lot
                dynamicResolutionOptions = dynamicResolutionOptions.apply {
                    // Disabled cause generating some camera stream wrong scaling ratio
                    enabled = false
                    homogeneousScaling = true
                    quality = QualityLevel.MEDIUM
                }

                // MSAA is needed with dynamic resolution MEDIUM
                multiSampleAntiAliasingOptions = multiSampleAntiAliasingOptions.apply {
                    enabled = false
                }

                // FXAA is pretty cheap and helps a lot
                antiAliasing = AntiAliasing.FXAA
                // Ambient occlusion is the cheapest effect that adds a lot of quality
                ambientOcclusionOptions = ambientOcclusionOptions.apply {
                    enabled = false
                }
                // Bloom is pretty expensive but adds a fair amount of realism
//                bloomOptions = bloomOptions.apply {
//                    enabled = true
//                }
                // Change the ToneMapper to FILMIC to avoid some over saturated colors, for example
                // material orange 500.
                colorGrading = ColorGrading.Builder()
                    .toneMapper(ToneMapper.Filmic())
                    .build(engine)
                setShadowingEnabled(false)
            }

        fun createRenderer(engine: Engine) = engine.createRenderer()

        fun createModelLoader(engine: Engine, context: Context) = engine.createModelLoader(context)
        fun createMaterialLoader(engine: Engine, context: Context) =
            engine.createMaterialLoader(context)

        fun createEnvironmentLoader(engine: Engine, context: Context) =
            engine.createEnvironmentLoader(context)

        fun createCameraNode(engine: Engine): CameraNode = DefaultCameraNode(engine)

        fun createDefaultCameraManipulator(
            orbitHomePosition: Position? = null,
            targetPosition: Position? = null
        ) = CameraGestureDetector.DefaultCameraManipulator(
            orbitHomePosition = orbitHomePosition,
            targetPosition = targetPosition
        )

        fun createViewNodeManager(context: Context) = ViewNode2.WindowManager(context)

        fun createMainLightNode(engine: Engine): LightNode = DefaultLightNode(engine)

        fun createEnvironment(environmentLoader: EnvironmentLoader, isOpaque: Boolean = true) =
            createEnvironment(
                engine = environmentLoader.engine,
                isOpaque = isOpaque,
                indirectLight = KTX1Loader.createIndirectLight(
                    environmentLoader.engine,
                    environmentLoader.context.assets.readBuffer(
                        fileLocation = "environments/neutral/neutral_ibl.ktx"
                    ),
                )
            )

        fun createEnvironment(
            engine: Engine,
            isOpaque: Boolean = true,
            indirectLight: IndirectLight? = null,
            skybox: Skybox? = Skybox.Builder()
                .color(colorOf(rgb = 0.0f, a = if (isOpaque) 1.0f else 0.0f).toFloatArray())
                .build(engine),
            sphericalHarmonics: List<Float>? = null
        ) = Environment(indirectLight, skybox, sphericalHarmonics)

        fun createCollisionSystem(view: View) = CollisionSystem(view)
    }
}
