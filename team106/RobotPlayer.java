package team106;

import battlecode.common.*;

import java.util.*;

public class RobotPlayer {

    /**
	 * Strategies
	 */
	private static int TankS = 1;
    private static int DroneS = 2;

    /**
	 * Turn constants
	 */
	private static int moveAwayFromHQTurn = 300;
    // Turn to stop building barracks and miner factories
	// Start building strategy buildings
	private static int ExecuteStrategyTurn = 300;
	private static int StopSoldierSpawnTurn = 300;
	private static int StopMinerSpawnTurn = 800;
	private static int SpawnCommanderTurn = 850;

    //The turn to attack if we have enough combat units
    static int ChargeTurn = 1300;
    static int SolderSurroundEndTurn = ChargeTurn - 200;

    //The turn to attack even if we don't have enough combat units
    static int LastAttackTurn = 1700;
    //Miners attack in this turn
    static int MinerAttackTurn = 1700;
    /**
     * Unit limits
     */
    private static int MaxMiner = 60;
    private static int maxBeavers = 15;

    static Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};

    /**
     * Channels
     */
    final static int MinerNumChannel = 3;
    final static int BeaverNumChannel = 2;
    final static int BarracksNumChannel = 5;
    final static int MinerFactoryNumChannel = 6;
    final static int HelipadNumChannel = 7;
    final static int TankFactoryNumChannel = 8;
    final static int StrategyNumChannel = 100;
    final static int CloseDistanceChannel = 99;
    final static int CombatUnitNumChannel = 98;
    final static int ActualAttackTurnChannel = 50;

    //Update this channel with current turn number to signal help needed as of this turn
    final static int HelpNeedChannel = 68;
    final static int SoldierRallyXChannel = 70;
    final static int SoldierRallyYChannel = 69;
    /**
     * Unit number limits
     */
    final static int tooManyUnits = 12;
    final static int largeDroneNum = 80;
    final static int largeTankNum = 40;
    final static int smallCombatUnitNum = 60;

    /**
     * Threshold and Round number to alternate unit production
     */
    //Choose between soldiers and miners, soldiers more expensive (60)
    final static int MinerSoldierThreshold = RobotType.SOLDIER.oreCost;
    final static int TurnDiveder = 3;
    final static int MinerTurnRemainder = 0;
    final static int SoldierTurnRemainder = 1;

    /**
     * Distance constants
     */
    final static int NearBuilding = 64;

    public static void run(RobotController rc) {
        BaseBot myself;
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
    	protected boolean hasRallied;
    	protected boolean hasCharged;
        protected int currentRoundNum;
        protected RobotInfo[] enemies;
        protected int strategy;
        protected Random rand;
        protected Direction facing;

        public BaseBot(RobotController rc) {
            this.rc = rc;
            this.myHQ = rc.senseHQLocation();
            this.theirHQ = rc.senseEnemyHQLocation();
            this.myTeam = rc.getTeam();
            this.theirTeam = this.myTeam.opponent();
            this.enemyTowers = rc.senseEnemyTowerLocations();
            this.hasRallied = false;
            this.hasCharged = false;
            rand = new Random(rc.getID());
            facing = getRandomDirection();
        }

        public MapLocation getMyHQ() {
            return myHQ;
        }

        public Direction[] getDirectionsToward(MapLocation dest) {
            Direction toDest = rc.getLocation().directionTo(dest);
            Direction[] dirs = {toDest,
                    toDest.rotateLeft(), toDest.rotateRight(),
                    toDest.rotateLeft().rotateLeft(), toDest.rotateRight().rotateRight()};
            return dirs;
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

        //Avoid towers when moving to a destination
        //Assigned to Zhu Liang
        public Direction getMoveDirAvoidTowers(MapLocation dest) {
            Direction[] dirs = getDirectionsToward(dest);
            for (Direction d : dirs) {
                //Check if the direction is safe
                MapLocation infrontLocation = rc.getLocation().add(d);
                boolean safe = safetyCheckTower(infrontLocation);
                if (rc.canMove(d) && safe) {
                    return d;
                }
            }
            return null;
        }

        private boolean safetyCheckTower(MapLocation infrontLocation) {
            for(MapLocation m:enemyTowers){
                if(m.distanceSquaredTo(infrontLocation) <= RobotType.TOWER.attackRadiusSquared + 1){
                    //Can be attack by enemy towers
                    return false;
                }
            }
            return true;
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
            enemies = rc.senseNearbyRobots(rc.getType().attackRadiusSquared, theirTeam);
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
            enemies = getEnemiesInAttackingRange();
            if (enemies.length > 0) {
                //attack!
                if (rc.isWeaponReady()) {
                    attackLeastHealthEnemy(enemies);
                }
            }
        }

        public boolean tryBuild(RobotType type) throws GameActionException {
            //Check the cost first
            if(rc.getTeamOre() < type.oreCost){
                return false;
            }
            Direction newDir = getBuildDirection(type);
            MapLocation buildingLoc;
            if(newDir == null){
                buildingLoc = null;
            }else{
                buildingLoc = rc.getLocation().add(newDir);
            }
            int closeDistance = rc.readBroadcast(CloseDistanceChannel);
            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(closeDistance/2, myTeam);
            //Avoid building minefactory together
            //Avoid building near obstacles
            if(checkBuildingClash(type, nearbyAllies) || checkBuildingNearWall(buildingLoc)){
                mineOrMove();
            }
            //Avoid building around the HQ (UNLESS IT IS THE ANTI DRONE RUSH MACHINE) and causing congestion, encourage going further away
            //else if(rc.getLocation().distanceSquaredTo(getMyHQ()) < closeDistance && type != RobotType.BARRACKS){
            //    mineOrMove();
            ////Avoid building around too many other units to avoid congestion
            //}
            else if(nearbyAllies.length > tooManyUnits){
                mineOrMove();
            }else if(rc.isCoreReady() && rc.getTeamOre() > type.oreCost){
                if (newDir != null) {
                    rc.build(newDir, type);
                    System.out.println("Type: " + type.name());
                    return true;
                }
            }
            return false;
        }

        private boolean checkBuildingNearWall(MapLocation buildingLoc) {
            if(buildingLoc == null){
                return false;
            }
            MapLocation nearbyLoc;
            for(Direction d:directions){
                nearbyLoc = buildingLoc.add(d);
                if(rc.senseTerrainTile(nearbyLoc) != TerrainTile.NORMAL){
                    return true;
                }
            }
            return false;
        }

        private boolean checkBuildingClash(RobotType type, RobotInfo[] nearbyAllies) {
            if(type == RobotType.MINERFACTORY){
                for(RobotInfo m:nearbyAllies){
                    if(m.type == type){
                        return true;
                    }
                }
            }
            return false;
        }

        public void mineOrMove() throws GameActionException {
            //Avoid concentrating around the HQ, encourage going further away
            //Allow near HQ in early game, only use this later in the game
            if(currentRoundNum > moveAwayFromHQTurn && rc.getLocation().distanceSquaredTo(getMyHQ()) < rc.readBroadcast(CloseDistanceChannel)){
                if(rc.isCoreReady() && rc.senseOre(rc.getLocation())>15 && rc.canMine() && rand.nextInt(10) < 2){
                    rc.mine();
                }else{
                    moveRandom();
                }
            }else if(rc.isCoreReady() && rc.senseOre(rc.getLocation())>5 && rc.canMine()){
                rc.mine();
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

        //Move infront, using get direction with location two grids ahead
        public void moveRandom() throws GameActionException {
            //randomTurnFacing();
            if(facing == null){
                facing = getRandomDirection();
            }
            MapLocation infrontLocation = rc.getLocation().add(facing).add(facing);
            Direction newFacing = getMoveDirSafely(infrontLocation);
            if(newFacing != null && rc.isCoreReady() && rc.canMove(newFacing)){
                facing = newFacing;
                    rc.move(newFacing);
            }else{
                turnFacing();
            }
            //else if(rc.isCoreReady() && rc.canMove(facing.opposite())){
            //    rc.move(facing.opposite());
            //}
        }
        //public void randomTurnFacing() {
        //    if(rand.nextInt(20) < 1){
        //        turnFacing();
        //    }
        //}

        public void turnFacing() {
            if(rand.nextInt(10) < 5){
                facing = facing.rotateLeft().rotateLeft();
            }else {
                facing = facing.rotateRight().rotateRight();
            }
        }

        public MapLocation getNearestEnemyTower() throws GameActionException {
            enemyTowers = rc.senseEnemyTowerLocations();
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
                currentRoundNum = Clock.getRoundNum();
                int needhelp = rc.readBroadcast(HelpNeedChannel);
                if(needhelp == currentRoundNum || needhelp == currentRoundNum - 1){
                    //Our buildings under attack, go back
                    int x = rc.readBroadcast(SoldierRallyXChannel);
                    int y = rc.readBroadcast(SoldierRallyYChannel);
                    facing = getMoveDirAvoidTowers(new MapLocation(x, y));
                    if(facing!=null){
                        rc.move(facing);
                    }else{
                        moveRandom();
                    }
                }else if(rc.getType() == RobotType.SOLDIER && currentRoundNum < SolderSurroundEndTurn) {
                    //Buildings are safe, soldiers destroy their base
                    facing = getMoveDirSafely(theirHQ);
                    if (facing != null && !this.hasRallied) {
                        rc.move(facing);
                    } else {
                        moveRandom();
                    }
                }else{
                    int rallyX = rc.readBroadcast(0);
                    int rallyY = rc.readBroadcast(1);
                    MapLocation rallyPoint = new MapLocation(rallyX, rallyY);
                    //If moving to HQ, then avoid towers and sorround HQ fisrt
                    //Then after 50 turns, attack HQ
                    if(rallyX == theirHQ.x && rallyY == theirHQ.y){
                        int actualAttackTurn = rc.readBroadcast(ActualAttackTurnChannel);
                        if(currentRoundNum < (actualAttackTurn + 50)){
                            //Surround the HQ first
                            facing = getMoveDirSafely(rallyPoint);
                        }else{
                            //Attack HQ directly
                            facing = getMoveDir(rallyPoint);
                        }
                    }else{
                        facing = getMoveDir(rallyPoint);
                    }
                    if (facing != null) {
                        rc.move(facing);
                    }else{
                        moveRandom();
                    }
                }
            }
        }

        protected Direction getRandomDirection() {
            return directions[rand.nextInt(8)];
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

        public void senseNearbyEnemyCallHelp() throws GameActionException {
            currentRoundNum = Clock.getRoundNum();
            if(currentRoundNum < 1200){
                RobotInfo[] enemies = rc.senseNearbyRobots(NearBuilding,theirTeam);
                int others_need_help = rc.readBroadcast(HelpNeedChannel);
                if(enemies.length > 0 && others_need_help < currentRoundNum){
                    //Enemy detected and nobody else calling for help, call soldiers to defend our HQ
                    rc.broadcast(SoldierRallyXChannel, enemies[0].location.x);
                    rc.broadcast(SoldierRallyYChannel, enemies[0].location.y);
                    rc.broadcast(HelpNeedChannel, currentRoundNum);
                } else{
                    //surround their HQ
                    //Check if other buildings are calling for help in this/last round
                    if(rc.readBroadcast(HelpNeedChannel) < currentRoundNum - 1){
                        //No other buildings need help, proceed to enemy HQ
                        rc.broadcast(SoldierRallyXChannel, theirHQ.x);
                        rc.broadcast(SoldierRallyYChannel, theirHQ.y);
                    }
                }
            }
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
            //System.out.println("x range: " + (xMax - xMin));
            //System.out.println("y range: " + (yMax - yMin));
            //System.out.println("map size: " + mapSize);
            CloseDistance = (mapSize / 6) * (mapSize / 6);
            //System.out.println("CloseDistance: " + CloseDistance);
        }

    	public void chooseStrategy() throws GameActionException{
            //System.out.println("TowerThreat: "+towerThreat);
            //System.out.println("mapRatio: "+mapRatio);

            //0 is defend, 1 is attack (with tanks), 2 is attack (with drones)
    		if(mapRatio < 0.72){
    			//too many void squares, build drones to attack
    			strategy = DroneS;
    		} else{
    			//default strategy, attack with tanks
    			strategy = TankS;
    		}

            //Choose point of attack based on tower threat
            if(towerThreat > 30){

            }else{

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
        	enemyTowers = rc.senseEnemyTowerLocations();
            towerThreat = 0;

            for (int i=0; i<enemyTowers.length; ++i) {
                MapLocation towerLoc = enemyTowers[i];

                //Modified formula for ranged units, 50 --> 25
                if ((xMin <= towerLoc.x && towerLoc.x <= xMax && yMin <= towerLoc.y && towerLoc.y <= yMax) || towerLoc.distanceSquaredTo(this.theirHQ) <= 50) {
                    for (int j=0; j<enemyTowers.length; ++j) {
                        if (enemyTowers[j].distanceSquaredTo(towerLoc) <= 25) {
                            towerThreat++;
                        }
                    }
                }
            }

        	return true;
        	
        }

        public void execute() throws GameActionException {
        	//ANTI-RUSH
        	//check for nearby enemies
            senseNearbyEnemyCallHelp();
            currentRoundNum = Clock.getRoundNum();

            //Tell the other units the close distance to HQ
            //This is used to encourage units to move away from HQ to avoid congestion
            rc.broadcast(CloseDistanceChannel, CloseDistance);
            int numBeavers = rc.readBroadcast(BeaverNumChannel);

            //Limit the number of beavers by only spawning them at certain turns
            if (numBeavers < maxBeavers && currentRoundNum%80 == 0) {
                boolean spawned = trySpawn(RobotType.BEAVER);
                if(spawned){
                    rc.broadcast(BeaverNumChannel, numBeavers + 1);
                }
            }
            MapLocation rallyPoint;
            int combatUnitNum = rc.readBroadcast(CombatUnitNumChannel);
            if (combatUnitNum < getLargeNumber()) {
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
                if (currentRoundNum == ChargeTurn){
                    countSoldiersAsCombatUnits();
                }
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
                        //Attack if we have enough combat units
                        //Less tanks are needed
                        if(combatUnitNum > getLargeNumber() || currentRoundNum > LastAttackTurn){
                            //Report the turn of attack if not set yet
                            if(rc.readBroadcast(ActualAttackTurnChannel) == 0){
                                rc.broadcast(ActualAttackTurnChannel, currentRoundNum);
                            }
                            //Attack towers if low tower threat
                            if(towerThreat < 30){
                                rallyPoint = nearestEnemyTower;
                            }else{
                                rallyPoint = this.theirHQ;
                            }
                        }
                        //Defend own HQ if we do not have enough combat units to take down any towers
                        //else if(Clock.getRoundNum() > LastAttackTurn){
                        //    rallyPoint = myHQ;
                        //}
                        //Default rally point
                        else{
                            rallyPoint = closestTowerToEnemy;
                        }
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

        private int getLargeNumber() {
            if(strategy == DroneS){
                return largeDroneNum;
            }else{
                return largeTankNum;
            }
        }

        private void countSoldiersAsCombatUnits() throws GameActionException {
            int soldierCount = 0;
            int combatUnitNum = rc.readBroadcast(CombatUnitNumChannel);
            RobotInfo[] allMyUnits = rc.senseNearbyRobots(99999, myTeam);
            for(RobotInfo m:allMyUnits){
                if(m.type == RobotType.SOLDIER){
                    soldierCount++;
                }
            }
            rc.broadcast(CombatUnitNumChannel, soldierCount/2 + combatUnitNum);
        }
    }


    /**
     * BEAVER
     */
    public static class Beaver extends BaseBot {

        int barrack_num;
        int heli_num;
        int minerfactory_num;
        int tankfactory_num;
        public Beaver(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
        	strategy = rc.readBroadcast(100);
        	
            autoAttack();
            currentRoundNum = Clock.getRoundNum();
            barrack_num = rc.readBroadcast(BarracksNumChannel);
            heli_num = rc.readBroadcast(HelipadNumChannel);
            minerfactory_num = rc.readBroadcast(MinerFactoryNumChannel);
            tankfactory_num = rc.readBroadcast(TankFactoryNumChannel);
            //Build at least one barrack/helipad, counter drone rush
            if(barrack_num == 0 && heli_num == 0){
                buildEarlyBuildings(strategy, barrack_num, heli_num);
            } else if(minerfactory_num == 0){
            	boolean built = tryBuild(RobotType.MINERFACTORY);
                if(built){
                    rc.broadcast(MinerFactoryNumChannel, minerfactory_num + 1);
                }
            }else if(currentRoundNum < ExecuteStrategyTurn){
                if(minerfactory_num < 2){
                    boolean built = tryBuild(RobotType.MINERFACTORY);
                    if(built){
                        rc.broadcast(MinerFactoryNumChannel, minerfactory_num + 1);
                    }
                }else if(barrack_num < 2 && heli_num < 2 && rc.getTeamOre() > RobotType.BARRACKS.oreCost){
                    buildEarlyBuildings(strategy, barrack_num, heli_num);
                }else{
                    boolean built = tryBuild(RobotType.MINERFACTORY);
                    if(built){
                        rc.broadcast(MinerFactoryNumChannel, minerfactory_num + 1);
                    }
                }
            }else {
            	executeStrategy(strategy, minerfactory_num);
            }
            mineOrMove();
            rc.yield();
        }

        private void buildEarlyBuildings(int strategy, int barrack_num, int heli_num) throws GameActionException {
            if(strategy == TankS){
                boolean built = tryBuild(RobotType.BARRACKS);
                if(built){
                    rc.broadcast(BarracksNumChannel, barrack_num + 1);
                }
            }else if(strategy == DroneS){
                boolean built = tryBuild(RobotType.HELIPAD);
                if(built){
                    rc.broadcast(HelipadNumChannel, heli_num + 1);
                }
            }
        }


        public void executeStrategy(int strategy, int minerfactory_num) throws GameActionException{
            //Must build at least 2 miner factories to ensure ore income
            if(minerfactory_num < 2){
                boolean built = tryBuild(RobotType.MINERFACTORY);
                if(built){
                    rc.broadcast(MinerFactoryNumChannel, minerfactory_num + 1);
                }
            }
        	if(currentRoundNum >= SpawnCommanderTurn){
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
                //Mid game
                midGameBuildings(strategy);
            }
        }

        private void midGameBuildings(int strategy) throws GameActionException {
            switch (strategy){
            case 0:
            case 1:	if(rc.getTeamOre() > RobotType.TANKFACTORY.oreCost){
                        //Ensure at least one tank factory first
                        if(tankfactory_num < 1){
                            boolean built = tryBuild(RobotType.TANKFACTORY);
                            if(built){
                                rc.broadcast(TankFactoryNumChannel, tankfactory_num + 1);
                            }
                        }else if(currentRoundNum%2 == 0){
                            tryBuild(RobotType.TANKFACTORY);
                        }else{
                            tryBuild(RobotType.SUPPLYDEPOT);
                        }
                      }
                    break;
            case 2:if(rc.getTeamOre() > RobotType.HELIPAD.oreCost){
                        //Ensure at least one helipad first
                        if(heli_num < 1){
                            boolean built = tryBuild(RobotType.HELIPAD);
                            if(built){
                                rc.broadcast(HelipadNumChannel, heli_num + 1);
                            }
                        }else if(currentRoundNum%2 == 0){
                            tryBuild(RobotType.HELIPAD);
                        }else{
                            tryBuild(RobotType.SUPPLYDEPOT);
                        }
                      }
                    break;
            default:if(currentRoundNum%2 == 0){
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
            //ANTI-RUSH
            //check for nearby enemies
            senseNearbyEnemyCallHelp();

            int numMiners = rc.readBroadcast(MinerNumChannel);
            currentRoundNum = Clock.getRoundNum();
            //Allow spawning of other units by alternating turn number
            if(currentRoundNum < StopMinerSpawnTurn && rc.getTeamOre() > MinerSoldierThreshold && currentRoundNum%TurnDiveder == MinerTurnRemainder){
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
            currentRoundNum = Clock.getRoundNum();
            if(currentRoundNum > MinerAttackTurn){
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
            currentRoundNum = Clock.getRoundNum();
            if(currentRoundNum < StopSoldierSpawnTurn && rc.getTeamOre() > MinerSoldierThreshold && currentRoundNum%TurnDiveder == SoldierTurnRemainder){
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
    public static class Tower extends SimpleBuilding {
        public Tower(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
            //ANTI-RUSH
            //check for nearby enemies
            senseNearbyEnemyCallHelp();
            autoAttack();
            rc.yield();
        }
    }




}