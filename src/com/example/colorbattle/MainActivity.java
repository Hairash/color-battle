package com.example.colorbattle;

import android.app.*;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.*;
import android.widget.*;
import android.util.AtomicFile;
import java.io.*;
import java.util.ArrayDeque;
import java.util.Locale;

public final class MainActivity extends Activity {
    abstract static class Action implements Runnable, View.OnClickListener, DialogInterface.OnClickListener {
        public abstract void run();
        public void onClick(View v){run();}
        public void onClick(DialogInterface d,int n){run();}
    }
    abstract static class Choice implements DialogInterface.OnClickListener {
        public abstract void choose(int n);
        public void onClick(DialogInterface d,int n){choose(n);}
    }
    Game game; Game.Unit selected; Board board; ResourceBar resourceBar; TextView status, hint, unitInfo; Sprites sprites;
    final Handler botHandler=new Handler();
    boolean botScheduled;
    int baseCursor=-1;
    static final class UndoEntry {
        final byte[] state;
        final int kind,x,y;
        UndoEntry(byte[] state,int kind,int x,int y){this.state=state;this.kind=kind;this.x=x;this.y=y;}
    }
    final ArrayDeque<UndoEntry> undoHistory=new ArrayDeque<>();
    final Art art=new Art();
    String message="Tap your flagged base to recruit. Drag to explore.";
    final int[] colors={0xff42a5ff,0xffffa442,0xffff535d,0xffff83cd};
    AtomicFile saveFile(){return new AtomicFile(new File(getFilesDir(),"battle.save"));}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        sprites=new Sprites(getAssets());
        try(ObjectInputStream in=new ObjectInputStream(saveFile().openRead())){game=(Game)in.readObject();game.upgradeSave();}catch(Exception e){game=null;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xff101e2b);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets i){v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();}});
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(17);status.setPadding(16,12,16,6);root.addView(status);
        resourceBar=new ResourceBar();root.addView(resourceBar,new LinearLayout.LayoutParams(-1,dp(72)));
        hint=new TextView(this);hint.setTextColor(0xffb8ccdc);hint.setTextSize(13);hint.setPadding(16,0,16,8);root.addView(hint);
        unitInfo=new TextView(this);unitInfo.setTextColor(Color.WHITE);unitInfo.setTextSize(13);unitInfo.setGravity(Gravity.CENTER_VERTICAL);
        unitInfo.setPadding(dp(12),0,dp(12),0);unitInfo.setBackgroundColor(0xff101e2b);
        root.addView(unitInfo,new LinearLayout.LayoutParams(-1,dp(36)));
        board=new Board();root.addView(board,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(this);root.addView(actions);
        button(actions,0,"New game",new Action(){public void run(){newGame();}});
        button(actions,1,"Help",new Action(){public void run(){help();}});
        button(actions,2,"Next unmoved unit",new Action(){public void run(){nextUnit();}});
        button(actions,3,"Next owned base",new Action(){public void run(){nextBase();}});
        button(actions,5,"Undo last action",new Action(){public void run(){undoLastAction();}});
        button(actions,4,"End turn",new Action(){public void run(){endHumanTurn();}});
        setContentView(root);refresh();if(game==null)newGame();else board.post(new Action(){public void run(){board.home();scheduleBot();}});
    }
    void button(LinearLayout row,int icon,String title,final Action action){Button b=new Button(this);b.setText("");b.setBackground(art.controlIcon(icon));b.setContentDescription(title);b.setOnClickListener(action);row.addView(b,new LinearLayout.LayoutParams(0,dp(54),1));}
    void newGame(){new AlertDialog.Builder(MainActivity.this).setTitle(game==null?"Color Battle · Players":"Replace saved game · Players").setItems(new String[]{"2 players","3 players","4 players"},new Choice(){public void choose(int n){chooseBots(n+2);}}).setNegativeButton("Cancel",null).show();}
    void chooseBots(final int players){String[] choices=new String[players];choices[0]="All human";for(int n=1;n<players;n++)choices[n]=n+" bot"+(n==1?"":"s")+" · "+(players-n)+" human"+(players-n==1?"":"s");
        new AlertDialog.Builder(this).setTitle("Choose opponents").setItems(choices,new Choice(){public void choose(int bots){game=new Game(players,System.currentTimeMillis(),bots);selected=null;baseCursor=-1;undoHistory.clear();message="Tap your fortress to recruit. Drag to explore; pinch to zoom.";refresh();board.home();}}).setNegativeButton("Cancel",null).show();}
    void nextUnit(){if(game==null||game.winner>=0||game.isBot(game.current))return;int start=selected==null?-1:game.units.indexOf(selected);for(int step=1;step<=game.units.size();step++){Game.Unit candidate=game.units.get((start+step)%game.units.size());if(game.canMove(candidate)){selected=candidate;board.center(candidate.x,candidate.y);message="Unit ready to move.";refresh();return;}}selected=null;message="No units left to move.";refresh();}
    void nextBase(){if(game==null||game.winner>=0||game.isBot(game.current))return;int total=game.size*game.size;for(int step=1;step<=total;step++){int index=(baseCursor+step)%total,x=index%game.size,y=index/game.size;Game.Cell cell=game.cells[y][x];if(cell.building==1&&cell.owner==game.current){baseCursor=index;selected=null;board.center(x,y);message="Fortress · tap to recruit.";refresh();return;}}message="No owned fortresses.";refresh();}
    void endHumanTurn(){if(game==null||game.winner>=0||game.isBot(game.current))return;
        new AlertDialog.Builder(this).setTitle(Game.SHAPES[game.current]+" · Finish turn?").setMessage("Units on buildings will capture them.").setNegativeButton("Back",null).setPositiveButton("End turn",new Action(){public void run(){recordAction();game.endTurn();selected=null;baseCursor=-1;message="Income and base upkeep applied.";refresh();if(game.winner>=0)return;if(game.isBot(game.current)){scheduleBot();return;}showHumanTurn();}}).show();}
    void showHumanTurn(){if(game==null||game.winner>=0)return;new AlertDialog.Builder(this).setTitle("Pass to "+Game.SHAPES[game.current]).setMessage(Game.SHAPES[game.current]+" units · Turn "+game.turn).setPositiveButton("Ready",null).setCancelable(false).show();}
    boolean humanAlive(){if(game==null)return false;for(int p=0;p<game.players;p++)if(!game.isBot(p)&&game.alive(p))return true;return false;}
    void scheduleBot(){if(botScheduled||game==null||game.winner>=0||!game.isBot(game.current))return;
        if(!humanAlive()){message="All human players were eliminated. Start a new game.";refresh();return;}
        botScheduled=true;final Game active=game;
        botHandler.postDelayed(new Runnable(){public void run(){if(game!=active)return;botScheduled=false;if(game.winner>=0||!game.isBot(game.current))return;int player=game.current;Bot.playTurn(game);selected=null;baseCursor=-1;message=Game.SHAPES[player]+" bot finished its turn.";refresh();if(game.winner>=0)return;if(game.isBot(game.current))scheduleBot();else showHumanTurn();}},350);}
    void help(){new AlertDialog.Builder(MainActivity.this).setTitle("How to play").setMessage("Players are Circle, Square, Triangle, and Star. Building flags show the owner's robot sign. Crystals show resources; left arrows between them show which color wins, and the curved top arrow shows Blue killing Pink. Colored robot images in the recruitment menu show units.\n\nTap your fortress to recruit. The first unit of a color costs 5 matching crystals; each living unit you own of that color adds 1 to the next price. Each unit can make ONE move of up to 3 cells per turn. Unused distance is lost. Firing prevents later movement. You can move and then fire. New units can act immediately.\n\nA white contour follows a unit that can still move; spent and enemy units have no contour. A green frame marks your selection without covering the tile. Green dots and outlines mark reachable cells, including buildings. Tap a highlighted enemy to fire. Tapping another owned unit selects it unless your selected unit has moved and can shoot it. Tapping an unreachable owned fortress opens recruitment; other invalid destinations clear selection.\n\nOrange kills Blue → Pink → Red → Orange. Shots travel straight up to 3 cells; water and units block them. You can shoot your own units if the colors match the kill rule. Shootable friendly units show !; long press one to request a confirmed friendly shot. Invalid attacks do nothing. A kill stains the tile, blocking units of the killer's color.\n\nEnd your turn on a fortress or mine to capture it. Each turn: +2 of every resource, +2 per matching mine, then −1 of every resource per base. Each color always ends up at least 1 higher than it was at the end of your previous turn. Each player starts with 5 of each before their first income.\n\nLast player with any unit or building wins. New game lets you choose human and bot players. Bottom icons: plus = new game, question mark = help, unit arrow = next unmoved unit, fortress arrow = next owned base, curved arrow = undo the last action, right arrow = end turn. Each tap of Undo restores one purchase, move, shot, or End Turn, including bot responses to that End Turn. The turn number advances after all players have had their turn. Drag or pinch the map. Tap your selected unit again or long press to deselect. Progress saves automatically.").setPositiveButton("Play",null).show();}
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    float dp(float n){return n*getResources().getDisplayMetrics().density;}
    CharSequence iconText(int color,int owner,String suffix){
        SpannableStringBuilder text=new SpannableStringBuilder("  "+suffix);
        Drawable icon=owner<0?art.icon(colors[color],owner,dp(26)):sprites.unitIcon(getResources(),owner,color);icon.setBounds(0,0,dp(26),dp(26));
        text.setSpan(new ImageSpan(icon,ImageSpan.ALIGN_BOTTOM),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return text;
    }
    void save(){if(game==null)return;FileOutputStream out=null;try{out=saveFile().startWrite();ObjectOutputStream o=new ObjectOutputStream(out);o.writeObject(game);o.flush();saveFile().finishWrite(out);}catch(Exception e){if(out!=null)saveFile().failWrite(out);message="Could not save progress.";}}
    byte[] snapshot(){if(game==null)return null;try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();ObjectOutputStream out=new ObjectOutputStream(bytes);out.writeObject(game);out.close();return bytes.toByteArray();}catch(Exception e){return null;}}
    void recordAction(){recordAction(0,-1,-1);}
    void recordAction(int kind,int x,int y){byte[] state=snapshot();if(state!=null){undoHistory.addLast(new UndoEntry(state,kind,x,y));if(undoHistory.size()>40)undoHistory.removeFirst();}}
    void undoLastAction(){if(game==null||undoHistory.isEmpty()){message="No action to undo.";refresh();return;}
        try{UndoEntry entry=undoHistory.peekLast();ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(entry.state));Game restored=(Game)in.readObject();in.close();restored.upgradeSave();
            undoHistory.removeLast();game=restored;botScheduled=false;selected=entry.kind==1?game.at(entry.x,entry.y):null;baseCursor=-1;
            message=entry.kind==1||entry.kind==2?cellInfo(entry.x,entry.y):"Last action undone.";
            refresh();if(entry.kind==1||entry.kind==2)board.center(entry.x,entry.y);
        }catch(Exception e){message="Could not undo action.";refresh();}
    }
    @Override public void onPause(){super.onPause();save();}
    void refresh(){if(game==null){status.setText("COLOR BATTLE");hint.setText("Start a new local game for 2–4 players.");unitInfo.setText("");return;}save();
        status.setText(game.winner>=0?Game.SHAPES[game.winner]+" wins!":Game.SHAPES[game.current]+(game.isBot(game.current)?" bot":"")+" · Turn "+game.turn);
        resourceBar.invalidate();
        hint.setText(message);
        unitInfo.setText(selected==null?"":iconText(selected.color,selected.owner,(game.canMove(selected)?"Move ready · up to "+game.rules.movement+" cells":"Move unavailable")+" · "+(selected.fired?"Shot used":"Shot ready")));
        board.invalidate();}
    String cellInfo(int x,int y){
        if(game==null||!game.inside(x,y))return "Outside map";
        Game.Cell cell=game.cells[y][x];
        if(!cell.land)return "Water";
        String info=cell.building==1?"Fortress":cell.building==2?Game.COLORS[cell.mine]+" mine":"Land";
        if(cell.owner>=0&&cell.building!=0)info+=" • "+Game.SHAPES[cell.owner];
        if(cell.stain>=0)info+=" • "+Game.COLORS[cell.stain].toLowerCase(Locale.ROOT);
        Game.Unit unit=game.at(x,y);
        if(unit!=null)info+=" • "+Game.SHAPES[unit.owner]+" "+Game.COLORS[unit.color].toLowerCase(Locale.ROOT)+" unit";
        return info;
    }
    void tap(int x,int y){if(game==null||game.winner>=0||game.isBot(game.current))return;
        if(!game.inside(x,y)){selected=null;message=cellInfo(x,y);refresh();return;}
        Game.Unit u=game.at(x,y);Game.Cell c=game.cells[y][x];
        if(selected!=null){
            if(u!=null&&u!=selected&&u.owner==game.current&&selected.moves==0&&game.canAttack(selected,u)){
                recordAction();game.attack(selected,u);message=cellInfo(x,y);
            }else if(u!=null&&u!=selected&&u.owner==game.current){
                selected=u;message=cellInfo(x,y);
            }else if(u!=null&&u!=selected&&game.canAttack(selected,u)){
                recordAction();game.attack(selected,u);message=cellInfo(x,y);
            }else if(u==null&&game.canMove(selected)&&game.distances(selected)[y][x]>0){
                recordAction(1,selected.x,selected.y);game.move(selected,x,y);message=cellInfo(x,y);
            }else if(u==null&&c.building==1&&c.owner==game.current){
                selected=null;message=cellInfo(x,y);refresh();recruit(x,y);return;
            }else{selected=null;message=cellInfo(x,y);}
        }
        else if(u!=null&&u.owner==game.current){selected=u;message=cellInfo(x,y);}
        else if(c.building==1&&c.owner==game.current){message=cellInfo(x,y);refresh();recruit(x,y);return;}
        else {message=cellInfo(x,y);}
        refresh();
    }
    void confirmFriendlyShot(final Game.Unit shooter,final Game.Unit target){
        new AlertDialog.Builder(this).setTitle("! Shoot your own unit?")
            .setMessage("This will destroy your "+Game.COLORS[target.color]+" unit and stain its tile "+Game.COLORS[shooter.color]+".")
            .setNegativeButton("Keep unit",null)
            .setPositiveButton("Shoot",new Action(){public void run(){if(game.canAttack(shooter,target)){recordAction();game.attack(shooter,target);message=cellInfo(target.x,target.y);}refresh();}}).show();
    }
    void recruit(final int x,final int y){
        LinearLayout choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);choices.setPadding(dp(16),dp(8),dp(16),dp(8));
        final AlertDialog dialog=new AlertDialog.Builder(MainActivity.this).setTitle("Recruit · "+Game.SHAPES[game.current]).setView(choices).setNegativeButton("Cancel",null).create();
        for(int i=0;i<4;i++){final int color=i;int cost=game.unitCost(game.current,i);Button choice=new Button(this);choice.setAllCaps(false);choice.setTextSize(18);choice.setGravity(Gravity.CENTER);
            choice.setText(android.text.TextUtils.concat(iconText(i,game.current,"    "),iconText(i,-1,""+cost+"    /    "+game.resources[game.current][i])));
            choice.setContentDescription("Recruit "+Game.COLORS[i]+" "+Game.SHAPES[game.current]+", costs "+cost+", available "+game.resources[game.current][i]);
            choice.setEnabled(game.resources[game.current][i]>=cost&&game.cells[y][x].stain!=i&&game.at(x,y)==null);
            choice.setOnClickListener(new Action(){public void run(){recordAction(2,x,y);message=game.buy(x,y,color);selected=game.at(x,y);dialog.dismiss();refresh();}});
            choices.addView(choice,new LinearLayout.LayoutParams(-1,dp(60)));
        }
        TextView legend=new TextView(this);legend.setText("Unit     ·     Cost / Available");legend.setGravity(Gravity.CENTER);choices.addView(legend);dialog.show();
    }
    final class ResourceBar extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        ResourceBar(){super(MainActivity.this);setContentDescription("Orange kills Blue; Red kills Orange; Pink kills Red; Blue kills Pink");}
        void line(Canvas canvas,float x,float y,float xx,float yy){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(0xff9ccbc1);paint.setStrokeCap(Paint.Cap.ROUND);canvas.drawLine(x,y,xx,yy,paint);paint.setStyle(Paint.Style.FILL);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(game==null)return;
            float w=getWidth(),gap=w/4f,top=dp(7),middle=dp(43)+10f;
            // Each left arrow reads "left crystal is killed by right crystal".
            for(int i=1;i<4;i++){float x=gap*i;line(canvas,x+dp(7),middle,x-dp(8),middle);line(canvas,x-dp(8),middle,x-dp(2),middle-dp(5));line(canvas,x-dp(8),middle,x-dp(2),middle+dp(5));}
            // The curved arrow wraps Blue around to Pink and closes the color cycle.
            float blue=gap*.5f,pink=gap*3.5f;
            Path wrap=new Path();wrap.moveTo(blue,dp(31));wrap.cubicTo(blue,top,pink,top,pink,dp(31));
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(0xff9ccbc1);paint.setStrokeCap(Paint.Cap.ROUND);canvas.drawPath(wrap,paint);paint.setStyle(Paint.Style.FILL);
            // Rotate only this arrowhead 45 degrees counterclockwise.
            line(canvas,pink,dp(31),pink-dp(8.5f),dp(29.5f));
            line(canvas,pink,dp(31),pink-dp(1.5f),dp(22.5f));
            for(int i=0;i<4;i++){
                float center=gap*(i+.5f),y=dp(49);art.gem(canvas,center-dp(15),y,dp(11),colors[i]);
                paint.setColor(Color.WHITE);paint.setTextSize(dp(18));paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(""+game.resources[game.current][i],center+dp(1),y+dp(6),paint);
            }
        }
    }
    final class Board extends View {
        Paint p=new Paint(3);float tile=64,ox=0,oy=0,lastX,lastY,downX,downY;boolean dragged,pinchGesture;ScaleGestureDetector scale;
        Board(){super(MainActivity.this);tile=44*getResources().getDisplayMetrics().density;scale=new ScaleGestureDetector(MainActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){float old=tile;tile=Math.max(18*getResources().getDisplayMetrics().density,Math.min(96*getResources().getDisplayMetrics().density,tile*d.getScaleFactor()));ox=d.getFocusX()-(d.getFocusX()-ox)*tile/old;oy=d.getFocusY()-(d.getFocusY()-oy)*tile/old;dragged=true;invalidate();return true;}});setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){
            if(game!=null&&selected!=null){int x=(int)Math.floor((downX-ox)/tile),y=(int)Math.floor((downY-oy)/tile);
                if(game.inside(x,y)){Game.Unit target=game.at(x,y);
                    if(target!=null&&target!=selected&&target.owner==game.current&&game.canAttack(selected,target)){
                        confirmFriendlyShot(selected,target);return true;
                    }
                }
            }
            int x=(int)Math.floor((downX-ox)/tile),y=(int)Math.floor((downY-oy)/tile);
            selected=null;message=cellInfo(x,y);refresh();return true;
        }});}
        void home(){if(game==null)return;for(int y=0;y<game.size;y++)for(int x=0;x<game.size;x++)if(game.cells[y][x].owner==game.current){center(x,y);return;}for(Game.Unit u:game.units)if(u.owner==game.current){center(u.x,u.y);return;}}
        void center(int x,int y){ox=getWidth()/2f-(x+.5f)*tile;oy=getHeight()/2f-(y+.5f)*tile;invalidate();}
        void box(Canvas c,float x,float y,float w,float h,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRect(x,y,x+w,y+h,p);}
        void label(Canvas c,String s,float x,float y,float size,int color){p.setColor(color);p.setTextSize(size);p.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y,p);}
        void outline(Canvas c,float left,float top,int color,float inset){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,tile*.035f));p.setColor(color);c.drawRoundRect(new RectF(left+tile*inset,top+tile*inset,left+tile*(1-inset),top+tile*(1-inset)),tile*.10f,tile*.10f,p);p.setStyle(Paint.Style.FILL);}
        @Override protected void onDraw(Canvas canvas){sprites.background(canvas,getWidth(),getHeight());if(game==null)return;int[][] reach=selected==null?null:game.distances(selected);
            for(int y=0;y<game.size;y++)for(int x=0;x<game.size;x++)if(game.cells[y][x].land&&(y==game.size-1||!game.cells[y+1][x].land)){
                float left=ox+x*tile,top=oy+y*tile;
                if(left<=getWidth()&&top<=getHeight()&&left+tile>=0&&top+tile*2.05f>=0)sprites.floating(canvas,game.seed,x,y,left,top,tile);
            }
            for(int y=0;y<game.size;y++)for(int x=0;x<game.size;x++){float left=ox+x*tile,top=oy+y*tile;if(left>getWidth()||top>getHeight()||left+tile<0||top+tile<0)continue;Game.Cell c=game.cells[y][x];if(!c.land)continue;
                sprites.ground(canvas,game.seed,x,y,left,top,tile);
                if(c.stain>=0)sprites.spot(canvas,game.seed,x,y,c.stain,left,top,tile);
                boolean chosen=selected!=null&&selected.x==x&&selected.y==y;
                if(c.building!=0){sprites.building(canvas,c.building,c.mine,left,top,tile);if(c.owner>=0)sprites.flag(canvas,c.owner,left,top,tile);}
                Game.Unit u=game.at(x,y);if(u!=null){
                    boolean ready=u.owner==game.current&&!u.fired&&u.moves==game.rules.movement&&u.moves>0;
                    sprites.unit(canvas,u.owner,u.color,left,top,tile,ready);
                    if(selected!=null&&game.canAttack(selected,u)){
                        outline(canvas,left,top,0xffff7979,.065f);
                        if(u.owner==selected.owner){
                            art.circle(canvas,left+tile*.19f,top+tile*.2f,tile*.15f,0xff63272d);
                            label(canvas,"!",left+tile*.19f,top+tile*.30f,tile*.3f,Color.WHITE);
                        }
                    }
                }
                // Destination markers are drawn last so buildings cannot obscure them.
                if(reach!=null&&reach[y][x]>0){outline(canvas,left,top,0xffa8e6cd,.065f);float cx=left+tile*.5f,cy=top+tile*(c.building==0?.5f:.89f);art.circle(canvas,cx,cy,tile*.095f,0xff102b40);art.circle(canvas,cx,cy,tile*.055f,0xffbaffde);}
                if(chosen)outline(canvas,left,top,0xffa5f5c0,.045f);
            }
            label(canvas,"Drag · Pinch to zoom · Long press to deselect",getWidth()/2f,getHeight()-14,12*getResources().getDisplayMetrics().density,Color.WHITE);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            scale.onTouchEvent(e);
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    lastX=downX=e.getX();lastY=downY=e.getY();dragged=false;pinchGesture=false;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    dragged=true;pinchGesture=true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if(e.getPointerCount()>1){pinchGesture=true;dragged=true;break;}
                    if(pinchGesture)break; // Wait for a fresh touch after lifting a zoom finger.
                    if(Math.abs(e.getX()-downX)+Math.abs(e.getY()-downY)>12)dragged=true;
                    if(dragged&&!scale.isInProgress()){
                        ox+=e.getX()-lastX;oy+=e.getY()-lastY;invalidate();
                    }
                    lastX=e.getX();lastY=e.getY();
                    break;
                case MotionEvent.ACTION_UP:
                    if(!dragged&&!pinchGesture){
                        if(e.getEventTime()-e.getDownTime()>500)performLongClick();
                        else {
                            int x=(int)Math.floor((e.getX()-ox)/tile),y=(int)Math.floor((e.getY()-oy)/tile);
                            if(game!=null&&selected!=null&&selected==game.at(x,y)){selected=null;message=cellInfo(x,y);refresh();}
                            else tap(x,y);
                            performClick();
                        }
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:dragged=false;pinchGesture=false;break;
            }
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
