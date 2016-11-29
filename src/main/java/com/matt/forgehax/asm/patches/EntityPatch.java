package com.matt.forgehax.asm.patches;

import com.matt.forgehax.asm.events.WebMotionEvent;
import com.matt.forgehax.asm.helper.AsmHelper;
import com.matt.forgehax.asm.helper.AsmMethod;
import com.matt.forgehax.asm.helper.ClassTransformer;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

public class EntityPatch extends ClassTransformer {
    public final AsmMethod APPLY_ENTITY_COLLISION = new AsmMethod()
            .setName("applyEntityCollision")
            .setObfuscatedName("i")
            .setArgumentTypes(NAMES.ENTITY)
            .setReturnType(void.class)
            .setHooks(NAMES.ON_APPLY_COLLISION);

    public final AsmMethod MOVE_ENTITY = new AsmMethod()
            .setName("moveEntity")
            .setObfuscatedName("a")
            .setArgumentTypes(NAMES.MOVERTYPE, double.class, double.class, double.class)
            .setReturnType(void.class)
            .setHooks(NAMES.ON_WEB_MOTION);

    public final AsmMethod DO_APPLY_COLLISIONS = new AsmMethod()
            .setName("doBlockCollisions")
            .setObfuscatedName("ac")
            .setArgumentTypes()
            .setReturnType(void.class)
            .setHooks(NAMES.ON_DO_BLOCK_COLLISIONS);

    public EntityPatch() {
        registerHook(APPLY_ENTITY_COLLISION);
        registerHook(MOVE_ENTITY);
        registerHook(DO_APPLY_COLLISIONS);
    }

    @Override
    public boolean onTransformMethod(MethodNode method) {
        if(method.name.equals(APPLY_ENTITY_COLLISION.getRuntimeName()) &&
                method.desc.equals(APPLY_ENTITY_COLLISION.getDescriptor())) {
            updatePatchedMethods(applyEntityCollisionPatch(method));
            return true;
        } else if(method.name.equals(MOVE_ENTITY.getRuntimeName()) &&
                method.desc.equals(MOVE_ENTITY.getDescriptor())) {
            updatePatchedMethods(applyMoveEntityPatch(method));
            return true;
        } else if(method.name.equals(DO_APPLY_COLLISIONS.getRuntimeName()) &&
                method.desc.equals(DO_APPLY_COLLISIONS.getDescriptor())) {
            updatePatchedMethods(doBlockCollisionsPatch(method));
            return true;
        } else return false;
    }

    private final int[] applyPushToThisPreNode = {
            ALOAD, DLOAD, DNEG, DCONST_0, DLOAD, DNEG, INVOKEVIRTUAL
    };
    private final int[] applyPushToThisPostNode = {
            INVOKEVIRTUAL
    };

    private final int[] applyPushToOtherPreNode = {
            ALOAD, DLOAD, DCONST_0, DLOAD, INVOKEVIRTUAL
    };
    private final int[] applyPushToOtherPostNode = {
            INVOKEVIRTUAL
    };

