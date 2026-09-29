package com.example.colorbattle;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Resolution-independent geometric artwork shared by board, resource bar and menus. */
public final class Art {
    public static final int[] COLORS={0xff42a5ff,0xffffa442,0xffff535d,0xffff83cd};
    private static final int INK=0xff102331,STONE=0xffa8bfc2,LIGHT=0xffe2eee8;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private void fill(int color){paint.setStyle(Paint.Style.FILL);paint.setColor(color);}
    public void rect(Canvas c,float x,float y,float w,float h,int color){fill(color);c.drawRect(x,y,x+w,y+h,paint);}
    public void circle(Canvas c,float x,float y,float r,int color){fill(color);c.drawCircle(x,y,r,paint);}
    public void line(Canvas c,float x,float y,float xx,float yy,float width,int color){fill(color);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,xx,yy,paint);}
    public void shape(Canvas c,int owner,float x,float y,float r,int color){fill(color);Path path=new Path();
        if(owner==0)c.drawCircle(x,y,r,paint);
        else if(owner==1)c.drawRoundRect(new RectF(x-r,y-r,x+r,y+r),r*.12f,r*.12f,paint);
        else {int n=owner==2?3:10;for(int i=0;i<n;i++){double a=-Math.PI/2+i*2*Math.PI/n;float rr=owner==3&&i%2==1?r*.48f:r;float px=x+(float)Math.cos(a)*rr,py=y+(float)Math.sin(a)*rr;if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}path.close();c.drawPath(path,paint);}
    }
    public void unit(Canvas c,int owner,float x,float y,float radius,int border,int body){
        float outer=owner<2?radius*.8f:radius*1.1f;
        float inner=owner<2?outer-(radius*.24f)/2f:outer*.76f;
        shape(c,owner,x,y,outer,border);
        shape(c,owner,x,y,inner,body);
    }
    public void gem(Canvas c,float x,float y,float r,int color){
        Path path=new Path();path.moveTo(x,y-r);path.lineTo(x+r*.8f,y-r*.25f);path.lineTo(x+r*.65f,y+r*.6f);path.lineTo(x,y+r);path.lineTo(x-r*.65f,y+r*.6f);path.lineTo(x-r*.8f,y-r*.25f);path.close();fill(color);c.drawPath(path,paint);
        line(c,x,y-r*.7f,x-r*.42f,y-r*.12f,r*.13f,0x99ffffff);line(c,x,y-r*.5f,x,y+r*.6f,r*.08f,0x550d2235);
    }
    // Coordinates below use a normalized 100 × 100 tile.
    public void base(Canvas c){
        fill(0x44000000);c.drawOval(new RectF(9,75,93,95),paint);
        rect(c,20,39,62,44,INK);rect(c,23,42,56,37,STONE);
        rect(c,13,30,23,53,STONE);rect(c,66,30,23,53,STONE);
        for(int x:new int[]{13,28,66,81})rect(c,x,22,8,15,LIGHT);
        rect(c,13,39,23,5,LIGHT);rect(c,66,39,23,5,LIGHT);
        rect(c,23,52,5,12,INK);rect(c,74,52,5,12,INK);
        fill(INK);c.drawRoundRect(new RectF(40,55,62,91),11,11,paint);
        rect(c,40,76,22,10,INK);line(c,44,84,37,94,3,STONE);line(c,58,84,65,94,3,STONE);
    }
    public void mine(Canvas c,int color){
        Path roof=new Path();roof.moveTo(7,84);roof.lineTo(13,51);roof.quadTo(20,27,38,25);roof.quadTo(61,16,78,38);roof.lineTo(92,84);roof.close();
        fill(color);c.drawPath(roof,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5);paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(INK);c.drawPath(roof,paint);paint.setStyle(Paint.Style.FILL);
        // A dark tunnel opening stays legible against each resource color.
        fill(INK);c.drawRoundRect(new RectF(28,43,72,91),22,22,paint);rect(c,28,70,44,20,INK);
        line(c,27,43,73,43,5,STONE);line(c,28,45,24,83,5,STONE);line(c,72,45,76,83,5,STONE);
        line(c,11,83,25,83,3,INK);line(c,75,83,91,83,3,INK);
        line(c,43,71,30,96,3,STONE);line(c,58,71,70,96,3,STONE);line(c,37,83,63,83,3,STONE);line(c,33,91,67,91,3,STONE);
        mineGem(c,83,75,8,color);
        line(c,17,57,30,33,3,STONE);fill(STONE);Path pick=new Path();pick.moveTo(13,34);pick.quadTo(26,24,39,37);pick.quadTo(26,31,13,34);c.drawPath(pick,paint);
    }
    private void mineGem(Canvas c,float x,float y,float r,int color){
        Path crystal=new Path();crystal.moveTo(x,y-r);crystal.lineTo(x+r*.8f,y-r*.2f);crystal.lineTo(x+r*.55f,y+r*.65f);crystal.lineTo(x,y+r);crystal.lineTo(x-r*.7f,y+r*.5f);crystal.lineTo(x-r*.8f,y-r*.2f);crystal.close();
        fill(color);c.drawPath(crystal,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(INK);c.drawPath(crystal,paint);paint.setStyle(Paint.Style.FILL);
        line(c,x,y-r*.65f,x-r*.3f,y-r*.1f,1.5f,0xff66747a);
    }
    public void flag(Canvas c,int owner,boolean mine){
        int ground=mine?STONE:LIGHT;
        line(c,80,8,80,39,2,ground);rect(c,56,6,25,25,INK);rect(c,58,8,21,21,ground);shape(c,owner,68.5f,18.5f,8,INK);
    }
    public Drawable icon(final int color,final int owner,final int pixels){return new Drawable(){
        public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/100f,b.height()/100f);if(owner<0)gem(c,50,50,38,color);else unit(c,owner,50,50,43,LIGHT,color);c.restore();}
        public void setAlpha(int alpha){} public void setColorFilter(ColorFilter filter){} public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        public int getIntrinsicWidth(){return pixels;}public int getIntrinsicHeight(){return pixels;}
    };}
    public Drawable controlIcon(final int type){return new Drawable(){
        public void draw(Canvas c){Rect b=getBounds();float w=b.width(),h=b.height(),corner=Math.min(w,h)*.2f;
            fill(0xff1e3d49);c.drawRoundRect(new RectF(b.left+2,b.top+2,b.right-2,b.bottom-2),corner,corner,paint);
            fill(0xff294f58);c.drawRoundRect(new RectF(b.left+5,b.top+5,b.right-5,b.bottom-5),corner*.82f,corner*.82f,paint);
            float size=Math.min(w,h)*.9f;
            c.save();c.translate(b.left+(w-size)/2f,b.top+(h-size)/2f);c.scale(size/100f,size/100f);
            if(type==0){line(c,50,28,50,72,7,LIGHT);line(c,28,50,72,50,7,LIGHT);}
            else if(type==1){fill(LIGHT);paint.setTextSize(57);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);c.drawText("?",50,69,paint);}
            else if(type==2){unit(c,0,39,48,24,LIGHT,0xff42a5ff);arrow(c,58,46);}
            else if(type==3){c.save();c.translate(13,13);c.scale(.64f,.64f);base(c);c.restore();arrow(c,65,48);}
            else if(type==4){line(c,30,49,65,49,7,LIGHT);Path head=new Path();head.moveTo(55,32);head.lineTo(73,49);head.lineTo(55,66);head.close();fill(LIGHT);c.drawPath(head,paint);}
            else if(type==5){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(7);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(LIGHT);
                c.drawArc(new RectF(25,25,75,75),-55,255,false,paint);paint.setStyle(Paint.Style.FILL);
                Path head=new Path();head.moveTo(21,29);head.lineTo(20,54);head.lineTo(43,42);head.close();fill(LIGHT);
                c.save();c.rotate(45,28,42);c.drawPath(head,paint);c.restore();
            }
            c.restore();}
        public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    };}
    private void arrow(Canvas c,float x,float y){line(c,x-9,y+21,x+13,y+21,5,LIGHT);Path head=new Path();head.moveTo(x+5,y+12);head.lineTo(x+18,y+21);head.lineTo(x+5,y+30);head.close();fill(LIGHT);c.drawPath(head,paint);}
}
