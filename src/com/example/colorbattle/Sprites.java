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
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.content.res.Resources;
import java.io.IOException;
import java.io.InputStream;

/** Bitmap artwork for the map. Player order is walker, saucer, tank, wheel turret. */
public final class Sprites {
    private static final String[] TYPES={"walker","saucer","tank","wheel_turret"};
    private static final String[] COLORS={"blue","orange","red","pink"};
    private final Bitmap[] ground=new Bitmap[36];
    private final Bitmap[] floating=new Bitmap[8];
    private final Bitmap[][] units=new Bitmap[4][4];
    private final Bitmap[] fullSpots=new Bitmap[4],borderSpots=new Bitmap[4];
    private final Bitmap[] mines=new Bitmap[4];
    private final Bitmap[] flags=new Bitmap[4];
    private final Bitmap base,background,exclamation;
    private final Bitmap panel,button,contextBorder,wideButton;
    private final Bitmap wideFrame,tallFrame;
    private final Bitmap[] menuIcons=new Bitmap[6],playerSigns=new Bitmap[4],teamSigns=new Bitmap[4];
    private final Bitmap menuArrow,wrapArrow,hourglass;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint contourPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint buildingUnitFade=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint warningBackground=new Paint(Paint.ANTI_ALIAS_FLAG);

    public Sprites(AssetManager assets){
        wideFrame=trimTransparent(load(assets,"frame_wide.png"));tallFrame=trimTransparent(load(assets,"frame_tall.png"));
        wideButton=trimTransparent(load(assets,"button_wide.png"));contextBorder=load(assets,"context_border.png");panel=load(assets,"panel.png");button=load(assets,"button.png");
        String[] menuNames={"menu","help","walker","base","end","undo"};
        for(int i=0;i<6;i++)menuIcons[i]=load(assets,"menu_"+menuNames[i]+".png");
        for(int i=0;i<4;i++){playerSigns[i]=load(assets,"menu_"+TYPES[i]+".png");teamSigns[i]=load(assets,"team_"+TYPES[i]+".png");}
        menuArrow=load(assets,"menu_arrow.png");
        hourglass=load(assets,"menu_hourglass.png");
        // The edited sprite has transparent vertical padding; retain the original arrow viewport.
        Bitmap arrowSheet=load(assets,"menu_wrap_arrow_tail70.png");
        wrapArrow=Bitmap.createBitmap(arrowSheet,0,212,arrowSheet.getWidth(),217);
        for(int i=0;i<36;i++)ground[i]=load(assets,String.format(java.util.Locale.ROOT,"ground_%02d.png",i+1));
        for(int i=0;i<8;i++)floating[i]=load(assets,String.format(java.util.Locale.ROOT,"floating_island_%02d.png",i+1));
        for(int owner=0;owner<4;owner++){
            for(int color=0;color<4;color++)units[owner][color]=load(assets,"unit_"+TYPES[owner]+"_"+COLORS[color]+".png");
            flags[owner]=load(assets,"flag_"+TYPES[owner]+".png");
        }
        for(int color=0;color<4;color++){
            mines[color]=load(assets,"mine_"+COLORS[color]+".png");
            fullSpots[color]=load(assets,"spot_full_"+COLORS[color]+".png");
            borderSpots[color]=load(assets,"spot_border_"+COLORS[color]+".png");
        }
        base=load(assets,"base.png");
        background=load(assets,"background.png");
        exclamation=load(assets,"exclamation.png");
        warningBackground.setColor(0xffffffff);
        contourPaint.setColorFilter(new PorterDuffColorFilter(0xffffffff,PorterDuff.Mode.SRC_IN));
    }

