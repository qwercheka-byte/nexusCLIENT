import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;
/** Data-flow validation without loading Minecraft classes. */
public class Verify {
    public static void main(String[] args) throws Exception {
        for (String arg : args) {
            byte[] b=Files.readAllBytes(Path.of(arg)); b[7]=61;
            ClassNode c=new ClassNode(); new ClassReader(b).accept(c,0);
            int count=0;
            for (MethodNode m : c.methods) if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE))==0) {
                try { new Analyzer<BasicValue>(new BasicVerifier()).analyze(c.name,m); }
                catch (Exception ex) { throw new IllegalStateException(c.name+"."+m.name+m.desc,ex); }
                count++;
            }
            System.out.println("Verified " + c.name + " (" + count + " methods)");
        }
    }
}
