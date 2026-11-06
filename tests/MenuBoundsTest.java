import io.github.kacperla.touchzoom.MenuBounds;

public final class MenuBoundsTest {
    static int checks;
    static void bounds(int w,int h,int l,int t,int r,int b,boolean ime,int keyboard,int... expected){
        MenuBounds actual=MenuBounds.calculate(w,h,l,t,r,b,ime,keyboard);
        int[] result={actual.left,actual.top,actual.width,actual.height};
        for(int i=0;i<4;i++){checks++;if(result[i]!=expected[i])throw new AssertionError(result[i]+" != "+expected[i]);}
    }
    public static void main(String[] args){
        bounds(1080,2400,0,80,0,60,false,0,0,80,1080,2260);
        bounds(1080,2400,0,80,0,60,true,900,0,80,1080,1420);
        bounds(1080,2400,0,80,0,60,false,900,0,80,1080,2260);
        bounds(1080,2400,0,80,0,60,true,0,0,80,1080,2260);
        bounds(2400,1080,120,40,80,60,true,500,120,40,2200,540);
        bounds(300,400,40,50,40,30,true,200,40,50,220,150);
        bounds(300,400,40,50,40,30,true,500,40,50,220,0);
        checks++;if(MenuBounds.calculate(300,400,40,50,40,30,true,500).usable())throw new AssertionError("No room must not open an input overlay");
        bounds(300,400,-10,-20,-10,-30,false,-500,0,0,300,400);
        bounds(300,400,500,500,500,500,false,0,300,400,0,0);
        for(int width:new int[]{300,1080,2400})for(int height:new int[]{400,1080,2400})
        for(int keyboard=0;keyboard<height;keyboard+=50){
            MenuBounds b=MenuBounds.calculate(width,height,10,30,20,20,true,keyboard);
            checks++;if(b.left+b.width>width-20||b.top+b.height>height||b.top<30)throw new AssertionError("Outside safe display bounds");
            checks++;if(b.usable()&&b.top+b.height>height-Math.max(20,keyboard))throw new AssertionError("Covers keyboard");
        }
        System.out.println(checks+" menu safe bounds assertions passed");
    }
}
