package com.ruminate.vdisplay;

import android.app.*;
import android.content.*;
import android.hardware.display.*;
import android.os.*;
import android.graphics.Color;
import android.util.Log;
import android.view.*;
import android.widget.*;

/** Foreground POC controller. Android retains ownership of both physical displays. */
public final class HostActivity extends Activity implements DisplayManager.DisplayListener {
    private static final String TAG="VDisplayPOC";
    // @SystemApi on the matched AOSP base; omitted from the public SDK stubs.
    // Destroy VD tasks on release/owner death rather than moving them onto HDMI 1.
    private static final int DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private DisplayManager manager;
    private SceneRenderer renderer;
    private VirtualDisplay virtual;
    private Presentation secondary;
    private TextView status,secondaryStatus;
    private int secondaryId=-1;
    private boolean vertical,closing;
    private String lastError="";
    private final Runnable stats=new Runnable(){public void run(){
        if(closing)return;
        long a=0,b=0; if(renderer!=null)synchronized(renderer.swaps){a=renderer.swaps[0];b=renderer.swaps[1];}
        String s="HOST • "+(vertical?"TOP / BOTTOM":"LEFT / RIGHT")+" • virtual "+(virtual==null?"starting":virtual.getDisplay().getDisplayId())
                +" • source frames "+(renderer==null?0:renderer.frames)+" • output swaps "+a+" / "+b
                +(secondaryId<0?" • connect a second display":" • HDMI display "+secondaryId);
        status.setText(s+(lastError.isEmpty()?"":"\n"+lastError));
        if(secondaryStatus!=null)secondaryStatus.setText("HOST OUTPUT 2 • "+(vertical?"BOTTOM":"RIGHT")+" • "+s);
        if(renderer!=null && renderer.frames%150<35)Log.i(TAG,s);
        ui.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        manager=getSystemService(DisplayManager.class);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(8,15,28));
        status=label(this,"Starting virtual display…");root.addView(status,new LinearLayout.LayoutParams(-1,40));
        LinearLayout controls=new LinearLayout(this);
        button(controls,"Left / right",()->setLayout(false));button(controls,"Top / bottom",()->setLayout(true));
        button(controls,"Launch activity",this::launchScene);button(controls,"Stop",this::finishAndRemoveTask);
        root.addView(controls,new LinearLayout.LayoutParams(-1,88));
        renderer=new SceneRenderer(surface->ui.post(()->{
            if(closing)return;
            try {
                virtual=manager.createVirtualDisplay("Ruminate spanning scene",1920,540,160,surface,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DESTROY_CONTENT_ON_REMOVAL);
                if(virtual==null)throw new IllegalStateException("Virtual display creation returned null");
                Log.i(TAG,"CREATED virtual="+virtual.getDisplay().getDisplayId()+" owner="+android.os.Process.myUid());
                launchScene();
            }catch(RuntimeException e){error("Create virtual display: "+e);}
        }),this::error);
        root.addView(output(this,0),new LinearLayout.LayoutParams(-1,0,1));
        TextView foot=label(this,"HOST OUTPUT 1 • independent overlay • activity pixels below come through a real virtual display");root.addView(foot,new LinearLayout.LayoutParams(-1,40));
        setContentView(root);manager.registerDisplayListener(this,ui);updateSecondary();ui.post(stats);
    }
    private TextView label(Context c,String s){TextView t=new TextView(c);t.setTextColor(Color.WHITE);t.setTextSize(13);t.setPadding(12,7,12,7);t.setText(s);return t;}
    private void button(LinearLayout row,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setTextSize(12);b.setOnClickListener(v->action.run());row.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
    private SurfaceView output(Context c,int index){
        SurfaceView view=new SurfaceView(c);
        view.getHolder().addCallback(new SurfaceHolder.Callback(){
            public void surfaceCreated(SurfaceHolder holder){}
            public void surfaceChanged(SurfaceHolder holder,int format,int w,int h){renderer.attach(index,holder.getSurface(),w,h);}
            public void surfaceDestroyed(SurfaceHolder holder){renderer.detach(index);}
        });return view;
    }
    private void setLayout(boolean value){
        if(virtual==null || closing)return;
        vertical=value;
        renderer.layout(value,()->ui.post(()->{
            if(closing || virtual==null)return;
            try {virtual.resize(value?960:1920,value?1080:540,160);Log.i(TAG,"LAYOUT "+(value?"vertical 960x1080":"horizontal 1920x540"));}
            catch(RuntimeException e){error("Resize: "+e);}
        }));
    }
    private void launchScene(){
        if(virtual==null || closing)return;
        Intent intent=new Intent(this,SceneActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ActivityOptions options=ActivityOptions.makeBasic().setLaunchDisplayId(virtual.getDisplay().getDisplayId());
        try{startActivity(intent,options.toBundle());Log.i(TAG,"LAUNCH SceneActivity display="+virtual.getDisplay().getDisplayId());}
        catch(RuntimeException e){error("Activity launch: "+e);}
    }
    private void updateSecondary(){
        if(closing)return;
        int host=getDisplay().getDisplayId();Display selected=null;
        for(Display d:manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)){
            if(d.getDisplayId()!=host && (virtual==null || d.getDisplayId()!=virtual.getDisplay().getDisplayId())){selected=d;break;}
        }
        if(selected!=null && selected.getDisplayId()==secondaryId && secondary!=null)return;
        if(secondary!=null){secondary.dismiss();secondary=null;}secondaryStatus=null;secondaryId=-1;
        if(selected==null)return;
        final Display d=selected;
        secondary=new Presentation(this,d){@Override protected void onCreate(Bundle b){
            super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            LinearLayout root=new LinearLayout(getContext());root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(8,15,28));
            secondaryStatus=label(getContext(),"HOST OUTPUT 2");root.addView(secondaryStatus,new LinearLayout.LayoutParams(-1,128));
            root.addView(output(getContext(),1),new LinearLayout.LayoutParams(-1,0,1));
            root.addView(label(getContext(),"Independent host information • no Android primary-display replacement"),new LinearLayout.LayoutParams(-1,40));setContentView(root);
        }};
        try{secondary.show();secondaryId=d.getDisplayId();Log.i(TAG,"PRESENTATION display="+secondaryId);}
        catch(WindowManager.InvalidDisplayException e){secondary=null;error("Second display unavailable: "+e);}
    }
    private void error(String message){Log.e(TAG,message);ui.post(()->lastError=message);}
    @Override public void onDisplayAdded(int id){updateSecondary();}
    @Override public void onDisplayRemoved(int id){updateSecondary();}
    @Override public void onDisplayChanged(int id){updateSecondary();}
    @Override protected void onDestroy(){
        closing=true;ui.removeCallbacks(stats);manager.unregisterDisplayListener(this);
        if(virtual!=null){virtual.release();virtual=null;}
        if(secondary!=null){secondary.dismiss();secondary=null;}
        renderer.detach(0);renderer.detach(1);renderer.close();
        Log.i(TAG,"DESTROYED host and virtual display");super.onDestroy();
    }
}
