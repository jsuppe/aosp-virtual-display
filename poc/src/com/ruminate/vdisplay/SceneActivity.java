package com.ruminate.vdisplay;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.graphics.*;
import android.view.*;
import android.content.Context;
import android.util.Log;

/** A real WindowManager activity; it knows nothing about either physical output. */
public final class SceneActivity extends Activity {
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        Log.i("VDisplayPOC","SCENE created on display="+getDisplay().getDisplayId());
        setContentView(new Scene(this));
    }
    private static final class Scene extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);long frame;
        Scene(Context c){super(c);}
        @Override protected void onDraw(Canvas c){
            int w=getWidth(),h=getHeight();boolean vertical=h>w;frame++;
            c.drawColor(Color.rgb(13,35,61));
            p.setColor(Color.rgb(48,40,78));c.drawRect(vertical?0:w/2f,vertical?h/2f:0,w,h,p);
            p.setStrokeWidth(2);p.setColor(Color.rgb(47,77,101));
            for(int x=0;x<w;x+=90)c.drawLine(x,0,x,h,p);
            for(int y=0;y<h;y+=90)c.drawLine(0,y,w,y,p);
            p.setColor(Color.WHITE);p.setTextSize(Math.max(22,Math.min(w,h)*.055f));p.setTypeface(Typeface.DEFAULT_BOLD);
            String first=vertical?"TOP HALF":"LEFT HALF",second=vertical?"BOTTOM HALF":"RIGHT HALF";
            c.drawText(first,30,65,p);c.drawText(second,vertical?30:w/2f+30,vertical?h/2f+65:65,p);
            p.setTextSize(22);p.setTypeface(Typeface.MONOSPACE);
            String stamp="Activity display "+getDisplay().getDisplayId()+"  frame "+frame;
            c.drawText(stamp,30,105,p);c.drawText(stamp,vertical?30:w/2f+30,vertical?h/2f+105:105,p);
            p.setColor(Color.CYAN);p.setStrokeWidth(6);
            if(vertical)c.drawLine(w/2f,150,w/2f,h-50,p);else c.drawLine(50,h/2f,w-50,h/2f,p);
            float phase=(SystemClock.uptimeMillis()%6000)/6000f;
            p.setColor(Color.rgb(255,194,61));
            c.drawCircle(vertical?w/2f:50+phase*(w-100),vertical?150+phase*(h-200):h/2f,38,p);
            p.setColor(Color.WHITE);p.setTextSize(20);
            c.drawText(w+" x "+h+" logical canvas • one Android activity",30,h-25,p);
            postInvalidateDelayed(33);
        }
    }
}
