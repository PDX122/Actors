#version 330 core
uniform sampler2D EarthSurfaceSampler;
uniform sampler2D EarthWeatherSampler;
uniform sampler2D InSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D BombMeshSampler;
uniform sampler2D BombAlbedoSampler;
uniform sampler2D BombNormalSampler;
uniform sampler2D BombMaterialSampler;
uniform float CameraX;
uniform float CameraY;
uniform float CameraZ;
uniform float RightX;
uniform float RightY;
uniform float RightZ;
uniform float UpX;
uniform float UpY;
uniform float UpZ;
uniform float ForwardX;
uniform float ForwardY;
uniform float ForwardZ;
uniform float TargetX;
uniform float TargetY;
uniform float TargetZ;
uniform float RocketX;
uniform float RocketY;
uniform float RocketZ;
uniform float AxisX;
uniform float AxisY;
uniform float AxisZ;
uniform float ProjectionX;
uniform float ProjectionY;
uniform float DepthA;
uniform float DepthB;
uniform float ReverseDepth;
uniform float Progress;
uniform float BlastTime;
uniform float Time;
uniform float Scale;
uniform float BlastScale;
uniform float SpaceView;
uniform float SecondSeparation;
uniform float Ending;
uniform float EndingStart;
uniform float ClipSeconds;
uniform float CinematicBars;
uniform float CloudBase;
uniform float CloudThickness;
uniform float Preview;
uniform float DeltaOutput;
uniform float Type;
uniform float DropHeight;
uniform float Heading;
uniform float Spread;
uniform float BombCount;
uniform float Duration;
uniform float BoltX;
uniform float BoltY;
uniform float BoltZ;
uniform float BoltAge;
uniform float BoltSeed;
uniform float Ground0;
uniform float Ground1;
uniform float Ground2;
uniform float Ground3;
uniform float Ground4;
uniform float Ground5;
uniform float Ground6;
uniform float Ground7;
uniform float Ground8;
uniform float Ground9;
uniform float Ground10;
uniform float Ground11;
uniform float Ground12;
uniform float Ground13;
uniform float Ground14;
uniform float Ground15;
uniform float Ground16;
uniform float Ground17;
uniform float Ground18;
uniform float Ground19;
uniform float Ground20;
uniform float Ground21;
uniform float Ground22;
uniform float Ground23;

uniform float ThirdX;
uniform float ThirdY;
uniform float ThirdZ;
uniform float ThirdAxisX;
uniform float ThirdAxisY;
uniform float ThirdAxisZ;
uniform float FairingX;
uniform float FairingY;
uniform float FairingZ;
uniform float FairingAxisX;
uniform float FairingAxisY;
uniform float FairingAxisZ;
uniform float Travel;
uniform float ArcHeight;
uniform float Ignition;
uniform float Separation;
uniform float Burnout;
uniform float Shroud;
uniform float Reentry;
uniform float Impact;
uniform float BoosterX;
uniform float BoosterY;
uniform float BoosterZ;
uniform float BoosterAxisX;
uniform float BoosterAxisY;
uniform float BoosterAxisZ;
uniform float UpperX;
uniform float UpperY;
uniform float UpperZ;
uniform float UpperAxisX;
uniform float UpperAxisY;
uniform float UpperAxisZ;
in vec2 texCoord;
out vec4 fragColor;

