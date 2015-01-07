package team106;

import battlecode.common.*;
import java.util.*;

public class RobotPlayer {
    static int ChargeTurn = 1300;
    private static int TankBuildTurn = 600;
    private static int StopMinerFactoryBuildTurn = 500;
    private static int StopMinerSpawnTurn = 800;
    private static int MaxMiner = 80;
    static Random rand;
    static Direction facing;

    static Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};


    final static int MinerNumChannel = 3;
    final static int BeaverNumChannel = 2;

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
        } else if (rc.getType() == RobotType.BARRACKS) {
            myself = new Barracks(rc);
        } else if (rc.getType() == RobotType.SOLDIER) {
            myself = new Soldier(rc);
        } else if (rc.getType() == RobotType.TOWER) {
            myself = new Tower(rc);
        } else if (rc.getType() == RobotType.MINERFACTORY) {
            myself = new MinerFactory(rc);
        } else if (rc.getType() == RobotType.TANKFACTORY) {
            myself = new TankFactory(rc);
        } else if (rc.getType() == RobotType.TANK) {
            myself = new Tank(rc);
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

        public void beginningOfTurn() {
            if (rc.senseEnemyHQLocation() != null) {
                this.theirHQ = rc.senseEnemyHQLocation();
            }
        }

        public void endOfTurn() {
        }

        public void go() throws GameActionException {
            beginningOfTurn();
            execute();
            endOfTurn();
        }

        public void execute() throws GameActionException {
            rc.yield();
        }
    }

    /**
     * HQ
     */
    public static class HQ extends BaseBot {
        public HQ(RobotController rc) {
            super(rc);
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
            }
            else {
                MapLocation nearestTower = getNearestTower();
                if(nearestTower != null){
                    rallyPoint = nearestTower;
                }else{
                    rallyPoint = this.theirHQ;
                }
            }
            rc.broadcast(0, rallyPoint.x);
            rc.broadcast(1, rallyPoint.y);

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
            autoAttack();
            if(Clock.getRoundNum() < StopMinerFactoryBuildTurn){
                tryBuild(RobotType.MINERFACTORY);
            }else if(Clock.getRoundNum() < TankBuildTurn){
                tryBuild(RobotType.BARRACKS);
            }else if(rc.getTeamOre() > RobotType.TANKFACTORY.oreCost*1.5){
                tryBuild(RobotType.TANKFACTORY);
            }
            mineOrMove();
            rc.yield();
        }
    }

    /**
     * MINER FACTORY
     */
    private static class MinerFactory extends BaseBot {
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
    public static class Barracks extends BaseBot {
        public Barracks(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
            if(Clock.getRoundNum() < TankBuildTurn){
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
     * TANK FACTORY
     */
    public static class TankFactory extends BaseBot {
        public TankFactory(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
            trySpawn(RobotType.TANK);
            rc.yield();
        }
    }

    /**
     * SOLDIER
     */
    public static class Soldier extends BaseBot {
        public Soldier(RobotController rc) {
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
     * TANK
     */
    private static class Tank extends BaseBot {
        public Tank(RobotController rc) {
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