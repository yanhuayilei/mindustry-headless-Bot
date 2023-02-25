import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.util.Align;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.*;
import mindustry.server.ServerLauncher;

import java.lang.reflect.Field;
import static mindustry.Vars.*;

public class Main {
    public static void main(String[] args) {
        //System.out.println("Hello world!");
        Events.on(SendMessageCallPacket2.class,i->{
            Log.info(i.message);
        });
        /*Events.on(InfoPopupCallPacket.class,i->{
            Log.info(Align.topLeft);
            Log.info(i.align+"::"+i.duration+"::"+i.bottom+"::"+i.top+"::"+i.left+"::"+i.right);

        });*/
        Events.on(InfoPopupReliableCallPacket.class,i->{
        });
        Events.on(EventType.ServerLoadEvent.class,i->{
            player = Player.create();
            player.name = "Yan-Bot";

            playerColors[0] = Color.red;
            net = new NetG(Vars.platform.getNet());
            netClient = new NetClientG();
            Core.app.addListener(netClient);
            NetClientG.connect("other.xem8k5.top",10404);
        });
        //ServerLauncher.main(new String[]{});
        ServerL.main(new String[]{});
    }
    public static <T> T getField(String name,Class requireClass,Class<T> returnType,Object obj){
        try {
            Field field = requireClass.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(obj);
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static void setField(String name,Class requireClass,Object setting,Object obj){
        try{
            Field field = requireClass.getDeclaredField(name);
            field.setAccessible(true);
            field.set(obj,setting);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}
