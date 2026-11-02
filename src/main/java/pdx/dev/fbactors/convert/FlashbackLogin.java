package pdx.dev.fbactors.convert;

final class FlashbackLogin {
    private FlashbackLogin() {}
    static int packetId(byte[] payload) {
        int value=0;
        for(int i=0;i<5 && i<payload.length;i++) {
            int b=payload[i]&255; value|=(b&127)<<(i*7);
            if((b&128)==0) return value;
        }
        throw new IllegalArgumentException("Invalid play packet header");
    }
    static byte[] withPlayerId(byte[] payload,int playerId) {
        packetId(payload);
        int start=0;
        while((payload[start++]&128)!=0) { }
        if(payload.length-start<4) throw new IllegalArgumentException("Truncated Login player ID");
        byte[] result=payload.clone();
        for(int i=0;i<4;i++) result[start+i]=(byte)(playerId >>> (24-i*8));
        return result;
    }
}
