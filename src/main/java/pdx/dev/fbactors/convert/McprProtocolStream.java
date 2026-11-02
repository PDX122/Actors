package pdx.dev.fbactors.convert;

import java.util.*;

final class McprProtocolStream {
    record Action(boolean configuration,byte[] payload) {}
    record Prepared(List<byte[]> configuration,byte[] login,TreeMap<Integer,List<Action>> ticks,byte[] localPlayer) {}
    private enum State { LOGIN, CONFIGURATION, PLAY }
    static Prepared prepare(McprReader.Recording recording,int[] ids) {
        return prepare(recording,ids,null);
    }
    static Prepared prepare(McprReader.Recording recording,int[] ids,McprReplayPackets packets) {
        if(ids.length!=4) throw new IllegalArgumentException("Missing protocol IDs");
        var configuration=new ArrayList<byte[]>();
        var ticks=new TreeMap<Integer,List<Action>>();
        byte[] login=null;
        State state=State.LOGIN;
        for(var entry:recording.ticks().entrySet()) for(byte[] packet:entry.getValue()) {
            int id=FlashbackLogin.packetId(packet);
            if(state==State.LOGIN) {
                if(id!=ids[0]) throw new IllegalArgumentException("Unsupported MCPR login negotiation packet "+id);
                if(packets!=null) packets.loginProfile(packet);
                state=State.CONFIGURATION;
            } else if(state==State.CONFIGURATION) {
                if(id==ids[1]) { state=State.PLAY; continue; }
                byte[] replayPacket=packets==null?packet:packets.packet(true,packet);
                if(replayPacket==null) continue;
                if(login==null) configuration.add(replayPacket);
                else ticks.computeIfAbsent(entry.getKey(),k -> new ArrayList<>()).add(new Action(true,replayPacket));
            } else {
                if(id==ids[3]) { state=State.CONFIGURATION; continue; }
                if(login==null) {
                    if(id!=ids[2]) throw new IllegalArgumentException("MCPR play stream does not begin with Login");
                    if(packets!=null) packets.gameLogin(packet);
                    login=packet;
                } else {
                    byte[] replayPacket=packets==null?packet:packets.packet(false,packet);
                    if(replayPacket!=null) ticks.computeIfAbsent(entry.getKey(),k -> new ArrayList<>()).add(new Action(false,replayPacket));
                }
            }
        }
        if(login==null || state!=State.PLAY) throw new IllegalArgumentException("MCPR ends before protocol negotiation finishes");
        if(packets!=null && packets.localPlayer()==null) throw new IllegalArgumentException("Recording has no supported initial player position; no replay was written");
        return new Prepared(List.copyOf(configuration),login,ticks,packets==null?null:packets.localPlayer());
    }
}