const float PI=3.14159265;
vec3 rocket,axis,side,across,target;
float hash(vec3 p) { p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z); }
float noise(vec3 p) {
    vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
        mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1)),f.x),f.y),f.z);
}
float fbm(vec3 p) { return .57*noise(p)+.28*noise(p*2.03+4.1)+.15*noise(p*4.07-8.3); }
vec2 sphereHit(vec3 ro,vec3 rd,vec3 center,float radius) {
    vec3 oc=ro-center;float b=dot(oc,rd),h=b*b-dot(oc,oc)+radius*radius;
    if(h<0.)return vec2(1e6,-1.);h=sqrt(h);return vec2(-b-h,-b+h);
}
float cylinder(vec3 p,float radius,float halfHeight) {
    vec2 d=vec2(length(p.xz)-radius,abs(p.y)-halfHeight);
    return min(max(d.x,d.y),0.)+length(max(d,0.));
}
float box(vec3 p,vec3 size) {vec3 q=abs(p)-size;return length(max(q,0.))+min(max(q.x,max(q.y,q.z)),0.);}
// Capped cone, measured between its lower and upper axial endpoints.
float cone(vec3 p,float h,float r1,float r2) {
    vec2 q=vec2(length(p.xz),p.y),k1=vec2(r2,h),k2=vec2(r2-r1,2.*h);
    vec2 ca=vec2(q.x-min(q.x,q.y<0.?r1:r2),abs(q.y)-h);
    vec2 cb=q-k1+k2*clamp(dot(k1-q,k2)/dot(k2,k2),0.,1.);
    return (cb.x<0. && ca.y<0.?-1.:1.)*sqrt(min(dot(ca,ca),dot(cb,cb)));
}
vec3 local(vec3 p) {vec3 d=(p-rocket)/Scale;return vec3(dot(d,side),dot(d,axis),dot(d,across));}
vec2 closer(vec2 a,vec2 b) {return a.x<b.x?a:b;}
float torus(vec3 p,float r,float tube) {return length(vec2(length(p.xz)-r,p.y))-tube;}
float bell(vec3 p,float h,float bottom,float top) {
    float radius=mix(bottom,top,clamp((p.y+h)/(2.*h),0.,1.));
    return max((abs(length(p.xz)-radius)-.045)*.75,abs(p.y)-h);
}
// Imported supplied mesh: six independently animated, prebuilt component BVHs.
vec3 meshPart(vec3 ro,vec3 rd,inout float limit,vec3 background,vec3 center,vec3 direction,int part);
vec3 meshPartFiltered(vec3 ro,vec3 rd,inout float limit,vec3 background,vec3 center,vec3 direction,int part);
vec3 frameRight(vec3 direction) {
    vec3 reference=normalize(vec3(.83,.27,.49));
    return normalize(reference-direction*dot(reference,direction));
}
vec3 flightPath(float f) {
    float u=1.-f,range=max(1.,length(target.xz));
    float lateral=ArcHeight*2.4*f*f*u*u*(1.-2.*f);
    float turn=ArcHeight*.12*f*f/(f*f+.0036)*pow(u,5.);
    lateral+=ArcHeight*.018*f*f/(f*f+.0016)*exp(-f*12.)*u*u;
    vec3 p=3.*u*f*f*target+f*f*f*target;
    p.xz+=(length(target.xz)<.000001?vec2(0,1):vec2(-target.z,target.x)/range)*lateral;
    p.xz+=target.xz/range*turn;
    p.y+=3.*u*u*f*ArcHeight+3.*u*f*f*ArcHeight*.9+31.4*Scale*(1.-f);
    return p;
}
vec4 flight(vec3 ro,vec3 rd,float limit,vec3 background) {
    for(int part=0;part<6;part++) {
        float offset=part==0?-30.48:part==1?-15.925:part==2?-8.984:-4.216;
        vec3 center=rocket+axis*offset*Scale,direction=axis;
        if(part<3) {
            float release=part==0?Separation:part==1?SecondSeparation:Shroud+.02;
            if(Progress>=release && Preview!=1.) {
                float age=(Progress-release)*Duration;if(age>3.5)continue;
                center=part==0?vec3(BoosterX,BoosterY,BoosterZ):part==1?vec3(UpperX,UpperY,UpperZ):vec3(ThirdX,ThirdY,ThirdZ);
                vec3 releasedAxis=part==0?vec3(BoosterAxisX,BoosterAxisY,BoosterAxisZ):part==1?vec3(UpperAxisX,UpperAxisY,UpperAxisZ):vec3(ThirdAxisX,ThirdAxisY,ThirdAxisZ);
                direction=normalize(releasedAxis+vec3(sin(age)*age*.25,-age*.16,cos(age)*age*.22));
            }
        } else if(part<5) {
            if(Progress>=Shroud && Preview!=1.) {
                float age=(Progress-Shroud)*Duration;if(age>2.8)continue;
                float signHalf=part==3?-1.:1.;
                vec3 releaseAxis=vec3(FairingAxisX,FairingAxisY,FairingAxisZ);
                vec3 lateral=frameRight(releaseAxis);
                center=vec3(FairingX,FairingY,FairingZ)+lateral*signHalf*age*7.*Scale;
                direction=normalize(releaseAxis+lateral*signHalf*age*.7);
            }
        } else {
            if(Progress<Shroud || Preview==1.)continue;
        }
        background=meshPartFiltered(ro,rd,limit,background,center,direction,part);
    }
    if(Progress>Shroud && Progress<Reentry && Preview!=1.) {
        float phase=mod((Progress-Shroud)*Duration,1.3);
        if(phase<.16)for(int jet=0;jet<2;jet++) {
            float signJet=jet==0?-1.:1.;
            vec3 center=rocket-axis*3.6*Scale+side*signJet*1.8*Scale;
            vec3 delta=center-ro;float t=dot(delta,rd);
            if(t>0. && t<limit)background+=vec3(.8,1.4,2.)*exp(-dot(delta-rd*t,delta-rd*t)/(Scale*Scale*.32))*sin(phase/.16*PI);
        }
    }
    // Hot exhaust core and turbulent, tapering plume, integrated with world occlusion.
    bool boost=Progress<Burnout;
    if(boost && Preview!=1.) {
        float nozzle=(Progress>Reentry?-2.8:Progress<Separation?-31.4:Progress<SecondSeparation?-16.51:-9.57)*Scale;
        vec3 exhaust=rocket+axis*nozzle;
        float lightup=smoothstep(0.,Ignition,Progress);
        float plumeLength=(boost?mix(7.,40.,lightup):17.)*Scale;
        vec2 bounds=sphereHit(ro,rd,exhaust-axis*plumeLength*.4,plumeLength*.65);
        float a=max(.02,bounds.x),b=min(limit,bounds.y);
        if(b>a) {
            float stepSize=(b-a)/28.;vec3 glow=vec3(0);float opacity=0.;
            for(int i=0;i<28;i++) {
                vec3 p=ro+rd*(a+(float(i)+.5)*stepSize)-exhaust;
                float along=-dot(p,axis),radial=length(p+axis*along);
                if(along>0. && along<plumeLength) {
                    float vacuum=smoothstep(800.,5000.,rocket.y);
                    float u=along/plumeLength,width=(.65+u*mix(2.8,11.,vacuum))*Scale;
                    float turbulence=fbm(p/Scale*1.7+vec3(0,Time*-9.,0));
                    float diamonds=.8+.2*cos(along/Scale*2.8-Time*28.);
                    float density=exp(-radial*radial/(width*width))*(1.-u)*(.75+turbulence)*diamonds*lightup;
                    float alpha=1.-exp(-density*stepSize*.24/Scale);
                    vec3 fire=mix(vec3(4.5,.42,.035),vec3(9.,7.5,4.5),exp(-u*4.)*exp(-radial/(Scale*1.5)));
                    glow+=(1.-opacity)*alpha*fire;opacity+=(1.-opacity)*alpha;
                }
            }
            background=background*(1.-opacity*.35)+glow;
        }
    }
    // Launch smoke rolling out from the pad, then dissipating.
    if(Progress<.30 && Preview!=1.) {
        float grow=smoothstep(0.,.065,Progress),fade=1.-smoothstep(.16,.30,Progress);
        vec3 center=vec3(0,(1.4+grow*2.)*Scale,0);float radius=(2.+grow*22.)*Scale;
        vec2 bounds=sphereHit(ro,rd,center,radius);float a=max(.02,bounds.x),b=min(limit,bounds.y);
        if(b>a) {
            float stepSize=(b-a)/18.,opacity=0.;vec3 smoke=vec3(0);
            for(int i=0;i<18;i++) {
                vec3 p=ro+rd*(a+(float(i)+.5)*stepSize);
                float cloud=1.-length((p-center)/vec3(radius,(3.5+grow*3.)*Scale,radius));
                float d=max(0.,cloud+(fbm(p/Scale*.7-vec3(0,Time,0))-.5)*.8)*grow*fade;
                float alpha=1.-exp(-d*stepSize*.4/Scale);vec3 lit=mix(vec3(.19,.21,.22),vec3(.75,.69,.58),.45+.4*noise(p/Scale));
                smoke+=(1.-opacity)*alpha*lit;opacity+=(1.-opacity)*alpha;
            }
            background=background*(1.-opacity)+smoke;
        }
    }
    if(Preview!=1. && Progress>Ignition && Progress<Reentry) {
        // A broad, lingering white exhaust trail follows the actual curved ascent.
        float trailFade=1.-smoothstep(Burnout,Reentry,Progress);
        for(int puff=0;puff<18;puff++) {
            float f=(float(puff)+.4)/32.;if(f>Travel || f>.46)continue;
            vec3 point=flightPath(f);
            float age=max(0.,Travel-f);float radius=(1.5+age*14.)*Scale;
            vec2 bounds=sphereHit(ro,rd,point,radius);float a=max(.02,bounds.x),b=min(limit,bounds.y);
            if(b<=a)continue;
            float stepSize=(b-a)/8.,opacity=0.;vec3 smoke=vec3(0);
            for(int i=0;i<8;i++) {
                vec3 q=ro+rd*(a+(float(i)+.5)*stepSize)-point;
                float d=max(0.,1.-length(q)/radius+(fbm(q/Scale*.75+vec3(0,-Time*.8,0))-.5)*.65);
                float alpha=1.-exp(-d*stepSize*.17/Scale*trailFade);
                smoke+=(1.-opacity)*alpha*mix(vec3(.34,.37,.40),vec3(.86,.84,.77),noise(q/Scale));opacity+=(1.-opacity)*alpha;
            }
            background=background*(1.-opacity)+smoke;
        }
    }
    if(Progress>Reentry && Preview!=1.) {
        // Bow shock and an incandescent ablative wake; no powered burn during ballistic coast.
        vec2 bounds=sphereHit(ro,rd,rocket-axis*4.*Scale,7.*Scale);float a=max(.02,bounds.x),b=min(limit,bounds.y);
        float heat=smoothstep(Reentry,Reentry+.025,Progress);
        if(b>a) {
            float stepSize=(b-a)/20.;vec3 glow=vec3(0);
            for(int i=0;i<20;i++) {
                vec3 q=(ro+rd*(a+(float(i)+.5)*stepSize)-rocket)/Scale;
                float along=dot(q,axis),r=length(q-axis*along);
                float bow=exp(-pow((along-.25+r*r*.28)*2.8,2.))*exp(-r*r*.55);
                float wake=exp(-r*r/(1.1+max(0.,-along)*.4))*exp(-abs(along+3.)*.35)*step(along,0.);
                float shimmer=.7+.3*noise(q*3.-Time*22.);
                glow+=vec3(6.5,1.1,.06)*(bow+wake*.18)*stepSize/Scale*.22*shimmer*heat;
            }
            background+=glow;
        }
    }
    return vec4(background,1.);
}

