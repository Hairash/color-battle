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
    static final int BUTTON_TEXT=15, HEADER_TEXT=22, NUMBER_TEXT=18;
    LinearLayout actions;
    View menuScreen;int displayedPlayer;
    Dialog resultDialog;int shownResult=-1;
    boolean setupFromGame;int setupStep,setupPlayers;
    FrameLayout gameFrame;View cellHint;boolean dismissHintTouch;
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
    CharSequence message="Tap your flagged base to recruit. Drag to explore.";
    final int[] colors={0xff42a5ff,0xffffa442,0xffff535d,0xffff83cd};
    AtomicFile saveFile(){return new AtomicFile(new File(getFilesDir(),"battle.save"));}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        sprites=new Sprites(getAssets());
        try(ObjectInputStream in=new ObjectInputStream(saveFile().openRead())){game=(Game)in.readObject();game.upgradeSave();}catch(Exception e){game=null;}
        if(game!=null){displayedPlayer=game.current;if(game.isBot(displayedPlayer)){for(int player=0;player<game.players;player++)if(!game.isBot(player))displayedPlayer=player;}}
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xff101e2b);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets i){v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();}});
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(17);status.setPadding(16,12,16,6);root.addView(status);
        resourceBar=new ResourceBar();resourceBar.setBackground(sprites.panelBackground(getResources()));root.addView(resourceBar,new LinearLayout.LayoutParams(-1,dp(80)));
        hint=new TextView(this);hint.setTextColor(0xffb8ccdc);hint.setTextSize(13);hint.setPadding(16,0,16,8);root.addView(hint);
        unitInfo=new TextView(this);unitInfo.setTextColor(Color.WHITE);unitInfo.setTextSize(13);unitInfo.setGravity(Gravity.CENTER_VERTICAL);
        unitInfo.setPadding(dp(12),0,dp(12),0);unitInfo.setBackgroundColor(0xff101e2b);
        root.addView(unitInfo,new LinearLayout.LayoutParams(-1,dp(36)));
        // Retain the status views and their updates for later reuse.
        status.setVisibility(View.GONE);hint.setVisibility(View.GONE);unitInfo.setVisibility(View.GONE);
        board=new Board();root.addView(board,new LinearLayout.LayoutParams(-1,0,1));
        actions=new LinearLayout(this);actions.setBackground(sprites.panelBackground(getResources()));
        actions.setPadding(dp(12)+3,dp(10),dp(12)+3,dp(10));root.addView(actions);
        button(actions,0,"Menu",new Action(){public void run(){showMenu();}});
        button(actions,1,"Help",new Action(){public void run(){help();}});
        button(actions,2,"Next unmoved unit",new Action(){public void run(){nextUnit();}});
        button(actions,3,"Next owned base",new Action(){public void run(){nextBase();}});
        button(actions,5,"Undo last action",new Action(){public void run(){undoLastAction();}});
        button(actions,4,"End turn",new Action(){public void run(){endHumanTurn();}});
        gameFrame=new FrameLayout(this);gameFrame.addView(root,new FrameLayout.LayoutParams(-1,-1));
        setContentView(gameFrame);refresh();if(game==null)newGame();else board.post(new Action(){public void run(){board.home();scheduleBot();}});
    }
    void button(LinearLayout row,final int icon,String title,final Action action){
        View b=new View(this){
            @Override protected void onDraw(Canvas canvas){sprites.menuButton(canvas,icon,displayedPlayer,getWidth(),getHeight(),isPressed());}
            @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
            @Override public android.view.accessibility.AccessibilityNodeInfo createAccessibilityNodeInfo(){
                android.view.accessibility.AccessibilityNodeInfo info=super.createAccessibilityNodeInfo();info.setClassName(Button.class.getName());return info;
            }
        };
        b.setFocusable(true);b.setContentDescription(title);b.setOnClickListener(action);
        row.addView(b,new LinearLayout.LayoutParams(0,dp(60),1));
    }
    void closeMenu(){
        setupStep=0;
        if(menuScreen!=null){gameFrame.removeView(menuScreen);menuScreen=null;}
        scheduleBot();
    }
    LinearLayout menuPage(CharSequence title){return menuPage(title,false);}
    LinearLayout menuPage(CharSequence title,boolean compact){
        hideCellHelp();
        if(menuScreen!=null)gameFrame.removeView(menuScreen);
        FrameLayout screen=new FrameLayout(this);
        View sky=new View(this){@Override protected void onDraw(Canvas c){sprites.background(c,getWidth(),getHeight());}};
        if(!compact)screen.addView(sky,new FrameLayout.LayoutParams(-1,-1));
        else screen.setBackgroundColor(0x33000000);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout center=new LinearLayout(this);center.setOrientation(1);center.setGravity(Gravity.CENTER);center.setPadding(dp(20),dp(28),dp(20),dp(28));
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(16),dp(compact?14:22),dp(16),dp(compact?14:22));
        card.setBackground(sprites.menuBackground(getResources()));
        center.addView(card,new LinearLayout.LayoutParams(Math.min(dp(compact?340:280),getResources().getDisplayMetrics().widthPixels-dp(40)),-2));
        TextView heading=new TextView(this);heading.setText(title);heading.setTextSize(HEADER_TEXT);heading.setTextColor(0xffffe2a4);heading.setGravity(Gravity.CENTER);heading.setPadding(0,0,0,dp(compact?8:16));card.addView(heading);
        scroll.addView(center);screen.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        if(!compact){
            final View logo=new TileTitle();
            FrameLayout.LayoutParams logoSize=new FrameLayout.LayoutParams(-1,-2);logoSize.leftMargin=dp(20);logoSize.rightMargin=dp(20);
            screen.addView(logo,logoSize);
            screen.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){
                public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){
                    // Screen-relative anchor stays unchanged as setup panels change height.
                    logo.setY(Math.max(dp(24),(b-t)*.17f-logo.getMeasuredHeight()*.5f));
                }
            });
        }
        screen.setClickable(true);menuScreen=screen;gameFrame.addView(screen,new FrameLayout.LayoutParams(-1,-1));
        return card;
    }
    final class TileTitle extends View {
        final Paint letters=new Paint(Paint.ANTI_ALIAS_FLAG);
        TileTitle(){super(MainActivity.this);setContentDescription("Color Battle");}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec);boolean single=width>=dp(48)*12;
            setMeasuredDimension(width,Math.round(single?dp(48):dp(102)));
        }
        @Override protected void onDraw(Canvas canvas){
            boolean single=getWidth()>=dp(48)*12;
            if(single)drawWord(canvas,"COLOR BATTLE",0,48);
            else {drawWord(canvas,"COLOR",0,48);drawWord(canvas,"BATTLE",dp(54),48);}
        }
        void drawWord(Canvas canvas,String word,float top,int tileDp){
            float tile=dp(tileDp),left=(getWidth()-word.length()*tile)*.5f;
            letters.setTypeface(Typeface.DEFAULT_BOLD);letters.setTextSize(tile*.64f);letters.setTextAlign(Paint.Align.CENTER);
            letters.setColor(0xffffedc4);letters.setShadowLayer(dp(1),0,dp(1),0xff302316);
            for(int i=0;i<word.length();i++){
                float x=left+i*tile;
                sprites.ground(canvas,7351,i,top==0?0:1,x,top,tile);
                char letter=word.charAt(i);
                if(letter!=' '){
                    int index=top==0?(word.length()>6&&i>5?i-1:i):i+5;
                    sprites.spot(canvas,titleColors[index],false,false,x,top,tile);
                    float baseline=top+tile*.5f-(letters.ascent()+letters.descent())*.5f;
                    canvas.drawText(String.valueOf(letter),x+tile*.5f,baseline,letters);
                }
            }
        }
    }
    final int[] titleColors={2,1,0,3,1,3,0,2,1,0,2};
    final class ResultTitle extends View {
        final String words;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        ResultTitle(String value){super(MainActivity.this);words=value;setContentDescription(value);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){setMeasuredDimension(MeasureSpec.getSize(widthSpec),dp(52));}
        @Override protected void onDraw(Canvas canvas){
            float tile=Math.min(dp(46),(getWidth()-dp(4))/(float)words.length());
            float left=(getWidth()-tile*words.length())*.5f,top=(getHeight()-tile)*.5f;
            paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(tile*.62f);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(0xffffedc4);
            paint.setShadowLayer(dp(1),0,dp(1),0xff302316);
            for(int i=0;i<words.length();i++){
                float x=left+i*tile;sprites.ground(canvas,7352,i,0,x,top,tile);
                if(words.charAt(i)!=' '){sprites.spot(canvas,titleColors[i%titleColors.length],false,false,x,top,tile);
                    canvas.drawText(words.substring(i,i+1),x+tile*.5f,top+tile*.5f-(paint.ascent()+paint.descent())*.5f,paint);}
            }
        }
    }
    void showResultIfNeeded(){
        if(game==null){shownResult=-1;return;}
        int result=game.winner>=0?game.winner:humanAlive()?-1:-2;
        if(result<0&&result!=-2){shownResult=-1;return;}
        if(shownResult==result||menuScreen!=null)return;
        shownResult=result;
        final Dialog dialog=new Dialog(this);resultDialog=dialog;dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(20),dp(22),dp(20),dp(20));
        card.setBackground(sprites.menuBackground(getResources()));
        boolean won=result>=0&&!game.isBot(result);
        card.addView(new ResultTitle(won?"YOU WIN":"YOU LOSE"),new LinearLayout.LayoutParams(-1,dp(56)));
        menuButtonPair(card,"New game",new Runnable(){public void run(){dialog.dismiss();resultDialog=null;setupFromGame=true;newGame();}},
            "View field",new Runnable(){public void run(){dialog.dismiss();resultDialog=null;}});
        dialog.setContentView(card);dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();dialog.getWindow().setLayout(Math.min(dp(360),getResources().getDisplayMetrics().widthPixels-dp(40)),-2);
    }
    void menuLabel(LinearLayout card,CharSequence label){
        TextView text=new TextView(this);text.setText(label);text.setTextColor(0xffead7b8);text.setTextSize(BUTTON_TEXT);text.setGravity(Gravity.CENTER);text.setPadding(0,dp(8),0,dp(10));card.addView(text);
    }
    void menuHeading(LinearLayout card,CharSequence label){
        menuLabel(card,label);
        ((TextView)card.getChildAt(card.getChildCount()-1)).setTextSize(HEADER_TEXT);
    }
    void menuChoice(LinearLayout card,String title,final Runnable action){
        Button button=new Button(this);button.setText(title);button.setAllCaps(false);button.setTextSize(BUTTON_TEXT);button.setTextColor(0xff302316);
        button.setBackground(sprites.wideButtonBackground(getResources()));button.setPadding(dp(16),dp(8),dp(16),dp(8));
        int width=Math.max(dp(156),Math.round(button.getPaint().measureText(title))+dp(32));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(Math.min(width,getResources().getDisplayMetrics().widthPixels-dp(72)),dp(48));layout.gravity=Gravity.CENTER;layout.setMargins(0,dp(5),0,dp(5));card.addView(button,layout);
        button.setOnClickListener(new View.OnClickListener(){public void onClick(View view){action.run();}});
    }
    void menuButtonPair(LinearLayout card,String first,Runnable firstAction,String second,Runnable secondAction){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowSize=new LinearLayout.LayoutParams(-1,-2);rowSize.topMargin=dp(8);card.addView(row,rowSize);
        String[] titles={first,second};final Runnable[] actions={firstAction,secondAction};
        for(int i=0;i<2;i++){
            final int index=i;
            Button button=new Button(this);button.setText(titles[i]);button.setAllCaps(false);button.setTextSize(BUTTON_TEXT);
            button.setTextColor(0xff302316);button.setSingleLine(true);button.setMinWidth(0);button.setMinimumWidth(0);
            button.setPadding(dp(8),0,dp(8),0);button.setBackground(sprites.wideButtonBackground(getResources()));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(48),1);if(i==0)size.rightMargin=dp(6);
            row.addView(button,size);button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){actions[index].run();}});
        }
    }
    void possessionRow(LinearLayout card,boolean mines,int owner){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
        for(int color=0;color<4;color++){
            int count=0;
            if(mines){for(Game.Cell[] line:game.cells)for(Game.Cell cell:line)if(cell.owner==owner&&cell.building==2&&cell.mine==color)count++;}
            else for(Game.Unit unit:game.units)if(unit.owner==owner&&unit.color==color)count++;
            LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setGravity(Gravity.CENTER);
            ImageView image=helpImage(mines?sprites.buildingIcon(getResources(),2,color):sprites.unitIcon(getResources(),owner,color));
            item.addView(image,new LinearLayout.LayoutParams(dp(52),dp(48)));
            TextView number=new TextView(this);number.setText(""+count);number.setTypeface(Typeface.DEFAULT_BOLD);number.setTextColor(Color.WHITE);number.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,NUMBER_TEXT);number.setGravity(Gravity.CENTER);item.addView(number);
            row.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        card.addView(row);
    }
    void showMenu(){
        setupStep=0;
        if(game==null){newGame();return;}
        int owner=displayedPlayer;
        LinearLayout card=menuPage(teamText("Turn "+game.turn+" ",owner,""),true);
        SpannableStringBuilder teams=new SpannableStringBuilder("Humans: ");
        for(int p=0;p<game.players;p++)if(!game.isBot(p))teams.append(teamText("",p," "));
        teams.append("    Bots: ");boolean bots=false;
        for(int p=0;p<game.players;p++)if(game.isBot(p)){teams.append(teamText("",p," "));bots=true;}
        if(!bots)teams.append("—");
        menuLabel(card,teams);
        View teamGap=new View(this);card.addView(teamGap,new LinearLayout.LayoutParams(-1,dp(5)));
        possessionRow(card,true,owner);
        View inventoryGap=new View(this);card.addView(inventoryGap,new LinearLayout.LayoutParams(-1,dp(13)));
        possessionRow(card,false,owner);
        menuButtonPair(card,"New game",new Runnable(){public void run(){confirmNewGame();}},
            "Continue",new Runnable(){public void run(){closeMenu();}});
    }

    void confirmNewGame(){confirmAction("Starting a new game replaces your current game.",new Runnable(){public void run(){setupFromGame=true;newGame();}});}
    void confirmAction(String description,final Runnable action){
        final Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(22),dp(22),dp(22),dp(22));card.setBackground(sprites.menuBackground(getResources()));
        menuHeading(card,description==null?"End of turn":"Are you sure?");
        if(description!=null)menuLabel(card,description);
        Runnable yes=new Runnable(){public void run(){dialog.dismiss();action.run();}};
        Runnable no=new Runnable(){public void run(){dialog.dismiss();}};
        if(description==null)menuButtonPair(card,"No",no,"Yes",yes);
        else menuButtonPair(card,"Yes",yes,"No",no);
        dialog.setContentView(card);dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();dialog.getWindow().setLayout(Math.min(dp(360),getResources().getDisplayMetrics().widthPixels-dp(40)),-2);
    }
    void newGame(){
        setupStep=1;
        LinearLayout card=menuPage("Number of players");
        for(int n=2;n<=4;n++){final int players=n;menuChoice(card,n+" players",new Runnable(){public void run(){chooseBots(players);}});}
        if(setupFromGame&&game!=null)menuChoice(card,"Back",new Runnable(){public void run(){closeMenu();}});
    }
    void chooseBots(final int players){
        setupStep=2;setupPlayers=players;
        LinearLayout card=menuPage("Choose teams");
        for(int n=1;n<=players;n++){
            final int humans=n;
            menuChoice(card,n+" human"+(n==1?"":"s")+" · "+(players-n)+" bot"+(players-n==1?"":"s"),new Runnable(){public void run(){chooseMap(players,players-humans);}});
        }
        menuChoice(card,"Back",new Runnable(){public void run(){newGame();}});
    }
    void chooseMap(final int players,final int bots){
        setupStep=3;setupPlayers=players;
        LinearLayout card=menuPage("Map size");
        String[] names={"Small","Medium","Large"};int[] sizes={Game.Rules.SMALL_MAP,Game.Rules.MEDIUM_MAP,Game.Rules.LARGE_MAP};
        for(int i=0;i<3;i++){final int size=sizes[i];menuChoice(card,names[i],new Runnable(){public void run(){
            botHandler.removeCallbacksAndMessages(null);botScheduled=false;
            game=new Game(players,System.currentTimeMillis(),bots,size);displayedPlayer=0;selected=null;baseCursor=-1;undoHistory.clear();shownResult=-1;
            message="Tap your fortress to recruit.";closeMenu();refresh();board.home();showHumanTurn();
        }});}
        menuChoice(card,"Back",new Runnable(){public void run(){chooseBots(players);}});
    }
    @Override public void onBackPressed(){if(setupStep==3){chooseBots(setupPlayers);return;}if(setupStep==2){newGame();return;}if(menuScreen!=null&&game!=null)closeMenu();else if(game!=null)showMenu();else super.onBackPressed();}
    void nextUnit(){if(game==null||game.winner>=0||game.isBot(game.current))return;int start=selected==null?-1:game.units.indexOf(selected);for(int step=1;step<=game.units.size();step++){Game.Unit candidate=game.units.get((start+step)%game.units.size());if(game.canMove(candidate)){selected=candidate;board.center(candidate.x,candidate.y);message="Unit ready to move.";refresh();return;}}selected=null;message="No units left to move.";refresh();}
    void nextBase(){if(game==null||game.winner>=0||game.isBot(game.current))return;int total=game.size*game.size;for(int step=1;step<=total;step++){int index=(baseCursor+step)%total,x=index%game.size,y=index/game.size;Game.Cell cell=game.cells[y][x];if(cell.building==1&&cell.owner==game.current){baseCursor=index;selected=null;board.center(x,y);message="Fortress · tap to recruit.";refresh();return;}}message="No owned fortresses.";refresh();}
    void endHumanTurn(){if(game==null||game.winner>=0||game.isBot(game.current))return;
        confirmAction(null,new Runnable(){public void run(){
            recordAction();game.endTurn();if(isHotseat())undoHistory.clear();selected=null;baseCursor=-1;message="Income and base upkeep applied.";refresh();
            if(game.winner>=0)return;if(game.isBot(game.current)){scheduleBot();return;}showHumanTurn();
        }});
    }
    boolean isHotseat(){int humans=0;if(game!=null)for(int p=0;p<game.players;p++)if(!game.isBot(p))humans++;return humans>1;}
    void showHumanTurn(){
        if(game==null||game.winner>=0)return;
        int humans=0;for(int p=0;p<game.players;p++)if(!game.isBot(p))humans++;if(humans==1)return;
        final Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(24),dp(24),dp(24),dp(24));
        card.setBackground(sprites.menuBackground(getResources()));
        menuHeading(card,teamText("Turn "+game.turn+". Pass to ",game.current,""));
        // The handoff itself is the only label; tapping the card acknowledges it.
        card.setClickable(true);card.setFocusable(true);card.setContentDescription("Tap to begin turn");
        card.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();}});
        dialog.setContentView(card);dialog.setCanceledOnTouchOutside(false);dialog.setCancelable(false);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();dialog.getWindow().setLayout(Math.min(dp(360),getResources().getDisplayMetrics().widthPixels-dp(40)),-2);
    }
    boolean humanAlive(){if(game==null)return false;for(int p=0;p<game.players;p++)if(!game.isBot(p)&&game.alive(p))return true;return false;}
    void scheduleBot(){if(menuScreen!=null||botScheduled||game==null||game.winner>=0||!game.isBot(game.current))return;
        if(!humanAlive()){message="All human players were eliminated. Start a new game.";refresh();return;}
        botScheduled=true;final Game active=game;
        botHandler.postDelayed(new Runnable(){public void run(){if(game!=active)return;botScheduled=false;if(menuScreen!=null)return;if(game.winner>=0||!game.isBot(game.current))return;int player=game.current;Bot.playTurn(game);selected=null;baseCursor=-1;message=teamText("",player," bot finished its turn.");refresh();if(game.winner>=0)return;if(game.isBot(game.current))scheduleBot();else showHumanTurn();}},350);}
    void guideText(LinearLayout card,String text){
        TextView label=new TextView(this);label.setText(text.replace(". ",".\n").replace("! ","!\n"));label.setTextColor(0xffead7b8);label.setTextSize(BUTTON_TEXT);
        label.setPadding(dp(8),dp(4),dp(8),dp(12));label.setGravity(Gravity.CENTER);card.addView(label);
    }
    void guideImages(LinearLayout card,Drawable... icons){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
        for(Drawable icon:icons){ImageView image=helpImage(icon);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(48),dp(44));if(row.getChildCount()>0)size.leftMargin=2;row.addView(image,size);}
        card.addView(row);
    }
    void guideHeading(LinearLayout card,String title){
        View line=new View(this);line.setBackgroundColor(0xff78684c);
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,dp(1));size.setMargins(dp(8),dp(8),dp(8),dp(8));card.addView(line,size);
        menuHeading(card,title);
    }
    void guideOutline(Canvas canvas,Paint paint,float x,float y,float size,int color){
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,size*.035f));paint.setColor(color);
        canvas.drawRoundRect(new RectF(x+size*.065f,y+size*.065f,x+size*.935f,y+size*.935f),size*.1f,size*.1f,paint);
        paint.setStyle(Paint.Style.FILL);
    }
    void help(){
        final Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout frame=new LinearLayout(this);frame.setOrientation(1);frame.setPadding(dp(14),dp(12),dp(14),dp(12));
        frame.setBackground(sprites.menuBackground(getResources()));menuHeading(frame,"How to play");
        ScrollView scroll=new ScrollView(this);LinearLayout card=new LinearLayout(this);card.setOrientation(1);scroll.addView(card);
        frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        final int owner=displayedPlayer;
        final int enemy=(owner+1)%(game==null?4:game.players);
        guideImages(card,sprites.teamIcon(getResources(),0),sprites.teamIcon(getResources(),1),sprites.teamIcon(getResources(),2),sprites.teamIcon(getResources(),3));
        LinearLayout teamIcons=(LinearLayout)card.getChildAt(card.getChildCount()-1);
        for(int i=0;i<teamIcons.getChildCount();i++){
            View sign=teamIcons.getChildAt(i);
            sign.getLayoutParams().width=Math.round(dp(48)/1.5f);
            sign.getLayoutParams().height=Math.round(dp(44)/1.5f);
        }
        LinearLayout.LayoutParams saucerSpacing=(LinearLayout.LayoutParams)teamIcons.getChildAt(1).getLayoutParams();
        saucerSpacing.leftMargin+=5;saucerSpacing.rightMargin+=5;
        guideText(card,"Robot shapes identify teams. Colors decide who can shoot whom.");
        guideHeading(card,"Recruit");
        guideImages(card,sprites.buildingIcon(getResources(),1,0),sprites.unitIcon(getResources(),owner,0),art.icon(colors[0],-1,dp(32)));
        LinearLayout recruitmentImages=(LinearLayout)card.getChildAt(card.getChildCount()-1);
        View baseExample=new View(this){@Override protected void onDraw(Canvas c){
            float size=Math.min(getWidth(),getHeight()),left=(getWidth()-size)*.5f,top=(getHeight()-size)*.5f;
            sprites.building(c,1,0,left,top,size);sprites.flag(c,owner,left,top,size);
        }};
        LinearLayout.LayoutParams baseSize=(LinearLayout.LayoutParams)recruitmentImages.getChildAt(0).getLayoutParams();
        baseSize.rightMargin=Math.round(dp(48)*.15f);
        recruitmentImages.removeViewAt(0);recruitmentImages.addView(baseExample,0,baseSize);
        TextView recruitHint=new TextView(this);recruitHint.setText(teamText("Tap your empty base (marked with the ",owner," sign)."));
        recruitHint.setTextSize(BUTTON_TEXT);recruitHint.setTextColor(0xffead7b8);recruitHint.setGravity(Gravity.CENTER);recruitHint.setPadding(dp(8),dp(4),dp(8),dp(4));card.addView(recruitHint);
        guideText(card,"Cost: 5 matching crystals + the number of your living units of that color. New units can act immediately.");
        guideHeading(card,"Move");
        View movement=new View(this){final Paint p=new Paint(3);@Override protected void onDraw(Canvas c){
            float size=Math.min(getWidth()/5f,dp(46)),left=(getWidth()-4*size)*.5f;
            for(int i=0;i<4;i++){sprites.ground(c,91,i,0,left+i*size,0,size);if(i>0){guideOutline(c,p,left+i*size,0,size,0xffa8e6cd);p.setColor(0xff8ae18a);c.drawCircle(left+(i+.5f)*size,size*.5f,dp(3),p);}}
            sprites.unit(c,displayedPlayer,0,left,0,size,true);
        }};
        card.addView(movement,new LinearLayout.LayoutParams(-1,dp(48)));
        guideText(card,"Select a unit, then a green dot. Move up to "+(game==null?3:game.rules.movement)+" cells once per turn. Units cannot move through air. White contour = ready to move.");
        guideText(card,"To deselect a unit, tap any cell outside its movement range.");
        guideHeading(card,"Shoot");
        View shooting=new View(this){final Paint p=new Paint(3);@Override protected void onDraw(Canvas c){
            float size=Math.min(getWidth()/5f,dp(46)),left=(getWidth()-4*size)*.5f;
            for(int i=0;i<4;i++)sprites.ground(c,91,i,0,left+i*size,0,size);
            sprites.unit(c,owner,1,left,0,size,true);
            sprites.unit(c,enemy,0,left+3*size,0,size,false);
            guideOutline(c,p,left,0,size,0xffa5f5c0);
            guideOutline(c,p,left+3*size,0,size,Color.WHITE);
            float cx=left+3.5f*size,cy=size*.5f;
            p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));
            c.drawLine(cx-size*.25f,cy,cx+size*.25f,cy,p);c.drawLine(cx,cy-size*.25f,cx,cy+size*.25f,p);c.drawCircle(cx,cy,size*.12f,p);p.setStyle(Paint.Style.FILL);
        }};
        card.addView(shooting,new LinearLayout.LayoutParams(-1,dp(48)));
        guideText(card,"Tap a highlighted target to shoot once in a straight line, up to "+(game==null?3:game.rules.range)+" cells. Shots cross air gaps; intervening units block them. Move then shoot, or shoot and stay.");
        guideText(card,"A small black dot in the bottom-right corner marks your units that have fired this turn.");
        guideText(card,"Friendly fire works too! A marked teammate can be shot.");
        guideHeading(card,"Color matchups");
        int[] attackers={1,0,3,2},victims={0,3,2,1};
        for(int i=0;i<4;i++){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
            row.addView(helpImage(sprites.unitIcon(getResources(),owner,attackers[i])),new LinearLayout.LayoutParams(dp(44),dp(36)));
            View arrow=new View(this){@Override protected void onDraw(Canvas c){
                c.save();c.rotate(180,getWidth()*.5f,getHeight()*.5f);
                sprites.resourceArrow(c,getWidth()*.5f,getHeight()*.5f,dp(26),dp(20));c.restore();
            }};row.addView(arrow,new LinearLayout.LayoutParams(dp(38),dp(36)));
            row.addView(helpImage(sprites.unitIcon(getResources(),enemy,victims[i])),new LinearLayout.LayoutParams(dp(44),dp(36)));card.addView(row);
        }
        guideText(card,"Only these color matchups kill. Other attacks do nothing. Follow the arrows in the top resource panel to check the matchups anytime.");
        guideHeading(card,"Paint changes paths");
        View stain=new View(this){@Override protected void onDraw(Canvas c){float size=dp(52),left=(getWidth()-size)*.5f;sprites.ground(c,91,0,0,left,0,size);sprites.spot(c,2,false,false,left,0,size);}};
        card.addView(stain,new LinearLayout.LayoutParams(-1,dp(54)));
        guideText(card,"A kill paints the cell in the shooter's color. Units of that color cannot enter it.");
        guideHeading(card,"Capture & income");
        guideImages(card,sprites.buildingIcon(getResources(),1,0),sprites.buildingIcon(getResources(),2,0),sprites.buildingIcon(getResources(),2,3));
        guideText(card,"Keep a unit on a building until the start of your next turn to capture it. Enemies can stop the capture by killing the unit first. Ownership stays when units leave.");
        guideText(card,"Each turn: +2 of every crystal, +2 per matching mine, −1 of each per base. Each resource still grows by at least 1.");
        guideHeading(card,"Win & controls");
        guideText(card,"Capture enemy fortresses and destroy their units. Mines alone cannot keep a team in the game. The last team with a unit or fortress wins.");
        int[] controlIcons={0,1,2,3,5,4};
        String[] controlLabels={"Game menu","How to play","Next unmoved unit","Next owned base","Undo last action","End turn"};
        for(int i=0;i<controlIcons.length;i++){
            final int icon=controlIcons[i];
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(3),dp(12),dp(3));
            row.setTranslationX(dp(48)*1.30f);
            View preview=new View(this){@Override protected void onDraw(Canvas c){sprites.menuButton(c,icon,owner,getWidth(),getHeight(),false);}};
            preview.setContentDescription(controlLabels[i]);row.addView(preview,new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView caption=new TextView(this);caption.setText(controlLabels[i]);caption.setTextSize(BUTTON_TEXT);caption.setTextColor(0xffead7b8);caption.setPadding(dp(12),0,0,0);
            row.addView(caption,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);
        }
        guideText(card,"Drag to pan • Pinch to zoom\nLong press a cell for details");
        guideText(card,"Hot-seat undo stops at the start of your turn. Single-player undo can reverse turns, including bot moves.");
        menuChoice(frame,"Continue",new Runnable(){public void run(){dialog.dismiss();}});
        dialog.setContentView(frame);dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();dialog.getWindow().setLayout(Math.min(dp(360),getResources().getDisplayMetrics().widthPixels-dp(32)),(int)(getResources().getDisplayMetrics().heightPixels*.82f));
    }
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
    void undoLastAction(){if(game!=null&&isHotseat()&&game.isBot(game.current))return;if(game==null||undoHistory.isEmpty()){message="No action to undo.";refresh();return;}
        try{UndoEntry entry=undoHistory.peekLast();ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(entry.state));Game restored=(Game)in.readObject();in.close();restored.upgradeSave();
            undoHistory.removeLast();game=restored;botScheduled=false;selected=entry.kind==1?game.at(entry.x,entry.y):null;baseCursor=-1;
            message=entry.kind==1||entry.kind==2?cellInfo(entry.x,entry.y):"Last action undone.";
            refresh();if(entry.kind==1||entry.kind==2)board.center(entry.x,entry.y);
        }catch(Exception e){message="Could not undo action.";refresh();}
    }
    @Override public void onPause(){super.onPause();hintHandler.removeCallbacks(replaceHint);hideCellHelp();save();}
    void refresh(){if(game!=null&&!game.isBot(game.current))displayedPlayer=game.current;if(game==null){status.setText("COLOR BATTLE");hint.setText("Start a new local game for 2–4 players.");unitInfo.setText("");return;}save();
        status.setText(game.winner>=0?teamText("",game.winner," wins!"):teamText("",game.current,(game.isBot(game.current)?" bot":"")+" · Turn "+game.turn));
        resourceBar.invalidate();
        for(int i=0;i<actions.getChildCount();i++)actions.getChildAt(i).invalidate();
        hint.setText(message);
        unitInfo.setText(selected==null?"":iconText(selected.color,selected.owner,(game.canMove(selected)?"Move ready · up to "+game.rules.movement+" cells":"Move unavailable")+" · "+(selected.fired?"Shot used":"Shot ready")));
        board.invalidate();showResultIfNeeded();}
    CharSequence cellInfo(int x,int y){
        if(game==null||!game.inside(x,y))return "Outside map";
        Game.Cell cell=game.cells[y][x];
        if(!cell.land)return "Air";
        SpannableStringBuilder info=new SpannableStringBuilder(cell.building==1?"Fortress":cell.building==2?Game.COLORS[cell.mine]+" mine":"Land");
        if(cell.owner>=0&&cell.building!=0)info.append(teamText(" • ",cell.owner,""));
        if(cell.stain>=0)info.append(" • "+Game.COLORS[cell.stain].toLowerCase(Locale.ROOT));
        Game.Unit unit=game.at(x,y);
        if(unit!=null)info.append(teamText(" • ",unit.owner," "+Game.COLORS[unit.color].toLowerCase(Locale.ROOT)+" unit"));
        return info;
    }
    void tap(int x,int y){if(game==null||game.winner>=0||game.isBot(game.current))return;
        if(!game.inside(x,y)){selected=null;message=cellInfo(x,y);refresh();return;}
        Game.Unit u=game.at(x,y);Game.Cell c=game.cells[y][x];
        if(selected!=null){
            if(u!=null&&u!=selected&&u.owner==game.current&&game.canAttack(selected,u)){
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
    SpannableStringBuilder teamText(String prefix,int owner,String suffix){
        SpannableStringBuilder text=new SpannableStringBuilder(prefix);
        int start=text.length();text.append("\ufffc");
        Drawable sign=sprites.teamIcon(getResources(),owner);
        int height=Math.round(13*getResources().getDisplayMetrics().scaledDensity);
        int width=Math.max(1,Math.round(height*(float)sign.getIntrinsicWidth()/sign.getIntrinsicHeight()));
        sign.setBounds(0,0,width,height);
        text.setSpan(new ImageSpan(sign,ImageSpan.ALIGN_BASELINE),start,start+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text.append(suffix);
    }
    ImageView helpImage(Drawable image){
        ImageView view=new ImageView(this);view.setImageDrawable(image);view.setScaleType(ImageView.ScaleType.FIT_CENTER);return view;
    }
    void helpLayer(LinearLayout box,View image,CharSequence description){
        if(box.getChildCount()>0){View divider=new View(this);divider.setBackgroundColor(0xff78684c);box.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));}
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(7),dp(8),dp(7));
        row.addView(image,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView text=new TextView(this);text.setTextColor(Color.WHITE);text.setTextSize(13);text.setText(description);text.setPadding(dp(8),0,0,0);
        row.addView(text,new LinearLayout.LayoutParams(0,-2,1));box.addView(row);
    }
    void showCellHelp(final int x,final int y){
        if(game==null||!game.inside(x,y)||!game.cells[y][x].land)return;
        final Game.Cell cell=game.cells[y][x];Game.Unit unit=game.at(x,y);
        LinearLayout layers=new LinearLayout(this);layers.setOrientation(LinearLayout.VERTICAL);layers.setPadding(dp(2),dp(2),dp(2),dp(2));
        layers.setPadding(dp(6),dp(6),dp(6),dp(6));
        layers.setBackground(sprites.contextBackground(getResources()));
        if(unit!=null){
            SpannableStringBuilder info=teamText("",unit.owner," "+Game.COLORS[unit.color]+" unit");
            if(unit.owner==game.current)info.append("\nMoved: "+(unit.moves==0?"Yes":"No")+"\nShot fired: "+(unit.fired?"Yes":"No"));
            helpLayer(layers,helpImage(sprites.unitIcon(getResources(),unit.owner,unit.color)),info);
        }
        if(cell.building!=0){
            SpannableStringBuilder info=new SpannableStringBuilder(cell.building==1?"Base":"Mine • "+Game.COLORS[cell.mine]);
            info.append("\n");
            if(cell.owner<0)info.append("Unclaimed");
            else {
                info.append(teamText("Controlled by ",cell.owner,cell.owner==game.current?" (you)":""));
            }
            helpLayer(layers,helpImage(sprites.buildingIcon(getResources(),cell.building,cell.mine)),info);
        }
        final long seed=game.seed;final boolean land=cell.land;final int stain=cell.stain;
        View ground=new View(this){@Override protected void onDraw(Canvas canvas){
            float size=Math.min(getWidth(),getHeight());
            if(land){sprites.ground(canvas,seed,x,y,0,0,size);if(stain>=0)sprites.spot(canvas,stain,false,false,0,0,size);}
            else sprites.background(canvas,getWidth(),getHeight());
        }};
        helpLayer(layers,ground,land?(stain<0?"Uncolored":Game.COLORS[stain]):"Air");
        hideCellHelp();
        int[] boardPosition=new int[2],framePosition=new int[2];
        board.getLocationInWindow(boardPosition);gameFrame.getLocationInWindow(framePosition);
        int boardLeft=boardPosition[0]-framePosition[0],boardTop=boardPosition[1]-framePosition[1];
        float cellLeft=boardLeft+board.ox+x*board.tile,cellRight=cellLeft+board.tile;
        int margin=dp(8),width=Math.min(dp(220),gameFrame.getWidth()-2*margin);
        float leftSpace=cellLeft-margin,rightSpace=gameFrame.getWidth()-margin-cellRight;
        int popupLeft=Math.round(rightSpace>=leftSpace?cellRight+margin:cellLeft-margin-width);
        popupLeft=Math.max(margin,Math.min(popupLeft,gameFrame.getWidth()-width-margin));
        layers.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int height=Math.min(layers.getMeasuredHeight(),Math.max(dp(60),board.getHeight()-2*margin));
        int popupTop=Math.round(boardTop+board.oy+(y+.5f)*board.tile-height*.5f);
        popupTop=Math.max(boardTop+margin,Math.min(popupTop,boardTop+board.getHeight()-height-margin));
        FrameLayout.LayoutParams placement=new FrameLayout.LayoutParams(width,height);
        placement.leftMargin=popupLeft;placement.topMargin=popupTop;
        cellHint=layers;layers.setElevation(dp(8));gameFrame.addView(layers,placement);
    }
    void hideCellHelp(){if(cellHint!=null){gameFrame.removeView(cellHint);cellHint=null;}}
    float hintDownX,hintDownY;
    final Handler hintHandler=new Handler();
    final Runnable replaceHint=new Runnable(){public void run(){
        int[] position=new int[2];board.getLocationOnScreen(position);
        float x=hintDownX-position[0],y=hintDownY-position[1];
        if(x>=0&&y>=0&&x<board.getWidth()&&y<board.getHeight())
            showCellHelp((int)Math.floor((x-board.ox)/board.tile),(int)Math.floor((y-board.oy)/board.tile));
    }};
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            dismissHintTouch=cellHint!=null;
            if(dismissHintTouch){
                hideCellHelp();hintDownX=event.getRawX();hintDownY=event.getRawY();
                hintHandler.postDelayed(replaceHint,ViewConfiguration.getLongPressTimeout());
            }
        }
        if(dismissHintTouch){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_POINTER_DOWN
                ||(action==MotionEvent.ACTION_MOVE&&Math.hypot(event.getRawX()-hintDownX,event.getRawY()-hintDownY)>ViewConfiguration.get(this).getScaledTouchSlop()))
                hintHandler.removeCallbacks(replaceHint);
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                hintHandler.removeCallbacks(replaceHint);dismissHintTouch=false;
            }
            return true;
        }
        return super.dispatchTouchEvent(event);
    }
    void confirmFriendlyShot(final Game.Unit shooter,final Game.Unit target){
        new AlertDialog.Builder(this).setTitle("! Shoot your own unit?")
            .setMessage("This will destroy your "+Game.COLORS[target.color]+" unit and stain its tile "+Game.COLORS[shooter.color]+".")
            .setNegativeButton("Keep unit",null)
            .setPositiveButton("Shoot",new Action(){public void run(){if(game.canAttack(shooter,target)){recordAction();game.attack(shooter,target);message=cellInfo(target.x,target.y);}refresh();}}).show();
    }
    void recruit(final int x,final int y){
        final Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);
        choices.setPadding(dp(16),dp(16),dp(16),dp(16));choices.setBackground(sprites.menuBackground(getResources()));
        menuHeading(choices,teamText("Recruit · ",game.current,""));
        LinearLayout headings=new LinearLayout(this);headings.setPadding(dp(8),0,dp(8),dp(4));
        TextView unitHeading=new TextView(this);unitHeading.setText("Unit");unitHeading.setTextSize(BUTTON_TEXT);unitHeading.setTextColor(0xffead7b8);unitHeading.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        unitHeading.setTranslationX(-unitHeading.getPaint().measureText("n")*.5f);
        headings.addView(unitHeading,new LinearLayout.LayoutParams(dp(36),-2));
        TextView costHeading=new TextView(this);costHeading.setText("Cost");costHeading.setTextSize(BUTTON_TEXT);costHeading.setTextColor(0xffead7b8);costHeading.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        costHeading.setTranslationX(-costHeading.getPaint().measureText("n"));
        headings.addView(costHeading,new LinearLayout.LayoutParams(0,-2,1));choices.addView(headings);
        for(int i=0;i<4;i++){
            final int color=i;int cost=game.unitCost(game.current,i);
            LinearLayout choice=new LinearLayout(this);choice.setGravity(Gravity.CENTER);choice.setPadding(dp(8),dp(4),dp(8),dp(4));
            choice.setBackground(sprites.wideButtonBackground(getResources()));
            ImageView robot=helpImage(sprites.unitIcon(getResources(),game.current,i));
            choice.addView(robot,new LinearLayout.LayoutParams(dp(36),dp(28)));
            ImageView crystal=helpImage(art.icon(colors[i],-1,dp(16)));
            LinearLayout payment=new LinearLayout(this);payment.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            payment.addView(crystal,new LinearLayout.LayoutParams(dp(18),dp(18)));
            TextView price=new TextView(this);price.setTextColor(Color.BLACK);price.setTypeface(Typeface.DEFAULT_BOLD);price.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,NUMBER_TEXT);
            price.setGravity(Gravity.CENTER);price.setText(cost+" / "+game.resources[game.current][i]);
            payment.setTranslationX(-price.getPaint().measureText("0")*.5f);

                        price.setPadding(dp(2),0,0,0);
            payment.addView(price,new LinearLayout.LayoutParams(-2,-2));
            choice.addView(payment,new LinearLayout.LayoutParams(0,-1,1));
            boolean available=game.resources[game.current][i]>=cost&&game.cells[y][x].stain!=i&&game.at(x,y)==null;
            choice.setEnabled(available);choice.setAlpha(available?1f:.45f);choice.setFocusable(true);
            choice.setContentDescription("Recruit "+Game.COLORS[i]+" unit, costs "+cost+", available "+game.resources[game.current][i]);
            choice.setOnClickListener(new Action(){public void run(){recordAction(2,x,y);message=game.buy(x,y,color);selected=game.at(x,y);dialog.dismiss();refresh();}});
            LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,dp(48));row.setMargins(0,dp(3),0,dp(3));choices.addView(choice,row);
        }
        menuChoice(choices,"Cancel",new Runnable(){public void run(){dialog.dismiss();}});
        View cancel=choices.getChildAt(choices.getChildCount()-1);
        LinearLayout.LayoutParams cancelSize=(LinearLayout.LayoutParams)cancel.getLayoutParams();
        cancelSize.width=dp(98);cancelSize.height=dp(30);cancelSize.topMargin=dp(12);cancelSize.bottomMargin=0;cancelSize.gravity=Gravity.CENTER;
        ((Button)cancel).setTextColor(Color.BLACK);((Button)cancel).setTypeface(Typeface.DEFAULT);((Button)cancel).setTextSize(BUTTON_TEXT);((Button)cancel).setMinWidth(0);((Button)cancel).setMinimumWidth(0);cancel.setPadding(dp(4),0,dp(4),0);
        dialog.setContentView(choices);dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();dialog.getWindow().setLayout(Math.min(dp(188),getResources().getDisplayMetrics().widthPixels-dp(40)),-2);
    }
    final class ResourceBar extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        ResourceBar(){super(MainActivity.this);setContentDescription("Orange kills Blue; Red kills Orange; Pink kills Red; Blue kills Pink");}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(game==null)return;
            canvas.save();canvas.translate(dp(20),dp(9.2f));
            float w=getWidth()-dp(40),gap=w/4f;
            // Each left arrow reads "left crystal is killed by right crystal".
            paint.setTextSize(dp(NUMBER_TEXT));paint.setTypeface(Typeface.DEFAULT_BOLD);
            for(int i=1;i<4;i++){
                float leftNumberEnd=gap*(i-.5f)+dp(1)+paint.measureText(""+game.resources[displayedPlayer][i-1]);
                float rightCrystalLeft=gap*(i+.5f)-dp(15)-dp(11)*.8f;
                sprites.resourceArrow(canvas,(leftNumberEnd+rightCrystalLeft)*.5f,dp(42),dp(22),dp(17));
            }
            // The long image wraps Blue around to Pink and closes the color cycle.
            float blue=gap*.5f,pink=gap*3.5f;
            sprites.wrapArrow(canvas,blue,pink,dp(6),dp(28));
            for(int i=0;i<4;i++){
                float center=gap*(i+.5f),y=dp(42);art.gem(canvas,center-dp(15),y,dp(11),colors[i]);
                paint.setColor(Color.WHITE);paint.setTextSize(dp(NUMBER_TEXT));paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(""+game.resources[displayedPlayer][i],center+dp(1),y+dp(6),paint);
            }
            canvas.restore();
        }
    }
    final class Board extends View {
        Paint p=new Paint(3);float tile=64,ox=0,oy=0,lastX,lastY,downX,downY;boolean dragged,pinchGesture;ScaleGestureDetector scale;
        boolean held;
        final Runnable holdAction=new Runnable(){public void run(){if(!dragged&&!pinchGesture){held=true;performLongClick();}}};
        Board(){super(MainActivity.this);tile=44*getResources().getDisplayMetrics().density;scale=new ScaleGestureDetector(MainActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){float old=tile;tile=Math.max(18*getResources().getDisplayMetrics().density,Math.min(96*getResources().getDisplayMetrics().density,tile*d.getScaleFactor()));ox=d.getFocusX()-(d.getFocusX()-ox)*tile/old;oy=d.getFocusY()-(d.getFocusY()-oy)*tile/old;dragged=true;invalidate();return true;}});scale.setQuickScaleEnabled(false);setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){
            int x=(int)Math.floor((downX-ox)/tile),y=(int)Math.floor((downY-oy)/tile);
            showCellHelp(x,y);return true;
        }});}
        void home(){if(game==null)return;for(int y=0;y<game.size;y++)for(int x=0;x<game.size;x++)if(game.cells[y][x].owner==game.current){center(x,y);return;}for(Game.Unit u:game.units)if(u.owner==game.current){center(u.x,u.y);return;}}
        void center(int x,int y){ox=getWidth()/2f-(x+.5f)*tile;oy=getHeight()/2f-(y+.5f)*tile;invalidate();}
        void box(Canvas c,float x,float y,float w,float h,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRect(x,y,x+w,y+h,p);}
        void label(Canvas c,String s,float x,float y,float size,int color){p.setColor(color);p.setTextSize(size);p.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y,p);}
        void outline(Canvas c,float left,float top,int color,float inset){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,tile*.035f));p.setColor(color);c.drawRoundRect(new RectF(left+tile*inset,top+tile*inset,left+tile*(1-inset),top+tile*(1-inset)),tile*.10f,tile*.10f,p);p.setStyle(Paint.Style.FILL);}
        void shotTarget(Canvas c,float left,float top){
            float cx=left+tile*.5f,cy=top+tile*.5f,half=tile*.25f;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,tile*.03f));p.setColor(Color.WHITE);
            c.drawLine(cx-half,cy,cx+half,cy,p);
            c.drawLine(cx,cy-half,cx,cy+half,p);
            c.drawCircle(cx,cy,tile*.12f,p);
            p.setStyle(Paint.Style.FILL);
        }
        @Override protected void onDraw(Canvas canvas){sprites.background(canvas,getWidth(),getHeight());if(game==null)return;int[][] reach=selected==null?null:game.distances(selected);
            for(int y=0;y<game.size;y++)for(int x=0;x<game.size;x++)if(game.cells[y][x].land&&(y==game.size-1||!game.cells[y+1][x].land)&&sprites.hasFloating(game.seed,x,y)){
                float left=ox+x*tile,top=oy+y*tile;
                if(left<=getWidth()&&top<=getHeight()&&left+tile>=0&&top+tile*2.05f>=0)sprites.floating(canvas,game.seed,x,y,left,top,tile);
            }
            for(int y=0;y<game.size;y++)for(int x=0;x<game.size;x++){float left=ox+x*tile,top=oy+y*tile;if(left>getWidth()||top>getHeight()||left+tile<0||top+tile<0)continue;Game.Cell c=game.cells[y][x];if(!c.land)continue;
                sprites.ground(canvas,game.seed,x,y,left,top,tile);
                Game.Unit u=game.at(x,y);
                if(c.stain>=0)sprites.spot(canvas,c.stain,c.building!=0||u!=null,c.building==1,left,top,tile);
                boolean chosen=selected!=null&&selected.x==x&&selected.y==y;
                if(c.building!=0){sprites.building(canvas,c.building,c.mine,left,top,tile);if(c.owner>=0)sprites.flag(canvas,c.owner,left,top,tile);}
                if(u!=null){
                    if(c.building!=0)sprites.buildingUnitFade(canvas,left,top,tile);
                    boolean ready=u.owner==game.current&&!u.fired&&u.moves==game.rules.movement&&u.moves>0;
                    sprites.unit(canvas,u.owner,u.color,left,top,tile,ready);
                    if(selected!=null&&game.canAttack(selected,u)){
                        outline(canvas,left,top,Color.WHITE,.045f);
                        shotTarget(canvas,left,top);
                        if(u.owner==selected.owner){
                            sprites.exclamation(canvas,left,top,tile);
                        }
                    }
                }
                // Destination markers are drawn last so buildings cannot obscure them.
                if(reach!=null&&reach[y][x]>0){outline(canvas,left,top,0xffa8e6cd,.065f);float cx=left+tile*.5f,cy=top+tile*.5f;art.circle(canvas,cx,cy,tile*.095f,0xff102b40);art.circle(canvas,cx,cy,tile*.055f,0xffbaffde);}
                if(chosen)outline(canvas,left,top,0xffa5f5c0,.045f);
                if(u!=null&&u.owner==game.current&&u.fired)
                    art.circle(canvas,left+tile*.86f,top+tile*.86f,tile*.07f,Color.BLACK);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            scale.onTouchEvent(e);
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    lastX=downX=e.getX();lastY=downY=e.getY();dragged=false;pinchGesture=false;
                    held=false;removeCallbacks(holdAction);postDelayed(holdAction,ViewConfiguration.getLongPressTimeout());
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    removeCallbacks(holdAction);
                    dragged=true;pinchGesture=true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if(e.getPointerCount()>1){pinchGesture=true;dragged=true;break;}
                    if(pinchGesture)break; // Wait for a fresh touch after lifting a zoom finger.
                    if(Math.abs(e.getX()-downX)+Math.abs(e.getY()-downY)>12){dragged=true;removeCallbacks(holdAction);}
                    if(held)break;
                    if(dragged&&!scale.isInProgress()){
                        ox+=e.getX()-lastX;oy+=e.getY()-lastY;invalidate();
                    }
                    lastX=e.getX();lastY=e.getY();
                    break;
                case MotionEvent.ACTION_UP:
                    removeCallbacks(holdAction);
                    if(!held&&!dragged&&!pinchGesture){
                        if(e.getEventTime()-e.getDownTime()>=ViewConfiguration.getLongPressTimeout())performLongClick();
                        else {
                            int x=(int)Math.floor((e.getX()-ox)/tile),y=(int)Math.floor((e.getY()-oy)/tile);
                            if(game!=null&&selected!=null&&selected==game.at(x,y)){selected=null;message=cellInfo(x,y);refresh();}
                            else tap(x,y);
                            performClick();
                        }
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:removeCallbacks(holdAction);dragged=false;pinchGesture=false;break;
            }
            return true;
        }
        @Override protected void onDetachedFromWindow(){removeCallbacks(holdAction);super.onDetachedFromWindow();}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
