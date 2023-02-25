import arc.Events;
import arc.func.Cons;
import arc.func.Cons2;
import arc.struct.IntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.net.*;

public class NetG extends Net {
    public NetG(NetProvider provider) {
        super(provider);
    }
    @Override
    public <T> void handleClient(Class<T> type, Cons<T> listener) {
    }
    public <T> void handCli(Class<T> type,Cons<T> li){
        super.handleClient(type,li);
    }

    @Override
    public <T> void handleServer(Class<T> type, Cons2<NetConnection, T> listener) {

    }
    public IntMap<Streamable.StreamBuilder> streams = Main.getField("streams",Net.class, IntMap.class,this);
    public Streamable.StreamBuilder currentStream = Main.getField("currentStream", Net.class, Streamable.StreamBuilder.class,this);
    public Seq<Packet> packetQueue = Main.getField("packetQueue",Net.class, Seq.class,this);
    public ObjectMap clientListeners = Main.getField("clientListeners",Net.class,ObjectMap.class,this);



    @Override
    public void handleClientReceived(Packet object) {
        object.handled();
        Events.fire(object);
        if (object instanceof Packets.StreamBegin) {
            Packets.StreamBegin b = (Packets.StreamBegin)object;
            this.streams.put(b.id, this.currentStream = new Streamable.StreamBuilder(b));
        } else if (object instanceof Packets.StreamChunk) {
            Packets.StreamChunk c = (Packets.StreamChunk)object;
            Streamable.StreamBuilder builder = (Streamable.StreamBuilder)this.streams.get(c.id);
            if (builder == null) {
                throw new RuntimeException("Received stream chunk without a StreamBegin beforehand!");
            }

            builder.add(c.data);
            Vars.netClient.resetTimeout();
            if (builder.isDone()) {
                this.streams.remove(builder.id);
                this.handleClientReceived(builder.build());
                this.currentStream = null;
            }
        } else {
            int p = object.getPriority();
            if (!Main.getField("clientLoaded",Net.class, Boolean.class,this) && p != 2) {
                if (p != 0) {
                    this.packetQueue.add(object);
                }
            } else if (this.clientListeners.get(object.getClass()) != null) {
                ((Cons)this.clientListeners.get(object.getClass())).get(object);
            } else {
                try {
                    if (object instanceof LabelCallPacket)
                        return;
                    if (object instanceof InfoPopupCallPacket)
                        return;
                    if (object instanceof MenuCallPacket)
                        return;
                    if (object instanceof InfoToastCallPacket)
                        return;
                    if (object instanceof InfoMessageCallPacket message) {
                        Log.info(message.message);
                        return;
                    }
                    if (object instanceof WorldDataBeginCallPacket){
                        NetClientG.worldDataBegin();
                        return;
                    }
                    object.handleClient();
                }catch (NullPointerException e){
                    Log.info(e);
                }
            }
        }

    }

}
