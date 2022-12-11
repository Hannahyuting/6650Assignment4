try (Jedis jedis = pool.getResource()) {
    jedis.sadd("resortId:" + resortId + "seasonId:" + seasonId + "dayId:" + dayId, skierIdValue);
    jedis.incrBy("resortId:" + resortId + "seasonId:" + seasonId + "dayId:" + dayId +
        "skierId:" + skierId + "vertical:", liftID * 10);
    jedis.incrBy("resortId:" + resortId + "seasonId:" + seasonId + "skierId:" + skierId +
        "vertical:", liftID * 10);
    jedis.incrBy("resortId:" + resortId + "skierId:" + skierId + "vertical:", liftID * 10);
    jedis.incrBy("skierId:" + skierId + "vertical:", liftID * 10);
}