    private boolean applyEntityCollisionPatch(MethodNode method) {
        AbstractInsnNode thisEntityPreNode = findPattern("applyEntityCollision", "thisEntityPreNode",
                method.instructions.getFirst(), applyPushToThisPreNode, "xxxxxxx");
        // start at preNode, and scan for next INVOKEVIRTUAL sig
        AbstractInsnNode thisEntityPostNode = findPattern("applyEntityCollision", "thisEntityPostNode",
                thisEntityPreNode, applyPushToThisPostNode, "x");
        AbstractInsnNode otherEntityPreNode = findPattern("applyEntityCollision", "otherEntityPreNode",
                thisEntityPostNode, applyPushToOtherPreNode, "xxxxx");
        // start at preNode, and scan for next INVOKEVIRTUAL sig
        AbstractInsnNode otherEntityPostNode = findPattern("applyEntityCollision", "otherEntityPostNode",
                otherEntityPreNode, applyPushToOtherPostNode, "x");
        if(thisEntityPostNode != null &&
                thisEntityPreNode != null &&
                otherEntityPostNode != null &&
                otherEntityPreNode != null) {
            LabelNode endJumpForThis = new LabelNode();
            LabelNode endJumpForOther = new LabelNode();

            // first we handle this.addVelocity

            InsnList insnThisPre = new InsnList();
            insnThisPre.add(new VarInsnNode(ALOAD, 0)); // push THIS
            insnThisPre.add(new VarInsnNode(ALOAD, 1));
            insnThisPre.add(new VarInsnNode(DLOAD, 2));
            insnThisPre.add(new InsnNode(DNEG)); // push -X
            insnThisPre.add(new VarInsnNode(DLOAD, 4));
            insnThisPre.add(new InsnNode(DNEG)); // push -Z
            insnThisPre.add(new MethodInsnNode(INVOKESTATIC,
                    NAMES.ON_APPLY_COLLISION.getParentClass().getRuntimeName(),
                    NAMES.ON_APPLY_COLLISION.getRuntimeName(),
                    NAMES.ON_APPLY_COLLISION.getDescriptor(),
                    false
            ));
            insnThisPre.add(new JumpInsnNode(IFNE, endJumpForThis));

            InsnList insnOtherPre = new InsnList();
            insnOtherPre.add(new VarInsnNode(ALOAD, 1)); // push entityIn
            insnOtherPre.add(new VarInsnNode(ALOAD, 0)); // push THIS
            insnOtherPre.add(new VarInsnNode(DLOAD, 2)); // push X
            insnOtherPre.add(new VarInsnNode(DLOAD, 4)); // push Z
            insnOtherPre.add(new MethodInsnNode(INVOKESTATIC,
                    NAMES.ON_APPLY_COLLISION.getParentClass().getRuntimeName(),
                    NAMES.ON_APPLY_COLLISION.getRuntimeName(),
                    NAMES.ON_APPLY_COLLISION.getDescriptor(),
                    false
            ));
            insnOtherPre.add(new JumpInsnNode(IFNE, endJumpForOther));

            method.instructions.insertBefore(thisEntityPreNode, insnThisPre);
            method.instructions.insert(thisEntityPostNode, endJumpForThis);

            method.instructions.insertBefore(otherEntityPreNode, insnOtherPre);
            method.instructions.insert(otherEntityPostNode, endJumpForOther);

            return true;
        } else return false;
    }

    private final int[] moveEntityMotionPreSig = {
            DLOAD, LDC, DMUL, DSTORE,
            0x00, 0x00,
            DLOAD, LDC, DMUL, DSTORE,
    };
    private final int[] moveEntityMotionPostSig = {
            PUTFIELD,
            0x00, 0x00, 0x00,
            DLOAD, DSTORE,
            0x00, 0x00,
            DLOAD, DSTORE
    };

    private final int[] isPlayerSneakingSig = {
            IFEQ, ALOAD, INSTANCEOF, IFEQ, ICONST_1, GOTO,
            0x00, 0x00,
            ICONST_0
    };

