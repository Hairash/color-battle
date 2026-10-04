package com.example.colorbattle;
import java.util.*;
import java.io.*;
public class GameTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static Game empty(){Game g=new Game(2,1);g.units.clear();for(Game.Cell[] row:g.cells)for(Game.Cell c:row){c.land=true;c.building=0;c.owner=-1;c.stain=-1;}g.cells[0][0].building=1;g.cells[0][0].owner=0;g.cells[38][38].building=1;g.cells[38][38].owner=1;return g;}
 public static void main(String[] args)throws Exception {
  for(int mapSize:new int[]{Game.Rules.SMALL_MAP,Game.Rules.MEDIUM_MAP,Game.Rules.LARGE_MAP})for(int n=2;n<=4;n++)for(int seed=0;seed<100;seed++){
   Game g=new Game(n,seed,0,mapSize);check(g.size==mapSize&&g.cells.length==mapSize,"Selected map dimensions");int count=0,sx=0,sy=0;for(int y=0;y<g.size;y++)for(int x=0;x<g.size;x++)if(g.cells[y][x].land){count++;sx=x;sy=y;}
   boolean[][] seen=new boolean[g.size][g.size];ArrayDeque<int[]> q=new ArrayDeque<>();q.add(new int[]{sx,sy});seen[sy][sx]=true;int visited=0;
   while(!q.isEmpty()){int[] p=q.remove();visited++;for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int x=p[0]+d[0],y=p[1]+d[1];if(g.inside(x,y)&&g.cells[y][x].land&&!seen[y][x]){seen[y][x]=true;q.add(new int[]{x,y});}}}
   check(count==visited,"Connected terrain seed "+seed);for(int p=0;p<n;p++){int bases=0;for(Game.Cell[] row:g.cells)for(Game.Cell c:row)if(c.owner==p&&c.building==1)bases++;check(bases==1,"Starting base");}
   check(g.islands.size()>=(mapSize==39?16:9)&&g.islands.size()<=(mapSize==25?9:mapSize==32?16:25),"Island count");
   int[] degree=new int[g.islands.size()],buildings=new int[g.islands.size()];
   boolean[][] edges=new boolean[degree.length][degree.length];
   for(Game.Bridge bridge:g.bridges){check(bridge.from!=bridge.to&&!edges[bridge.from][bridge.to],"Distinct bridges");edges[bridge.from][bridge.to]=edges[bridge.to][bridge.from]=true;degree[bridge.from]++;degree[bridge.to]++;check(bridge.width>=1&&bridge.width<=3,"Bridge width");}
   for(int d:degree)check(d>=2,"Two neighbours per island");
   check(g.bridges.size()>g.islands.size(),"Multiple graph loops");
   Set<Integer> reached=new HashSet<>();reached.add(0);boolean changed=true;
   while(changed){changed=false;for(Game.Bridge edge:g.bridges)if(reached.contains(edge.from)||reached.contains(edge.to)){changed|=reached.add(edge.from);changed|=reached.add(edge.to);}}
   check(reached.size()==g.islands.size(),"Connected island graph");
   boolean[] mineColors=new boolean[4];
   for(int y=0;y<g.size;y++)for(int x=0;x<g.size;x++)if(g.cells[y][x].building!=0){
    check(g.cells[y][x].land,"Building on land");int nearest=-1,best=Integer.MAX_VALUE;
    for(int i=0;i<g.islands.size();i++){Game.Island island=g.islands.get(i);int dx=x-island.x,dy=y-island.y,d=dx*dx+dy*dy;if(d<best){best=d;nearest=i;}}
    buildings[nearest]++;if(g.cells[y][x].building==2)mineColors[g.cells[y][x].mine]=true;
   }
   for(int b:buildings)check(b>=1&&b<=2,"One or two buildings per island");
   for(boolean color:mineColors)check(color,"Every mine color available");
   check(g.resources[0][0]==6&&g.resources[1][0]==5,"Initial income timing");
  }
  for(int mapSize:new int[]{Game.Rules.SMALL_MAP,Game.Rules.MEDIUM_MAP,Game.Rules.LARGE_MAP}){
   Game original=new Game(4,42,3,mapSize);ByteArrayOutputStream saved=new ByteArrayOutputStream();
   new ObjectOutputStream(saved).writeObject(original);
   Game loaded=(Game)new ObjectInputStream(new ByteArrayInputStream(saved.toByteArray())).readObject();
   check(loaded.size==mapSize&&loaded.cells.length==mapSize&&loaded.islands.size()==original.islands.size(),"Map size survives saving");
  }
  Game g=empty();g.buy(0,0,0);check(g.units.size()==1&&g.resources[0][0]==1,"Purchase");g.buy(0,0,1);check(g.units.size()==1,"Occupied base");Game.Unit u=g.units.get(0);g.move(u,2,0);check(u.x==2&&u.moves==0,"One move consumes allowance");g.move(u,4,0);check(u.x==2,"Cannot overspend movement");g.cells[0][3].stain=0;g.move(u,3,0);check(u.x==2,"Same color terrain blocked");
  g=empty();check(g.unitCost(0,3)==5&&g.unitCost(1,3)==5,"Base unit cost per player");
  g.buy(0,0,3);check(g.unitCost(0,3)==6&&g.resources[0][3]==1,"First Pink raises price to six");
  g.move(g.at(0,0),1,0);g.resources[0][3]=5;
  check(g.buy(0,0,3).startsWith("Not enough")&&g.at(0,0)==null,"Five cannot buy second Pink");
  g.resources[0][3]=6;g.buy(0,0,3);
  check(g.unitCost(0,3)==7&&g.resources[0][3]==0,"Second Pink raises price to seven");
  g.units.remove(g.at(1,0));check(g.unitCost(0,3)==6,"Death lowers price to six");
  check(g.unitCost(1,3)==5,"Other player's price unaffected");
  g=empty();u=new Game.Unit(0,1,2,2,3);g.units.add(u);
  g.cells[2][3].building=2;g.cells[2][3].mine=0;
  check(g.distances(u)[2][3]==1,"Buildings are reachable destinations");
  g.move(u,3,2);g.move(u,4,2);check(u.x==3,"Cannot split movement into two actions");
  Game.Unit afterMove=new Game.Unit(1,0,5,2,3);g.units.add(afterMove);check(g.canAttack(u,afterMove),"Can shoot after move");g.attack(u,afterMove);
  g.endTurn();g.endTurn();check(g.canMove(u),"Movement restored next turn");
  Game.Unit beforeMove=new Game.Unit(1,0,5,2,3);g.units.add(beforeMove);g.attack(u,beforeMove);g.move(u,3,3);check(u.y==2&&!g.canMove(u),"Cannot move after firing");check(g.distances(u)[3][3]<0,"No reachable dots after firing");
  g=empty();u=new Game.Unit(0,0,2,2,3);g.units.add(u);g.cells[2][3].stain=0;g.move(u,3,2);check(u.x==2&&g.canMove(u),"Blocked move preserves action");
  Game.Unit ignored=new Game.Unit(1,0,2,3,3);g.units.add(ignored);g.attack(u,ignored);check(g.canMove(u),"Ignored attack preserves movement");
  for(int c=0;c<4;c++)for(int t=0;t<4;t++){g=empty();u=new Game.Unit(0,c,2,2,3);Game.Unit target=new Game.Unit(1,t,5,2,3);g.units.add(u);g.units.add(target);boolean lethal=(t+1)%4==c;g.attack(u,target);check(g.units.contains(target)!=lethal,"Color matchup");check(u.fired==lethal,"Ignored shot free");if(lethal)check(g.cells[2][5].stain==c,"Kill stain");}
  g=empty();u=new Game.Unit(0,1,2,2,3);Game.Unit target=new Game.Unit(0,0,4,2,3);g.units.add(u);g.units.add(target);
  check(!g.canAttack(u,u),"A unit cannot shoot itself");
  check(g.canAttack(u,target),"Friendly units can be valid targets");
  g.attack(u,target);check(!g.units.contains(target)&&g.cells[2][4].stain==1&&u.fired,"Friendly shot kills and stains normally");
  g=empty();u=new Game.Unit(0,1,2,2,3);target=new Game.Unit(0,1,4,2,3);g.units.add(u);g.units.add(target);
  check(!g.canAttack(u,target),"Friendly fire still obeys color matchup");
  g=empty();u=new Game.Unit(0,1,2,2,3);target=new Game.Unit(1,0,5,2,3);g.units.add(u);g.units.add(target);g.cells[2][3].land=false;check(g.canAttack(u,target),"Shots cross air");check(g.distances(u)[2][3]<0,"Cannot move into air");g.cells[2][3].land=true;g.units.add(new Game.Unit(0,2,3,2,3));check(!g.canAttack(u,target),"Unit blocks shot");g.units.remove(2);g.attack(u,target);target=new Game.Unit(1,0,2,3,3);g.units.add(target);check(!g.canAttack(u,target),"One attack per turn");
  g=empty();u=new Game.Unit(0,0,4,4,0);g.units.add(u);g.cells[4][4].building=2;g.cells[4][4].mine=2;check(g.cells[4][4].owner==-1,"No early capture");g.endTurn();check(g.cells[4][4].owner==-1,"No capture at end of turn");int red=g.resources[0][2];g.endTurn();check(g.cells[4][4].owner==0,"Capture at start of next turn");check(g.resources[0][2]==red+3,"Captured mine pays income on that turn");check(u.moves==3&&!u.fired,"Unit refresh");
  g=empty();u=new Game.Unit(0,0,38,38,0);g.units.add(u);g.endTurn();
  Game.Unit guard=new Game.Unit(1,1,36,38,3);g.units.add(guard);
  g.attack(guard,u);g.endTurn();check(g.cells[38][38].owner==1&&g.winner==-1,"Killed occupier cannot capture next turn");
  g=empty();for(int x=0;x<8;x++){g.cells[0][x].building=1;g.cells[0][x].owner=0;}
  int[] previous={0,4,8,2};System.arraycopy(previous,0,g.resources[0],0,4);
  g.endTurn();g.endTurn();
  for(int c=0;c<4;c++)check(g.resources[0][c]==previous[c]+1,"Each resource gains at least one despite upkeep");
  g.endTurn();g.endTurn();
  for(int c=0;c<4;c++)check(g.resources[0][c]==previous[c]+2,"Minimum gain repeats each turn");
  g=empty();u=new Game.Unit(0,0,38,38,3);g.units.add(u);g.endTurn();check(g.winner==-1,"Last building remains enemy-owned during its turn");g.endTurn();check(g.winner==0,"Capture final building wins at next turn start");
  g=empty();g.cells[38][38].building=2;g.cells[38][38].owner=1;
  u=new Game.Unit(0,1,2,2,3);target=new Game.Unit(1,0,4,2,3);g.units.add(u);g.units.add(target);
  g.attack(u,target);check(g.winner==0,"Last enemy unit shot wins immediately when only mines remain");
  g=empty();u=new Game.Unit(0,1,2,2,3);target=new Game.Unit(1,0,4,2,3);g.units.add(u);g.units.add(target);
  g.attack(u,target);check(g.winner==-1,"Unoccupied enemy base can recruit after last unit is shot");
  g=empty();g.cells[37][38].building=2;g.cells[37][38].mine=0;g.cells[37][38].owner=1;
  u=new Game.Unit(0,0,38,38,0);g.units.add(u);g.endTurn();g.endTurn();
  check(g.winner==0,"Last enemy base capture wins even if enemy owns a mine");
  g=new Game(4,42);
  check(g.current==0&&g.turn==1,"Round starts at one");
  for(int p=1;p<4;p++){g.endTurn();check(g.current==p&&g.turn==1,"Other players share round one");}
  g.endTurn();check(g.current==0&&g.turn==2,"Next first-player turn starts round two");
  for(Game.Cell[] row:g.cells)for(Game.Cell cell:row)if(cell.owner==1)cell.owner=-1;
  g.endTurn();check(g.current==2&&g.turn==2,"Eliminated seat is skipped without incrementing round");
  g.endTurn();check(g.current==3&&g.turn==2,"Later seat keeps the same round");
  g.endTurn();check(g.current==0&&g.turn==3,"Round increments after skipped seat wraps");
  g=new Game(4,42);ByteArrayOutputStream data=new ByteArrayOutputStream();new ObjectOutputStream(data).writeObject(g);Game restored=(Game)new ObjectInputStream(new ByteArrayInputStream(data.toByteArray())).readObject();check(restored.players==4&&restored.seed==42&&restored.resources[0][0]==6,"Save round trip");
  g.roundVersion=0;g.turn=9;data=new ByteArrayOutputStream();new ObjectOutputStream(data).writeObject(g);
  restored=(Game)new ObjectInputStream(new ByteArrayInputStream(data.toByteArray())).readObject();restored.upgradeSave();
  check(restored.turn==3&&restored.roundVersion==1,"Old per-seat turn counter converts to rounds");
  g=empty();g.rules.price=3;g.balanceVersion=0;g.resources[0][2]=11;g.units.add(new Game.Unit(0,2,5,5,3));
  data=new ByteArrayOutputStream();new ObjectOutputStream(data).writeObject(g);restored=(Game)new ObjectInputStream(new ByteArrayInputStream(data.toByteArray())).readObject();restored.upgradeSave();
  check(restored.rules.price==5&&restored.unitCost(0,2)==6&&restored.resources[0][2]==11&&restored.units.size()==1,"Old save balance migration preserves progress");
  g=new Game(3,42,2);data=new ByteArrayOutputStream();new ObjectOutputStream(data).writeObject(g);restored=(Game)new ObjectInputStream(new ByteArrayInputStream(data.toByteArray())).readObject();
  check(!restored.isBot(0)&&restored.isBot(1)&&restored.isBot(2),"Bot seats survive save");
  g=empty();g.botPlayers[1]=true;g.current=1;
  g.units.add(new Game.Unit(1,1,2,2,3));g.units.add(new Game.Unit(0,0,4,2,3));
  Bot.playTurn(g);check(g.at(4,2)==null&&g.cells[2][4].stain==1,"Bot takes legal color shot");
  check(g.current==0,"Bot hands turn back");
  g=empty();g.botPlayers[1]=true;g.current=1;
  g.units.add(new Game.Unit(1,1,2,2,3));target=new Game.Unit(1,0,4,2,3);g.units.add(target);
  Bot.playTurn(g);check(g.units.contains(target),"Bot does not shoot allied units");
  g=empty();g.botPlayers[1]=true;g.current=1;
  g.units.add(new Game.Unit(1,0,3,3,3));g.cells[3][3].building=2;g.cells[3][3].mine=0;
  Bot.playTurn(g);check(g.cells[3][3].owner==-1,"Bot must wait through opponent turn to capture");g.endTurn();check(g.cells[3][3].owner==1,"Bot holds and captures building next turn");
  for(int seed=0;seed<1000;seed++){
   g=new Game(2,seed,1);g.endTurn();
   Bot.playTurn(g);
   int army=0;for(Game.Unit unit:g.units)if(unit.owner==1)army++;
   check(army==4,"Bot buys four units from one starting base, seed "+seed+": "+army);
  }
  g=empty();g.botPlayers[1]=true;g.current=1;
  g.cells[38][38].building=0;g.cells[38][38].owner=-1;
  g.cells[19][19].building=1;g.cells[19][19].owner=1;
  for(int c=0;c<4;c++)g.resources[1][c]=6;
  g.units.add(new Game.Unit(0,2,19,14,3)); // Red approaches from north; Pink counters Red.
  Bot.playTurn(g);
  boolean pink=false;for(Game.Unit unit:g.units)if(unit.owner==1&&unit.color==3)pink=true;
  check(pink,"Bot recruits Pink against approaching Red");
  check(g.cells[14][19].stain==3,"Pink bot moves north and kills Red");
  for(int seed=0;seed<8;seed++){
   g=new Game(4,seed,3);
   for(int turns=0;turns<24&&g.winner<0;turns++){
    if(g.isBot(g.current))Bot.playTurn(g);else g.endTurn();
    for(Game.Unit unit:g.units)check(g.cells[unit.y][unit.x].land,"Bot keeps units on land");
    check(g.current>=0&&g.current<g.players,"Valid next player");
   }
  }
  System.out.println("PASS: 900 maps, economy, movement, combat, capture, bots, persistence");
 }
}
