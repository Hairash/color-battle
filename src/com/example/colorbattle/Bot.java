package com.example.colorbattle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

/** A controller that plays through Game's public, validated commands. */
public final class Bot {
    private Bot() {}

    public static void playTurn(Game game) {
        if(game.winner>=0 || !game.isBot(game.current))return;
        int owner=game.current;
        for(Game.Unit unit:new ArrayList<Game.Unit>(game.units))
            if(unit.owner==owner && game.units.contains(unit))act(game,unit);

        // A new unit acts immediately and vacates its base, allowing another purchase.
        boolean purchased;
        do {
            purchased=false;
            for(int y=0;y<game.size;y++)for(int x=0;x<game.size;x++) {
                Game.Cell cell=game.cells[y][x];
                if(cell.building!=1 || cell.owner!=owner || game.at(x,y)!=null)continue;
                int color=chooseColor(game,x,y);
                if(color<0)continue;
                if(game.buy(x,y,color).startsWith("Unit recruited")) {
                    purchased=true;
                    act(game,game.at(x,y),canAffordMore(game));
                }
            }
        } while(purchased && game.winner<0);
        game.endTurn();
    }

    private static boolean canAffordMore(Game game) {
        for(int color=0;color<4;color++)if(game.resources[game.current][color]>=game.unitCost(game.current,color))return true;
        return false;
    }

    private static int chooseColor(Game game,int x,int y) {
        int best=-1;double bestScore=-1e9;
        for(int color=0;color<4;color++) {
            if(game.resources[game.current][color]<game.unitCost(game.current,color) || game.cells[y][x].stain==color)continue;
            double score=game.resources[game.current][color]*0.6;
            int same=0;
            for(Game.Unit unit:game.units) {
                int distance=Math.abs(x-unit.x)+Math.abs(y-unit.y);
                if(unit.owner==game.current) {
                    if(unit.color==color)same++;
                } else if((unit.color+1)%4==color) {
                    // Nearby enemy colors take priority over a small resource advantage.
                    score+=32.0/(1+distance);
                    if(distance<=game.rules.movement+game.rules.range)score+=3;
                }
            }
            score-=same*1.4;
            if(score>bestScore){bestScore=score;best=color;}
        }
        return best;
    }

    private static void act(Game game,Game.Unit unit) {act(game,unit,false);}

    private static void act(Game game,Game.Unit unit,boolean clearBaseForRecruitment) {
        if(game.winner>=0 || !game.units.contains(unit))return;
        // A unit holding a capturable building should stay on it until turn end.
        boolean capturing=game.cells[unit.y][unit.x].building!=0
            && game.cells[unit.y][unit.x].owner!=unit.owner;
        Game.Unit victim=clearBaseForRecruitment?null:target(game,unit);
        if(victim!=null)game.attack(unit,victim);
        else if(!capturing && game.canMove(unit)) {
            int[] destination=destination(game,unit);
            if(clearBaseForRecruitment && (destination==null ||
                    Math.abs(destination[0]-unit.x)+Math.abs(destination[1]-unit.y)<2)) {
                int[] clearing=clearingMove(game,unit);
                if(clearing!=null)destination=clearing;
            }
            if(destination!=null)game.move(unit,destination[0],destination[1]);
            victim=target(game,unit);
            if(victim!=null)game.attack(unit,victim);
        }
    }