    private Bitmap trimTransparent(Bitmap source){
        int left=source.getWidth(),top=source.getHeight(),right=-1,bottom=-1;
        int[] row=new int[source.getWidth()];
        for(int y=0;y<source.getHeight();y++){
            source.getPixels(row,0,row.length,0,y,row.length,1);
            for(int x=0;x<row.length;x++)if((row[x]>>>24)>32){
                left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
            }
        }
        return right<left?source:Bitmap.createBitmap(source,left,top,right-left+1,bottom-top+1);
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
    public Drawable buildingIcon(Resources resources,int kind,int color){return new BitmapDrawable(resources,kind==1?base:mines[color]);}
    public Drawable teamIcon(Resources resources,int owner){return new BitmapDrawable(resources,teamSigns[owner]);}

    public Drawable contextBackground(final Resources resources){
        return new Drawable(){
            final Paint framePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            @Override public void draw(Canvas canvas){
                Rect bounds=getBounds();float corner=16*resources.getDisplayMetrics().density;
                framePaint.setColor(0xff2e2922);
                canvas.drawRoundRect(new RectF(bounds),corner,corner,framePaint);
                // Nine slices preserve the stone corners and keep the rim thin at every hint height.
                int[] sx={237,347,1190,1300},sy={30,140,884,994};
                float[] dx={bounds.left,bounds.left+corner,bounds.right-corner,bounds.right};
                float[] dy={bounds.top,bounds.top+corner,bounds.bottom-corner,bounds.bottom};
                for(int row=0;row<3;row++)for(int col=0;col<3;col++){
                    if(row==1&&col==1)continue;
                    canvas.drawBitmap(contextBorder,new Rect(sx[col],sy[row],sx[col+1],sy[row+1]),
                        new RectF(dx[col],dy[row],dx[col+1],dy[row+1]),framePaint);
                }
            }
            @Override public void setAlpha(int alpha){framePaint.setAlpha(alpha);}
            @Override public void setColorFilter(android.graphics.ColorFilter filter){framePaint.setColorFilter(filter);}
            @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
        };
    }

    public Drawable menuBackground(final Resources resources){
        return new Drawable(){
            final Paint framePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            @Override public void draw(Canvas canvas){
                Rect bounds=getBounds();
                Bitmap frame=bounds.width()>=bounds.height()?wideFrame:tallFrame;
                int cut=Math.round(Math.min(frame.getWidth(),frame.getHeight())*.18f);
                float corner=Math.min(16*resources.getDisplayMetrics().density,Math.min(bounds.width(),bounds.height())*.25f);
                framePaint.setColor(0xff2e2922);
                canvas.drawRoundRect(new RectF(bounds),corner,corner,framePaint);
                int[] sx={0,cut,frame.getWidth()-cut,frame.getWidth()},sy={0,cut,frame.getHeight()-cut,frame.getHeight()};
                float[] dx={bounds.left,bounds.left+corner,bounds.right-corner,bounds.right};
                float[] dy={bounds.top,bounds.top+corner,bounds.bottom-corner,bounds.bottom};
                for(int row=0;row<3;row++)for(int col=0;col<3;col++){
                    if(row==1&&col==1)continue;
                    Rect source=new Rect(sx[col],sy[row],sx[col+1],sy[row+1]);
                    RectF target=new RectF(dx[col],dy[row],dx[col+1],dy[row+1]);
                    if(row!=1&&col!=1)canvas.drawBitmap(frame,source,target,framePaint);
                    else {
                        // Repeat native-proportion stone strips; clip the final tile instead of stretching it.
                        float scale=corner/cut;
                        float width=source.width()*scale,height=source.height()*scale;
                        canvas.save();canvas.clipRect(target);
                        if(col==1)for(float x=target.left;x<target.right;x+=width)
                            canvas.drawBitmap(frame,source,new RectF(x,target.top,x+width,target.bottom),framePaint);
                        else for(float y=target.top;y<target.bottom;y+=height)
                            canvas.drawBitmap(frame,source,new RectF(target.left,y,target.right,y+height),framePaint);
                        canvas.restore();
                    }
                }
            }
            @Override public void setAlpha(int alpha){framePaint.setAlpha(alpha);}
            @Override public void setColorFilter(android.graphics.ColorFilter filter){framePaint.setColorFilter(filter);}
            @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
        };
    }

    public Drawable wideButtonBackground(Resources resources){
        BitmapDrawable drawable=new BitmapDrawable(resources,wideButton);
        drawable.setGravity(android.view.Gravity.FILL);return drawable;
    }

    public Drawable buttonBackground(Resources resources){
        BitmapDrawable drawable=new BitmapDrawable(resources,button);
        drawable.setGravity(android.view.Gravity.FILL);return drawable;
    }

    public Drawable panelBackground(Resources resources){
        BitmapDrawable drawable=new BitmapDrawable(resources,panel);
        drawable.setGravity(android.view.Gravity.FILL);return drawable;
    }

    private void fit(Canvas canvas,Bitmap bitmap,float left,float top,float width,float height){
        float scale=Math.min(width/bitmap.getWidth(),height/bitmap.getHeight());
        float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
        draw(canvas,bitmap,left+(width-w)*.5f,top+(height-h)*.5f,left+(width+w)*.5f,top+(height+h)*.5f);
    }

    public void menuButton(Canvas canvas,int icon,int owner,float width,float height,boolean pressed){
        paint.setAlpha(pressed?185:255);
        draw(canvas,button,1.5f,2,width-1.5f,height-2);
        float size=Math.min(width,height)*.58f,x=(width-size)*.5f,y=(height-size)*.5f;
        if(icon==2||icon==3){
            fit(canvas,icon==2?playerSigns[owner]:menuIcons[3],x,y,size*.76f,size*.76f);
            fit(canvas,menuArrow,x+size*.44f+6f,y+size*.59f,size*.56f,size*.36f);
        }else fit(canvas,icon==4?hourglass:menuIcons[icon],x,y,size,size);
        paint.setAlpha(255);
    }

    public void resourceArrow(Canvas canvas,float centerX,float centerY,float width,float height){
        canvas.save();
        canvas.rotate(180f,centerX,centerY);
        fit(canvas,menuIcons[4],centerX-width*.5f,centerY-height*.5f,width,height);
        canvas.restore();
    }

    public void wrapArrow(Canvas canvas,float blueX,float pinkX,float top,float bottom){
        float margin=(pinkX-blueX)/30f;
        draw(canvas,wrapArrow,blueX-margin,top,pinkX+margin,bottom);
    }

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
        draw(canvas,ground[hash(seed,x,y)%36],left-1.5f,top-1.5f,left+tile+1.5f,top+tile+1.5f);
    }

