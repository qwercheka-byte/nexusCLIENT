import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Narrow, fail-closed transformations of the known 1.0.34 class files. */
public final class Patch implements Opcodes {
    static final String HUD = "dev/nexus/client/hud/RShiftHud";
    static final String PANEL = "dev/nexus/client/ui/panel/RShiftPanel";
    static final String HA = "dev/nexus/recovery/HudAdapter";
    static final String PA = "dev/nexus/recovery/PanelMotion";
    static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    static MethodNode method(ClassNode c, String name, String desc) {
        List<MethodNode> m = new ArrayList<>();
        for (MethodNode n : c.methods) if (n.name.equals(name) && n.desc.equals(desc)) m.add(n);
        check(m.size() == 1, c.name + "." + name + desc + ": expected exactly one method");
        return m.get(0);
    }
    static AbstractInsnNode realPrev(AbstractInsnNode n) {
        do { n = n.getPrevious(); } while (n != null && n.getOpcode() < 0);
        return n;
    }
    static void replace(MethodNode m, InsnList code) {
        m.instructions = code; m.tryCatchBlocks.clear();
        if (m.localVariables != null) m.localVariables.clear();
        m.visibleLocalVariableAnnotations = null; m.invisibleLocalVariableAnnotations = null;
    }
    static void patchHud(ClassNode c) {
        MethodNode render = method(c, "render", "(Ldev/nexus/client/event/events/HudRenderEvent;)V");
        MethodInsnNode call = null, firstZone = null;
        int dragCalls = 0, zoneCalls = 0;
        for (AbstractInsnNode n : render.instructions.toArray()) if (n instanceof MethodInsnNode) {
            MethodInsnNode x = (MethodInsnNode)n;
            if (x.owner.equals(HUD) && x.name.equals("handleDrag") && x.desc.equals("(Lnet/minecraft/class_332;ZII)V")) { call = x; dragCalls++; }
            if (x.owner.equals(HUD) && x.name.equals("zone") && x.desc.equals("(I)V")) { if (firstZone == null) firstZone = x; zoneCalls++; }
        }
        check(dragCalls == 1 && zoneCalls == 3, "unexpected HUD render structure");
        AbstractInsnNode a4=realPrev(call), a3=realPrev(a4), a2=realPrev(a3), a1=realPrev(a2);
        check(a1 instanceof VarInsnNode && a1.getOpcode()==ALOAD && ((VarInsnNode)a1).var==1 &&
              a2 instanceof VarInsnNode && a2.getOpcode()==ILOAD && ((VarInsnNode)a2).var==5 &&
              a3 instanceof VarInsnNode && a3.getOpcode()==ILOAD && ((VarInsnNode)a3).var==3 &&
              a4 instanceof VarInsnNode && a4.getOpcode()==ILOAD && ((VarInsnNode)a4).var==4, "unexpected drag arguments");
        AbstractInsnNode start = realPrev(firstZone);
        check(start != null && start.getOpcode() == ICONST_0, "unexpected first HUD zone");
        InsnList moved = new InsnList(); moved.add(new VarInsnNode(ALOAD, 1)); moved.add(new VarInsnNode(ILOAD, 5));
        moved.add(new VarInsnNode(ILOAD, 3)); moved.add(new VarInsnNode(ILOAD, 4));
        moved.add(new MethodInsnNode(INVOKESTATIC, HUD, "handleDrag", "(Lnet/minecraft/class_332;ZII)V", false));
        render.instructions.insertBefore(start, moved);
        render.instructions.remove(a1); render.instructions.remove(a2); render.instructions.remove(a3); render.instructions.remove(a4); render.instructions.remove(call);

        MethodNode zone = method(c, "zone", "(I)V"); InsnList z = new InsnList();
        for (int axis=0; axis<2; axis++) {
            z.add(new VarInsnNode(ILOAD, 0)); z.add(new InsnNode(axis == 0 ? ICONST_0 : ICONST_1));
            z.add(new MethodInsnNode(INVOKESTATIC, HA, "position", "(II)F", false));
            z.add(new FieldInsnNode(PUTSTATIC, HUD, axis == 0 ? "curDx" : "curDy", "F"));
        }
        z.add(new InsnNode(RETURN)); replace(zone, z);
        MethodNode drag = method(c, "handleDrag", "(Lnet/minecraft/class_332;ZII)V"); InsnList d = new InsnList();
        d.add(new VarInsnNode(ALOAD, 0)); d.add(new VarInsnNode(ILOAD, 1)); d.add(new VarInsnNode(ILOAD, 2)); d.add(new VarInsnNode(ILOAD, 3));
        d.add(new FieldInsnNode(GETSTATIC, HUD, "OFF", "[[F"));
        for (String f : new String[]{"scale", "offsetX", "offsetY"}) d.add(new FieldInsnNode(GETSTATIC, HUD, f, "F"));
        d.add(new MethodInsnNode(INVOKESTATIC, HA, "frame", "(Lnet/minecraft/class_332;ZII[[FFFF)V", false));
        d.add(new InsnNode(RETURN)); replace(drag, d);
        method(c, "save", "()V");
        for (MethodNode m : c.methods) check(!m.name.equals("nexus$save"), "save bridge already exists");
        MethodNode bridge = new MethodNode(ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, "nexus$save", "()V", null, null);
        bridge.instructions.add(new MethodInsnNode(INVOKESTATIC, HUD, "save", "()V", false)); bridge.instructions.add(new InsnNode(RETURN));
        c.methods.add(bridge);
    }
    static void patchPanel(ClassNode c) {
        MethodNode render = method(c, "render", "(Lnet/minecraft/class_332;IIII)V");
        InsnList start = new InsnList(); start.add(new VarInsnNode(ALOAD, 0));
        start.add(new MethodInsnNode(INVOKESTATIC, PA, "frame", "(Ljava/lang/Object;)V", false));
        render.instructions.insert(start);
        MethodNode layout = method(c, "updateLayout", "(II)V");
        int found = 0;
        for (AbstractInsnNode n : layout.instructions.toArray()) if (n instanceof FieldInsnNode) {
            FieldInsnNode f=(FieldInsnNode)n;
            if (f.getOpcode()==PUTFIELD && f.owner.equals(PANEL) && f.name.equals("panelY") && f.desc.equals("I")) {
                found++;
                InsnList add=new InsnList(); add.add(new VarInsnNode(ALOAD,0)); add.add(new InsnNode(DUP));
                add.add(new FieldInsnNode(GETFIELD,PANEL,"panelY","I")); add.add(new VarInsnNode(ALOAD,0));
                add.add(new VarInsnNode(ALOAD,0)); add.add(new FieldInsnNode(GETFIELD,PANEL,"unit","F"));
                add.add(new MethodInsnNode(INVOKESTATIC,PA,"offset","(Ljava/lang/Object;F)I",false));
                add.add(new InsnNode(IADD)); add.add(new FieldInsnNode(PUTFIELD,PANEL,"panelY","I"));
                layout.instructions.insert(n,add);
            }
        }
        check(found==1,"unexpected panel layout structure");
    }
    public static void main(String[] args) throws Exception {
        check(args.length==3,"usage: Patch input.class output.class hud|panel");
        byte[] bytes=Files.readAllBytes(Path.of(args[0]));
        check(bytes.length>8 && bytes[6]==0 && bytes[7]==65,"expected Java 21 class");
        // JDK 17's bundled ASM refuses the class-version header. Java 21 introduces no
        // new class-file constructs used here; restore the original header after rewriting.
        bytes[7]=61;
        ClassNode c=new ClassNode(); new ClassReader(bytes).accept(c,0);
        if (args[2].equals("hud")) { check(c.name.equals(HUD),"wrong HUD class"); patchHud(c); }
        else { check(args[2].equals("panel") && c.name.equals(PANEL),"wrong panel class"); patchPanel(c); }
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(w); byte[] out=w.toByteArray(); out[7]=65;
        Path output=Path.of(args[1]); Files.createDirectories(output.getParent()); Files.write(output,out);
    }
}
