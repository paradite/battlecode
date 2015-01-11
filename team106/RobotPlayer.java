package team106;

import battlecode.common.*;

import java.util.*;

public class RobotPlayer {
    static int ChargeTurn = 1300;
    private static int ExecuteStrategyTurn = 600;
    private static int StopMinerFactoryBuildTurn = 500;
    private static int StopMinerSpawnTurn = 800;
    private static int SpawnCommanderTurn = 850;
    private static int MaxMiner = 80;
    static Random rand;
    static Direction facing;

    static Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};


    final static int MinerNumChannel = 3;
    final static int BeaverNumChannel = 2;
    final static int StrategyNumChannel = 100;

    public static void run(RobotController rc) {
        BaseBot myself;
        rand = new Random(rc.getID());
        facing = getRandomDirection();
        if (rc.getType() == RobotType.HQ) {
            myself = new HQ(rc);
        } else if (rc.getType() == RobotType.BEAVER) {
            myself = new Beaver(rc);
        } else if (rc.getType() == RobotType.MINER) {
            myself = new Miner(rc);
        } else if (rc.getType() == RobotType.BARRACKS || rc.getType() == RobotType.HELIPAD || 
        		rc.getType() == RobotType.TANKFACTORY || rc.getType() == RobotType.MINERFACTORY || 
        		rc.getType() == RobotType.TRAININGFIELD || rc.getType() == RobotType.TECHNOLOGYINSTITUTE) {
            myself = new SimpleBuilding(rc);
        } else if (rc.getType() == RobotType.SOLDIER || rc.getType() == RobotType.DRONE || 
        		rc.getType() == RobotType.TANK || rc.getType() == RobotType.COMMANDER) {
            myself = new SimpleFighter(rc);
        } else if (rc.getType() == RobotType.TOWER) {
            myself = new Tower(rc);
        } else {
            myself = new BaseBot(rc);
        }

        while (true) {
            try {
                myself.go();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static class BaseBot {
        protected RobotController rc;
        protected MapLocation myHQ, theirHQ;
        protected Team myTeam, theirTeam;

        public BaseBot(RobotController rc) {
            this.rc = rc;
            this.myHQ = rc.senseHQLocation();
            this.theirHQ = rc.senseEnemyHQLocation();
            this.myTeam = rc.getTeam();
            this.theirTeam = this.myTeam.opponent();
        }

        public Direction[] getDirectionsToward(MapLocation dest) {
            Direction toDest = rc.getLocation().directionTo(dest);
            Direction[] dirs = {toDest,
                    toDest.rotateLeft(), toDest.rotateRight(),
                    toDest.rotateLeft().rotateLeft(), toDest.rotateRight().rotateRight()};

            return dirs;
        }

        public Direction getMoveDir(MapLocation dest) {
            Direction[] dirs = getDirectionsToward(dest);
            for (Direction d : dirs) {
                if (rc.canMove(d)) {
                    return d;
                }
            }
            return null;
        }

        public Direction getSpawnDirection(RobotType type) {
            Direction[] dirs = getDirectionsToward(this.theirHQ);
            for (Direction d : dirs) {
                if (rc.canSpawn(d, type)) {
                    return d;
                }
            }
            return null;
        }

        public Direction getBuildDirection(RobotType type) {
            Direction[] dirs = getDirectionsToward(this.theirHQ);
            for (Direction d : dirs) {
                if (rc.canBuild(d, type)) {
                    return d;
                }
            }
            return null;
        }

        public RobotInfo[] getAllies() {
            RobotInfo[] allies = rc.senseNearbyRobots(Integer.MAX_VALUE, myTeam);
            return allies;
        }

        public RobotInfo[] getEnemiesInAttackingRange() {
            RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.SOLDIER.attackRadiusSquared, theirTeam);
            return enemies;
        }

        public void attackLeastHealthEnemy(RobotInfo[] enemies) throws GameActionException {
            if (enemies.length == 0) {
                return;
            }

            double minEnergon = Double.MAX_VALUE;
            MapLocation toAttack = null;
            for (RobotInfo info : enemies) {
                if (info.health < minEnergon) {
                    toAttack = info.location;
                    minEnergon = info.health;
                }
            }

            rc.attackLocation(toAttack);
        }

        public void autoAttack() throws GameActionException {
            RobotInfo[] enemies = getEnemiesInAttackingRange();
            if (enemies.length > 0) {
                //attack!
                if (rc.isWeaponReady()) {
                    attackLeastHealthEnemy(enemies);
                }
            }
        }

       

        public boolean tryBuild(RobotType type) throws GameActionException {
            if(rc.isCoreReady() && rc.getTeamOre() > type.oreCost){
                Direction newDir = getBuildDirection(type);
                if (newDir != null) {
                    rc.build(newDir, type);
                    return true;
                }
            }
            return false;
        }

        public void mineOrMove() throws GameActionException {
            if(rc.isCoreReady() && rc.senseOre(rc.getLocation())>100 && rc.canMine()){
                if (rand.nextInt(10) < 9) {
                    rc.mine();
                } else {
                    moveRandom();
                }
            }else if(rc.isCoreReady() && rc.senseOre(rc.getLocation())>10 && rc.canMine()){
                if (rand.nextInt(10) < 8) {
                    rc.mine();
                } else {
                    moveRandom();
                }
            }else if(rc.isCoreReady() && rc.senseOre(rc.getLocation())>1 && rc.canMine()) {
                if (rand.nextInt(10) < 5) {
                    rc.mine();
                } else {
                    moveRandom();
                }
            }
            else if(rc.isCoreReady()){
                moveRandom();
            }
        }

        public void moveRandom() throws GameActionException {
            randomTurnFacing();
            MapLocation locInfront = rc.getLocation().add(facing);
            //Check safe
            MapLocation[] enemyTowers = rc.senseEnemyTowerLocations();
            boolean safe = true;
            for(MapLocation m:enemyTowers){
                if(m.distanceSquaredTo(locInfront) <= RobotType.TOWER.attackRadiusSquared){
                    safe = false;
                    break;
                }
            }

            if(rc.senseTerrainTile(locInfront) != TerrainTile.NORMAL){
                turnFacing();
            }
            if(!safe){
                turnBack();
            }
            if(rc.isCoreReady() && rc.canMove(facing)){
                rc.move(facing);
            }else if(rc.isCoreReady() && rc.canMove(facing.opposite())){
                rc.move(facing.opposite());
            }
        }

        public void randomTurnFacing() {
            if(rand.nextInt(20) < 1){
                turnFacing();
            }
        }

        public void turnFacing() {
            if(rand.nextInt(10) < 5){
                facing = facing.rotateLeft();
            }else {
                facing = facing.rotateRight();
            }
        }

        public void turnBack() {
            facing = facing.opposite();
        }

        public MapLocation getNearestTower() throws GameActionException {
            MapLocation[] enemyTowers = rc.senseEnemyTowerLocations();
            if(enemyTowers.length > 0){
                int nearestDis = 99999;
                MapLocation nearestTower = enemyTowers[0];
                for(MapLocation m:enemyTowers){
                    if(m.distanceSquaredTo(rc.getLocation()) <= nearestDis){
                        nearestDis = m.distanceSquaredTo(rc.getLocation());
                        nearestTower = m;
                    }
                }
                return nearestTower;
            }
            return null;
        }

        private void transferSupplies() throws GameActionException {
            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED,myTeam);
            double lowerestSupply = rc.getSupplyLevel();
            double transferAmount = 0;
            MapLocation transferLocation = null;
            for(RobotInfo m:nearbyAllies){
                if(m.supplyLevel < lowerestSupply){
                    lowerestSupply = m.supplyLevel;
                    transferAmount = (rc.getSupplyLevel() - m.supplyLevel)/2;
                    transferLocation = m.location;
                }
            }
            if(transferLocation != null && transferAmount > 0){
                rc.transferSupplies((int) transferAmount, transferLocation);
            }
        }

        public void beginningOfTurn() {
            if (rc.senseEnemyHQLocation() != null) {
                this.theirHQ = rc.senseEnemyHQLocation();
            }
        }

        public void endOfTurn() throws GameActionException {

        }

        public void go() throws GameActionException {
            beginningOfTurn();
            transferSupplies();
            execute();
            endOfTurn();
        }

        public void execute() throws GameActionException {
            rc.yield();
        }
    }
    public static class SimpleBuilding extends BaseBot {
        public SimpleBuilding(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
            RobotType toSpawn = null;

            if (rc.getType() == RobotType.BARRACKS) new Barracks(rc).execute();
            else if (rc.getType() == RobotType.HELIPAD) toSpawn = RobotType.DRONE;
            else if (rc.getType() == RobotType.TANKFACTORY) toSpawn = RobotType.TANK;
            else if (rc.getType() == RobotType.TRAININGFIELD) toSpawn = RobotType.COMMANDER;
            else if (rc.getType() == RobotType.MINERFACTORY)  new MinerFactory(rc).execute();
            
            if (toSpawn != null && rc.isCoreReady() && rc.getTeamOre() > toSpawn.oreCost && (rc.readBroadcast(100) != 0 || rc.getType() == RobotType.TANKFACTORY)) {
                Direction newDir = getSpawnDirection(toSpawn);
                if (newDir != null) {
                    rc.spawn(newDir, toSpawn);
                }
            }

            rc.yield();
        }
        
        public boolean trySpawn(RobotType type) throws GameActionException {
            if(rc.isCoreReady() && rc.getTeamOre() > type.oreCost){
                Direction newDir = getSpawnDirection(type);
                if (newDir != null) {
                    rc.spawn(newDir, type);
                    return true;
                }
            }
            return false;
        }
    }

    public static class SimpleFighter extends BaseBot {
        public SimpleFighter(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
        	  autoAttack();
              if (rc.isCoreReady()) {
                  int rallyX = rc.readBroadcast(0);
                  int rallyY = rc.readBroadcast(1);
                  MapLocation rallyPoint = new MapLocation(rallyX, rallyY);
            
                  Direction newDir = getMoveDir(rallyPoint);
                  if (newDir != null) {
                      rc.move(newDir);
                  }else{
                      moveRandom();
                  }
              }
             
            rc.yield();
        }
    }

    
    /**
     * HQ
     */
    public static class HQ extends SimpleBuilding {
    	public static int xMin, xMax, yMin, yMax;
        public static int xPos, yPos;
        public static int totalNormal, totalProcessed;
        public static int towerThreat;
        public static boolean isFinished;
        
        public static int strategy; //0 is defend, 1 is attack (with tanks), 2 is attack (with drones)
        
        public static double mapRatio;
        
    	public HQ(RobotController rc) {
            super(rc);
            
            xPos = xMin = Math.min(this.myHQ.x, this.theirHQ.x);
            xMax = Math.max(this.myHQ.x, this.theirHQ.x);
            yPos = yMin = Math.min(this.myHQ.y, this.theirHQ.y);
            yMax = Math.max(this.myHQ.y, this.theirHQ.y);
            
            towerThreat = totalNormal = totalProcessed = 0;
            isFinished = false;
        }
        
    	public void chooseStrategy() throws GameActionException{
    		System.out.println("TowerThreat: "+towerThreat);
    		System.out.println("mapRatio: "+mapRatio);
    		
    		if(mapRatio < 0.92){
    			//too many void squares, build drones to attack
    			strategy = 2;
    		} else{
    			//default strategy, attack with tanks
    			strategy = 1;
    		}
    		System.out.println("Strategy Chosen: "+strategy);
    		rc.broadcast(StrategyNumChannel, strategy);
    	}
    	
        public void analyseMap() throws GameActionException{
        	while(yPos <= yMax){
    			TerrainTile t = rc.senseTerrainTile(new MapLocation(xPos, yPos));
    			if(t == TerrainTile.NORMAL){
    				totalNormal++;
    				totalProcessed++;
    			} else if(t == TerrainTile.VOID){
    				totalProcessed++;
    			}
    			
    			if(Clock.getBytecodesLeft() < 100){
    				return;
    			}
    			xPos++;
    			if(xPos == xMax + 1){
    				xPos = xMin;
    				yPos++;
    			}
        	}
        	//int totalProcessed = (xMax - xMin + 1)*(yMax - yMin + 1);
        	mapRatio = (double)totalNormal/totalProcessed;
        	
        	isFinished = analyseTowers();
        	if(isFinished){
        		chooseStrategy();
        	}
        }
        
        public boolean analyseTowers(){
        	if(Clock.getBytecodesLeft() < 1000){
				return false;
			}
        	MapLocation[] towers = rc.senseEnemyTowerLocations();
            towerThreat = 0;

            for (int i=0; i<towers.length; ++i) {
                MapLocation towerLoc = towers[i];

                if ((xMin <= towerLoc.x && towerLoc.x <= xMax && yMin <= towerLoc.y && towerLoc.y <= yMax) || towerLoc.distanceSquaredTo(this.theirHQ) <= 50) {
                    for (int j=0; j<towers.length; ++j) {
                        if (towers[j].distanceSquaredTo(towerLoc) <= 50) {
                            towerThreat++;
                        }
                    }
                }
            }

        	return true;
        	
        }

        public void execute() throws GameActionException {
            int numBeavers = rc.readBroadcast(BeaverNumChannel);

            if (numBeavers < 10) {
                boolean spawned = trySpawn(RobotType.BEAVER);
                if(spawned){
                    rc.broadcast(BeaverNumChannel, numBeavers + 1);
                }
            }
            MapLocation rallyPoint;
            if (Clock.getRoundNum() < ChargeTurn) {
                rallyPoint = new MapLocation( (this.myHQ.x + this.theirHQ.x) / 2,
                        (this.myHQ.y + this.theirHQ.y) / 2);
                MapLocation[] myTowers = rc.senseTowerLocations();
                MapLocation closestTowerToEnemy = null;
                int closest = 900000;
	            for(MapLocation m:myTowers){
	            	if(m.distanceSquaredTo(theirHQ) <= closest){
	            		closest = m.distanceSquaredTo(theirHQ);
	            		closestTowerToEnemy = m;
	            	}
	            }
	            rallyPoint = closestTowerToEnemy;
            }
            else {
            	if(strategy == 0){
            		//TODO: come up with defensive strategy
            		MapLocation nearestTower = getNearestTower();
 	                if(nearestTower != null){
 	                    rallyPoint = nearestTower;
 	                }else{
 	                    rallyPoint = this.theirHQ;
 	                }
            	} else {
	                MapLocation nearestTower = getNearestTower();
	                if(nearestTower != null){
	                    rallyPoint = nearestTower;
	                }else{
	                    rallyPoint = this.theirHQ;
	                }
            	}
            }
            rc.broadcast(0, rallyPoint.x);
            rc.broadcast(1, rallyPoint.y);
            
            if(!isFinished){
            	analyseMap();
            }
            
            rc.yield();
        }
    }


    /**
     * BEAVER
     */
    public static class Beaver extends BaseBot {
        public Beaver(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
        	int strategy = rc.readBroadcast(100);
        	
            autoAttack();
            if(Clock.getRoundNum() < StopMinerFactoryBuildTurn){
                tryBuild(RobotType.MINERFACTORY);
            }else if(Clock.getRoundNum() < ExecuteStrategyTurn){
                tryBuild(RobotType.BARRACKS);
            }else {
            	executeStrategy(strategy);
            }
            mineOrMove();
            rc.yield();
        }
        
        public void executeStrategy(int strategy) throws GameActionException{
        	if(Clock.getRoundNum() >= SpawnCommanderTurn){
        		if(rc.checkDependencyProgress(RobotType.TECHNOLOGYINSTITUTE) == DependencyProgress.NONE){
	          		if(rc.getTeamOre() > RobotType.TECHNOLOGYINSTITUTE.oreCost*1.1){
	        			tryBuild(RobotType.TECHNOLOGYINSTITUTE);
	        		}
        		} else if (rc.checkDependencyProgress(RobotType.TRAININGFIELD) == DependencyProgress.NONE){
        			if(rc.getTeamOre() > RobotType.TRAININGFIELD.oreCost*1.1){
	        			tryBuild(RobotType.TRAININGFIELD);
	        		}
        		}
        	}
        		
        	else{
	        	switch (strategy){
	        	case 0:
	        	case 1:	if(rc.getTeamOre() > RobotType.TANKFACTORY.oreCost*1.5){
	                		if(Clock.getRoundNum()%2 == 0){
			                    tryBuild(RobotType.TANKFACTORY);
			                }else{
			                    tryBuild(RobotType.SUPPLYDEPOT);
			                }
			          	}
	        			break;
	        	case 2:if(rc.getTeamOre() > RobotType.HELIPAD.oreCost*1.5){
			        		if(Clock.getRoundNum()%2 == 0){
			                    tryBuild(RobotType.HELIPAD);
			                }else{
			                    tryBuild(RobotType.SUPPLYDEPOT);
			                }
			          	}
	        			break;
	        	default:if(Clock.getRoundNum()%2 == 0){
			                tryBuild(RobotType.BARRACKS);
			            }else{
			                tryBuild(RobotType.SUPPLYDEPOT);
			            }
				      	break;
	        	}
        	}
        }
    }

    /**
     * MINER FACTORY
     */
    private static class MinerFactory extends SimpleBuilding {
        public MinerFactory(RobotController rc) {
            super(rc);
        }
        public void execute() throws GameActionException {
            int numMiners = rc.readBroadcast(MinerNumChannel);

            if(Clock.getRoundNum() < StopMinerSpawnTurn){
                boolean spawned = trySpawn(RobotType.MINER);
                if(spawned){
                    rc.broadcast(MinerNumChannel, numMiners + 1);
                }
            }else{
                if(numMiners < MaxMiner){
                    boolean spawned = trySpawn(RobotType.MINER);
                    if(spawned){
                        rc.broadcast(MinerNumChannel, numMiners + 1);
                    }
                }
            }
            rc.yield();
        }
    }

    /**
     * MINER
     */
    public static class Miner extends BaseBot {
        public Miner(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
            autoAttack();
            mineOrMove();
            
            rc.yield();
        }
    }

    /**
     * BARRACKS
     */
    public static class Barracks extends SimpleBuilding {
        public Barracks(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
            if(Clock.getRoundNum() < ExecuteStrategyTurn){
                trySpawn(RobotType.SOLDIER);
            }else{
                if(rc.getTeamOre()>RobotType.TANKFACTORY.oreCost*1.1){
                    trySpawn(RobotType.SOLDIER);
                }
            }
            rc.yield();
        }
    }


    /**
     * TOWER
     */
    public static class Tower extends BaseBot {
        public Tower(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
            autoAttack();
            rc.yield();
        }
    }


    private static Direction getRandomDirection() {
        return directions[rand.nextInt(8)];
    }

}