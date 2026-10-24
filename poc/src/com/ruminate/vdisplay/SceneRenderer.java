package com.ruminate.vdisplay;

import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import java.nio.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** One GL owner samples a virtual-display frame once, then draws both output crops. */
final class SceneRenderer {
    private final HandlerThread thread = new HandlerThread("vdisplay-gl");
    private final Handler gl;
    private final Consumer<String> error;
    private EGLDisplay display;
    private EGLContext context;
    private EGLConfig config;
    private EGLSurface pbuffer;
    private final EGLSurface[] outputs = new EGLSurface[2];
    private final int[] widths = new int[2], heights = new int[2];
    private SurfaceTexture input;
    private Surface inputSurface;
    private int texture, program;
    private boolean vertical, frameReady, closed;
    private final float[] matrix = new float[16];
    private final FloatBuffer vertices = ByteBuffer.allocateDirect(16 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    volatile long frames;
    final long[] swaps = new long[2];

    SceneRenderer(Consumer<Surface> ready, Consumer<String> error) {
        this.error = error;
        vertices.put(new float[]{-1,-1,0,0, 1,-1,1,0, -1,1,0,1, 1,1,1,1}).position(0);
        thread.start(); gl = new Handler(thread.getLooper());
        gl.post(() -> {
            try { init(); ready.accept(inputSurface); }
            catch (RuntimeException e) { error.accept("Renderer init: " + e); }
        });
    }
    private void init() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        require(EGL14.eglInitialize(display, version, 0, version, 1), "eglInitialize");
        int[] attrs = {EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
                EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT|EGL14.EGL_PBUFFER_BIT,EGL14.EGL_NONE};
        EGLConfig[] configs = new EGLConfig[1]; int[] count = new int[1];
        require(EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0) && count[0]>0,"config");
        config=configs[0];
        context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        require(context!=EGL14.EGL_NO_CONTEXT,"context");
        pbuffer=EGL14.eglCreatePbufferSurface(display,config,
                new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
        current(pbuffer);
        int vs=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 aPosition; attribute vec2 aTex; uniform mat4 uMatrix; uniform vec4 uCrop; varying vec2 vTex; void main(){ gl_Position=vec4(aPosition,0.,1.); vTex=(uMatrix*vec4(uCrop.xy+aTex*uCrop.zw,0.,1.)).xy; }");
        int fs=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES uTexture; varying vec2 vTex; void main(){ gl_FragColor=texture2D(uTexture,vTex); }");
        program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,vs); GLES20.glAttachShader(program,fs);
        GLES20.glLinkProgram(program); int[] linked=new int[1]; GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);
        require(linked[0]!=0,GLES20.glGetProgramInfoLog(program)); GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs);
        int[] tex=new int[1]; GLES20.glGenTextures(1,tex,0); texture=tex[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        input=new SurfaceTexture(texture); input.setDefaultBufferSize(1920,540);
        inputSurface=new Surface(input);
        input.setOnFrameAvailableListener(st -> {
            if(closed)return;
            try { current(pbuffer); input.updateTexImage(); input.getTransformMatrix(matrix); frameReady=true; frames++; drawAll(); }
            catch(RuntimeException e){ error.accept("Frame: "+e); }
        },gl);
    }
    void attach(int index,Surface surface,int width,int height) {
        gl.post(() -> {
            if(closed || display==null || program==0)return;
            try {
                detachNow(index); current(pbuffer);
                outputs[index]=EGL14.eglCreateWindowSurface(display,config,surface,new int[]{EGL14.EGL_NONE},0);
                require(outputs[index]!=EGL14.EGL_NO_SURFACE,"output surface");
                widths[index]=width;heights[index]=height;
                if(frameReady)drawAll();
            }catch(RuntimeException e){error.accept("Output "+index+": "+e);}
        });
    }
    void detach(int index) {
        CountDownLatch latch=new CountDownLatch(1);
        if(!gl.post(() -> {try{detachNow(index);}finally{latch.countDown();}})) return;
        try { if(!latch.await(3,TimeUnit.SECONDS)) error.accept("Output teardown timed out"); }
        catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    private void detachNow(int index) {
        if(outputs[index]!=null){current(pbuffer);EGL14.eglDestroySurface(display,outputs[index]);outputs[index]=null;}
    }
    void layout(boolean topBottom,Runnable ready) {
        gl.post(() -> {
            if(closed || input==null)return;
            vertical=topBottom;
            input.setDefaultBufferSize(vertical?960:1920,vertical?1080:540);
            frameReady=false;
            ready.run();
        });
    }
    private void drawAll() {
        for(int i=0;i<2;i++){
            if(outputs[i]==null || widths[i]<=0 || heights[i]<=0)continue;
            current(outputs[i]); GLES20.glViewport(0,0,widths[i],heights[i]);
            GLES20.glClearColor(.015f,.025f,.045f,1); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            // Both layouts give each output a 960x540 region. Letterbox, never stretch.
            float scale=Math.min(widths[i]/960f,heights[i]/540f);
            int w=Math.round(960*scale),h=Math.round(540*scale);
            GLES20.glViewport((widths[i]-w)/2,(heights[i]-h)/2,w,h);
            GLES20.glUseProgram(program);
            int pos=GLES20.glGetAttribLocation(program,"aPosition"),uv=GLES20.glGetAttribLocation(program,"aTex");
            vertices.position(0); GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,16,vertices);
            vertices.position(2); GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,vertices);
            GLES20.glEnableVertexAttribArray(pos);GLES20.glEnableVertexAttribArray(uv);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uMatrix"),1,false,matrix,0);
            GLES20.glUniform4f(GLES20.glGetUniformLocation(program,"uCrop"),vertical?0:i*.5f,vertical?(i==0?.5f:0):0,vertical?1:.5f,vertical?.5f:1);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTexture"),0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            require(GLES20.glGetError()==GLES20.GL_NO_ERROR,"GL draw");
            if(!EGL14.eglSwapBuffers(display,outputs[i])){error.accept("Swap failed on output "+i);detachNow(i);}
            else {synchronized(swaps){swaps[i]++;}}
        }
        current(pbuffer);
    }
    void close() {
        gl.post(() -> {
            closed=true;
            try {
                if(input!=null)input.setOnFrameAvailableListener(null);
                if(inputSurface!=null)inputSurface.release();
                if(display!=null && pbuffer!=null){current(pbuffer);detachNow(0);detachNow(1);
                    if(input!=null)input.release(); if(program!=0)GLES20.glDeleteProgram(program);
                    if(texture!=0)GLES20.glDeleteTextures(1,new int[]{texture},0);
                    EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                    EGL14.eglDestroySurface(display,pbuffer);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display);}
            }finally{thread.quitSafely();}
        });
    }
    private void current(EGLSurface surface){require(EGL14.eglMakeCurrent(display,surface,surface,context),"make current");}
    private static void require(boolean b,String what){if(!b)throw new IllegalStateException(what+" EGL="+Integer.toHexString(EGL14.eglGetError()));}
    private static int shader(int type,String source){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);require(ok[0]!=0,GLES20.glGetShaderInfoLog(s));return s;}
}
