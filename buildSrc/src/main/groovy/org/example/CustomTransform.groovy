package org.example

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Status
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.ide.common.internal.WaitableExecutor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import groovyjarjarasm.asm.commons.AdviceAdapter
import org.apache.commons.io.FileUtils

import java.util.concurrent.Callable

class CustomTransform extends Transform {

    /**
     * 可以并发编译，提升速度:线程池。。。
     */
    private WaitableExecutor mWaitableExecutor = WaitableExecutor.useGlobalSharedThreadPool()


    /**
     * 用来表示当前Transform的名称，名称用来创建目录，放在app/build/intermediates/transform目录下
     * @return
     */
    @Override
    String getName() {
        return "CustomTransform"
    }

    /**
     * 需要处理的数据类型，用于确定需要对那些类型的结果进行转换
     *      CONTENT_CLASS: 需要处理java的class文件
     *      CONTENT_JARS: 需要处理java的class与资源文件
     *      CONTENT_RESOURCES: 需要处理java的资源文件
     *      CONTENT_NATIVE_LIBS: 需要处理native库的代码
     *      CONTENT_DEX: 需要处理DEX文件
     *      CONTENT_DEX_WITH_RESOURCES: 需要处理DEX与java的资源文件
     * @return
     */
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        // 需要处理的数据类型
        return TransformManager.CONTENT_CLASS
    }

    /**
     * 表示Transform要操作的内容范围
     * SCOPE_FULL_PROJECT：包含了Scope.PROJECT Scope.SUB_PROJECTS Scope.EXTERNAL_LIBRARIES
     * @return
     */
    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        // 作用范围
        return TransformManager.SCOPE_FULL_PROJECT
    }

    /**
     * 开启了增量编译之后，需要检查每个文件的Status，根据文件的Status进行不同的操作
     *      NOTCHANGED: 当前文件不需要操作，连复制操作也不需要
     *      ADDED: 正常处理，输出给下一个任务
     *      CHANGED: 正常处理，输出给下一个任务
     *      REMOVED: 移除outputprovider获取路径对应的文件
     * @return
     */
    @Override
    boolean isIncremental() {
        // 是否支持增量编译, 一般都是要支持增量编译
        return true
    }

    /**
     * 具体的转换逻辑，分为消费性Transform和引用型Transform
     * @param transformInvocation the invocation object containing the transform inputs.
     * @throws TransformException
     * @throws InterruptedException
     * @throws IOException
     */
    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)
        // TransformOutputProvider管理输出路径，如果消费型输入为空，则outputProvider也为空
        TransformOutputProvider outputProvider = transformInvocation.outputProvider

        // 当前是否是增量编译，由isIncremental方法决定的
        // 不一定等于重写的isIncremental的值，根据当时环境决定
        boolean isIncremental = transformInvocation.isIncremental()
        if (!isIncremental) {
            outputProvider.deleteAll()
        }

        // transformInvocation.inputs的类型是Collection<TransformInput>,可以从中获取到jar包和class文件夹路径
        // 需要输出给下一个任务
        transformInvocation.inputs.each { input ->
            input.jarInputs.each { jarInput ->
                // 处理jar
                mWaitableExecutor.execute(new Callable<Object>() {
                    @Override
                    Object call() throws Exception {
                        processJarInput(jarInput, outputProvider, isIncremental)
                        return null
                    }
                })
            }
            input.directoryInputs.each { directoryInput ->
                // 处理源码文件
                mWaitableExecutor.execute(new Callable<Object>() {
                    @Override
                    Object call() throws Exception {
                        processDirectoryInput(directoryInput, outputProvider, isIncremental)
                        return null
                    }
                })
            }
        }
        // 等待所有的任务结束
        mWaitableExecutor.waitForTasksWithQuickFail(true)
    }

    static void processJarInput(JarInput jarInput, TransformOutputProvider outputProvider, Boolean isIncremental) {
        def status = jarInput.status
        File dest = outputProvider.getContentLocation(
                jarInput.file.absolutePath,
                jarInput.contentTypes,
                jarInput.scopes,
                Format.JAR
        )
        if (isIncremental) {
            switch (status) {
                case Status.NOTCHANGED: break
                case Status.ADDED:
                case Status.CHANGED:
                    transformJar(jarInput.file, dest)
                    break
                case Status.REMOVED:
                    if (dest.exists()) {
                        FileUtils.forceDelete(dest)
                    }
                    break
            }
        } else {
            transformJar(jarInput.file, dest)
        }
    }

    static void transformJar(File jarInputFile, File dest) {
        // 将修改过的字节码copy到dest，就可以实现编译期干预字节码的目的
        println("copy file $dest ----")
        FileUtils.copyFile(jarInputFile, dest)
    }

    static void processDirectoryInput(DirectoryInput directoryInput, TransformOutputProvider outputProvider, Boolean isIncremental) {
        File dest = outputProvider.getContentLocation(
                directoryInput.name,
                directoryInput.contentTypes,
                directoryInput.scopes,
                Format.DIRECTORY
        )
        FileUtils.forceMkdir(dest)
        println("isIncremental = $isIncremental")
        if (isIncremental) {
            String srcDirPath = directoryInput.getFile().getAbsolutePath()
            String destDirPath = dest.getAbsolutePath()
            Map<File, Status> fileStatusMap = directoryInput.getChangedFiles()
            for (Map.Entry<File, Status> changedFile : fileStatusMap.entrySet()) {
                Status status = changedFile.getValue()
                File inputFile = changedFile.getKey()
                String destFilePath = inputFile.getAbsolutePath().replace(srcDirPath, destDirPath)
                File destFile = new File(destFilePath)
                switch (status) {
                    case Status.NOTCHANGED:
                        break
                    case Status.ADDED:
                    case Status.CHANGED:
                        FileUtils.touch(destFile)
                        transformSingleFile(inputFile, destFile)
                        break
                    case Status.REMOVED:
                        if (destFile.exists()) {
                            FileUtils.forceDelete(destFile)
                        }
                        break
                }
            }
        } else {
            transformDirectory(directoryInput.file, dest)
        }

        // 将修改过的字节码copy到dest，就可以实现编译期间干预字节码的目的
//        println("copy fileFolder $dest ----")
//        FileUtils.copyDirectory(directoryInput.file, dest)
    }

    static void transformSingleFile(File inputFile, File destFile) {
        println("copy单个文件")
        FileUtils.copyFile(inputFile, destFile)
    }

    static void transformDirectory(File directoryInputFile, File dest) {
        println("copy文件夹 $dest -----")
        FileUtils.copyDirectory(directoryInputFile, dest)
    }

    /**
     * ClassReader读取字节码文件的时候，数据会通过ClassVisitor回调回来
     * 咱们可以自定义一个ClassWriter用来接受读取到的字节数据
     * 同时可以插入一点东西在这些数据的前面或者后面
     * 最后通过ClassWriter的toByteArray将数据导出，这就是所谓的“插桩”！！！！！！
     * @param inputFiles
     * @param outputFiles
     */
    static void copyFile(File inputFiles, File outputFiles) {
        FileInputStream inputStream = new FileInputStream(inputFiles)
        FileOutputStream outputStream = new FileOutputStream(outputFiles)
        // 1. 构建ClassReader对象
        ClassReader classReader = new ClassReader(inputStream)
        // 2. 构建ClassVisitor的实现类ClassWriter
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
        // 3. 将ClassReader读取的内容回调给ClassVisitor接口
//        classReader.accept(classWriter, ClassReader.EXPAND_FRAMES)

        classReader.accept(new HelloClassVisitor((ClassVisitor)classWriter), ClassReader.EXPAND_FRAMES)



        // 4. 通过classWriter对象的toByteArray方法拿到完整的字节流
        outputStream.write(classWriter.toByteArray())
        inputStream.close()
        outputStream.close()
    }

    // 修改如下的代码文件
//    static void test() {
//        println("test")
//    }
//
//    static void test() {
//        println("Hello World")
//        println("test")
//    }

    class HelloClassVisitor extends ClassVisitor {

        HelloClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM7, cv)
        }

        @Override
        MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            def methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions)
            return new HelloMethodVisitor(api, methodVisitor, access, name, descriptor)
        }
    }

    /**
     * 对方法进行插桩
     */
    class HelloMethodVisitor extends AdviceAdapter {

        protected HelloMethodVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
            super(api, methodVisitor, access, name, descriptor)
        }

        /**
         * 方法进入
         */
        @Override
        protected void onMethodEnter() {
            super.onMethodEnter()
            // 这里的mv是MethodVisitor
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream")
            mv.visitLdcInsn("Hello World!")
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false)
        }
    }

}







