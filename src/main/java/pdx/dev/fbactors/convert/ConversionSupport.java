package pdx.dev.fbactors.convert;

final class ConversionSupport {
    private ConversionSupport() {}
    static String unsupportedReason(McprReader.Recording recording) {
        var format=recording.meta().get("fileFormatVersion");
        if(format!=null && format.getAsInt()>14) return
            "This ReplayMod file format is newer than this converter supports. Conversion was stopped before writing a replay.";
        return null;
    }
}
