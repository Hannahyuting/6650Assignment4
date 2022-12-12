import io.swagger.client.model.LiftRide;

public class RequestData {
    private Integer resortId;
    private String seasonId;
    private String dayId;
    private Integer skierId;
    private LiftRide liftRide;

    public RequestData(Integer resortId, String seasonId, String dayId, Integer skierId, LiftRide liftRide) {
        this.resortId = resortId;
        this.seasonId = seasonId;
        this.dayId = dayId;
        this.skierId = skierId;
        this.liftRide = liftRide;
    }
}