    public boolean hasFloating(long seed,int x,int y){
        return hash(seed^0x4d3ca217L,x,y)%5==0;
    }

    public void floating(Canvas canvas,long seed,int x,int y,float left,float top,float tile){
        Bitmap bitmap=floating[hash(seed^0x6688bbddL,x,y)%8];
        draw(canvas,bitmap,left,top+tile*.73f,left+tile,top+tile*2.05f);
    }

    public void spot(Canvas canvas,int color,boolean occupied,boolean base,float left,float top,float tile){
        Bitmap bitmap=occupied?borderSpots[color]:fullSpots[color];
        float inset=base?tile*.00125f:tile*.025f;
        draw(canvas,bitmap,left+inset,top+inset,left+tile-inset,top+tile-inset);
    }

    public void building(Canvas canvas,int kind,int color,float left,float top,float tile){
        Bitmap bitmap=kind==1?base:mines[color];
        float inset=kind==1?tile*.035f:tile*.065f;
        draw(canvas,bitmap,left+inset,top+inset,left+tile-inset,top+tile-inset);
    }

    public void flag(Canvas canvas,int owner,float left,float top,float tile){
        Bitmap bitmap=flags[owner];
        float height=tile*.34f;
        float width=tile*(.68f/1.5f)*bitmap.getWidth()/bitmap.getHeight();
        draw(canvas,bitmap,left,top,left+width,top+height);
    }

    public void exclamation(Canvas canvas,float left,float top,float tile){
        float height=tile*.32f,width=height*exclamation.getWidth()/exclamation.getHeight();
        float x=left+tile*.81f-width*.5f+1f,y=top+tile*.035f+4f;
        float padding=tile*.025f,radius=tile*.06f;
        canvas.drawRoundRect(new RectF(x-padding,y-padding,x+width+padding,y+height+padding),
            radius,radius,warningBackground);
        draw(canvas,exclamation,x,y,x+width,y+height);
    }

    public void buildingUnitFade(Canvas canvas,float left,float top,float tile){
        float centerX=left+tile*.5f,centerY=top+tile*.5f,radius=tile*.5f;
        buildingUnitFade.setShader(new RadialGradient(centerX,centerY,radius,
            new int[]{0x80ffffff,0x80ffffff,0x00ffffff},
            new float[]{0f,.5f,1f},Shader.TileMode.CLAMP));
        canvas.save();
        canvas.clipRect(left,top,left+tile,top+tile);
        canvas.drawCircle(centerX,centerY,radius,buildingUnitFade);
        canvas.restore();
        buildingUnitFade.setShader(null);
    }

    public void unit(Canvas canvas,int owner,int color,float left,float top,float tile,boolean ready){
        float size=tile*.57f;
        Bitmap bitmap=units[owner][color];
        float width=size,height=size;
        if(owner==1){
            // Preserve the saucer's source proportions and its previous on-cell area.
            float aspect=(float)bitmap.getWidth()/bitmap.getHeight();
            width=size*(float)Math.sqrt(aspect);height=size/(float)Math.sqrt(aspect);
        }
        float x=left+(tile-width)*.5f,y=top+(tile-height)*.5f;
        if(ready){
            float radius=Math.max(1.5f,tile*.034f);
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)
                canvas.drawBitmap(bitmap,null,new RectF(x+dx*radius,y+dy*radius,x+width+dx*radius,y+height+dy*radius),contourPaint);
        }
        draw(canvas,bitmap,x,y,x+width,y+height);
    }
}
