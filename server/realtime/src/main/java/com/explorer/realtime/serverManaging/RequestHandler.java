package com.explorer.realtime.serverManaging;

import com.explorer.realtime.global.broadcasting.Broadcasting;
import com.explorer.realtime.global.redis.RedisService;
import com.explorer.realtime.global.session.SessionManager;
import com.explorer.realtime.global.teamCode.TeamCodeGenerator;
import com.explorer.realtime.sessionhandling.ingame.IngameSessionHandler;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;

@Component
public class RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private final TeamCodeGenerator teamCodeGenerator;
    private final SessionManager sessionManager;
    private final RedisService redisService;
    private final Broadcasting broadcasting;
    private final IngameSessionHandler ingameSessionHandler;

    public RequestHandler(TeamCodeGenerator teamCodeGenerator, SessionManager sessionManager, RedisService redisService, Broadcasting broadcasting, IngameSessionHandler ingameSessionHandler) {
        this.teamCodeGenerator = teamCodeGenerator;
        this.sessionManager = sessionManager;
        this.redisService = redisService;
        this.broadcasting = broadcasting;
        this.ingameSessionHandler = ingameSessionHandler;
    }

    public Mono<Void> handleRequest(NettyInbound inbound, NettyOutbound outbound) {

        return inbound
                .receive()                                      // 1) receive data
                .asString()                                     // 2) convert data : byte -> string
                .flatMap(msg -> {                               // 3) process data
                    try{

                        JSONObject json = new JSONObject(msg);      // parse string to json
                        log.info("Received Json Data: {}", json);

                        String type = json.getString("type");
                        String event = json.getString("event");

                        switch(type) {
                                case "waitingRoomSession" :
                                    log.info("waiting room");
                                    switch(event) {
                                        case "createWaitingRoom":
                                            log.info("create Waiting Room");

                                            String teamCode = "abcde";
                                            String uid = json.getString("uid");

                                            inbound.withConnection(connection -> {
                                                sessionManager.setConnection(uid, connection);
                                                redisService.saveUidToTeamCode(teamCode, uid, "waitingRoom").subscribe();
                                                log.info("uid: {}, connection: {}", sessionManager.getUid(connection), sessionManager.getConnection(uid));
                                            });
                                    }
                                    break;


                            case "ingameSession" :
                                log.info("start game");
                                switch (event) {
                                    case "gameStart":
                                        log.info("gameStart");
                                        String teamCode = json.getString("teamCode");
                                        broadcasting.broadcasting(teamCode).subscribe();
                                        break;
                                }
                                ingameSessionHandler.ingameHandler(json);
                                break;
                        }

                        return outbound.sendString(Mono.just("success"));   // echoing

                    } catch (JSONException e) {
                        log.error("ERROR : {}", e.getMessage());
                        return Mono.empty();
                    }
                })
                .then();                                        // 4) complete reactive sequence

    }
}
