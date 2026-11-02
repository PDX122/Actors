package pdx.dev.fbactors.convert;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.util.TreeMap;

class ConversionSupportTest {
    private McprReader.Recording recording(int version) {
        var meta=new JsonObject(); meta.addProperty("fileFormatVersion",version);
        return new McprReader.Recording(meta,1,"26.2","QA",new TreeMap<>(),1);
    }
    @Test void supportsKnownStatefulFormatAndRejectsFutureFormats() {
        assertNull(ConversionSupport.unsupportedReason(recording(14)));
        assertNotNull(ConversionSupport.unsupportedReason(recording(15)));
    }
    @Test void keepsLegacyPlayOnlyRecordingsEligible() {
        assertNull(ConversionSupport.unsupportedReason(recording(13)));
    }
}
