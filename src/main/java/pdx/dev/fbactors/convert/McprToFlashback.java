package pdx.dev.fbactors.convert;

import com.moulberry.flashback.action.ActionConfigurationPacket;
import com.moulberry.flashback.action.ActionGamePacket;
import com.moulberry.flashback.action.ActionNextTick;
import com.moulberry.flashback.io.ReplayWriter;
import com.moulberry.flashback.record.FlashbackChunkMeta;
import com.moulberry.flashback.record.FlashbackMeta;
import net.minecraft.core.RegistryAccess;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class McprToFlashback {

    private static final String CHUNK = "c0.flashback";
    private static final String METADATA = "metadata.json";

    private McprToFlashback() {
    }

    public record Result(boolean ok, Path path, String message) {
    }

    public static Result convert(Path mcpr, @Nullable RegistryAccess registries,
                                 String gameVersion, int dataVersion, int protocolVersion,
                                 Path outDir) {
        return convert(mcpr, registries, gameVersion, dataVersion, protocolVersion, outDir, null);
    }

    public static Result convert(Path mcpr, @Nullable RegistryAccess registries,
                                 String gameVersion, int dataVersion, int protocolVersion,
                                 Path outDir, @Nullable Path loginSource) {
        McprReader.Recording recording;
        try {
            recording = McprReader.read(mcpr);
        } catch (Exception e) {
            FlashbackActors.LOGGER.error("Could not read {} as an .mcpr file", mcpr.getFileName(), e);
            return new Result(false, null, "Could not read the .mcpr file, see the log");
        }

        if (!gameVersion.equals(recording.mcVersion())) {
            return new Result(false, null, "Recording is for Minecraft " + recording.mcVersion()
                + " but this game is " + gameVersion + "; same-version conversion only");
        }
        if(recording.meta().has("protocol") && recording.meta().get("protocol").getAsInt()!=protocolVersion)
            return new Result(false,null,"Recording protocol does not match this Minecraft version");

        String unsupported=ConversionSupport.unsupportedReason(recording);
        if(unsupported!=null) return new Result(false,null,unsupported);
        ensureActions();

        boolean stateful=recording.meta().has("fileFormatVersion") && recording.meta().get("fileFormatVersion").getAsInt()==14;
        McprProtocolStream.Prepared prepared;
        var packetFilter=new McprReplayPackets(pdx.dev.fbactors.compat.Compat.conversionPacketNames(true),pdx.dev.fbactors.compat.Compat.conversionPacketNames(false));
        try { prepared=stateful?McprProtocolStream.prepare(recording,pdx.dev.fbactors.compat.Compat.conversionProtocolIds(),
            packetFilter):null; }
        catch(Exception failure) { return new Result(false,null,"Invalid or unsupported ReplayMod protocol stream: "+failure.getMessage()); }
        byte[] login = prepared==null?loginBytes(outDir, loginSource, gameVersion):prepared.login();
        if (login == null) {
            return new Result(false, null,
                "No same-version Flashback replay to borrow a login from;"
                    + " open any Flashback replay once first");
        }
        if(prepared==null) {
            try {
                packetFilter.gameLogin(login);
                int loginId=FlashbackLogin.packetId(login);
                for(var entry:recording.ticks().entrySet()) {
                    var filtered=new ArrayList<byte[]>();
                    for(byte[] packet:entry.getValue()) {
                        if(FlashbackLogin.packetId(packet)==loginId) continue;
                        byte[] replayPacket=packetFilter.packet(false,packet);
                        if(replayPacket!=null) filtered.add(replayPacket);
                    }
                    entry.setValue(filtered);
                }
            } catch(IllegalArgumentException failure) {
                return new Result(false,null,"Unsupported ReplayMod recording: "+failure.getMessage());
            }
        }

        byte[] chunk;
        try {
            chunk = buildChunk(recording, registries, login, prepared);
        } catch (Exception e) {
            FlashbackActors.LOGGER.error("Could not convert {} to a Flashback chunk", mcpr.getFileName(), e);
            return new Result(false, null, "Conversion failed, see the log");
        }

        String base = mcpr.getFileName().toString().replaceAll("\\.mcpr$", "");
        Path target = uniquePath(outDir, base + " (converted)");
        try {
            writeZip(target, chunk, metadata(recording, base, dataVersion, protocolVersion));
        } catch (Exception e) {
            FlashbackActors.LOGGER.error("Could not write converted replay for {}", mcpr.getFileName(), e);
            return new Result(false, null, "Could not write the converted replay, see the log");
        }

        FlashbackActors.LOGGER.info("Converted {} ({} ticks) to {}",
            mcpr.getFileName(), recording.totalTicks(), target.getFileName());
        return new Result(true, target, "Converted " + target.getFileName());
    }

    static void ensureActions() {
        registerOnce(com.moulberry.flashback.action.ActionRegistry.getActions()
            .contains(ActionGamePacket.INSTANCE), ActionGamePacket.INSTANCE);
        registerOnce(com.moulberry.flashback.action.ActionRegistry.getActions()
            .contains(ActionConfigurationPacket.INSTANCE), ActionConfigurationPacket.INSTANCE);
        registerOnce(com.moulberry.flashback.action.ActionRegistry.getActions()
            .contains(ActionNextTick.INSTANCE), ActionNextTick.INSTANCE);
        registerOnce(com.moulberry.flashback.action.ActionRegistry.getActions()
            .contains(com.moulberry.flashback.action.ActionCreateLocalPlayer.INSTANCE), com.moulberry.flashback.action.ActionCreateLocalPlayer.INSTANCE);
    }

    private static void registerOnce(boolean present,
                                     com.moulberry.flashback.action.Action action) {
        if (!present) {
            try {
                com.moulberry.flashback.action.ActionRegistry.register(action);
            } catch (IllegalArgumentException already) {

            }
        }
    }

    static byte[] buildChunk(McprReader.Recording recording,
                             @Nullable RegistryAccess registries, byte[] login) {
        return buildChunk(recording,registries,login,null);
    }
    private static byte[] buildChunk(McprReader.Recording recording,
                             @Nullable RegistryAccess registries, byte[] login,McprProtocolStream.Prepared prepared) {
        ReplayWriter writer = new ReplayWriter(registries);
        writer.startSnapshot();
        if(prepared!=null) for(byte[] packet:prepared.configuration()) {
            writer.startAction(ActionConfigurationPacket.INSTANCE);
            writer.friendlyByteBuf().writeBytes(packet);
            writer.finishAction(ActionConfigurationPacket.INSTANCE);
        }
        writer.startAction(ActionGamePacket.INSTANCE);
        writer.friendlyByteBuf().writeBytes(login);
        writer.finishAction(ActionGamePacket.INSTANCE);
        if(prepared!=null && prepared.localPlayer()!=null) {
            var action=com.moulberry.flashback.action.ActionCreateLocalPlayer.INSTANCE;
            writer.startAction(action);writer.friendlyByteBuf().writeBytes(prepared.localPlayer());writer.finishAction(action);
        }
        writer.endSnapshot();

        for (int tick = 0; tick < recording.totalTicks(); tick++) {
            if(prepared!=null) {
                for(var action:prepared.ticks().getOrDefault(tick,List.of())) {
                    var kind=action.configuration()?ActionConfigurationPacket.INSTANCE:ActionGamePacket.INSTANCE;
                    writer.startAction(kind); writer.friendlyByteBuf().writeBytes(action.payload()); writer.finishAction(kind);
                }
                writer.startAndFinishAction(ActionNextTick.INSTANCE);
                continue;
            }
            List<byte[]> packets = recording.ticks().get(tick);
            if (packets != null) {
                for (byte[] payload : packets) {
                    writer.startAction(ActionGamePacket.INSTANCE);
                    writer.friendlyByteBuf().writeBytes(payload);
                    writer.finishAction(ActionGamePacket.INSTANCE);
                }
            }
            writer.startAndFinishAction(ActionNextTick.INSTANCE);
        }
        return writer.popBytes();
    }

    @Nullable
    static byte[] loginBytes(Path folder, @Nullable Path loginSource, String gameVersion) {
        try {
            if (loginSource != null && Files.isRegularFile(loginSource)) {
                byte[] login = loginFrom(loginSource, gameVersion);
                if (login != null) {
                    return login;
                }
            }
            if (folder == null || !Files.isDirectory(folder)) {
                return null;
            }
            List<Path> candidates = new ArrayList<>();
            try (var files = Files.list(folder)) {
                for (Path file : files
                        .filter(p -> p.getFileName().toString().endsWith(".zip")).toList()) {
                    candidates.add(file);
                }
            }
            candidates.sort((a, b) -> Long.compare(modified(b), modified(a)));
            for (Path candidate : candidates) {
                if (candidate.equals(loginSource)) {
                    continue;
                }
                byte[] login = loginFrom(candidate, gameVersion);
                if (login != null) {
                    FlashbackActors.LOGGER.info("Borrowing a login for conversion from {}",
                        candidate.getFileName());
                    return login;
                }
            }
        } catch (Exception e) {
            FlashbackActors.LOGGER.warn("Could not find a login to borrow", e);
        }
        return null;
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    @Nullable
    private static byte[] loginFrom(Path zip, String gameVersion) {
        try {
            String meta = McprReader.entryText(zip, "metadata.json");
            com.google.gson.JsonObject parsed =
                com.google.gson.JsonParser.parseString(meta).getAsJsonObject();
            if (parsed.has("version_string")
                && !gameVersion.equals(parsed.get("version_string").getAsString())) {
                return null;
            }
            byte[] chunk = readChunk(zip);
            if (chunk == null) {
                return null;
            }
            FlashbackChunk.Parsed parsed2 = FlashbackChunk.parse(chunk);
            int gamePacket = parsed2.names().indexOf("flashback:action/game_packet");
            if (gamePacket < 0) {
                return null;
            }
            int loginId=pdx.dev.fbactors.compat.Compat.clientboundLoginPacketId();
            for (FlashbackChunk.Action action : parsed2.snapshot()) {
                if (action.index() == gamePacket && FlashbackLogin.packetId(action.payload()) == loginId) {
                    return withPlayerId(action.payload(), 2000000000);
                }
            }
        } catch (Exception e) {
            FlashbackActors.LOGGER.debug("Not a login source: {}", zip.getFileName());
        }
        return null;
    }

    @Nullable
    private static byte[] readChunk(Path zip) {
        try (java.util.zip.ZipFile file = new java.util.zip.ZipFile(zip.toFile())) {
            java.util.zip.ZipEntry entry = file.getEntry("c0.flashback");
            if (entry == null) {
                return null;
            }
            long size = entry.getSize();
            if (size > 256L * 1024L * 1024L) {
                return null;
            }
            try (var in = file.getInputStream(entry)) {
                byte[] data = in.readAllBytes();
                if (data.length > 256 * 1024 * 1024) {
                    return null;
                }
                return data;
            }
        } catch (Exception e) {
            return null;
        }
    }

    static byte[] withPlayerId(byte[] payload, int playerId) {
        return FlashbackLogin.withPlayerId(payload,playerId);
    }

    private static String metadata(McprReader.Recording recording, String name,
                                   int dataVersion, int protocolVersion) {
        FlashbackMeta meta = new FlashbackMeta();
        meta.replayIdentifier = UUID.randomUUID();
        meta.name = name;
        meta.totalTicks = recording.totalTicks();
        meta.dataVersion = dataVersion;
        meta.protocolVersion = protocolVersion;
        meta.worldName = recording.serverName();
        FlashbackChunkMeta chunk = new FlashbackChunkMeta();
        chunk.duration = recording.totalTicks();
        chunk.forcePlaySnapshot = true;
        LinkedHashMap<String, FlashbackChunkMeta> chunks = new LinkedHashMap<>();
        chunks.put(CHUNK, chunk);
        meta.chunks = chunks;
        var json=meta.toJson();
        json.addProperty("version_string",recording.mcVersion());
        return json.toString();
    }

    private static void writeZip(Path target, byte[] chunk, String metadata) throws IOException {
        Files.createDirectories(target.getParent());
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))) {
            out.putNextEntry(new ZipEntry(METADATA));
            out.write(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry(CHUNK));
            out.write(chunk);
            out.closeEntry();
        }
    }

    private static Path uniquePath(Path folder, String name) {
        String stamp = name.replaceAll("[^A-Za-z0-9_(). -]", "_");
        Path candidate = folder.resolve(stamp + ".zip");
        int n = 2;
        while (Files.exists(candidate)) {
            candidate = folder.resolve(stamp + " " + n + ".zip");
            n++;
        }
        return candidate;
    }
}
