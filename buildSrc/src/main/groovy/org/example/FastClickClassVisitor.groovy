package org.example

import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.Label
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import groovyjarjarasm.asm.commons.AdviceAdapter

/**
 * 点击防抖
 */
class FastClickClassVisitor extends ClassVisitor {

    FastClickClassVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM7, classVisitor)
    }

    @Override
    MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        def methodVisitor = cv.visitMethod(access, name, descriptor, signature)
        if (name = "onClick" && descriptor == "(Landroid/view/View;)V") {
            return new FastMethodVisitor(api, methodVisitor, access, name, descriptor)
        }
        return methodVisitor
    }

    class FastMethodVisitor extends AdviceAdapter {

        protected FastMethodVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
            super(api, methodVisitor, access, name, descriptor)
        }

        /**
         * 方法进入
         */
        @Override
        protected void onMethodEnter() {
            super.onMethodEnter()
            mv.visitMethodInsn(INVOKESTATIC, "org/example/FastClickUtil", "isFastDoubleClick", "()Z", false)
            Label label = new Label()
            mv.visitJumpInsn(IFEQ, label)
            mv.visitInsn(RETURN)
            mv.visitLabel(label)
        }
    }
}
