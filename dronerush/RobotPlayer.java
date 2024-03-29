package dronerush;

import battlecode.common.*;

import java.util.*;

public class RobotPlayer {
    //The turn to attack if we have enough combat units
    static int ChargeTurn = 1300;

    //The turn to attack even if we don't have enough combat units
    //Miners also attack in this turn
    static int LastAttackTurn = 1500;

    private static int ExecuteStrategyTurn = 600;
    private static int StopSoldierSpawnTurn = 700;
    private static int StopMinerFactoryBuildTurn = 500;
    private static int StopMinerSpawnTurn = 800;
    private static int SpawnCommanderTurn = 850;
    private static int MaxMiner = 80;
    private static int maxBeavers = 10;
    static Random rand;
    static Direction facing;

    static Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};

    /**
     * Channels
     */
    final static int MinerNumChannel = 3;
    final static int BeaverNumChannel = 2;
    final static int BarracksNumChannel = 5;
    final static int MinerFactoryNumChannel = 6;
    final static int StrategyNumChannel = 100;
    final static int CloseDistanceChannel = 99;
    final static int CombatUnitNumChannel = 98;

    /**
     * Unit number limits
     */
    final static int tooManyUnits = 5;
    final static int largeCombatUnitNum = 90;
    final static int smallCombatUnitNum = 60;

    /**
     * Threshold and Round number to alternate unit production
     */
    //Choose between soldiers and miners, soldiers more expensive (60)
    final static int MinerSoldierThreshold = RobotType.SOLDIER.oreCost;
    final static int TurnDiveder = 3;
    final static int MinerTurnRemainder = 0;
    final static int SoldierTurnRemainder = 1;

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
        protected MapLocation[] enemyTowers;
        private boolean isAttacker;
        
        public BaseBot(RobotController rc) {
            this.rc = rc;
            this.myHQ = rc.senseHQLocation();
            this.theirHQ = rc.senseEnemyHQLocation();
            this.myTeam = rc.getTeam();
            this.theirTeam = this.myTeam.opponent();
            this.enemyTowers = rc.senseEnemyTowerLocations();
            isAttacker =  Clock.getRoundNum() > 800;
        }

        public MapLocation getMyHQ() {
            return myHQ;
        }

        public Direction[] getDirectionsToward(MapLocation dest) {
            Direction toDest = rc.getLocation().directionTo(dest);
            if(new Random(rc.getID()).nextInt(20) > 9){
            	//favour left
	            Direction[] dirs = {toDest,
	                    toDest.rotateLeft(), toDest.rotateRight(),
	                    toDest.rotateLeft().rotateLeft(), toDest.rotateRight().rotateRight()};
	            return dirs;
            } else{
            	//favour right
            	 Direction[] dirs = {toDest,
                         toDest.rotateRight(), toDest.rotateLeft(),
                         toDest.rotateRight().rotateRight(), toDest.rotateLeft().rotateLeft()};
            	 return dirs;
            }
            
        }

        //Avoid towers and enemy HQ when moving to a destination
        //Assigned to Zhu Liang
        public Direction getMoveDirSafely(MapLocation dest) {
            Direction[] dirs = getDirectionsToward(dest);
            
            for (Direction d : dirs) {
                //Check if the direction is safe
                MapLocation infrontLocation = rc.getLocation().add(d);
                boolean safe = safetyCheck(infrontLocation);
                if (rc.canMove(d) && safe) {
                    return d;
                }
            }
            return null;
        }

        private boolean safetyCheck(MapLocation infrontLocation) {
            if(theirHQ.distanceSquaredTo(infrontLocation) <= RobotType.HQ.attackRadiusSquared + 1){
                //Can be attack by enemy HQ
                return false;
            }else{
                for(MapLocation m:enemyTowers){
                    if(m.distanceSquaredTo(infrontLocation) <= RobotType.TOWER.attackRadiusSquared + 1){
                        //Can be attack by enemy towers
                        return false;
                    }
                }
            }
            return true;
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
                //Avoid building near the wall
                if(rc.senseTerrainTile(rc.getLocation().add(d).add(d)) != TerrainTile.NORMAL){
                    return null;
                }
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
                //Add attacking of HQ as priority
                if(info.type == RobotType.HQ){
                    toAttack = info.location;
                    break;
                }else if (info.health < minEnergon) {
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
            int closeDistance = rc.readBroadcast(CloseDistanceChannel);
            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(closeDistance/2, myTeam);
            boolean buildingClash = false;
            //Avoid building minefactory together
            if(type == RobotType.MINERFACTORY){
                for(RobotInfo m:nearbyAllies){
                    if(m.type == type){
                        buildingClash = true;
                        break;
                    }
                }
            }
            if(buildingClash){
                mineOrMove();
            //Avoid building around the HQ and causing congestion, encourage going further away
            }else if(rc.getLocation().distanceSquaredTo(getMyHQ()) < closeDistance){
                mineOrMove();
            //Avoid building around too many other units to avoid congestion
            }else if(nearbyAllies.length > tooManyUnits){
                mineOrMove();
            }else if(rc.isCoreReady() && rc.getTeamOre() > type.oreCost){
                Direction newDir = getBuildDirection(type);
                if (newDir != null) {
                    rc.build(newDir, type);
                    System.out.println("Type: " + type.name());
                    return true;
                }
            }
            return false;
        }

        public void mineOrMove() throws GameActionException {
            //Avoid concentrating around the HQ, encourage going further away
            if(rc.getLocation().distanceSquaredTo(getMyHQ()) < rc.readBroadcast(CloseDistanceChannel)){
                if(rc.isCoreReady() && rc.senseOre(rc.getLocation())>15 && rc.canMine() && rand.nextInt(10) < 2){
                    rc.mine();
                }else{
                    moveRandom();
                }
            }
            else if(rc.isCoreReady() && rc.senseOre(rc.getLocation())>15 && rc.canMine()){
                if (rand.nextInt(10) < 9) {
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
            MapLocation infrontLocation = rc.getLocation().add(facing);
            //Check safe
            boolean safe = safetyCheck(infrontLocation);

            if(rc.senseTerrainTile(infrontLocation) != TerrainTile.NORMAL){
                turnFacing();
            }
            //Move not safe, try turning back and test if safe again
            if(!safe){
                facing = facing.opposite();
                infrontLocation = rc.getLocation().add(facing);
                safe = safetyCheck(infrontLocation);
            }
            if(rc.isCoreReady() && rc.canMove(facing) && safe){
                rc.move(facing);
            }
            //else if(rc.isCoreReady() && rc.canMove(facing.opposite())){
            //    rc.move(facing.opposite());
            //}
        }

        public void randomTurnFacing() {
            if(rand.nextInt(20) < 1){
                turnFacing();
            }
        }

        public void turnFacing() {
            if(rand.nextInt(10) < 5){
                facing = facing.rotateLeft().rotateLeft();
            }else {
                facing = facing.rotateRight().rotateRight();
            }
        }

        public MapLocation getNearestEnemyTower() throws GameActionException {
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

        public void combatUnitActions() throws GameActionException {
            if (rc.isCoreReady()) {
                int rallyX = rc.readBroadcast(0);
                int rallyY = rc.readBroadcast(1);
                int combatUnits = rc.readBroadcast(CombatUnitNumChannel);
                MapLocation rallyPoint = new MapLocation(rallyX, rallyY);
                Direction newDir;
                if(Clock.getRoundNum() < ChargeTurn)
                	newDir = getMoveDirSafely(rallyPoint);
                else
                	newDir = getMoveDir(rallyPoint);
                
                if(rc.getType() == RobotType.DRONE && !isAttacker){
                    Direction HQDir= getMoveDirSafely(theirHQ);
                    if (HQDir != null) {
                        rc.move(HQDir);
                    }else{
                        moveRandom();
                    }
                }else if (newDir != null) {
                    rc.move(newDir);
                }else{
                    moveRandom();
                }
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

    /**
     * Buildings
     */
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
            
            //if (toSpawn != null && rc.isCoreReady() && rc.getTeamOre() > toSpawn.oreCost && (rc.readBroadcast(100) != 0)) {
            //    Direction newDir = getSpawnDirection(toSpawn);
            //    if (newDir != null) {
            //        rc.spawn(newDir, toSpawn);
            //    }
            //}
            if(toSpawn != null){
                boolean spawned = trySpawn(toSpawn);
                if(spawned){
                    //Record the number of combat units spawned
                    int combatUnitNum = rc.readBroadcast(CombatUnitNumChannel);
                    rc.broadcast(CombatUnitNumChannel, combatUnitNum + 1);
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

    /**
     * Combat Units
     */
    public static class SimpleFighter extends BaseBot {
        public SimpleFighter(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
            autoAttack();
            combatUnitActions();
            if(Clock.getRoundNum() > ChargeTurn && rc.isCoreReady()){
                Direction newDir = getMoveDir(this.theirHQ);
                if (newDir != null) {
                    rc.move(newDir);
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
        public static int mapSize;

        //Distance that is too close to HQ, possibly causing congestion
        //This will be changed based on the size of the map by HQ
        public static int CloseDistance;

        //Rally point by default
        MapLocation closestTowerToEnemy = null;

    	public HQ(RobotController rc) {
            super(rc);
            
            xPos = xMin = Math.min(this.myHQ.x, this.theirHQ.x);
            xMax = Math.max(this.myHQ.x, this.theirHQ.x);
            yPos = yMin = Math.min(this.myHQ.y, this.theirHQ.y);
            yMax = Math.max(this.myHQ.y, this.theirHQ.y);
            towerThreat = totalNormal = totalProcessed = 0;
            isFinished = false;
            mapSize = Math.max(xMax - xMin,yMax - yMin);
            System.out.println("x range: " + (xMax - xMin));
            System.out.println("y range: " + (yMax - yMin));
            System.out.println("map size: " + mapSize);
            CloseDistance = (mapSize / 6) * (mapSize / 6);
            System.out.println("CloseDistance: " + CloseDistance);
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

            //Tell the other units the close distance to HQ
            //This is used to encourage units to move away from HQ to avoid congestion
            rc.broadcast(CloseDistanceChannel, CloseDistance);
            int numBeavers = rc.readBroadcast(BeaverNumChannel);

            if (numBeavers < maxBeavers) {
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
                int closest = 900000;
	            for(MapLocation m:myTowers){
	            	if(m.distanceSquaredTo(theirHQ) <= closest){
	            		closest = m.distanceSquaredTo(theirHQ);
	            		closestTowerToEnemy = m;
	            	}
	            }
	            rallyPoint = closestTowerToEnemy;
            }else {
            	if(strategy == 0){
            		//TODO: come up with defensive strategy
            		MapLocation nearestTower = getNearestEnemyTower();
 	                if(nearestTower != null){
 	                    rallyPoint = nearestTower;
 	                }else{
 	                    rallyPoint = this.theirHQ;
 	                }
            	} else {
	                MapLocation nearestEnemyTower = getNearestEnemyTower();
	                if(nearestEnemyTower != null){
                        int combatUnitNum = rc.readBroadcast(CombatUnitNumChannel);
                        //Attack if we have enough units
                        if(true){
                            rallyPoint = nearestEnemyTower;
                        }
                        //Defend own HQ if we do not have enough combat units to take down any towers
                       
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
            int roundNum = Clock.getRoundNum();
            int barrack_num = rc.readBroadcast(BarracksNumChannel);
            if(roundNum < ChargeTurn){
            	tryBuild(RobotType.HELIPAD);
            }
            mineOrMove();
            rc.yield();
        }
        
        public void executeStrategy(int strategy) throws GameActionException{
        	if(Clock.getRoundNum() >= SpawnCommanderTurn){
                //Late game
                //Build high level units
        		if(rc.checkDependencyProgress(RobotType.TECHNOLOGYINSTITUTE) == DependencyProgress.NONE){
	          		if(rc.getTeamOre() > RobotType.TECHNOLOGYINSTITUTE.oreCost*1.1){
	        			tryBuild(RobotType.TECHNOLOGYINSTITUTE);
	        		}
        		} else if (rc.checkDependencyProgress(RobotType.TRAININGFIELD) == DependencyProgress.NONE){
        			if(rc.getTeamOre() > RobotType.TRAININGFIELD.oreCost*1.1){
	        			tryBuild(RobotType.TRAININGFIELD);
	        		}
        		} else if(rc.getTeamOre() > RobotType.TANKFACTORY.oreCost * 2){
                    midGameBuildings(strategy);
                }
        	}
        		
        	else{
                //Early game
                midGameBuildings(strategy);
            }
        }

        private void midGameBuildings(int strategy) throws GameActionException {
            switch (strategy){
            case 0:
            case 1:	if(rc.getTeamOre() > RobotType.TANKFACTORY.oreCost*1.1){
                        if(Clock.getRoundNum()%2 == 0){
                            tryBuild(RobotType.TANKFACTORY);
                        }else{
                            tryBuild(RobotType.SUPPLYDEPOT);
                        }
                      }
                    break;
            case 2:if(rc.getTeamOre() > RobotType.HELIPAD.oreCost*1.1){
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

    /**
     * MINER FACTORY
     */
    private static class MinerFactory extends SimpleBuilding {
        public MinerFactory(RobotController rc) {
            super(rc);
        }
        public void execute() throws GameActionException {
            int numMiners = rc.readBroadcast(MinerNumChannel);
            int roundNum = Clock.getRoundNum();
            //Allow spawning of other units by alternating turn number
            if(roundNum < StopMinerSpawnTurn && rc.getTeamOre() > MinerSoldierThreshold && roundNum%TurnDiveder == MinerTurnRemainder){
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
            if(Clock.getRoundNum() > LastAttackTurn){
                combatUnitActions();
            }else{
                mineOrMove();
            }
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
            int roundNum = Clock.getRoundNum();
            if(roundNum < StopSoldierSpawnTurn && rc.getTeamOre() > MinerSoldierThreshold && roundNum%TurnDiveder == SoldierTurnRemainder){
                trySpawn(RobotType.SOLDIER);
            }else{
                if(rc.getTeamOre()>RobotType.TANKFACTORY.oreCost*2){
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