import dev.nexus.recovery.HudMotion;

public class HudMotionTest {
    static void check(boolean v, String msg) { if (!v) throw new AssertionError(msg); }
    static void near(float a, float b, float tol, String msg) { check(Math.abs(a-b) <= tol, msg + ": " + a + " vs " + b); }
    static void step(HudMotion m, float[][] t, boolean chat, boolean down, double x, double y, long ms) {
        m.step(t,chat,down,x,y,ms*1_000_000L,1,false);
    }
    public static void main(String[] args) {
        float[][] t=new float[5][2]; t[0][0]=100; t[3][1]=17;
        HudMotion m=new HudMotion(); step(m,t,false,false,0,0,1000);
        near(m.position(0,0),100,0,"load must snap");
        // Select at displayed position, move, then verify visual lag and target persistence.
        step(m,t,true,true,130,30,1016); step(m,t,true,true,230,130,1032);
        near(t[0][0],200,0.01f,"drag target x"); near(t[0][1],100,0.01f,"drag target y");
        check(m.position(0,0)>100 && m.position(0,0)<200,"smooth follow");
        float visual=m.position(0,0); step(m,t,true,false,230,130,1048);
        check(m.takeDirty() && !m.takeDirty(),"one save on release");
        check(m.position(0,0)>visual && m.position(0,0)<200,"continues on release");
        for(int i=0;i<200;i++) step(m,t,false,false,0,0,1064+i*16);
        near(m.position(0,0),200,0.001f,"settles");
        // Closing chat while held commits; box bounds use height for y, not width.
        step(m,t,true,true,230,130,5000); step(m,t,true,true,9999,9999,5016);
        near(t[0][0],948,0.01f,"right bound"); near(t[0][1],633,0.01f,"bottom bound uses height");
        step(m,t,false,true,9999,9999,5032); check(m.takeDirty(),"save on chat close while down");
        // Held mouse on opening chat must not start a drag.
        float[][] held=new float[5][2]; HudMotion h=new HudMotion();
        step(h,held,false,true,20,20,1000); step(h,held,true,true,20,20,1016); step(h,held,true,true,100,100,1032);
        near(held[0][0],0,0,"no drag from held click");
        // Instant setting follows immediately.
        step(h,held,true,false,0,0,1048); step(h,held,true,true,20,20,1064);
        h.step(held,true,true,100,100,1080*1_000_000L,1,true);
        near(h.position(0,0),held[0][0],0,"instant");
        // Inactive 1/2 zones cannot intercept clicks.
        float[][] b=new float[5][2]; HudMotion hidden=new HudMotion();
        step(hidden,b,true,false,0,0,1000); step(hidden,b,true,true,30,300,1016); step(hidden,b,true,true,130,400,1032);
        near(b[1][0],0,0,"hidden zone");
        // Interpolation is frame-rate independent for equal elapsed time.
        float[][] a=new float[5][2], c=new float[5][2]; HudMotion slow=new HudMotion(), fast=new HudMotion();
        step(slow,a,false,false,0,0,0); step(fast,c,false,false,0,0,0); a[0][0]=100; c[0][0]=100;
        for(int ms=20;ms<=1000;ms+=20) step(slow,a,false,false,0,0,ms);
        for(int ms=10;ms<=1000;ms+=10) step(fast,c,false,false,0,0,ms);
        near(slow.position(0,0),fast.position(0,0),0.001f,"frame independence");
        System.out.println("HudMotionTest OK");
    }
}
