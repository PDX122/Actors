#version 330 core
uniform sampler2D InSampler;
uniform float Time;
uniform float Amount;
uniform float SensorMode;
uniform float SensorGain;
uniform float SensorContrast;
uniform float SensorDetail;
uniform float SensorNoise;
uniform float SensorLines;
uniform float Hud;
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
uniform float LiveSpeed;
uniform float LiveAltitude;
uniform float LiveClimb;
uniform float LiveHeading;
uniform float LivePitch;
uniform float LiveRoll;
uniform float LiveX;
uniform float LiveZ;
uniform float LiveRange;
uniform float LiveFov;
uniform float LiveAzimuth;
uniform float LiveSensorAltitude;
uniform float LiveTracking;
in vec2 texCoord;
out vec4 fragColor;
// Rounded vector strokes keep the sensor overlay independent of game fonts and pixel grids.
float line(vec2 p,vec2 a,vec2 b,float width){
    vec2 v=b-a;
    float t=clamp(dot(p-a,v)/max(dot(v,v),0.0001),0.0,1.0);
    float aa=max(.001,max(fwidth(p.x),fwidth(p.y))*.65);
    return 1.0-smoothstep(width-aa,width+aa,length(p-a-t*v));
}
float glyph(int code,vec2 p){
    if(p.x<0.0 || p.x>=5.0 || p.y<0.0 || p.y>=7.0) return 0.0;
    if(code==46) return line(p,vec2(2.25,6.25),vec2(2.25,6.25),.28);
    if(code==58) return max(line(p,vec2(2.25,2.0),vec2(2.25,2.0),.26),line(p,vec2(2.25,5.0),vec2(2.25,5.0),.26));
    uint mask=0u;
    switch(code){
        case 48: mask=63u; break;
        case 49: mask=6144u; break;
        case 50: mask=91u; break;
        case 51: mask=79u; break;
        case 52: mask=102u; break;
        case 53: mask=109u; break;
        case 54: mask=125u; break;
        case 55: mask=769u; break;
        case 56: mask=127u; break;
        case 57: mask=111u; break;
        case 65: mask=119u; break;
        case 66: mask=127u; break;
        case 67: mask=57u; break;
        case 68: mask=6159u; break;
        case 69: mask=121u; break;
        case 70: mask=113u; break;
        case 71: mask=125u; break;
        case 72: mask=118u; break;
        case 73: mask=6153u; break;
        case 75: mask=1328u; break;
        case 76: mask=56u; break;
        case 77: mask=438u; break;
        case 78: mask=1206u; break;
        case 79: mask=63u; break;
        case 80: mask=115u; break;
        case 82: mask=1139u; break;
        case 83: mask=109u; break;
        case 84: mask=6145u; break;
        case 85: mask=62u; break;
        case 86: mask=24576u; break;
        case 87: mask=1590u; break;
        case 88: mask=1920u; break;
        case 90: mask=777u; break;
        case 45: mask=64u; break;
        case 47: mask=768u; break;
    }
    const vec4 strokes[15]=vec4[15](
        vec4(0.40,0.35,4.10,0.35),
        vec4(4.20,0.45,4.20,3.50),
        vec4(4.20,3.50,4.20,6.45),
        vec4(0.40,6.55,4.10,6.55),
        vec4(0.30,3.50,0.30,6.45),
        vec4(0.30,0.45,0.30,3.50),
        vec4(0.40,3.50,4.10,3.50),
        vec4(0.40,0.45,2.25,3.50),
        vec4(4.10,0.45,2.25,3.50),
        vec4(0.40,6.45,2.25,3.50),
        vec4(4.10,6.45,2.25,3.50),
        vec4(2.25,0.45,2.25,3.50),
        vec4(2.25,3.50,2.25,6.45),
        vec4(0.40,0.45,2.25,6.45),
        vec4(4.10,0.45,2.25,6.45)
    );
    float ink=0.0;
    for(int i=0;i<15;i++) if((mask&(1u<<uint(i)))!=0u)
        ink=max(ink,line(p,strokes[i].xy,strokes[i].zw,.20));
    return ink;
}
float word(vec2 p,vec2 origin,int label){
    p-=origin;
    if(p.x<0.0 || p.x>=48.0 || p.y<0.0 || p.y>=7.0) return 0.0;
    int at=int(floor(p.x/6.0));
    int chars[8];
    switch(label){
        case 0: chars=int[8](69,79,32,32,32,32,32,32); break;
        case 1: chars=int[8](73,82,32,32,32,32,32,32); break;
        case 2: chars=int[8](87,72,84,32,72,79,84,32); break;
        case 3: chars=int[8](66,76,75,32,72,79,84,32); break;
        case 4: chars=int[8](87,70,79,86,32,32,32,32); break;
        case 5: chars=int[8](77,70,79,86,32,32,32,32); break;
        case 6: chars=int[8](78,70,79,86,32,32,32,32); break;
        case 7: chars=int[8](86,78,70,79,86,32,32,32); break;
        case 8: chars=int[8](82,69,67,32,32,32,32,32); break;
        case 9: chars=int[8](65,90,32,32,32,32,32,32); break;
        case 10: chars=int[8](69,76,32,32,32,32,32,32); break;
        case 11: chars=int[8](82,78,71,32,32,32,32,32); break;
        case 12: chars=int[8](65,76,84,32,32,32,32,32); break;
        case 13: chars=int[8](88,32,32,32,32,32,32,32); break;
        case 14: chars=int[8](90,32,32,32,32,32,32,32); break;
        case 15: chars=int[8](77,32,32,32,32,32,32,32); break;
        case 16: chars=int[8](70,84,32,32,32,32,32,32); break;
        case 17: chars=int[8](65,85,84,79,32,84,82,75); break;
        case 18: chars=int[8](85,84,67,32,32,32,32,32); break;
        case 19: chars=int[8](70,79,86,32,32,32,32,32); break;
        case 20: chars=int[8](83,84,65,66,32,32,32,32); break;
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
float symbols(vec2 p,vec2 viewport){
    float ink=0.0;
    vec2 margin=vec2(viewport.y*HudMargin);
    vec2 tl=margin, tr=vec2(viewport.x-margin.x-72.0,margin.y);
    vec2 bl=vec2(margin.x,viewport.y-margin.y-29.0);
    vec2 br=vec2(viewport.x-margin.x-90.0,bl.y);
    if(viewport.x>400.0) ink=max(ink,word(p,vec2(viewport.x*.5-12.0,margin.y),20));
    ink=max(ink,word(p,tl,SensorMode<.5?0:1));
    if(SensorMode>=.5) ink=max(ink,word(p,tl+vec2(18,0),SensorMode<1.5?2:3));
    int field=LiveFov>25.0?4:LiveFov>12.0?5:LiveFov>4.0?6:7;
    ink=max(ink,word(p,tl+vec2(0,11),field));
    ink=max(ink,number(p,tl+vec2(36,11),LiveFov,3,1));
    if(Recording>.5) ink=max(ink,word(p,tr,8));
    if(ShowClock>.5){
        float seconds=mod(max(0.0,Time*ClockRate+ClockOffset),86400.0);
        ink=max(ink,clockText(p,tr+vec2(0,11),seconds));
        ink=max(ink,word(p,tr+vec2(54,11),18));
    }
    if(ShowTelemetry>.5){
        ink=max(ink,word(p,bl,9));
        ink=max(ink,number(p,bl+vec2(18,0),LiveAzimuth,4,1));
        ink=max(ink,word(p,bl+vec2(0,11),10));
        ink=max(ink,number(p,bl+vec2(18,11),-LivePitch,3,1));
        ink=max(ink,word(p,bl+vec2(0,22),11));
        if(LiveTracking>.5) ink=max(ink,number(p,bl+vec2(24,22),LiveRange,5,0));
        else for(int i=0;i<4;i++) ink=max(ink,glyph(45,p-bl-vec2(30+i*6,22)));
        ink=max(ink,word(p,bl+vec2(66,22),15));
        ink=max(ink,word(p,br,13));
        ink=max(ink,number(p,br+vec2(12,0),LiveX,6,0));
        ink=max(ink,word(p,br+vec2(0,11),14));
        ink=max(ink,number(p,br+vec2(12,11),LiveZ,6,0));
        ink=max(ink,word(p,br+vec2(0,22),12));
        ink=max(ink,number(p,br+vec2(18,22),(LiveSensorAltitude-AltitudeOffset)*(Imperial>.5?3.28084:1.0),5,0));
        ink=max(ink,word(p,br+vec2(66,22),Imperial>.5?16:15));
    }
    vec2 q=p-viewport*.5;
    // Open reticle and tracker gates leave the subject visible.
    ink=max(ink,line(q,vec2(-18,0),vec2(-5,0),.35));
    ink=max(ink,line(q,vec2(5,0),vec2(18,0),.35));
    ink=max(ink,line(q,vec2(0,-18),vec2(0,-5),.35));
    ink=max(ink,line(q,vec2(0,5),vec2(0,18),.35));
    if(LiveTracking>.5){
        for(int x=-1;x<=1;x+=2) for(int y=-1;y<=1;y+=2){
            vec2 corner=vec2(x,y)*vec2(30,22);
            ink=max(ink,line(q,corner,corner-vec2(x*8,0),.35));
            ink=max(ink,line(q,corner,corner-vec2(0,y*8),.35));
        }
        ink=max(ink,word(p,viewport*.5+vec2(-24,30),17));
    }
    if(ShowHorizon>.5){
        vec2 origin=vec2(viewport.x-margin.x,viewport.y*.5);
        for(int i=-3;i<=3;i++){
            float y=float(i)*10;
            ink=max(ink,line(p,origin+vec2(-4,y),origin+vec2(0,y),.35));
        }
        float pitch=clamp(LivePitch,-90.0,90.0)/3.0;
        ink=max(ink,line(p,origin+vec2(-8,pitch),origin+vec2(-3,pitch),.5));
    }
    return ink;
}
vec3 cameraHud(vec3 image,vec2 uv){
    if(Hud<=0.0) return image;
    vec2 size=vec2(textureSize(InSampler,0));
    vec2 viewport=vec2(size.x/size.y*360.0,360.0)/max(.65,HudScale);
    vec2 p=vec2(uv.x,1.0-uv.y)*viewport-vec2(HudX,HudY)*viewport;
    float shadow=symbols(p-vec2(.55),viewport);
    float ink=symbols(p,viewport);
    image=mix(image,vec3(.015),shadow*Hud*.85);
    image=mix(image,vec3(.92),ink*Hud);
    if(Recording>.5){
        vec2 rec=vec2(viewport.x-viewport.y*HudMargin-78.0,viewport.y*HudMargin+3.5);
        float dot=line(p,rec,rec,1.1);
        image=mix(image,vec3(.94,.25,.20),dot*Hud);
    }
    return image;
}
float luma(vec3 c){return dot(c,vec3(.2126,.7152,.0722));}
float noise(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453)-.5;}
vec3 sensor(vec2 uv){
    vec2 size=vec2(textureSize(InSampler,0));
    float lines=min(size.y,max(256.0,SensorLines));
    vec2 grid=vec2(lines*size.x/size.y,lines);
    vec2 at=(floor(uv*grid)+.5)/grid;
    vec2 px=1.0/grid;
    vec3 center=texture(InSampler,at).rgb;
    vec3 blur=(texture(InSampler,at+vec2(px.x,0)).rgb+texture(InSampler,at-vec2(px.x,0)).rgb
        +texture(InSampler,at+vec2(0,px.y)).rgb+texture(InSampler,at-vec2(0,px.y)).rgb)*.25;
    vec3 c=mix(center,blur,.2)+clamp(center-blur,vec3(-.1),vec3(.1))*SensorDetail;
    if(SensorMode>=.5){
        // Visible-color approximation: cold sky/foliage and warm surfaces, not measured temperature.
        float cold=max(0.0,c.b-max(c.r,c.g));
        float foliage=max(0.0,c.g-max(c.r,c.b));
        float warm=max(0.0,c.r-c.b);
        float heat=clamp(.16+luma(c)*.6+warm*.25-cold*.65-foliage*.25,0.0,1.0);
        heat=(heat-.5)*SensorContrast+.5;
        heat=clamp(heat*SensorGain,.025,.975);
        if(SensorMode>=1.5) heat=1.0-heat;
        c=vec3(heat);
    } else {
        c=mix(vec3(luma(c)),c,.8);
        c=(c-.5)*SensorContrast+.5;
        c*=SensorGain;
        c=c/(vec3(1.0)+max(c-vec3(.75),vec3(0.0))*.35);
    }
    c+=noise(floor(uv*grid)+floor(Time*30.0)*vec2(19,7))*SensorNoise;
    return clamp(c,0.0,1.0);
}
void main(){
    vec4 original=texture(InSampler,texCoord);
    vec3 c=cameraHud(sensor(texCoord),texCoord);
    fragColor=vec4(mix(original.rgb,c,Amount),original.a);
}
