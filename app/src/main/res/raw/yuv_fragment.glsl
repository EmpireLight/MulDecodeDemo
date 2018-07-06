precision mediump float;
uniform sampler2D texY,texU,texV;
varying vec2 vTextureCoord;

void main(){
/*
//湖广午王
  vec4 color = vec4((texture2D(texY, vTextureCoord).r - 16./255.) * 1.164);
  vec4 U = vec4(texture2D(texU, vTextureCoord).r - 128./255.);
  vec4 V = vec4(texture2D(texV, vTextureCoord).r - 128./255.);
  color += U * vec4(0, -0.392, 2.017, 0);
  color += V * vec4(1.596, -0.813, 0, 0);
  color.a = 1.0;
  gl_FragColor = color;
*/

/*
    float Y = (texture2D(texY, vTextureCoord).r - 16./255.)*1.164;
    float U = texture2D(texU, vTextureCoord).r - 128./255.;
    float V = texture2D(texV, vTextureCoord).r - 128./255.;

    float cr = clamp(Y + 1.596*U, 0. , 1.);
    float cg = clamp(Y -0.813*U -0.392*V, 0. , 1.);
    float cb = clamp(Y +2.017 *V, 0. , 1.);

    gl_FragColor = vec4(cb,cg,cr,1.);
*/

	vec4 c = vec4((texture2D(texY, vTextureCoord).r - 16./255.) * 1.164);
	vec4 U = vec4(texture2D(texU, vTextureCoord).r - 128./255.);
	vec4 V = vec4(texture2D(texV, vTextureCoord).r - 128./255.);
	c += V * vec4(1.596, -0.813, 0, 0);
	c += U * vec4(0, -0.392, 2.017, 0);
	c.a = 1.0;
	gl_FragColor = c;
}