// Attribute-simplified source meshes and filtered materials in stackless BVHs.
vec4 bombData(int index) {ivec2 size=textureSize(BombMeshSampler,0);return texelFetch(BombMeshSampler,ivec2(index%size.x,index/size.x),0);}
bool boxHit(vec3 ro,vec3 inverseRay,vec3 low,vec3 high,float closest) {
    vec3 a=(low-ro)*inverseRay,b=(high-ro)*inverseRay;
    vec3 lo=min(a,b),hi=max(a,b);float nearHit=max(lo.x,max(lo.y,lo.z)),farHit=min(hi.x,min(hi.y,hi.z));
    return farHit>=max(.0001,nearHit) && nearHit<closest;
}
vec3 meshPart(vec3 ro,vec3 rd,inout float limit,vec3 background,vec3 center,vec3 direction,int part) {
    vec3 right=frameRight(direction),back=cross(right,direction);
    vec3 origin=vec3(dot(ro-center,right),dot(ro-center,direction),dot(ro-center,back))/Scale;
    vec3 ray=vec3(dot(rd,right),dot(rd,direction),dot(rd,back));
    vec3 inverseRay=sign(ray+vec3(.00000001))/max(abs(ray),vec3(.0000001));
    vec4 header=bombData(part);int nodes=int(header.x),triangleBase=int(header.y),nodeBase=int(header.w),cursor=0,triangle=-1;
    float closest=limit/Scale;vec2 bary=vec2(0);
    for(int visit=0;visit<1024;visit++) {
        if(cursor>=nodes)break;
        vec4 low=bombData(nodeBase+cursor*3),high=bombData(nodeBase+1+cursor*3);int skip=int(bombData(nodeBase+2+cursor*3).x);
        if(!boxHit(origin,inverseRay,low.xyz,high.xyz,closest)) {cursor=skip;continue;}
        int count=int(high.w),first=int(low.w);
        for(int leaf=0;leaf<6;leaf++) {
            if(leaf>=count)break;int index=triangleBase+(first+leaf)*9;
            vec3 a=bombData(index).xyz,b=bombData(index+3).xyz,c=bombData(index+6).xyz;
            vec3 e1=b-a,e2=c-a,h=cross(ray,e2);float det=dot(e1,h);if(abs(det)<.00000001)continue;
            float inv=1./det;vec3 delta=origin-a;float u=dot(delta,h)*inv;if(u<0. || u>1.)continue;
            vec3 q=cross(delta,e1);float v=dot(ray,q)*inv;if(v<0. || u+v>1.)continue;
            float t=dot(e2,q)*inv;
            if(t>.0001 && t<closest) {closest=t;triangle=index;bary=vec2(u,v);}
        }
        cursor++;
    }
    if(triangle<0)return background;
    vec3 weights=vec3(1.-bary.x-bary.y,bary);vec3 normalLocal=vec3(0),tangentLocal=vec3(0);vec2 uv=vec2(0);float hand=0.;
    for(int v=0;v<3;v++) {
        vec4 point=bombData(triangle+v*3),n=bombData(triangle+v*3+1),t=bombData(triangle+v*3+2);
        normalLocal+=n.xyz*weights[v];tangentLocal+=t.xyz*weights[v];hand+=t.w*weights[v];uv+=vec2(point.w,n.w)*weights[v];
    }
    normalLocal=normalize(normalLocal);tangentLocal=normalize(tangentLocal-normalLocal*dot(tangentLocal,normalLocal));
    vec4 va=bombData(triangle),vb=bombData(triangle+3),vc=bombData(triangle+6);
    vec2 ua=vec2(va.w,bombData(triangle+1).w),ub=vec2(vb.w,bombData(triangle+4).w),uc=vec2(vc.w,bombData(triangle+7).w);
    float uvPerMeter=max(length(ub-ua)/max(.001,length(vb.xyz-va.xyz)),length(uc-ua)/max(.001,length(vc.xyz-va.xyz)));
    float footprint=closest*2.*ProjectionY/float(textureSize(InSampler,0).y)*uvPerMeter*float(textureSize(BombAlbedoSampler,0).x);
    float lod=clamp(log2(max(1.,footprint)),0.,12.);
    vec3 detail=textureLod(BombNormalSampler,uv,lod).xyz*2.-1.;
    normalLocal=normalize(tangentLocal*detail.x+cross(normalLocal,tangentLocal)*sign(hand)*detail.y+normalLocal*detail.z);
    vec3 n=normalize(right*normalLocal.x+direction*normalLocal.y+back*normalLocal.z);if(dot(n,rd)>0.)n=-n;
    vec3 base=textureLod(BombAlbedoSampler,uv,lod).rgb;vec3 mr=textureLod(BombMaterialSampler,uv,lod).rgb;
    float roughness=max(.18,mr.g),metallic=mr.b;vec3 sun=normalize(vec3(-.5,.9,.6));
    float diffuse=max(0.,dot(n,sun)),spec=pow(max(0.,dot(n,normalize(sun-rd))),mix(120.,8.,roughness));
    vec3 f0=mix(vec3(.04),base,metallic);
    limit=closest*Scale;
    if(Type<.5 && Progress>Reentry && Preview!=1.)base+=vec3(5.,.60,.025)*smoothstep(Reentry,Impact,Progress)*pow(max(0.,dot(n,direction)),2.);
    return base*(.42+.75*diffuse)*(1.-metallic*.25)+f0*spec*2.+vec3(.12,.16,.20)*pow(1.-max(0.,dot(n,-rd)),3.);
}
vec3 meshPartFiltered(vec3 ro,vec3 rd,inout float limit,vec3 background,vec3 center,vec3 direction,int part) {
    float distance=length(ro-center),pixels=float(textureSize(InSampler,0).y);
    if(Scale*pixels/max(1.,distance)>12.)return meshPart(ro,rd,limit,background,center,direction,part);
    vec3 right=frameRight(direction),back=cross(right,direction);
    vec3 origin=vec3(dot(ro-center,right),dot(ro-center,direction),dot(ro-center,back))/Scale;
    vec3 ray=vec3(dot(rd,right),dot(rd,direction),dot(rd,back));
    int root=int(bombData(part).w);
    float margin=distance*ProjectionY/pixels/Scale;
    vec3 inverseRay=sign(ray+vec3(.00000001))/max(abs(ray),vec3(.0000001));
    if(!boxHit(origin,inverseRay,bombData(root).xyz-margin,bombData(root+1).xyz+margin,limit/Scale))return background;
    vec3 forward=vec3(ForwardX,ForwardY,ForwardZ),plane=rd/max(.001,dot(rd,forward));
    vec3 dx=vec3(RightX,RightY,RightZ)*ProjectionX/float(textureSize(InSampler,0).x)*.5;
    vec3 dy=vec3(UpX,UpY,UpZ)*ProjectionY/pixels*.5;
    vec3 color=vec3(0);float nearest=limit;
    for(int y=0;y<2;y++)for(int x=0;x<2;x++) {
        float sampleLimit=limit;
        color+=meshPart(ro,normalize(plane+dx*(x==0?-1.:1.)+dy*(y==0?-1.:1.)),sampleLimit,background,center,direction,part)*.25;
        nearest=min(nearest,sampleLimit);
    }
    limit=nearest;return color;
}
vec3 atomicBomb(vec3 ro,vec3 rd,float limit,vec3 background,vec3 center,vec3 direction) {
    return meshPartFiltered(ro,rd,limit,background,center,direction,0);
}
float groundHeight(int bomb) {
    if(bomb==0)return Ground0;
    if(bomb==1)return Ground1;
    if(bomb==2)return Ground2;
    if(bomb==3)return Ground3;
    if(bomb==4)return Ground4;
    if(bomb==5)return Ground5;
    if(bomb==6)return Ground6;
    if(bomb==7)return Ground7;
    if(bomb==8)return Ground8;
    if(bomb==9)return Ground9;
    if(bomb==10)return Ground10;
    if(bomb==11)return Ground11;
    if(bomb==12)return Ground12;
    if(bomb==13)return Ground13;
    if(bomb==14)return Ground14;
    if(bomb==15)return Ground15;
    if(bomb==16)return Ground16;
    if(bomb==17)return Ground17;
    if(bomb==18)return Ground18;
    if(bomb==19)return Ground19;
    if(bomb==20)return Ground20;
    if(bomb==21)return Ground21;
    if(bomb==22)return Ground22;
    if(bomb==23)return Ground23;
    return 0.;
}
vec3 dropTarget(int bomb) {
    if(Type<1.5)return target;
    float along=(float(bomb)/max(1.,BombCount-1.)-.5)*Spread,across=float(bomb%3-1)*Spread*.12;
    return target+vec3(sin(Heading)*along+cos(Heading)*across,groundHeight(bomb),cos(Heading)*along-sin(Heading)*across);
}
float releaseAt(int bomb) {return Type<1.5?0.:.04+float(bomb)/max(1.,BombCount-1.)*.32;}
float impactAt(int bomb) {return releaseAt(bomb)+(Type<1.5?Impact:.24);}
vec3 dropPosition(int bomb) {
    float t=clamp((Progress-releaseAt(bomb))/(impactAt(bomb)-releaseAt(bomb)),0.,1.);
    vec3 p=dropTarget(bomb)+vec3(0,DropHeight*(1.-t*t)+2.*Scale,0);
    if(Type>1.5)p+=vec3(sin(Heading),0,cos(Heading))*(t-1.)*18.;return p;
}
float conventionalBomb(vec3 p) {
    float d=cylinder(p-vec3(0,-.15,0),.32,.9);
    d=min(d,cone(p-vec3(0,1.,0),.42,.32,.035));
    d=min(d,cone(p-vec3(0,-1.2,0),.25,.16,.32));
    d=min(d,box(p-vec3(0,-1.6,0),vec3(.62,.35,.045)));
    d=min(d,box(p-vec3(0,-1.6,0),vec3(.045,.35,.62)));return d;
}
vec3 smallBomb(vec3 ro,vec3 rd,float limit,vec3 background,vec3 center) {
    vec2 hit=sphereHit(ro,rd,center,2.4*Scale);float t=max(.01,hit.x);
    for(int i=0;i<45;i++) {
        if(t>min(limit,hit.y))break;vec3 p=(ro+rd*t-center)/Scale;p.y=-p.y;
        float d=conventionalBomb(p);
        if(d<.008) {background=vec3(.15,.18,.12)*(.75+.25*noise(p*12.));break;}t+=max(.01,d*Scale*.8);
    }
    return background;
}
vec3 carpetBlast(vec3 ro,vec3 rd,float limit,vec3 background,vec3 point,float seconds) {
    if(seconds<0. || seconds>9.)return background;
    float grow=1.-exp(-seconds*3.),radius=(1.+grow*9.)*BlastScale;
    vec3 center=point+vec3(0,(2.+seconds*1.8)*BlastScale,0);float fade=1.-smoothstep(5.,9.,seconds);
    vec2 bounds=sphereHit(ro,rd,center,radius*1.3);float a=max(.01,bounds.x),b=min(limit,bounds.y);
    if(b>a) {
        float stepSize=(b-a)/24.,opacity=0.;vec3 glow=vec3(0);
        for(int i=0;i<24;i++) {
            vec3 p=ro+rd*(a+(float(i)+.5)*stepSize);
            float n=fbm(p/(BlastScale*1.4)-vec3(0,seconds*1.8,0));
            float density=max(0.,1.-length((p-center)/vec3(radius,radius*1.2,radius))+(n-.5)*.7)*fade;
            float heat=exp(-seconds*1.1)*(.5+n);
            vec3 color=mix(vec3(.075,.08,.085)*(.7+n),mix(vec3(2.4,.19,.015),vec3(7.,3.7,.6),heat),smoothstep(.12,.7,heat));
            float alpha=1.-exp(-density*stepSize*.6/BlastScale);glow+=(1.-opacity)*alpha*color;opacity+=(1.-opacity)*alpha;
        }
        background=background*(1.-opacity)+glow;
    }
    vec3 delta=point-ro;float t=dot(delta,rd);
    if(t>0. && t<limit+2.)background+=vec3(4.,1.6,.25)*exp(-seconds*12.)*exp(-dot(delta-rd*t,delta-rd*t)/(BlastScale*BlastScale*45.));
    for(int ember=0;ember<8;ember++) {
        float seed=float(ember)+1.,angle=seed*2.39996;
        vec3 pointEmber=point+vec3(cos(angle)*seconds*11.,seconds*(13.+hash(vec3(seed))*8.)-5.*seconds*seconds,sin(angle)*seconds*11.)*BlastScale;
        if(pointEmber.y<point.y)continue;vec3 d=pointEmber-ro;float distance=dot(d,rd);
        if(distance>0. && distance<limit)background+=vec3(2.5,.45,.025)*exp(-dot(d-rd*distance,d-rd*distance)/(BlastScale*BlastScale*.09))*exp(-seconds*.9);
    }
    return background;
}
float bomber(vec3 p) {
    float body=length(p/vec3(1.2,.85,9.))-1.;
    float wings=box(p-vec3(0,.05,.5),vec3(16.,.16,1.35));
    float tail=box(p-vec3(0,.3,-6.8),vec3(5.,.12,.75));
    float fin=box(p-vec3(0,1.15,-7.2),vec3(.15,1.2,1.4));
    return min(min(body*.85,wings),min(tail,fin));
}
vec3 carpetFlight(vec3 ro,vec3 rd,float limit,vec3 background) {
    vec3 plane=Preview==1.?vec3(0,8,0):target+vec3(sin(Heading),0,cos(Heading))*(Progress-.23)*Spread*3.+vec3(0,DropHeight+12.,0);
    vec2 bound=sphereHit(ro,rd,plane,19.);float t=max(.01,bound.x);
    for(int i=0;i<58;i++) {
        if(t>min(limit,bound.y))break;vec3 p=ro+rd*t-plane;
        p.xz=mat2(cos(Heading),-sin(Heading),sin(Heading),cos(Heading))*p.xz;
        float d=bomber(p);if(d<.02) {background=vec3(.13,.15,.17)*(.8+.2*noise(p*3.));limit=t;break;}t+=max(.02,d*.8);
    }
    for(int i=0;i<24;i++) {
        if(float(i)>=BombCount)break;
        if(Preview==1.) {if(i<3)background=smallBomb(ro,rd,limit,background,vec3(float(i-1)*3.,2.2,2.));continue;}
        float hit=impactAt(i);
        if(Progress>=releaseAt(i) && Progress<hit)background=smallBomb(ro,rd,limit,background,dropPosition(i));
        background=carpetBlast(ro,rd,limit,background,dropTarget(i),(Progress-hit)*Duration);
    }
    return background;
}

