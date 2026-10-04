package com.example.colorbattle;

import java.io.Serializable;
import java.util.*;

/** Pure Java rules and state: controllers (human or bot) issue the same commands. */
public final class Game implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String[] COLORS = {"Blue", "Orange", "Red", "Pink"};
    public static final String[] SHAPES = {"Circle", "Square", "Triangle", "Star"};
    public static class Rules implements Serializable {
        private static final long serialVersionUID = 7689591962289334790L;
        public static final int SMALL_MAP=25, MEDIUM_MAP=32, LARGE_MAP=39;
        public int movement=3, range=3, price=5, income=2, mineIncome=2, upkeep=1;
    }
    public static class Cell implements Serializable {
        public boolean land; public int stain=-1, building=0, mine=-1, owner=-1;
    }
    public static class Unit implements Serializable {
        public int owner,color,x,y,moves; public boolean fired;
        Unit(int p,int c,int x,int y,int m) {owner=p;color=c;this.x=x;this.y=y;moves=m;}
    }
    public final Rules rules = new Rules();
    public final int size, players;
    public final Cell[][] cells;
    public final int[][] resources;
    public final List<Unit> units = new ArrayList<>();
    public boolean[] botPlayers;
    public int balanceVersion=1;
    public int roundVersion=1;
    public int current=0, turn=1, winner=-1;
    public final long seed;
    public Game(int players,long seed) {this(players,seed,0);}
    public Game(int players,long seed,int botCount) {this(players,seed,botCount,Rules.LARGE_MAP);}
    public Game(int players,long seed,int botCount,int mapSize) {
        if(mapSize!=Rules.SMALL_MAP&&mapSize!=Rules.MEDIUM_MAP&&mapSize!=Rules.LARGE_MAP)throw new IllegalArgumentException("Unknown map size");
        size=mapSize;cells=new Cell[size][size];
        if(players<2||players>4) throw new IllegalArgumentException("Choose 2–4 players");
        if(botCount<0||botCount>=players)throw new IllegalArgumentException("Keep at least one human player");
        this.players=players; this.seed=seed; resources=new int[players][4];
        botPlayers=new boolean[players];
        for(int p=players-botCount;p<players;p++)botPlayers[p]=true;
        for(int[] r:resources) Arrays.fill(r,5);
        for(int y=0;y<size;y++) for(int x=0;x<size;x++) cells[y][x]=new Cell();
        generate(new Random(seed)); beginTurn();
    }
    public static class Island implements Serializable {
        public final int x,y;
        public Island(int x,int y){this.x=x;this.y=y;}
    }
    public static class Bridge implements Serializable {
        public final int from,to,width;
        public Bridge(int from,int to,int width){this.from=from;this.to=to;this.width=width;}
    }
    // Generation metadata is optional in saves produced by older versions.
    public final List<Island> islands=new ArrayList<>();
    public final List<Bridge> bridges=new ArrayList<>();
    private void generate(Random r) {
        int maximum=(size-4)/7;
        int columns=maximum==3?3:maximum-1+r.nextInt(2),rows=maximum==3?3:maximum-1+r.nextInt(2),count=columns*rows;
        List<List<int[]>> footprints=new ArrayList<>();
        for(int row=0;row<rows;row++)for(int col=0;col<columns;col++) {
            int x=4+col*7+r.nextInt(2)+(maximum-columns)*3;
            int y=4+row*7+r.nextInt(2)+(maximum-rows)*3;
            islands.add(new Island(x,y));
            List<int[]> tiles=new ArrayList<>();
            int radius=1+r.nextInt(2);
            for(int dy=-radius;dy<=radius;dy++) {
                int left=1+r.nextInt(2),right=1+r.nextInt(2);
                for(int dx=-left;dx<=right;dx++) {
                    cells[y+dy][x+dx].land=true;tiles.add(new int[]{x+dx,y+dy});
                }
            }
            footprints.add(tiles);
        }
        // Planar local candidates avoid crossings and bridges through unrelated islands.
        List<int[]> candidates=new ArrayList<>();
        for(int i=0;i<count;i++) {
            if(i%columns+1<columns)candidates.add(new int[]{i,i+1});
            if(i+columns<count)candidates.add(new int[]{i,i+columns});
        }
        Collections.shuffle(candidates,r);
        boolean[][] linked=new boolean[count][count];
        int[] component=new int[count],degree=new int[count];
        for(int i=0;i<count;i++)component[i]=i;
        // Random spanning tree guarantees global connectivity.
        for(int[] edge:candidates)if(component[edge[0]]!=component[edge[1]]) {
            int old=component[edge[1]],replacement=component[edge[0]];
            for(int i=0;i<count;i++)if(component[i]==old)component[i]=replacement;
            connect(edge,linked,degree,r);
        }
        // Add loops until every island has two distinct neighbours, then extra shortcuts.
        Collections.shuffle(candidates,r);
        for(int[] edge:candidates)if(degree[edge[0]]<2||degree[edge[1]]<2)connect(edge,linked,degree,r);
        for(int[] edge:candidates)if(r.nextDouble()<0.28)connect(edge,linked,degree,r);
        for(int[] edge:candidates)if(bridges.size()==count)connect(edge,linked,degree,r);
        for(Bridge bridge:bridges) {
            Island a=islands.get(bridge.from),b=islands.get(bridge.to);
            if(bridge.to==bridge.from+1) {
                int mid=(a.x+b.x)/2;
                strip(a.x,a.y,mid,a.y,bridge.width);
                strip(mid,a.y,mid,b.y,bridge.width);
                strip(mid,b.y,b.x,b.y,bridge.width);
            } else {
                int mid=(a.y+b.y)/2;
                strip(a.x,a.y,a.x,mid,bridge.width);
                strip(a.x,mid,b.x,mid,bridge.width);
                strip(b.x,mid,b.x,b.y,bridge.width);
            }
        }
        // Spread starting islands using farthest-point sampling with a random first player.
        int[] owners=new int[count];Arrays.fill(owners,-1);
        int next=r.nextInt(count);
        for(int p=0;p<players;p++) {
            owners[next]=p;int best=-1;
            for(int i=0;i<count;i++)if(owners[i]<0) {
                int nearest=Integer.MAX_VALUE;
                for(int j=0;j<count;j++)if(owners[j]>=0) {
                    int dx=islands.get(i).x-islands.get(j).x,dy=islands.get(i).y-islands.get(j).y;
                    nearest=Math.min(nearest,dx*dx+dy*dy);
                }
                if(nearest>best){best=nearest;next=i;}
            }
        }
        List<Integer> neutral=new ArrayList<>();
        for(int i=0;i<count;i++)if(owners[i]<0)neutral.add(i);
        Collections.shuffle(neutral,r);
        for(int i=0;i<count;i++) {
            List<int[]> tiles=footprints.get(i);Collections.shuffle(tiles,r);
            int buildings=1+r.nextInt(2);
            for(int n=0;n<buildings;n++) {
                int[] tile=tiles.get(n);Cell c=cells[tile[1]][tile[0]];
                c.building=r.nextInt(3)==0?1:2;
                if(c.building==2)c.mine=r.nextInt(4);
                if(n==0&&owners[i]>=0){c.building=1;c.mine=-1;c.owner=owners[i];}
                // Guarantee that every resource color exists somewhere on the map.
                int color=neutral.indexOf(i);
                if(n==0&&color>=0&&color<4){c.building=2;c.mine=color;}
            }
        }
    }
    private void connect(int[] edge,boolean[][] linked,int[] degree,Random r) {
        int a=edge[0],b=edge[1];if(linked[a][b])return;
        linked[a][b]=linked[b][a]=true;degree[a]++;degree[b]++;
        bridges.add(new Bridge(a,b,1+r.nextInt(3)));
    }
    private void strip(int x1,int y1,int x2,int y2,int width) {
        int low=-(width/2),high=low+width-1;
        // Overlapping square cross sections keep bends connected even at width three.
        for(int y=Math.min(y1,y2);y<=Math.max(y1,y2);y++)
            for(int x=Math.min(x1,x2);x<=Math.max(x1,x2);x++)
                for(int dy=low;dy<=high;dy++)for(int dx=low;dx<=high;dx++)
                    if(inside(x+dx,y+dy))cells[y+dy][x+dx].land=true;
    }
    public boolean inside(int x,int y) {return x>=0&&y>=0&&x<size&&y<size;}
    public boolean isBot(int p) {return botPlayers!=null&&p>=0&&p<botPlayers.length&&botPlayers[p];}
    /** Serialized games from earlier releases retain their armies and resources. */
    public void upgradeSave() {
        if(balanceVersion<1){rules.price=5;balanceVersion=1;}
        if(roundVersion<1){turn=Math.max(1,(turn-1)/players+1);roundVersion=1;}
    }
    public int unitCost(int player,int color) {
        if(player<0||player>=players||color<0||color>=4)throw new IllegalArgumentException("Invalid player or color");
        int cost=rules.price;
        for(Unit unit:units)if(unit.owner==player&&unit.color==color)cost++;
        return cost;
    }
    public Unit at(int x,int y) {for(Unit u:units) if(u.x==x&&u.y==y)return u;return null;}
    public boolean alive(int p) {
        for(Unit u:units)if(u.owner==p)return true;
        for(Cell[] row:cells)for(Cell c:row)if(c.building==1&&c.owner==p)return true;
        return false;
    }
    private void beginTurn() {
        // A unit must survive the other teams' turns before it claims a building.
        for(Unit u:units)if(u.owner==current&&cells[u.y][u.x].building!=0)cells[u.y][u.x].owner=current;
        checkWinner();if(winner>=0)return;
        int bases=0;
        int[] before=resources[current].clone();
        for(int c=0;c<4;c++)resources[current][c]+=rules.income;
        for(Cell[] row:cells)for(Cell c:row)if(c.owner==current) {
            if(c.building==1)bases++;
            if(c.building==2)resources[current][c.mine]+=rules.mineIncome;
        }
        for(int c=0;c<4;c++) {
            int afterPayment=Math.max(0,resources[current][c]-bases*rules.upkeep);
            resources[current][c]=Math.max(afterPayment,before[c]+1);
        }
        for(Unit u:units)if(u.owner==current){u.moves=rules.movement;u.fired=false;}
    }
    public String buy(int x,int y,int color) {
        if(winner>=0)return "Game finished.";
        if(!inside(x,y)||color<0||color>3)return "Invalid purchase.";
        Cell c=cells[y][x];
        if(c.building!=1||c.owner!=current)return "Choose your own base.";
        if(at(x,y)!=null)return "Move the unit off this base first.";
        if(c.stain==color)return "This base's stained tile blocks that color.";
        int cost=unitCost(current,color);
        if(resources[current][color]<cost)return "Not enough "+COLORS[color]+" resources (need "+cost+").";
        resources[current][color]-=cost;units.add(new Unit(current,color,x,y,rules.movement));
        return "Unit recruited. It can move and attack now.";
    }
    public boolean canMove(Unit u) {
        return winner<0&&units.contains(u)&&u.owner==current&&!u.fired&&u.moves==rules.movement&&u.moves>0;
    }
    public int[][] distances(Unit u) {
        int[][] d=new int[size][size];for(int[] row:d)Arrays.fill(row,-1);
        if(!canMove(u))return d;
        ArrayDeque<int[]> q=new ArrayDeque<>();q.add(new int[]{u.x,u.y});d[u.y][u.x]=0;
        int[] dx={1,-1,0,0},dy={0,0,1,-1};
        while(!q.isEmpty()) {int[] p=q.remove();if(d[p[1]][p[0]]>=u.moves)continue;
            for(int i=0;i<4;i++){int x=p[0]+dx[i],y=p[1]+dy[i];
                if(inside(x,y)&&d[y][x]<0&&cells[y][x].land&&cells[y][x].stain!=u.color&&at(x,y)==null) {
                    d[y][x]=d[p[1]][p[0]]+1;q.add(new int[]{x,y});
                }
            }
        }return d;
    }
    public String move(Unit u,int x,int y) {
        if(winner>=0||!units.contains(u)||u.owner!=current)return "Select your unit.";
        if(!canMove(u))return "Movement used. A unit can move once, before firing.";
        if(!inside(x,y))return "Outside map.";
        int d=distances(u)[y][x];if(d<=0)return "No path within remaining movement.";
        u.x=x;u.y=y;u.moves=0;return "Moved. Capture happens at the start of your next turn if this unit survives.";
    }
    public boolean canAttack(Unit u,Unit target) {
        if(winner>=0||!units.contains(u)||!units.contains(target)||u==target||u.owner!=current||u.fired)return false;
        if((target.color+1)%4!=u.color)return false;
        int distance=Math.abs(u.x-target.x)+Math.abs(u.y-target.y);
        if(distance<1||distance>rules.range||(u.x!=target.x&&u.y!=target.y))return false;
        int dx=Integer.signum(target.x-u.x),dy=Integer.signum(target.y-u.y);
        for(int i=1;i<distance;i++)if(at(u.x+dx*i,u.y+dy*i)!=null)return false;
        return true;
    }
    public String attack(Unit u,Unit target) {
        if(!canAttack(u,target))return "Ignored: check color, straight range, clear line of fire, and remaining shot.";
        cells[target.y][target.x].stain=u.color;units.remove(target);u.fired=true;checkWinner();
        return (target.owner==u.owner?"Friendly unit":"Enemy")+" eliminated; tile stained "+COLORS[u.color]+".";
    }
    private void checkWinner(){int count=0,last=-1;for(int p=0;p<players;p++)if(alive(p)){count++;last=p;}if(count==1)winner=last;}
    public void endTurn() {
        if(winner>=0)return;
        int previous=current;
        do {current=(current+1)%players;}while(!alive(current));
        if(current<=previous)turn++;
        beginTurn();
    }
}