    private boolean applyMoveEntityPatch(MethodNode method) {
        boolean isPatched = false;
        // for web motion
        /*
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // DISCONTINUED USAGE OF THIS METHOD
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

        AbstractInsnNode preNode = findPattern("moveEntity", "preNode",
                method.instructions.getFirst(), moveEntityMotionPreSig, "xxxx??xxxx");
        AbstractInsnNode postNode = findPattern("moveEntity", "postNode",
                method.instructions.getFirst(), moveEntityMotionPostSig, "x???xx??xx");
        if(preNode != null && postNode != null) {
            LabelNode endJump = new LabelNode();

            int identifier = 59;
            InsnList insnList = new InsnList();
            insnList.add(new VarInsnNode(ALOAD, 0)); // push this
            insnList.add(new VarInsnNode(DLOAD, 2)); // push this
            insnList.add(new VarInsnNode(DLOAD, 4)); // push this
            insnList.add(new VarInsnNode(DLOAD, 6)); // push this
            insnList.add(new MethodInsnNode(INVOKESTATIC,
                    NAMES.ON_WEB_MOTION.getParentClass().getRuntimeName(),
                    NAMES.ON_WEB_MOTION.getRuntimeName(),
                    NAMES.ON_WEB_MOTION.getDescriptor(),
                    false
            ));
            insnList.add(new VarInsnNode(ASTORE, identifier));
            // set x
            insnList.add(new LabelNode());
            insnList.add(new VarInsnNode(ALOAD, identifier));
            insnList.add(new MethodInsnNode(INVOKEVIRTUAL,
                    NAMES.WEB_MOTION_EVENT.getRuntimeName(),
                    "getX",
                    "()D",
                    false
            ));
            insnList.add(new VarInsnNode(DSTORE, 1));
            // set y
            insnList.add(new LabelNode());
            insnList.add(new VarInsnNode(ALOAD, identifier));
            insnList.add(new MethodInsnNode(INVOKEVIRTUAL,
                    NAMES.WEB_MOTION_EVENT.getRuntimeName(),
                    "getY",
                    "()D",
                    false
            ));
            insnList.add(new VarInsnNode(DSTORE, 3));
            // set z
            insnList.add(new LabelNode());
            insnList.add(new VarInsnNode(ALOAD, identifier));
            insnList.add(new MethodInsnNode(INVOKEVIRTUAL,
                    NAMES.WEB_MOTION_EVENT.getRuntimeName(),
                    "getZ",
                    "()D",
                    false
            ));
            insnList.add(new VarInsnNode(DSTORE, 5));
            // check if event is canceled
            insnList.add(new LabelNode());
            insnList.add(new VarInsnNode(ALOAD, identifier));
            insnList.add(new MethodInsnNode(INVOKEVIRTUAL,
                    NAMES.WEB_MOTION_EVENT.getRuntimeName(),
                    "isCanceled",
                    "()Z",
                    false
            ));
            insnList.add(new JumpInsnNode(IFNE, endJump));

            InsnList pop = new InsnList();
            pop.insert(endJump);

            method.instructions.insertBefore(preNode, insnList);
            method.instructions.insert(postNode, pop);
            isPatched = true;
        }
        */

        // for sneak flag
        AbstractInsnNode sneakFlagNode = findPattern("moveEntity", "sneakFlagNode",
                method.instructions.getFirst(), isPlayerSneakingSig, "xxxxxx??x");
        if(sneakFlagNode != null &&
                sneakFlagNode instanceof JumpInsnNode) {
            // the original label to the jump
            LabelNode jumpToLabel = ((JumpInsnNode) sneakFlagNode).label;
            // the or statement jump if isSneaking returns false
            LabelNode orJump = new LabelNode();

            InsnList insnList = new InsnList();
            insnList.add(new JumpInsnNode(IFNE, orJump)); // if not equal, jump past the ForgeHaxHooks.isSafeWalkActivated
            insnList.add(new FieldInsnNode(GETSTATIC,
                    NAMES.IS_SAFEWALK_ACTIVE.getParentClass().getRuntimeName(),
                    NAMES.IS_SAFEWALK_ACTIVE.getRuntimeName(),
                    NAMES.IS_SAFEWALK_ACTIVE.getTypeDescriptor()
            ));// get the value of isSafeWalkActivated
            insnList.add(new JumpInsnNode(IFEQ, jumpToLabel));
            insnList.add(orJump);

            AbstractInsnNode previousNode = sneakFlagNode.getPrevious();
            method.instructions.remove(sneakFlagNode); // delete IFEQ
            method.instructions.insert(previousNode, insnList); // insert new instructions
            isPatched = true;
        } else {
            isPatched = false;
        }

        return isPatched;
    }

    private final int[] doBlockCollisionsPreSig = {
            ALOAD, INVOKEINTERFACE, ALOAD, GETFIELD, ALOAD, ALOAD, ALOAD, INVOKEVIRTUAL,
            0x00, 0x00,
            GOTO
    };
    private final int[] doBlockCollisionsPostSig = {
            INVOKEVIRTUAL,
            0x00, 0x00,
            GOTO,
            0x00, 0x00, 0x00,
            ASTORE,
            0x00, 0x00,
            ALOAD, LDC, INVOKESTATIC, ASTORE,
            0x00, 0x00,
            ALOAD, LDC, INVOKEVIRTUAL, ASTORE
    };

    private boolean doBlockCollisionsPatch(MethodNode method) {
        /*
        AbstractInsnNode preNode = findPattern("doBlockCollisions", "preNode",
                method.instructions.getFirst(), doBlockCollisionsPreSig, "xxxxxxxx??x");
        AbstractInsnNode postNode = findPattern("doBlockCollisions", "postNode",
                method.instructions.getFirst(), doBlockCollisionsPostSig, "x??x???x??xxxx??xxxx");
        if(preNode != null &&
                postNode != null) {
            LabelNode endJump = new LabelNode();

            InsnList insnList = new InsnList();
            insnList.add(new VarInsnNode(ALOAD, 0)); // push this
            insnList.add(new VarInsnNode(ALOAD, 4)); // push this
            insnList.add(new VarInsnNode(ALOAD, 8)); // push this
            insnList.add(new MethodInsnNode(INVOKESTATIC,
                    NAMES.ON_DO_BLOCK_COLLISIONS.getParentClass().getRuntimeName(),
                    NAMES.ON_DO_BLOCK_COLLISIONS.getRuntimeName(),
                    NAMES.ON_DO_BLOCK_COLLISIONS.getDescriptor(),
                    false
            ));
            insnList.add(new JumpInsnNode(IFNE, endJump));

            method.instructions.insertBefore(preNode, insnList);
            method.instructions.insert(postNode, endJump);

            return true;
        } else return false;
        */
        return true;
    }
}
