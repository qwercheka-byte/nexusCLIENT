import dev.nexus.recovery.HudAdapter;
import dev.nexus.recovery.PanelMotion;
import dev.nexus.client.hud.RShiftHud;
import dev.nexus.client.render.Render2D;
import dev.nexus.client.ui.animation.AnimationSettings;
import net.minecraft.class_310;
import net.minecraft.class_332;
import org.lwjgl.glfw.GLFW;

public class AdapterTest {
    static void check(boolean v, String m) { if (!v) throw new AssertionError(m); }
    static void frame(float[][] off, boolean chat, double x, double y, boolean down) {
        class_310.INSTANCE.field_1729.x=x; class_310.INSTANCE.field_1729.y=y;
        GLFW.down=down?1:0; Render2D.count=0;
        HudAdapter.frame(new class_332(),chat,1280,720,off,1,0,0);
    }
    public static void main(String[] args) throws Exception {
        AnimationSettings.instant=true;
        float[][] off=new float[5][2]; off[0][0]=100; off[3][0]=150;
        frame(off,true,0,0,false);
        check(HudAdapter.position(0,0)==100 && Render2D.count==3,"loaded positions and visible zones");
        check(Render2D.x==116 && Render2D.y==17,"top outline doesn't inherit center offset");
        frame(off,true,130,30,true); frame(off,true,230,130,true);
        check(off[0][0]==200 && off[0][1]==100,"adapter updates target");
        check(Render2D.x==216 && Render2D.y==117,"outline tracks visible element");
        frame(off,false,230,130,true);
        check(RShiftHud.saves==1,"close chat saves even while held");
        Object p=new Object(); AnimationSettings.instant=false; PanelMotion.frame(p);
        check(PanelMotion.offset(p,1)==20,"opening offset");
        Thread.sleep(30); PanelMotion.frame(p);
        int next=PanelMotion.offset(p,1);
        check(next<20 && next>0,"panel interpolates");
        check(PanelMotion.offset(p,1)==next,"input layout sees same displayed offset");
        AnimationSettings.instant=true; PanelMotion.frame(p);
        check(PanelMotion.offset(p,1)==0,"instant GUI");
        System.out.println("AdapterTest OK");
    }
}
