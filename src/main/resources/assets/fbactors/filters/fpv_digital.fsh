#version 330 core
uniform sampler2D InSampler;
uniform float Time;
uniform float Amount;
uniform float Distortion;
uniform float Clarity;
uniform float Hud;
uniform float Layout;
uniform float HudScale;
uniform float HudMargin;
uniform float HudX;
uniform float HudY;
uniform float ShowClock;
uniform float ClockOffset;
uniform float ClockRate;
uniform float Recording;
uniform float ShowTelemetry;
uniform float ShowHorizon;
uniform float AltitudeOffset;
uniform float Imperial;
uniform float Battery;
uniform float BatteryDrain;
uniform float Link;
uniform float FrameRate;
uniform float Iso;
uniform float Shutter;
uniform float LiveSpeed;
uniform float LiveAltitude;
uniform float LiveClimb;
uniform float LiveHeading;
uniform float LivePitch;
uniform float LiveRoll;
uniform float LiveX;
uniform float LiveZ;
in vec2 texCoord;
out vec4 fragColor;
vec3 sampleAt(vec2 uv){return texture(InSampler,clamp(uv,vec2(0.001),vec2(0.999))).rgb;}
float luma(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
vec2 lens(vec2 uv){
    vec2 size=vec2(textureSize(InSampler,0)),p=uv*2.0-1.0;
    float aspect=size.x/size.y,r=length(p*vec2(aspect,1.0))/sqrt(aspect*aspect+1.0);
    float angle=max(0.001,Distortion*0.95);
    float scale=r>0.0001?tan(angle*r)/(r*tan(angle)):angle/tan(angle);
    return 0.5+p*scale*0.5;
}
float glyph(int code,vec2 point){
    ivec2 p=ivec2(floor(point));
    if(p.x<0 || p.x>=5 || p.y<0 || p.y>=7) return 0.0;
    uvec2 bits=uvec2(0u);
    switch(code){
        case 48: bits=uvec2(708142u,14905u); break;
        case 49: bits=uvec2(135556u,14468u); break;
        case 50: bits=uvec2(67118u,32004u); break;
        case 51: bits=uvec2(459838u,30753u); break;
        case 52: bits=uvec2(600258u,2143u); break;
        case 53: bits=uvec2(999967u,30753u); break;
        case 54: bits=uvec2(999950u,14897u); break;
        case 55: bits=uvec2(133183u,8456u); break;
        case 56: bits=uvec2(476718u,14897u); break;
        case 57: bits=uvec2(509486u,14369u); break;
        case 65: bits=uvec2(1033774u,17969u); break;
        case 66: bits=uvec2(1001022u,31281u); break;
        case 67: bits=uvec2(541199u,15888u); break;
        case 68: bits=uvec2(575038u,31281u); break;
        case 69: bits=uvec2(999967u,32272u); break;
        case 70: bits=uvec2(999967u,16912u); break;
        case 71: bits=uvec2(770575u,15921u); break;
        case 72: bits=uvec2(1033777u,17969u); break;
        case 73: bits=uvec2(135310u,14468u); break;
        case 75: bits=uvec2(807505u,18004u); break;
        case 76: bits=uvec2(541200u,32272u); break;
        case 77: bits=uvec2(710513u,17969u); break;
        case 78: bits=uvec2(714545u,18035u); break;
        case 79: bits=uvec2(575022u,14897u); break;
        case 80: bits=uvec2(1001022u,16912u); break;
        case 82: bits=uvec2(1001022u,18004u); break;
        case 83: bits=uvec2(475663u,30753u); break;
        case 84: bits=uvec2(135327u,4228u); break;
        case 85: bits=uvec2(575025u,14897u); break;
        case 86: bits=uvec2(575025u,4433u); break;
        case 87: bits=uvec2(706097u,10933u); break;
        case 88: bits=uvec2(141873u,17962u); break;
        case 90: bits=uvec2(133183u,32264u); break;
        case 58: bits=uvec2(4224u,132u); break;
        case 46: bits=uvec2(0u,4224u); break;
        case 45: bits=uvec2(1015808u,0u); break;
        case 47: bits=uvec2(133185u,16648u); break;
        case 37: bits=uvec2(133977u,19816u); break;
    }
    uint value=p.y<4?bits.x:bits.y;
    return float((value>>uint((p.y%4)*5+4-p.x))&1u);
}
float word(vec2 p,vec2 origin,int label){
    p-=origin;
    if(p.x<0.0 || p.x>=72.0 || p.y<0.0 || p.y>=7.0) return 0.0;
    int at=int(floor(p.x/6.0));
    int chars[12];
    switch(label){
        case 0: chars=int[12](82,69,67,32,32,32,32,32,32,32,32,32); break;
        case 1: chars=int[12](70,80,86,32,32,32,32,32,32,32,32,32); break;
        case 2: chars=int[12](65,67,82,79,32,32,32,32,32,32,32,32); break;
        case 3: chars=int[12](83,80,69,69,68,32,32,32,32,32,32,32); break;
        case 4: chars=int[12](65,76,84,32,32,32,32,32,32,32,32,32); break;
        case 5: chars=int[12](86,83,32,32,32,32,32,32,32,32,32,32); break;
        case 6: chars=int[12](72,68,71,32,32,32,32,32,32,32,32,32); break;
        case 7: chars=int[12](66,65,84,32,32,32,32,32,32,32,32,32); break;
        case 8: chars=int[12](76,73,78,75,32,32,32,32,32,32,32,32); break;
        case 9: chars=int[12](52,75,32,32,32,32,32,32,32,32,32,32); break;
        case 10: chars=int[12](73,83,79,32,32,32,32,32,32,32,32,32); break;
        case 11: chars=int[12](83,72,85,84,84,69,82,32,32,32,32,32); break;
        case 12: chars=int[12](65,85,84,79,32,32,32,32,32,32,32,32); break;
        case 13: chars=int[12](75,77,47,72,32,32,32,32,32,32,32,32); break;
        case 14: chars=int[12](77,47,83,32,32,32,32,32,32,32,32,32); break;
        case 15: chars=int[12](77,80,72,32,32,32,32,32,32,32,32,32); break;
        case 16: chars=int[12](70,84,32,32,32,32,32,32,32,32,32,32); break;
        case 17: chars=int[12](77,32,32,32,32,32,32,32,32,32,32,32); break;
        case 18: chars=int[12](85,65,86,32,32,32,32,32,32,32,32,32); break;
        case 19: chars=int[12](80,79,86,32,32,32,32,32,32,32,32,32); break;
        case 20: chars=int[12](84,73,77,69,32,32,32,32,32,32,32,32); break;
        case 21: chars=int[12](88,32,32,32,32,32,32,32,32,32,32,32); break;
        case 22: chars=int[12](90,32,32,32,32,32,32,32,32,32,32,32); break;
        case 23: chars=int[12](78,32,32,32,32,32,32,32,32,32,32,32); break;
        case 24: chars=int[12](69,32,32,32,32,32,32,32,32,32,32,32); break;
        case 25: chars=int[12](83,32,32,32,32,32,32,32,32,32,32,32); break;
        case 26: chars=int[12](87,32,32,32,32,32,32,32,32,32,32,32); break;
        case 27: chars=int[12](49,47,32,32,32,32,32,32,32,32,32,32); break;
        default: return 0.0;
    }
    return glyph(chars[at],vec2(mod(p.x,6.0),p.y));
}
float number(vec2 p,vec2 origin,float value,int digits,int decimals){
    p-=origin;
    int count=digits+(decimals>0?1:0);
    if(p.x<0.0 || p.x>=float(count+1)*6.0 || p.y<0.0 || p.y>=7.0) return 0.0;
    int at=int(floor(p.x/6.0)),code=32;
    if(at==0) code=value<0.0?45:32;
    else {
        at--;
        if(decimals>0 && at==digits-decimals) code=46;
        else {
            int place=digits-1-at+(decimals>0 && at>digits-decimals?1:0);
            float scaled=min(abs(value)*pow(10.0,float(decimals)),pow(10.0,float(digits))-1.0);
            code=48+int(mod(floor(scaled/pow(10.0,float(max(0,place)))),10.0));
        }
    }
    return glyph(code,vec2(mod(p.x,6.0),p.y));
}
float clockText(vec2 p,vec2 origin,float seconds){
    p-=origin;
    if(p.x<0.0 || p.x>=48.0 || p.y<0.0 || p.y>=7.0) return 0.0;
    int at=int(floor(p.x/6.0)),code=58;
    float hour=floor(seconds/3600.0),minute=mod(floor(seconds/60.0),60.0),second=mod(floor(seconds),60.0);
    if(at==0) code=48+int(floor(hour/10.0));
    if(at==1) code=48+int(mod(hour,10.0));
    if(at==3) code=48+int(floor(minute/10.0));
    if(at==4) code=48+int(mod(minute,10.0));
    if(at==6) code=48+int(floor(second/10.0));
    if(at==7) code=48+int(mod(second,10.0));
    return glyph(code,vec2(mod(p.x,6.0),p.y));
}
float line(vec2 p,vec2 a,vec2 b,float width){
    vec2 v=b-a;
    float t=clamp(dot(p-a,v)/max(dot(v,v),0.0001),0.0,1.0);
    return 1.0-smoothstep(width,width+0.35,length(p-a-t*v));
}
float rect(vec2 p,vec2 a,vec2 b,float width){
    return max(max(line(p,a,vec2(b.x,a.y),width),line(p,vec2(b.x,a.y),b,width)),
        max(line(p,b,vec2(a.x,b.y),width),line(p,vec2(a.x,b.y),a,width)));
}
vec3 cameraHud(vec3 image,vec2 uv){
    if(Hud<=0.0) return image;
    vec2 size=vec2(textureSize(InSampler,0));
    vec2 viewport=vec2(size.x/size.y*360.0,360.0)/max(0.65,HudScale);
    vec2 p=vec2(uv.x,1.0-uv.y)*viewport-vec2(HudX,HudY)*viewport;
    vec2 sh=vec2(1.0,1.0);
    float maskW=0.0;
    float maskB=0.0;
    float seconds=mod(max(0.0,Time*ClockRate+ClockOffset),86400.0);
    float battery=clamp(Battery-Time*BatteryDrain/60.0,0.0,100.0);
    float dist=length(vec2(LiveX,LiveZ));
    float hgt=LiveAltitude-AltitudeOffset;
    float hs=LiveSpeed;
    float vs=LiveClimb;
    vec2 center=viewport*0.5;
    float barC=Link>70.0?0.0:Link>40.0?1.0:2.0;
    vec3 barCol=barC<0.5?vec3(1.0):barC<1.5?vec3(1.0,0.62,0.0):vec3(1.0,0.23,0.19);
    float barY=viewport.y-4.0;
    maskW=max(maskW,step(abs(p.y-barY),1.5)*step(p.x,viewport.x));
    vec2 bl=vec2(viewport.x*0.02,viewport.y*0.88);
    maskW=max(maskW,glyph(78,p-bl));
    maskB=max(maskB,glyph(78,p-sh-bl));
    maskW=max(maskW,number(p,bl+vec2(8.0,0.0),dist,4,1));
    maskB=max(maskB,number(p-sh,bl+vec2(8.0,0.0),dist,4,1));
    maskW=max(maskW,glyph(77,p-bl-vec2(-52.0,12.0)));
    maskB=max(maskB,glyph(77,p-sh-bl-vec2(-52.0,12.0)));
    maskW=max(maskW,number(p,bl+vec2(8.0,12.0),hgt,4,1));
    maskB=max(maskB,number(p-sh,bl+vec2(8.0,12.0),hgt,4,1));
    maskW=max(maskW,number(p,bl+vec2(8.0,24.0),hs,2,1));
    maskB=max(maskB,number(p-sh,bl+vec2(8.0,24.0),hs,2,1));
    maskW=max(maskW,number(p,bl+vec2(44.0,24.0),vs,2,1));
    maskB=max(maskB,number(p-sh,bl+vec2(44.0,24.0),vs,2,1));
    vec2 br=vec2(viewport.x*0.98,viewport.y*0.88);
    maskW=max(maskW,number(p,br+vec2(-52.0,0.0),battery,3,0));
    maskB=max(maskB,number(p-sh,br+vec2(-52.0,0.0),battery,3,0));
    maskW=max(maskW,glyph(37,p-br-vec2(-16.0,0.0)));
    maskB=max(maskB,glyph(37,p-sh-br-vec2(-16.0,0.0)));
    maskW=max(maskW,number(p,br+vec2(-52.0,12.0),Link,3,0));
    maskB=max(maskB,number(p-sh,br+vec2(-52.0,12.0),Link,3,0));
    maskW=max(maskW,number(p,br+vec2(-52.0,24.0),18.0,2,0));
    maskB=max(maskB,number(p-sh,br+vec2(-52.0,24.0),18.0,2,0));
    vec2 tr=vec2(viewport.x*0.78,viewport.y*0.03);
    if(Recording>0.5){
        float dotM=1.0-smoothstep(2.4,2.9,length(p-tr-vec2(4.0,3.0)));
        maskW=max(maskW,dotM*step(0.5,fract(Time)));
    }
    if(ShowClock>0.5){
        maskW=max(maskW,clockText(p,tr+vec2(12.0,0.0),seconds));
        maskB=max(maskB,clockText(p-sh,tr+vec2(12.0,0.0),seconds));
    }
    vec2 le=vec2(viewport.x*0.02,viewport.y*0.35);
    maskW=max(maskW,line(p,le,le+vec2(0.0,90.0),0.8));
    maskB=max(maskB,line(p-sh,le,le+vec2(0.0,90.0),1.6));
    float gp=clamp((LivePitch+90.0)/120.0,0.0,1.0)*90.0;
    maskW=max(maskW,line(p,le+vec2(-4.0,gp),le+vec2(4.0,gp),1.2));
    if(ShowHorizon>0.5){
        maskW=max(maskW,line(p,center+vec2(-4.0,0.0),center+vec2(4.0,0.0),0.8));
        maskW=max(maskW,line(p,center+vec2(0.0,-4.0),center+vec2(0.0,4.0),0.8));
    }
    if(dist>1.0){
        float hb=atan(-LiveX,-LiveZ)-radians(LiveHeading);
        vec2 hp=center+vec2(sin(hb),-cos(hb))*70.0;
        hp.x=clamp(hp.x,20.0,viewport.x-20.0);
        hp.y=clamp(hp.y,20.0,viewport.y-20.0);
        maskW=max(maskW,glyph(72,hp+vec2(-2.0,-3.0)));
        maskB=max(maskB,glyph(72,hp+vec2(-3.0,-2.0)));
        maskW=max(maskW,number(p,hp+vec2(8.0,0.0),dist,3,0));
        maskB=max(maskB,number(p-sh,hp+vec2(8.0,0.0),dist,3,0));
    }
    if(hgt<10.0 && hgt>-50.0 && ShowTelemetry>0.5){
        vec2 o=vec2(center.x-16.0,viewport.y*0.82);
        maskW=max(maskW,number(p,o,hgt,2,1));
        maskB=max(maskB,number(p-sh,o,hgt,2,1));
        maskW=max(maskW,word(p,o+vec2(30.0,0.0),17));
        maskB=max(maskB,word(p-sh,o+vec2(30.0,0.0),17));
    }
    if(battery<30.0){
        vec2 o=vec2(center.x-70.0,viewport.y*0.35);
        float d=0.0;
        d=max(d,word(p,o,7));
        d=max(d,number(p-sh,o+vec2(24.0,0.0),battery,3,0));
        maskW=max(maskW,d*step(0.5,fract(Time*1.5)));
        maskB=max(maskB,d*step(0.5,fract(Time*1.5)));
    }
    vec3 c=image;
    c=mix(c,barCol,step(abs(p.y-barY),1.5)*step(p.x,viewport.x)*Hud*0.9);
    c=mix(c,vec3(0.0),clamp(maskB,0.0,1.0)*Hud*0.85);
    c=mix(c,vec3(1.0),clamp(maskW,0.0,1.0)*Hud);
    return c;
}

void main(){
vec4 original=texture(InSampler,texCoord);
vec2 size=vec2(textureSize(InSampler,0)),px=1.0/size,uv=lens(texCoord);
vec3 c=sampleAt(uv);
vec3 soft=(sampleAt(uv+vec2(px.x,0))+sampleAt(uv-vec2(px.x,0))+sampleAt(uv+vec2(0,px.y))+sampleAt(uv-vec2(0,px.y)))*0.25;
float detail=clamp(luma(c)-luma(soft),-0.08,0.08);
c+=detail*Clarity*0.65;
vec3 linear=pow(clamp(c,0.0,1.0),vec3(2.2));
linear=linear*1.04/(vec3(1.0)+linear*0.04);
c=pow(linear,vec3(1.0/2.2));
float shadow=1.0-smoothstep(0.08,0.5,luma(c));
c+=vec3(0.004,0.007,0.011)*shadow;
c=cameraHud(clamp(c,0.0,1.0),texCoord);
fragColor=vec4(mix(original.rgb,clamp(c,0.0,1.0),Amount),original.a);
}