// Art-directed cloud rise and toroidal circulation, inspired by historical film.
// Full-resolution bounded integration with rolling multiscale billows and directional lighting.
float cloudNoise(vec3 p) {
    vec3 cell=floor(p),f=fract(p);f=f*f*(3.-2.*f);
    vec2 uv=cell.xy+vec2(37.,17.)*cell.z+f.xy+.5;
    vec2 slices=textureLod(BombNormalSampler,fract(uv/512.),0.).rg;
    return mix(slices.r,slices.g,f.z);
}
vec3 nuclearCloud(vec3 ro,vec3 rd,float limit,vec3 background) {
    float age=BlastTime,unit=BlastScale*(Type<.5?2.5:6.),grow=1.-exp(-age*7.);
    float radius=4.+grow*38.,height=8.+pow(age,.65)*86.;
    float dust=5.+pow(age,.55)*120.,fade=1.-smoothstep(.78,1.,age);
    vec3 origin=(ro-target)/unit;
    vec3 inv=sign(rd+vec3(.00000001))/max(abs(rd),vec3(.0000001));
    vec3 lo=(vec3(-dust,-2.,-dust)-origin)*inv,hi=(vec3(dust,height+radius*.65,dust)-origin)*inv;
    vec3 nearV=min(lo,hi),farV=max(lo,hi);
    float a=max(.01,max(nearV.x,max(nearV.y,nearV.z))),b=min(limit/unit,min(farV.x,min(farV.y,farV.z)));
    float opacity=0.;vec3 light=vec3(0);
    if(b>a) {
        float stride=(b-a)/96.;
        // Stable per-pixel jitter breaks integration bands without temporal flicker.
        float jitter=.5;
        for(int i=0;i<96;i++) {
            vec3 q=origin+rd*(a+(float(i)+jitter)*stride);
            float r=length(q.xz),angle=atan(q.z,q.x);
            float cap=1.-length((q-vec3(0,height,0))/vec3(radius,radius*.58,radius));
            float neck=(5.+grow*7.)*(1.+.18*sin(q.y*.24-age*10.));
            float stem=1.-length(vec3(q.x/neck,(q.y-height*.47)/(height*.53),q.z/neck));
            float base=1.-length(vec3(q.x/dust,(q.y-1.)/(3.+grow*6.),q.z/dust));
            float shape=max(cap,max(stem,base));
            float vapour=0.;
            // Two short-lived condensation skirts expand around the rising column.
            for(int ring=0;ring<2;ring++) {
                float t=age-float(ring)*.065;
                float rr=8.+t*180.,yy=height*(.45+float(ring)*.30);
                float d=length(vec2((r-rr)/max(1.,rr*.10),(q.y-yy)/(1.4+grow*1.6)));
                vapour+=max(0.,1.-d)*smoothstep(0.,.04,t)*(1.-smoothstep(.35,.60,t));
            }
            if(shape<-.40 && vapour<.001)continue;
            // Polar advection rolls material over the cap, down its outer edge.
            vec2 radial=q.xz/max(.01,r),vortex=vec2(r-radius*.55,q.y-height);
            float spin=age*3.5;vortex=mat2(cos(spin),-sin(spin),sin(spin),cos(spin))*vortex;
            vec3 rolled=vec3(radial.x*(radius*.55+vortex.x),vortex.y+height,radial.y*(radius*.55+vortex.x));
            vec3 flow=mix(q-vec3(0,age*9.,0),rolled,smoothstep(height*.55,height*.85,q.y)*smoothstep(radius*.25,radius*.50,r));
            flow+=sin(flow.zxy*.14+vec3(age*2.,-age*3.,age))*2.1;
            float coarse=cloudNoise(flow*.105);
            float billow=.50*coarse+.27*cloudNoise(flow*.23+3.7)+.15*cloudNoise(flow*.51-9.1)+.08*cloudNoise(flow*1.07+17.);
            float density=max(0.,shape+(billow-.48)*.80)*fade;
            float alpha=1.-exp(-(density*1.05+vapour*.19)*stride);
            vec3 normal=normalize(vec3(q.x,q.y-height,q.z));
            if(stem>cap && stem>base)normal=normalize(vec3(q.x,.1,q.z));
            if(base>cap && base>stem)normal=normalize(vec3(q.x*.13,(q.y-1.)*2.,q.z*.13));
            normal=normalize(normal+vec3(cos(flow.y*.17),sin(flow.z*.19),cos(flow.x*.13))*(billow-.5));
            float diffuse=max(0.,dot(normal,normalize(vec3(-.55,.8,.35))));
            float shade=(.35+.65*diffuse)*exp(-density*.55);
            float underside=exp(-abs(q.y-(height-radius*.30))/max(1.,radius*.16));
            float cooling=1.-.48*smoothstep(height-radius*.10,height+radius*.32,q.y);
            float heat=(exp(-age*3.6)*cooling+underside*exp(-age*3.0)*.85)*(.35+.95*billow);
            vec3 smoke=mix(vec3(.035,.032,.030),vec3(.67,.58,.47),.3+billow*.7)*shade;
            vec3 fire=mix(vec3(1.5,.105,.006),vec3(5.2,2.9,.62),clamp(heat,0.,1.));
            float seams=smoothstep(.24,.66,billow);
            vec3 color=mix(smoke,fire,smoothstep(.18,.66,heat)*(.4+.6*seams));
            color=mix(color,vec3(.84,.85,.80),vapour/(vapour+density*5.+.001));
            light+=(1.-opacity)*alpha*color;opacity+=(1.-opacity)*alpha;
            if(opacity>.985)break;
        }
        background=background*(1.-opacity)+light;
    }
    vec3 delta=(target+vec3(0,3.*unit,0)-ro);float t=dot(delta,rd);
    if(t>0. && t<limit+unit*3.) {
        float r=length(delta-rd*t)/unit;
        float pulse=exp(-age*180.)+exp(-pow((age-.013)*85.,2.))*.7;
        background+=vec3(9.,7.5,5.)*pulse*exp(-r*r/650.);
        background+=vec3(2.,.85,.15)*exp(-age*14.)*exp(-r/(radius*.65));
    }
    return background;
}
// Closest distance to a finite 3D luminous segment; terrain depth clips every ray.
vec3 streak(vec3 ro,vec3 rd,float limit,vec3 bg,vec3 a,vec3 b,float width,vec3 emission) {
    vec3 v=b-a,w=ro-a;float vv=max(.001,dot(v,v)),rv=dot(rd,v);
    float s=clamp((dot(v,w)-rv*dot(rd,w))/max(.001,vv-rv*rv),0.,1.);
    vec3 delta=a+v*s-ro;float t=dot(delta,rd);
    if(t<=0. || t>limit)return bg;
    float d=length(delta-rd*t)/max(.03,width);
    if(d>7.)return bg;
    return bg+emission*(exp(-d*d*2.)+exp(-d*1.4)*.12);
}
vec2 volumeBounds(vec3 ro,vec3 rd,vec3 lo,vec3 hi,float limit) {
    vec3 inv=sign(rd+vec3(1e-8))/max(abs(rd),vec3(1e-7));
    vec3 a=(lo-ro)*inv,b=(hi-ro)*inv,n=min(a,b),f=max(a,b);
    return vec2(max(.01,max(n.x,max(n.y,n.z))),min(limit,min(f.x,min(f.y,f.z))));
}
float boltPulse(float age) {
    return step(0.,age)*(exp(-max(0.,age)*13.)+exp(-pow((age-.17)*34.,2.))*.8+exp(-pow((age-.36)*31.,2.))*.55+exp(-pow((age-.61)*36.,2.))*.3);
}
vec3 lightning(vec3 ro,vec3 rd,float limit,vec3 bg) {
    bool crawler=Type>6.5 && Type<7.5,superbolt=Type>5.5 && Type<6.5;
    float unit=BlastScale,pulse=boltPulse(BoltAge),seed=BoltSeed*37.71;
    if(superbolt)pulse+=step(0.,BoltAge)*exp(-max(0.,BoltAge)*2.5)*.22;
    if(pulse<.001)return bg;
    vec3 base=vec3(BoltX,BoltY,BoltZ),last=crawler?base+vec3(-340,0,-25)*unit:base;
    vec2 bounds=volumeBounds((ro-base)/unit,rd,crawler?vec3(-380,-100,-170):vec3(-120,-80,-140),crawler?vec3(380,80,170):vec3(120,335,140),limit/unit);
    if(bounds.y<=bounds.x)return bg;
    for(int i=1;i<=40;i++) {
        float k=float(i),f=k/40.,jag=sin(k*13.71+seed)*sin(k*3.13+seed);
        vec3 path=crawler?vec3(-340.+f*680.,sin(f*12.)*20.+jag*9.,sin(f*9.)*65.+cos(k*7.31)*12.)
            :vec3(sin(f*9.+seed)*26.*f+jag*14.*f,310.*f,cos(f*7.+seed)*20.*f+cos(k*7.31+seed)*11.*f);
        vec3 next=base+path*unit;
        float width=(superbolt?1.9:.65)*unit;
        bg=streak(ro,rd,limit,bg,last,next,width,vec3(7.,8.5,12.)*pulse*(superbolt?2.:1.));
        if(i>5 && i%3==0) {
            vec3 branch=next;float signX=sin(k*13.+seed)>0.?1.:-1.;
            for(int j=1;j<=7;j++) {
                float v=float(j);vec3 end=next+vec3(signX*(v*9.+sin(v*17.+k)*5.),-v*(crawler?8.:12.),sin(k+seed)*v*12.)*unit;
                bg=streak(ro,rd,limit,bg,branch,end,width*.32,vec3(2.8,4.,7.)*pulse*(1.-v*.075));branch=end;
            }
        }
        last=next;
    }
    vec3 delta=base+vec3(0,crawler?0.:110.,0)*unit-ro;float t=dot(delta,rd);
    if(t>0. && t<limit)bg+=vec3(.14,.22,.38)*pulse*(superbolt?2.:1.)*exp(-length(delta-rd*t)/(220.*unit));
    return bg;
}
vec3 stormClouds(vec3 ro,vec3 rd,float limit,vec3 bg) {
    float unit=BlastScale,fade=smoothstep(0.,.06,Progress)*(1.-smoothstep(.90,1.,Progress));
    vec3 origin=(ro-target)/unit;
    vec2 bounds=volumeBounds(origin,rd,vec3(-800,120,-650),vec3(800,400,650),limit/unit);
    float opacity=0.;vec3 light=vec3(0);float pulse=boltPulse(BoltAge);
    if(bounds.y>bounds.x) {
        float stride=(bounds.y-bounds.x)/72.;
        for(int i=0;i<72;i++) {
            vec3 q=origin+rd*(bounds.x+(float(i)+.5)*stride);
            float edge=1.-pow(length(q.xz/vec2(800,650)),3.);
            float shelf=1.-abs((q.y-265.)/85.);
            if(edge<-.55 || shelf<-.55)continue;
            vec3 flow=q+vec3(Time*4.,0,Time*1.3);
            float n=.55*cloudNoise(flow*.018)+.30*cloudNoise(flow*.047)+.15*cloudNoise(flow*.12);
            float density=max(0.,min(edge,shelf)+(n-.46)*1.1)*fade;
            float alpha=1.-exp(-density*stride*.09);
            float top=smoothstep(215.,330.,q.y);
            vec3 color=mix(vec3(.018,.025,.042),vec3(.26,.30,.36),top)*(.65+n*.6);
            vec3 bolt=(vec3(BoltX,BoltY,BoltZ)-target)/unit+vec3(0,Type<7.5?0.:235.,0);
            color+=vec3(.45,.62,1.)*pulse*exp(-length(q-bolt)/170.)*2.;
            light+=(1.-opacity)*alpha*color;opacity+=(1.-opacity)*alpha;if(opacity>.985)break;
        }
        bg=bg*(1.-opacity)+light;
    }
    // Rain is sampled in world space and clipped by terrain, never painted on the screen.
    if(Type>7.5)for(int sheet=0;sheet<12;sheet++) {
        float z=-520.+float(sheet)*95.;if(abs(rd.z)<.001)break;
        float t=(z-origin.z)/rd.z;
        if(t>0. && t<limit/unit) {
            vec3 q=origin+rd*t;
            if(q.y>4. && q.y<200. && abs(q.x)<650.) {
                vec2 cell=vec2(q.x*.45+q.y*.08,q.y*.06+Time*5.+float(sheet)*.37);
                float drop=step(.965,fract(cell.x))*smoothstep(.25,.85,fract(cell.y));
                bg=mix(bg,vec3(.42,.50,.62),drop*.17*fade);
            }
        }
    }
    return bg;
}
float meteorRock(vec3 q) {
    float a=Time*.19,c=cos(a),s=sin(a);q.xz=mat2(c,-s,s,c)*q.xz;
    float d=length(q/vec3(1.15,.72,.92))-1.;
    d=min(d,length((q-vec3(.58,.16,-.25))/vec3(.55,.54,.62))-1.);
    d=max(d,dot(q,normalize(vec3(1,.6,-.2)))-.84);
    d=max(d,dot(q,normalize(vec3(-.4,-1.,.5)))-.75);
    return d+(noise(q*3.1)-.5)*.22+(noise(q*9.)-.5)*.07;
}
vec3 meteorEntry(vec3 ro,vec3 rd,float limit,vec3 bg,vec3 end,float phase,float size,float seed) {
    if(phase<0. || phase>=1.)return bg;
    vec3 direction=normalize(vec3(-.8,-1.,-.35));float unit=BlastScale*size;
    float entryHeight=Type>8.5 && Type<9.5?950.:1450.;
    vec3 head=end-direction*pow(1.-phase,1.15)*entryHeight*BlastScale;
    vec3 origin=(ro-head)/unit;
    vec2 rock=sphereHit(origin,rd,vec3(0),31.);
    float near=max(.01,rock.x),far=min(limit/unit,rock.y);
    if(far>near)for(int step=0;step<64;step++) {
        vec3 p=origin+rd*near;float d=meteorRock(p/21.)*21.;
        if(d<.08) {
            vec2 e=vec2(.015,0);vec3 q=p/21.;
            vec3 n=normalize(vec3(meteorRock(q+e.xyy)-meteorRock(q-e.xyy),meteorRock(q+e.yxy)-meteorRock(q-e.yxy),meteorRock(q+e.yyx)-meteorRock(q-e.yyx)));
            float front=max(0.,dot(n,direction)),grain=noise(q*23.+seed);
            float cracks=1.-smoothstep(.008,.045,abs(noise(q*9.)-.49));
            vec3 crust=vec3(.035,.019,.014)*(.7+.8*max(0.,dot(n,normalize(vec3(-.6,.8,.3)))));
            bg=crust+vec3(5.,.6,.025)*(cracks*.55+pow(front,8.)*.75);limit=near*unit;break;
        }
        near+=max(.04,d*.65);if(near>far)break;
    }
    // A turbulent, expanding wake surrounds the body and cools into a smoke train.
    vec3 tail=head-direction*640.*unit;
    vec2 bounds=volumeBounds(ro,rd,min(head,tail)-vec3(65.*unit),max(head,tail)+vec3(65.*unit),limit);
    if(bounds.y>bounds.x) {
        float stride=(bounds.y-bounds.x)/40.,opacity=0.;vec3 light=vec3(0);
        for(int i=0;i<40;i++) {
            vec3 q=(ro+rd*(bounds.x+(float(i)+.5)*stride)-head)/unit;
            float along=dot(q,-direction);if(along<0. || along>640.)continue;
            vec3 radial=q+direction*along;float radius=15.+sqrt(along)*1.5;
            radial+=vec3(sin(along*.035+Time*5.),cos(along*.023-Time*7.),sin(along*.019))*along*.018;
            float shape=1.-length(radial)/radius;if(shape<-.3)continue;
            vec3 flow=q*.09+vec3(Time*5.,-Time*13.,Time*2.);
            float n=.6*cloudNoise(flow)+.4*cloudNoise(flow*2.7);
            float density=max(0.,shape+(n-.5)*.85)*(1.-smoothstep(450.,640.,along));
            float alpha=1.-exp(-density*stride/unit*.10);
            float heat=exp(-along/210.)*(.5+n*.8);
            vec3 smoke=mix(vec3(.055,.043,.04),vec3(.28,.20,.14),n);
            vec3 fire=mix(vec3(3.8,.24,.008),vec3(9.,5.,1.6),clamp(heat,0.,1.));
            vec3 color=mix(smoke,fire,smoothstep(.16,.6,heat));
            light+=(1.-opacity)*alpha*color;opacity+=(1.-opacity)*alpha;if(opacity>.98)break;
        }
        bg=bg*(1.-opacity)+light;
    }
    // Shock sheath and separate burning fragments reveal the speed and scale.
    bg=streak(ro,rd,limit,bg,head+direction*24.*unit,head-direction*38.*unit,28.*unit,vec3(.45,.12,.025));
    for(int i=0;i<12;i++) {
        float k=float(i)+1.,trail=40.+k*28.;
        vec3 chip=head-direction*trail*unit+vec3(sin(k*7.+seed)*k*4.,cos(k*4.+seed)*k*2.,sin(k*2.+seed)*k*4.)*unit;
        bg=streak(ro,rd,limit,bg,chip,chip-direction*(25.+k*2.)*unit,(1.2+k*.06)*unit,vec3(4.,.65,.035));
    }
    return bg;
}
vec3 eruptionCloud(vec3 ro,vec3 rd,float limit,vec3 bg,vec3 center,float seconds,bool volcano,bool airburst) {
    float unit=BlastScale,age=clamp((Progress-Impact)/(1.-Impact),0.,1.);
    float grow=1.-exp(-max(0.,seconds)*(volcano?.15:.7));
    float height=(volcano?365.:125.)*grow+8.,radius=(volcano?170.:185.)*grow+6.;
    vec3 origin=(ro-center)/unit;
    vec2 bounds=volumeBounds(origin,rd,vec3(-radius*1.9,-radius*.3,-radius*1.9),vec3(radius*2.2,height+radius,radius*1.9),limit/unit);
    if(bounds.y>bounds.x) {
        float stride=(bounds.y-bounds.x)/80.,opacity=0.;vec3 light=vec3(0);
        for(int i=0;i<80;i++) {
            vec3 q=origin+rd*(bounds.x+(float(i)+.5)*stride);
            float cap=1.-length((q-vec3(volcano?height*.23:0.,height,0))/vec3(radius,radius*(volcano?.48:.70),radius));
            float column=-1.;
            if(volcano) {
                // Successive lobes roll into a wind-sheared, widening eruption column.
                for(int puff=0;puff<6;puff++) {
                    float f=(float(puff)+.5)/6.,y=height*f;
                    float r=15.+f*f*100.;
                    vec3 pos=vec3(y*.20+sin(f*11.+seconds*.8)*r*.20,y,sin(f*8.)*r*.15);
                    column=max(column,1.-length((q-pos)/vec3(r,r*1.45,r)));
                }
            }
            float dust=airburst?-1.:1.-length(vec3(q.x/(radius*1.85),(q.y-8.)/(12.+grow*19.),q.z/(radius*1.85)));
            float shape=max(cap,max(column,dust));if(shape<-.4)continue;
            vec3 flow=q-vec3(seconds*2.,seconds*(volcano?12.:5.),0);flow+=sin(flow.zxy*.06+Time*.7)*5.;
            float n=.50*cloudNoise(flow*.035)+.30*cloudNoise(flow*.09)+.20*cloudNoise(flow*.24);
            float density=max(0.,shape+(n-.48)*1.1)*(1.-smoothstep(.85,1.,age));
            float alpha=1.-exp(-density*stride*.16);
            float heat=volcano?exp(-q.y/65.)*smoothstep(4.,20.,q.y)*(column>dust?1.:.015):exp(-max(0.,seconds)*.55);
            float lightSide=clamp(.6+q.x/radius*.25+q.y/(height+radius)*.3,.2,1.);
            vec3 ash=mix(vec3(.025,.024,.025),vec3(.48,.42,.36),n)*lightSide;
            vec3 fire=mix(vec3(2.5,.12,.004),vec3(8.,3.5,.4),clamp(heat*n,0.,1.));
            vec3 color=mix(ash,fire,clamp(heat,0.,1.)*smoothstep(.22,.65,n));
            light+=(1.-opacity)*alpha*color;opacity+=(1.-opacity)*alpha;if(opacity>.985)break;
        }
        bg=bg*(1.-opacity)+light;
    }
    for(int i=0;i<40;i++) {
        float k=float(i)+1.,angle=k*2.39996;
        float time=volcano?mod(max(0.,seconds)+k*.23,6.):seconds;
        vec3 velocity=vec3(cos(angle)*(16.+k*.9),75.+mod(k*17.,70.),sin(angle)*(16.+k*.9));
        vec3 point=center+(velocity*time-vec3(0,10.*time*time,0))*unit;
        if(seconds>=0. && point.y>center.y && (volcano || seconds<7.))bg=streak(ro,rd,limit,bg,point,point-velocity*.09*unit,(.7+mod(k,3.)*.25)*unit,vec3(5.,.6,.018));
    }
    vec3 delta=center+vec3(0,airburst?0.:15.,0)*unit-ro;float t=dot(delta,rd);
    if(t>0. && t<limit+unit*5.) {
        float r=length(delta-rd*t)/unit;
        float pulse=volcano?exp(-mod(max(0.,seconds),3.2)*8.)*.45:exp(-max(0.,seconds)*9.);
        bg+=vec3(8.,3.8,1.0)*pulse*exp(-r/(airburst?180.:volcano?25.:100.));
        if(airburst) {
            float shell=max(1.,seconds*180.);float distance=length(delta-rd*t)/unit;
            bg+=vec3(.75,.82,.9)*exp(-pow((distance-shell)/max(3.,shell*.025),2.))*exp(-seconds*.6);
        }
    }
    return bg;
}
vec3 naturalEvent(vec3 ro,vec3 rd,float limit,vec3 bg) {
    if(Type>=5. && Type<=8.) {
        if(Type>=7.)bg=stormClouds(ro,rd,limit,bg);
        return lightning(ro,rd,limit,bg);
    }
    float seconds=(Progress-Impact)*Duration;
    if(Type>3.5 && Type<4.5) {
        // A small active vent is visible before the main eruption.
        return eruptionCloud(ro,rd,limit,bg,target,max(0.,seconds),true,false);
    }
    bool shower=Type>8.5 && Type<9.5,airburst=Type>9.5;
    for(int meteor=0;meteor<6;meteor++) {
        if(meteor>0 && !shower)break;
        float k=float(meteor),impact=Impact+(shower?k*.09:0.);
        vec3 end=target+vec3(shower?sin(k*2.39996)*k*22.:0.,airburst?170.:0.,shower?cos(k*2.39996)*k*18.:0.)*BlastScale;
        float phase=(Progress-(impact-Impact))/Impact;
        bg=meteorEntry(ro,rd,limit,bg,end,phase,shower?.6:1.,k*11.);
        float hitAge=(Progress-impact)*Duration;
        if(hitAge>=0. && meteor>0) {
            vec3 delta=end-ro;float t=dot(delta,rd);
            if(t>0. && t<limit+5.)bg+=vec3(8.,3.8,.8)*exp(-hitAge*9.)*exp(-length(delta-rd*t)/(70.*BlastScale));
        }
    }
    if(seconds>=0.)bg=eruptionCloud(ro,rd,limit,bg,target+vec3(0,airburst?170.:0.,0)*BlastScale,seconds,false,airburst);
    return bg;
}
float sceneDistance(vec2 uv,vec3 ro,vec3 rd,vec3 forward) {
    float raw=texture(SceneDepthSampler,uv).r;
    if((ReverseDepth<.5 && raw>.999999) || (ReverseDepth>.5 && raw<.000001))return 100000.;
    float ndc=ReverseDepth>.5?raw:raw*2.-1.;
    float viewDepth=DepthB/(ndc+DepthA);
    if(viewDepth<=0.)return 100000.;
    float distance=viewDepth/max(.001,dot(rd,forward));
    if(distance<32.)return distance; // Foreground walls still hide the event.
    vec3 point=ro+rd*distance;
    bool weather=Type>6.5 && Type<8.5;
    bool cloud=CloudThickness>0. && (weather || ro.y>CloudBase+CloudThickness+2. && rd.y<-.01)
        && point.y>=CloudBase-.75 && point.y<=CloudBase+CloudThickness+.75;
    return cloud?100000.:distance;
}
vec2 lensUV(vec2 uv) {
    bool meteor=Type>2.5 && Type<3.5 || Type>8.5;
    if(Preview==1. || (Type>1.5 && !meteor) || Progress<Impact)return uv;
    vec3 delta=vec3(TargetX,TargetY+(Type>9.5?170.*BlastScale:4.),TargetZ)-vec3(CameraX,CameraY,CameraZ);
    vec3 forward=vec3(ForwardX,ForwardY,ForwardZ),right=vec3(RightX,RightY,RightZ),up=vec3(UpX,UpY,UpZ);
    float depth=dot(delta,forward);if(depth<=.05)return uv;
    vec2 center=.5+.5*vec2(dot(delta,right)/(depth*ProjectionX),dot(delta,up)/(depth*ProjectionY));
    if(Preview<.5) {
    float terrain=sceneDistance(clamp(center,vec2(.002),vec2(.998)),vec3(CameraX,CameraY,CameraZ),normalize(delta),forward);
    if(terrain<length(delta)-4.)return uv;
    }
    float seconds=(Progress-Impact)*Duration;vec2 radial=uv-center;float r=length(radial);
    float ring=seconds*(Type>.5?75.:45.)*BlastScale/max(10.,depth)*.5;
    float pressure=exp(-pow((r-ring)/.035,2.))*exp(-seconds*.8);
    float heat=exp(-r*depth/(BlastScale*70.))*exp(-seconds*.18)*sin(r*70.-Time*9.);
    return clamp(uv+normalize(radial+vec2(.000001))*(pressure*.035+heat*.006),vec2(.001),vec2(.999));
}
vec2 earthUV(vec3 normal) {
    // Geographic tangent frame over the western Atlantic, then real map UVs.
    vec3 anchor=vec3(.582563,.422618,-.694272),east=vec3(.766044,0,.642788),north=vec3(-.271654,.906308,.323744);
    vec3 globe=normalize(east*normal.x+north*normal.z+anchor*normal.y);
    return vec2(fract(.5+atan(globe.z,globe.x)/(2.*PI)),.5-asin(clamp(globe.y,-1.,1.))/PI);
}
vec4 earthMaterial(vec2 uv,float footprint) {
    float lod=clamp(log2(max(1.,footprint))-1.35,0.,10.);
    return textureLod(EarthSurfaceSampler,uv,lod);
}
float earthCloud(vec2 uv,float footprint,float grazing) {
    float lod=clamp(log2(max(1.,footprint))-1.35,0.,10.);
    float spread=min(.0015,footprint/max(.12,grazing)/float(textureSize(EarthWeatherSampler,0).x))*.30;
    vec2 along=vec2(0,spread);
    return (textureLod(EarthWeatherSampler,uv-along,lod).a+textureLod(EarthWeatherSampler,uv+along,lod).a
        +textureLod(EarthWeatherSampler,uv-along*.33,lod).a+textureLod(EarthWeatherSampler,uv+along*.33,lod).a)*.25;
}
vec3 spaceBackdrop(vec3 worldRo,vec3 rd,out float surface) {
    // Distances use Earth's actual mean radius; the cinematic replay is spatially compressed.
    float radius=6371000.,units=20.;vec3 ro=worldRo*units,planet=vec3(TargetX*.5*units,-radius,TargetZ*.5*units);
    vec3 sky=vec3(.00012,.00018,.00035),sun=normalize(vec3(-.5,.65,.6));
    float sunAngle=dot(rd,sun);
    // Bright daylight exposure suppresses most stars, as in orbital photography.
    vec3 cell=floor(rd*1600.);float star=hash(cell);
    sky+=vec3(.35,.43,.55)*smoothstep(.9996,1.,star)*pow(max(0.,1.-length(fract(rd*1600.)-.5)*1.7),4.)*.12;
    sky+=vec3(8.,7.3,6.1)*smoothstep(.99998,.99999,sunAngle);
    sky+=vec3(.08,.065,.045)*pow(max(0.,sunAngle),600.);
    vec2 hit=sphereHit(ro,rd,planet,radius);float groundDistance=1e9;surface=100000.;
    float pixel=2.*ProjectionY/float(textureSize(InSampler,0).y);
    if(hit.y>0.) {
        float t=hit.x>0.?hit.x:hit.y;groundDistance=t;surface=t/units;
        vec3 point=ro+rd*t,n=normalize(point-planet);vec2 uv=earthUV(n);
        float grazing=max(.025,abs(dot(n,-rd)));
        float footprint=t*pixel/radius*float(textureSize(EarthSurfaceSampler,0).x)/(2.*PI);
        float lod=clamp(log2(max(1.,footprint))-1.35,0.,10.);
        vec4 ground=earthMaterial(uv,footprint),night=textureLod(EarthWeatherSampler,uv,lod);
        float sunlight=max(0.,dot(n,sun));
        // Elevated cloud shadows offset toward the light instead of painting clouds on land.
        vec3 above=normalize(n+sun*10000./radius/max(.15,sunlight));
        float shadow=textureLod(EarthWeatherSampler,earthUV(above),lod).a;
        vec3 linear=pow(ground.rgb,vec3(2.2));
        linear=mix(linear,vec3(.006,.027,.065),ground.a*.85);
        vec3 lit=linear*(.012+sunlight*.93)*(1.-shadow*.55);
        vec3 halfV=normalize(sun-rd);float glint=pow(max(0.,dot(n,halfV)),160.);
        float fresnel=.025+.975*pow(1.-max(0.,dot(n,-rd)),5.);
        lit+=vec3(1.,.93,.82)*glint*fresnel*ground.a*sunlight*1.8;
        lit+=pow(night.rgb,vec3(2.2))*vec3(1.15,.73,.35)*(1.-smoothstep(-.10,.10,dot(n,sun)))*1.3;
        sky=lit;
    }
    vec2 clouds=sphereHit(ro,rd,planet,radius+10000.);
    if(clouds.y>0.) {
        float t=clouds.x>0.?clouds.x:clouds.y;
        if(t<groundDistance) {
            vec3 n=normalize(ro+rd*t-planet);vec2 uv=earthUV(n);
            float grazing=max(.035,abs(dot(n,-rd)));
            float footprint=t*pixel/radius*float(textureSize(EarthWeatherSampler,0).x)/(2.*PI);
            float lod=clamp(log2(max(1.,footprint))-1.35,0.,10.);
            float cloud=earthCloud(uv,footprint,grazing);
            vec2 offset=vec2(.00018,-.00013);
            float edge=textureLod(EarthWeatherSampler,uv+offset,lod).a-cloud;
            float light=.20+.88*max(0.,dot(n,sun))+edge*.35;
            sky=mix(sky,vec3(.95,.97,1.)*light,smoothstep(.02,.68,cloud)*.97);
        }
    }
    vec2 air=sphereHit(ro,rd,planet,radius+100000.);
    if(air.y>0.) {
        float a=max(.01,air.x),b=min(groundDistance,air.y);
        if(b>a) {
            float stride=(b-a)/8.;vec2 optical=vec2(0);vec3 scatter=vec3(0);
            vec3 betaR=vec3(5.8,13.5,33.1)*.000001;float betaM=.000021;
            float phaseR=.059683*(1.+sunAngle*sunAngle),g=.76;
            float phaseM=.119366*(1.-g*g)*(1.+sunAngle*sunAngle)/((2.+g*g)*pow(max(.04,1.+g*g-2.*g*sunAngle),1.5));
            for(int i=0;i<8;i++) {
                vec3 p=ro+rd*(a+(float(i)+.5)*stride),n=normalize(p-planet);
                float altitude=max(0.,length(p-planet)-radius);
                vec2 density=exp(-altitude/vec2(8000.,1200.));optical+=density*stride;
                float mu=max(.06,dot(n,sun));vec2 sunDepth=density*vec2(8000.,1200.)/mu;
                vec3 trans=exp(-betaR*(optical.x+sunDepth.x)-vec3(betaM)*(optical.y+sunDepth.y));
                scatter+=trans*(betaR*density.x*phaseR+vec3(betaM*density.y*phaseM))*stride*7.;
            }
            sky=sky*exp(-betaR*optical.x-vec3(betaM)*optical.y)+scatter;
        }
    }
    return pow(max(vec3(0),sky),vec3(1./2.2));
}
float cinemaMask() {
    float aspect=float(textureSize(InSampler,0).x)/float(textureSize(InSampler,0).y);
    float border=aspect>1.3?max(0.,(1.-aspect/2.39)*.5):0.;
    return CinematicBars>.5?step(border,texCoord.y)*step(texCoord.y,1.-border):1.;
}
void main() {
    float endingFade=Preview!=1. && Ending>.5?smoothstep(EndingStart,EndingStart+.025,Progress):0.;
    if(endingFade>=.9999) {
        float alpha=Preview>.5?1.:texture(InSampler,texCoord).a;
        vec3 image=vec3(0);
        if(DeltaOutput>.5)image-=texture(InSampler,texCoord).rgb;
        fragColor=vec4(image,alpha);return;
    }
    vec3 ro=vec3(CameraX,CameraY,CameraZ),forward=vec3(ForwardX,ForwardY,ForwardZ);
    vec2 sampleUV=lensUV(texCoord);
    vec2 screen=sampleUV*2.-1.;
    vec3 rd=normalize(forward+vec3(RightX,RightY,RightZ)*screen.x*ProjectionX+vec3(UpX,UpY,UpZ)*screen.y*ProjectionY);
    vec4 original;float limit;
    if(Preview>.5) {
        // Isolated studio: the preview uses this same 3D surface and animation as the world event.
        original=vec4(mix(vec3(.55,.63,.70),vec3(.83,.88,.91),texCoord.y),1.);limit=100000.;
        if(rd.y<-.0001) {
            float floorDistance=-ro.y/rd.y;
            if(floorDistance>0.) {
                vec3 point=ro+rd*floorDistance;
                float lines=1.-smoothstep(.025,.065,min(abs(fract(point.x*.25)-.5),abs(fract(point.z*.25)-.5)));
                float fade=exp(-length(point.xz)*.016);
                vec3 floorColor=mix(vec3(.52,.57,.60),vec3(.68,.73,.76),lines*fade*.4);
                float shadow=exp(-dot(point.xz,point.xz)/12.);
                original.rgb=mix(original.rgb,floorColor*(1.-shadow*.55),exp(-floorDistance*.003));limit=floorDistance;
            }
        }
    } else {
        original=texture(InSampler,sampleUV);
        vec2 fringe=(sampleUV-texCoord)*.18;
        original.r=texture(InSampler,clamp(sampleUV+fringe,vec2(.001),vec2(.999))).r;
        original.b=texture(InSampler,clamp(sampleUV-fringe,vec2(.001),vec2(.999))).b;
        limit=sceneDistance(sampleUV,ro,rd,forward);
    }
    if(Type<.5 && SpaceView>.5 && Preview!=1. && Progress<Impact) {
        float blend=smoothstep(1200.,4200.,ro.y);float surface;
        vec3 space=spaceBackdrop(ro,rd,surface);
        original.rgb=mix(original.rgb,space,blend);
        if(blend>.98)limit=min(limit,surface);
    }
    rocket=vec3(RocketX,RocketY,RocketZ);target=vec3(TargetX,TargetY,TargetZ);axis=normalize(vec3(AxisX,AxisY,AxisZ));
    side=frameRight(axis);across=normalize(cross(side,axis));
    vec3 color=original.rgb;
    if(Type<.5) {
        if(Progress<Impact || Preview==1.)color=flight(ro,rd,limit,color).rgb;
        else color=nuclearCloud(ro,rd,limit,color);
    } else if(Type<1.5) {
        if(Progress<Impact || Preview==1.) {
            float t=clamp(Progress/Impact,0.,1.);
            vec3 center=Preview==1.?vec3(0,2.05*Scale,0):dropPosition(0);
            vec3 direction=Preview==1.?vec3(0,1,0):normalize(vec3(sin(Time*2.)*.035,-1.,cos(Time*1.7)*.025)*(1.-t*.5));
            color=atomicBomb(ro,rd,limit,color,center,direction);
        } else {
            color=nuclearCloud(ro,rd,limit,color);
            // Atmospheric effects linger in the air; nothing paints or changes the terrain.
            float seconds=(Progress-Impact)*Duration;
            vec3 impactDelta=target+vec3(0,3,0)-ro;float viewDepth=dot(impactDelta,forward);
            float visibility=dot(impactDelta,rd)>0. && dot(impactDelta,rd)<limit+4.?1.:0.;
            float flash=exp(-seconds*12.)*visibility;
            color+=vec3(3.,2.6,1.8)*flash;
            float veil=exp(-seconds*.23)*smoothstep(.12,.6,seconds)*visibility;
            color=mix(color,vec3(.65,.38,.16),veil*.10);
            float ash=noise(vec3(floor(texCoord*vec2(850,500)),floor(Time*12.)));
            color+=vec3(.9,.3,.04)*step(.9985,ash)*(1.-smoothstep(.65,1.,BlastTime))*.35*visibility;
            float afterimage=exp(-seconds*.42)*smoothstep(.08,.7,seconds)*visibility;
            color+=vec3(.15,.09,.025)*afterimage;
        }
    } else if(Type>2.5)color=naturalEvent(ro,rd,limit,color);
    if(Preview>.5)color=pow(max(vec3(0),color)/(1.+max(vec3(0),color)*.55),vec3(.88));
    if(endingFade>0.)color=mix(color,vec3(0),endingFade);
    if(Preview!=1.)color*=cinemaMask();
    if(DeltaOutput>.5)color-=texture(InSampler,texCoord).rgb;
    fragColor=vec4(color,original.a);
}
