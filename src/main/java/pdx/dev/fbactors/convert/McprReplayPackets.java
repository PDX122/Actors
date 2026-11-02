package pdx.dev.fbactors.convert;

import java.io.*;
import java.util.*;

final class McprReplayPackets {
    private static final Set<String> CONNECTION = Set.of("ping","keep_alive","disconnect","cookie_request","store_cookie","transfer","custom_report_details","server_links","pong_response");
    private static final Set<String> UI = Set.of("bundle_delimiter","chunk_batch_start","chunk_batch_finished","commands","command_suggestions","update_recipes","recipe_book_add","recipe_book_remove","recipe_book_settings","award_stats","update_advancements","select_advancements_tab","place_ghost_recipe","open_sign_editor","open_book","test_instance_block_status","container_close","container_set_content","container_set_data","open_horse_screen","mount_screen_open","open_screen","merchant_offers","set_cursor_item","cooldown","player_abilities","player_combat_end","player_combat_enter","player_combat_kill","set_camera","tag_query","block_changed_ack","set_chunk_cache_radius","set_chunk_cache_center","set_simulation_distance","forget_level_chunk","custom_chat_completions","debug_sample","ticking_state","ticking_step","clear_dialog","show_dialog","tracked_waypoint");
    private record Position(double x,double y,double z,float yaw,float pitch) {}
    private final Map<Integer,String> configuration,game;
    private final Map<Integer,Position> positions=new HashMap<>();
    private final int absolute;
    private byte[] profile;
    private UUID localId;
    private Position initial;
    private int localEntity;
    void gameLogin(byte[] payload) {
        try {var in=new DataInputStream(new ByteArrayInputStream(payload));readVarInt(in);localEntity=in.readInt();}
        catch(IOException failure) {throw new IllegalArgumentException("Malformed game login",failure);}
    }
    McprReplayPackets(Map<Integer,String> configuration,Map<Integer,String> game) {
        this.configuration=configuration;this.game=game;
        absolute=game.entrySet().stream().filter(e -> name(e.getValue()).equals("entity_position_sync")).mapToInt(Map.Entry::getKey).findFirst().orElse(-1);
    }
    private static String name(String value) { return value.startsWith("minecraft:")?value.substring(10):value; }
    void loginProfile(byte[] payload) {
        try {
            var bytes=new ByteArrayInputStream(payload);var in=new DataInputStream(bytes);readVarInt(in);
            int start=payload.length-bytes.available();localId=new UUID(in.readLong(),in.readLong());
            skipString(in);int properties=readVarInt(in);
            if(properties<0 || properties>1024) throw new IOException("Invalid profile properties");
            for(int i=0;i<properties;i++){skipString(in);skipString(in);if(in.readBoolean())skipString(in);}
            profile=Arrays.copyOfRange(payload,start,payload.length-bytes.available());
        } catch(IOException failure) {throw new IllegalArgumentException("Malformed login profile",failure);}
    }
    byte[] localPlayer() {
        if(profile==null || initial==null) return null;
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            out.writeLong(localId.getMostSignificantBits());out.writeLong(localId.getLeastSignificantBits());
            out.writeDouble(initial.x);out.writeDouble(initial.y);out.writeDouble(initial.z);
            out.writeFloat(initial.pitch);out.writeFloat(initial.yaw);out.writeFloat(initial.yaw);
            out.writeDouble(0);out.writeDouble(0);out.writeDouble(0);out.write(profile);writeVarInt(out,0);
            return bytes.toByteArray();
        } catch(IOException impossible) {throw new IllegalStateException(impossible);}
    }
    private static void skipString(DataInputStream in) throws IOException {
        int size=readVarInt(in);if(size<0 || size>1_048_576)throw new IOException("Invalid string length");in.skipNBytes(size);
    }
    byte[] packet(boolean config,byte[] payload) {
        String type=(config?configuration:game).get(FlashbackLogin.packetId(payload));
        if(type==null) throw new IllegalArgumentException("Unknown "+(config?"configuration":"play")+" packet ID "+FlashbackLogin.packetId(payload));
        type=name(type);
        if(CONNECTION.contains(type) || !config && UI.contains(type)) return null;
        if(config) return payload;
        if(Set.of("player_chat","delete_chat","move_minecart_along_track","move_minecart").contains(type))
            throw new IllegalArgumentException("This recording contains unsupported ReplayMod packet "+type+"; no replay was written");
        try {
            var in=new DataInputStream(new ByteArrayInputStream(payload));readVarInt(in);
            if(type.equals("custom_payload")) {
                int size=readVarInt(in);
                if(size<0 || size>32767) throw new IOException("Invalid custom payload identifier");
                String channel=new String(in.readNBytes(size),java.nio.charset.StandardCharsets.UTF_8);
                if(Set.of("minecraft:brand","minecraft:register","minecraft:unregister").contains(channel)) return null;
                throw new IllegalArgumentException("Unsupported custom payload "+channel+"; no replay was written");
            }
            if(type.equals("add_entity")) {
                int entity=readVarInt(in);in.skipNBytes(16);readVarInt(in);
                double x=in.readDouble(),y=in.readDouble(),z=in.readDouble();
                float pitch=in.readByte()*360f/256f,yaw=in.readByte()*360f/256f;
                positions.put(entity,new Position(x,y,z,yaw,pitch));
            } else if(type.equals("entity_position_sync")) {
                int entity=readVarInt(in);double x=in.readDouble(),y=in.readDouble(),z=in.readDouble();
                in.skipNBytes(24);positions.put(entity,new Position(x,y,z,in.readFloat(),in.readFloat()));
            } else if(type.equals("move_entity_pos") || type.equals("move_entity_pos_rot") || type.equals("move_entity_rot")) {
                if(absolute<0) throw new IllegalArgumentException("Relative entity movement is unsupported on this Minecraft version");
                int entity=readVarInt(in);Position before=positions.get(entity);

                if(before==null) return null;
                double x=before.x,y=before.y,z=before.z;float yaw=before.yaw,pitch=before.pitch;
                if(!type.equals("move_entity_rot")) {x=delta(x,in.readShort());y=delta(y,in.readShort());z=delta(z,in.readShort());}
                if(!type.equals("move_entity_pos")) {yaw=in.readByte()*360f/256f;pitch=in.readByte()*360f/256f;}
                boolean ground=in.readBoolean();
                Position after=new Position(x,y,z,yaw,pitch);positions.put(entity,after);
                var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
                writeVarInt(out,absolute);writeVarInt(out,entity);
                out.writeDouble(x);out.writeDouble(y);out.writeDouble(z);
                out.writeDouble(0);out.writeDouble(0);out.writeDouble(0);
                out.writeFloat(yaw);out.writeFloat(pitch);out.writeBoolean(ground);
                return bytes.toByteArray();
            } else if(type.equals("teleport_entity")) {

                int entity=readVarInt(in);double x=in.readDouble(),y=in.readDouble(),z=in.readDouble();
                if(absolute>=0) {
                    in.skipNBytes(24);float yaw=in.readFloat(),pitch=in.readFloat();
                    if(in.readInt()!=0) throw new IllegalArgumentException("Relative entity teleports are not supported by this converter");
                    positions.put(entity,new Position(x,y,z,yaw,pitch));
                } else positions.put(entity,new Position(x,y,z,in.readByte()*360f/256f,in.readByte()*360f/256f));
            } else if(type.equals("player_position")) {
                if(absolute>=0) {
                    readVarInt(in);double x=in.readDouble(),y=in.readDouble(),z=in.readDouble();
                    double vx=in.readDouble(),vy=in.readDouble(),vz=in.readDouble();float yaw=in.readFloat(),pitch=in.readFloat();
                    if(in.readInt()!=0) throw new IllegalArgumentException("Relative player teleports are not supported by this converter");
                    var position=new Position(x,y,z,yaw,pitch);positions.put(localEntity,position);
                    if(initial==null) {initial=position;return null;}
                    var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
                    writeVarInt(out,absolute);writeVarInt(out,localEntity);out.writeDouble(x);out.writeDouble(y);out.writeDouble(z);
                    out.writeDouble(vx);out.writeDouble(vy);out.writeDouble(vz);out.writeFloat(yaw);out.writeFloat(pitch);out.writeBoolean(false);
                    return bytes.toByteArray();
                } else throw new IllegalArgumentException("Player teleports are unsupported on this Minecraft version");
            }
            return payload;
        } catch(IOException failure) { throw new IllegalArgumentException("Malformed "+type+" packet",failure); }
    }
    private static double delta(double base,short amount) { return amount==0?base:(Math.round(base*4096)+amount)/4096.0; }
    static int readVarInt(DataInputStream input) throws IOException {
        int value=0;for(int i=0;i<5;i++){int b=input.readUnsignedByte();value|=(b&127)<<(i*7);if((b&128)==0)return value;}
        throw new IOException("Invalid VarInt");
    }
    static void writeVarInt(DataOutputStream output,int value) throws IOException {
        do {int part=value&127;value>>>=7;output.writeByte(value==0?part:part|128);}while(value!=0);
    }
}