    private static int[] clearingMove(Game game,Game.Unit unit) {
        int[][] reachable=game.distances(unit);
        int[] best=null;double score=-1e9;
        for(int y=0;y<game.size;y++)for(int x=0;x<game.size;x++) {
            int steps=reachable[y][x];if(steps<1)continue;
            int onward=0;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int xx=x+d[0],yy=y+d[1];
                if(game.inside(xx,yy)&&game.cells[yy][xx].land&&game.at(xx,yy)==null)onward++;
            }
            double value=steps*10+onward*2;
            if(game.cells[y][x].building!=0&&game.cells[y][x].owner!=unit.owner)value+=4;
            if(value>score){score=value;best=new int[]{x,y};}
        }
        return best;
    }

    private static Game.Unit target(Game game,Game.Unit unit) {
        Game.Unit best=null;int distance=Integer.MAX_VALUE;
        for(Game.Unit other:game.units)if(other.owner!=unit.owner&&game.canAttack(unit,other)) {
            int d=Math.abs(unit.x-other.x)+Math.abs(unit.y-other.y);
            if(d<distance){best=other;distance=d;}
        }
        return best;
    }

    private static int[] destination(Game game,Game.Unit unit) {
        int n=game.size,total=n*n,start=unit.y*n+unit.x;
        int[] distance=new int[total],previous=new int[total];
        Arrays.fill(distance,-1);
        boolean[][] occupied=new boolean[n][n];
        for(Game.Unit other:game.units)if(other!=unit)occupied[other.y][other.x]=true;
        ArrayDeque<Integer> queue=new ArrayDeque<>();queue.add(start);distance[start]=0;
        int[] dx={1,-1,0,0},dy={0,0,1,-1};
        while(!queue.isEmpty()) {
            int here=queue.remove(),x=here%n,y=here/n;
            for(int i=0;i<4;i++) {
                int xx=x+dx[i],yy=y+dy[i];
                if(!game.inside(xx,yy))continue;
                int next=yy*n+xx;Game.Cell cell=game.cells[yy][xx];
                if(distance[next]>=0||!cell.land||cell.stain==unit.color||occupied[yy][xx])continue;
                distance[next]=distance[here]+1;previous[next]=here;queue.add(next);
            }
        }
        int mines=0;
        for(Game.Cell[] row:game.cells)for(Game.Cell cell:row)
            if(cell.building==2 && cell.owner==game.current)mines++;
        boolean expansion=game.turn<=8 || mines<4;
        int goal=-1;double bestScore=Double.POSITIVE_INFINITY;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++) {
            Game.Cell cell=game.cells[y][x];int index=y*n+x;
            if(distance[index]<1||cell.building==0||cell.owner==unit.owner)continue;
            double reward=cell.building==2?(expansion?8:5):4;
            if(cell.owner>=0)reward+=2;
            double score=distance[index]-reward;
            if(score<bestScore){bestScore=score;goal=index;}
        }
        // For the color that kills an approaching enemy, route to a clear firing cell.
        for(Game.Unit enemy:game.units)if(enemy.owner!=unit.owner &&
                (enemy.color+1)%4==unit.color) {
            int threat=distanceToOwnedBuilding(game,enemy.x,enemy.y);
            double reward=threat<=7?12:9;
            for(int axis=0;axis<4;axis++)for(int range=1;range<=game.rules.range;range++) {
                int x=enemy.x+dx[axis]*range,y=enemy.y+dy[axis]*range;
                if(!game.inside(x,y))continue;
                int index=y*n+x;
                if(distance[index]<1||!clearShot(game,unit,x,y,enemy,occupied))continue;
                double score=distance[index]-reward;
                if(score<bestScore){bestScore=score;goal=index;}
            }
        }
        if(goal<0)return null;
        int step=goal;
        while(distance[step]>game.rules.movement)step=previous[step];
        if(step==start)return null;
        return new int[]{step%n,step/n};
    }

    private static int distanceToOwnedBuilding(Game game,int x,int y) {
        int best=Integer.MAX_VALUE;
        for(int yy=0;yy<game.size;yy++)for(int xx=0;xx<game.size;xx++)
            if(game.cells[yy][xx].building!=0 && game.cells[yy][xx].owner==game.current)
                best=Math.min(best,Math.abs(x-xx)+Math.abs(y-yy));
        return best;
    }

    private static boolean clearShot(Game game,Game.Unit shooter,int x,int y,
            Game.Unit enemy,boolean[][] occupied) {
        if(occupied[y][x]||game.cells[y][x].stain==shooter.color)return false;
        if(x!=enemy.x && y!=enemy.y)return false;
        int dx=Integer.signum(enemy.x-x),dy=Integer.signum(enemy.y-y);
        int range=Math.abs(enemy.x-x)+Math.abs(enemy.y-y);
        if(range<1||range>game.rules.range)return false;
        for(int step=1;step<range;step++) {
            int xx=x+dx*step,yy=y+dy*step;
            if(!game.cells[yy][xx].land||occupied[yy][xx])return false;
        }
        return true;
    }
}
