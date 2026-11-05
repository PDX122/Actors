package pdx.dev.fbactors.events;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bounded, deterministic sound design generated at playback, not a weapons simulation. */
public final class EventPressure {
    public static final int RATE=24000;
    private EventPressure() {}
    public static String key(double size,double distance) {
        int s=Math.max(0,Math.min(3,(int)Math.round(size-1))),d=Math.max(0,Math.min(3,(int)(distance/350)));
        return "events/generated/pressure_"+s+"_"+d;
    }
    public static double delay(double distance) {return Math.min(8,Math.max(0,distance)/343.);}
    public static short[] synthesize(int size,int distance) {
        int count=RATE*16;var pcm=new short[count];
        long seed=0x715193L+size*731+distance*113;double low=0,mid=0;
        double cutoff=1-Math.exp(-2*Math.PI*(950./(1+distance*1.6))/RATE);
        for(int i=0;i<count;i++) {
            double t=i/(double)RATE;
            seed^=seed<<13;seed^=seed>>>7;seed^=seed<<17;
            double white=(seed>>>11)*0x1.0p-52-1;
            low+=.008*(white-low);mid+=cutoff*(white-mid);
            double attack=(1-Math.exp(-t*70))*Math.exp(-t/(3.5+size*.6));
            double pressure=(1-t/.045)*Math.exp(-t/.045)*.35;
            double chamber=Math.sin(t*(2*Math.PI*(31-size*2.4))+Math.sin(t*3)*.8)*Math.exp(-t*.38);
            double roll=(low*4+mid*.8+chamber*.10)*(1+.22*Math.sin(t*8.3)+.12*Math.sin(t*15.7));
            double signal=(pressure+roll*attack)*Math.min(1,(16-t)/2);
            pcm[i]=(short)(Math.tanh(signal*1.5)*25000);
        }
        return pcm;
    }
    public static com.mojang.blaze3d.audio.SoundBuffer buffer(String location) {
        var parts=location.substring(location.lastIndexOf("pressure_")+9).replace(".ogg","").split("_");
        int size=Integer.parseInt(parts[0]),distance=Integer.parseInt(parts[1]);
        if(size<0 || size>3 || distance<0 || distance>3)throw new IllegalArgumentException("Invalid event sound parameters");
        var samples=synthesize(size,distance);
        var pcm=org.lwjgl.system.MemoryUtil.memAlloc(samples.length*2).order(ByteOrder.LITTLE_ENDIAN);
        pcm.asShortBuffer().put(samples);
        return new com.mojang.blaze3d.audio.SoundBuffer(pcm,new javax.sound.sampled.AudioFormat(RATE,16,1,true,false));
    }
}
