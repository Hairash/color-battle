package com.example.colorbattle;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.content.res.Resources;
import java.io.IOException;
import java.io.InputStream;

/** Bitmap artwork for the map. Player order is walker, saucer, tank, wheel turret. */
public final class Sprites {
    private static final String[] TYPES={"walker","saucer","tank","wheel_turret"};
    private static final String[] COLORS={"blue","orange","red","pink"};
    private final Bitmap[] ground=new Bitmap[16];
    private final Bitmap[] floating=new Bitmap[8];
    private final Bitmap[][] units=new Bitmap[4][4];
    private final Bitmap[][] spots=new Bitmap[4][5];
    private final Bitmap[] mines=new Bitmap[4];
    private final Bitmap[] flags=new Bitmap[4];
    private final Bitmap base,background;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint contourPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);

    public Sprites(AssetManager assets){
        for(int i=0;i<16;i++)ground[i]=load(assets,String.format(java.util.Locale.ROOT,"ground_%02d.png",i+1));
        for(int i=0;i<8;i++)floating[i]=load(assets,String.format(java.util.Locale.ROOT,"floating_island_%02d.png",i+1));
        for(int owner=0;owner<4;owner++){
            for(int color=0;color<4;color++)units[owner][color]=load(assets,"unit_"+TYPES[owner]+"_"+COLORS[color]+".png");
            flags[owner]=load(assets,"flag_"+TYPES[owner]+".png");
        }
        for(int color=0;color<4;color++){
            mines[color]=load(assets,"mine_"+COLORS[color]+".png");
            for(int i=0;i<5;i++)spots[color][i]=load(assets,String.format(java.util.Locale.ROOT,"spot_%s_%02d.png",COLORS[color],i+1));
        }
        base=load(assets,"base.png");
        background=load(assets,"background.png");
        contourPaint.setColorFilter(new PorterDuffColorFilter(0xffffffff,PorterDuff.Mode.SRC_IN));
    }

    private Bitmap load(AssetManager assets,String name){
        try(InputStream stream=assets.open("graphics/"+name)){
            BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
            Bitmap bitmap=BitmapFactory.decodeStream(stream,null,options);
            if(bitmap==null)throw new IOException("Cannot decode "+name);
            return bitmap;
        }catch(IOException error){throw new IllegalStateException("Missing game graphic: "+name,error);}
    }

    public Drawable unitIcon(Resources resources,int owner,int color){return new BitmapDrawable(resources,units[owner][color]);}

    public void background(Canvas canvas,int width,int height){
        float ratio=(float)width/height;
        int srcWidth=background.getWidth(),srcHeight=background.getHeight();
        if(ratio<1f)srcWidth=Math.max(1,Math.round(srcHeight*ratio));
        else srcHeight=Math.max(1,Math.round(srcWidth/ratio));
        Rect source=new Rect((background.getWidth()-srcWidth)/2,(background.getHeight()-srcHeight)/2,
            (background.getWidth()+srcWidth)/2,(background.getHeight()+srcHeight)/2);
        canvas.drawBitmap(background,source,new Rect(0,0,width,height),paint);
    }

    private void draw(Canvas canvas,Bitmap bitmap,float left,float top,float right,float bottom){
        canvas.drawBitmap(bitmap,null,new RectF(left,top,right,bottom),paint);
    }

    private int hash(long seed,int x,int y){
        int value=(int)seed^x*0x7feb352d^y*0x846ca68b;
        value^=value>>>16;value*=0x7feb352d;value^=value>>>15;
        return value&0x7fffffff;
    }

    public void ground(Canvas canvas,long seed,int x,int y,float left,float top,float tile){
        draw(canvas,ground[hash(seed,x,y)%16],left-.35f,top-.35f,left+tile+.35f,top+tile+.35f);
    }

    public void floating(Canvas canvas,long seed,int x,int y,float left,float top,float tile){
        Bitmap bitmap=floating[hash(seed^0x6688bbddL,x,y)%8];
        draw(canvas,bitmap,left,top+tile*.73f,left+tile,top+tile*2.05f);
    }

    public void spot(Canvas canvas,long seed,int x,int y,int color,float left,float top,float tile){
        Bitmap bitmap=spots[color][hash(seed^0x45319cdeL,x,y)%5];
        draw(canvas,bitmap,left+tile*.025f,top+tile*.025f,left+tile*.975f,top+tile*.975f);
    }

    public void building(Canvas canvas,int kind,int color,float left,float top,float tile){
        Bitmap bitmap=kind==1?base:mines[color];
        float inset=kind==1?tile*.035f:tile*.065f;
        draw(canvas,bitmap,left+inset,top+inset,left+tile-inset,top+tile-inset);
    }

    public void flag(Canvas canvas,int owner,float left,float top,float tile){
        draw(canvas,flags[owner],left+tile*.48f,top,left+tile,top+tile*.52f);
    }

    public void unit(Canvas canvas,int owner,int color,float left,float top,float tile,boolean ready){
        float size=tile*.57f;
        float x=left+(tile-size)*.5f,y=top+(tile-size)*.5f;
        Bitmap bitmap=units[owner][color];
        if(ready){
            float radius=Math.max(1.5f,tile*.034f);
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)
                canvas.drawBitmap(bitmap,null,new RectF(x+dx*radius,y+dy*radius,x+size+dx*radius,y+size+dy*radius),contourPaint);
        }
        draw(canvas,bitmap,x,y,x+size,y+size);
    }
}
