precision mediump float;
uniform sampler2D tex_y;
uniform sampler2D tex_u;
uniform sampler2D tex_v;
varying vec2 tc;

void main()
{
float Y = (texture2D(tex_y, tc).r - 16./255.)*1.164;
float U = texture2D(tex_u, tc).r - 128./255.;
float V = texture2D(tex_v, tc).r - 128./255.;

float cr = clamp(Y + 1.596*U, 0. , 1.);
float cg = clamp(Y -0.813*U -0.392*V, 0. , 1.);
float cb = clamp(Y +2.017 *V, 0. , 1.);

vec4 ss= vec4(cb,cg,cr,1.);

gl_FragColor = ss;
}