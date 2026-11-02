#version 330 core
uniform sampler2D InSampler;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;
uniform float Amount;
uniform float Span;
uniform float Threshold;
uniform float Vertical;
uniform float Speed;

vec4 at(vec2 uv){return texture(InSampler,clamp(uv,vec2(0.00001),vec2(0.99999)));}
float luma(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void main(){
    vec4 original=at(texCoord);
    if(luma(original.rgb)<Threshold||original.a<0.01){fragColor=original;return;}
    vec2 size=vec2(textureSize(InSampler,0));
    bool vertical=Vertical>0.5;
    float coordinate=vertical?texCoord.y*size.y:texCoord.x*size.x;
    float span=Span*max(1.0,size.y/720.0),stride=span/16.0;
    float shift=floor(Time*Speed*10.0);
    float base=floor((coordinate+shift)/span)*span-shift;
    int bin=int(clamp(floor((coordinate-base)/stride),0.0,15.0));
    vec3 colors[16];float keys[16];
    for(int i=0;i<16;i++){colors[i]=vec3(0);keys[i]=1e10;}
    int count=0,rank=0;
    for(int i=0;i<16;i++){
        float position=base+(float(i)+0.5)*stride;
        vec2 uv=vertical?vec2(texCoord.x,position/size.y):vec2(position/size.x,texCoord.y);
        vec4 color=at(uv);float value=luma(color.rgb);
        if(position>=0.0&&position<(vertical?size.y:size.x)&&color.a>=0.01&&value>=Threshold){
            colors[count]=color.rgb;keys[count]=value+float(i)*0.000001;
            if(i<bin)rank++;
            count++;
        }
    }
    if(count==0){fragColor=original;return;}

    for(int k=2;k<=16;k*=2)for(int j=k/2;j>0;j/=2)for(int i=0;i<16;i++){
        int partner=i^j;
        if(partner>i){
            bool ascending=(i&k)==0;
            if((ascending&&keys[i]>keys[partner])||(!ascending&&keys[i]<keys[partner])){
                float key=keys[i];keys[i]=keys[partner];keys[partner]=key;
                vec3 color=colors[i];colors[i]=colors[partner];colors[partner]=color;
            }
        }
    }
    fragColor=vec4(mix(original.rgb,colors[min(rank,count-1)],Amount),original.a);
}
