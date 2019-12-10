package samples.sm;

import com.example.sm.Sm;
import com.example.sm.persistence.Domain;
import com.google.protobuf.Empty;
import io.cloudstate.javasupport.EntityId;
import io.cloudstate.javasupport.eventsourced.*;

import java.util.*;
import java.util.stream.Collectors;

/** An event sourced entity. */
@EventSourcedEntity(persistenceId = "home-account", snapshotEvery = 10)
public class HomeEntity {
  private final String entityId;

  private static final int MAX_ACTIVE_SESSIONS = 2;
  private static final int SESSION_DURATION_IN_SECONDS = 2 * 60;

  private final Map<String, Sm.Session> sessions = new LinkedHashMap<>();
  private int maxActiveSessions = MAX_ACTIVE_SESSIONS;

  public HomeEntity(@EntityId String entityId) {
    this.entityId = entityId;
  }

  private Sm.SessionResponse convertToSessionResponse(Domain.Session session) {
    return Sm.SessionResponse.newBuilder()
        .setAccountId(entityId)
        .setSessionId(session.getSessionId())
        .setExpiration(session.getExpiration())
        .build();
  }

  private Sm.Session convert(Domain.Session session) {
    return Sm.Session.newBuilder()
        .setDeviceId(session.getDeviceId())
        .setSessionId(session.getSessionId())
        .setExpiration(session.getExpiration())
        .build();
  }

  private Domain.Session convert(Sm.Session session) {
    return Domain.Session.newBuilder()
        .setDeviceId(session.getDeviceId())
        .setSessionId(session.getSessionId())
        .setExpiration(session.getExpiration())
        .build();
  }

  private String generateSessionID() {
    return UUID.randomUUID().toString();
  }

  private long newSessionExpiration() {
    return java.time.Instant.now().plusSeconds(SESSION_DURATION_IN_SECONDS).getEpochSecond();
  }

  private boolean hasExpired(Sm.Session s) {
    long now = java.time.Instant.now().getEpochSecond();
    return now >= s.getExpiration();
  }

  public void purgeExpiredSessions(CommandContext ctx) {
    Vector<String> expiredSessions = new Vector<String>();

    for (Sm.Session s : sessions.values()) {
      if (hasExpired(s)) {
        expiredSessions.add(s.getSessionId());
      }
    }
    for (String sessionId : expiredSessions) {
      ctx.emit(Domain.SessionExpired.newBuilder().setSessionId(sessionId).build());
    }
  }

  @CommandHandler
  public Empty setMaxSession(Sm.MaxSession max, CommandContext ctx) {
    ctx.emit(
        Domain.MaxSessionSet.newBuilder().setNbActiveSessions(max.getMaxNbOfSession()).build());
    return Empty.getDefaultInstance();
  }

  @EventHandler
  public void maxSessionSet(Domain.MaxSessionSet evt) {
    maxActiveSessions = evt.getNbActiveSessions();
  }

  @CommandHandler
  public Sm.Home getHome(CommandContext ctx) {
    purgeExpiredSessions(ctx);

    return Sm.Home.newBuilder()
        .addAllSessions(sessions.values())
        .setNbActiveSessions(maxActiveSessions)
        .build();
  }

  @CommandHandler
  public Sm.SessionResponse createSession(Sm.SessionSetup sessionSetup, CommandContext ctx) {
    purgeExpiredSessions(ctx);

    // we should not only verify the max but also purge expired sessions
    if (sessions.size() >= maxActiveSessions) {
      ctx.fail(
          "Cannot create session. Max of active sessions (" + maxActiveSessions + ") exhausted!");
    }

    String newSessionID = generateSessionID();
    Domain.Session createdSession =
        Domain.Session.newBuilder()
            .setDeviceId(sessionSetup.getDeviceId())
            .setSessionId(newSessionID)
            .setExpiration(newSessionExpiration())
            .build();
    ctx.emit(Domain.SessionCreated.newBuilder().setSession(createdSession).build());
    return convertToSessionResponse(createdSession);
  }

  @EventHandler
  public void sessionCreated(Domain.SessionCreated sessionCreated) {
    Sm.Session session = convert(sessionCreated.getSession());
    sessions.put(session.getSessionId(), session);
  }

  @CommandHandler
  public Sm.SessionResponse heartBeat(Sm.HeartBeatSession session, CommandContext ctx) {
    // In case of heartbeat, the SM renew always the session. It will not check the max.
    Sm.Session s = sessions.get(session.getSessionId());
    if (s == null) {
      ctx.fail("Cannot renew non-existing session!");
    } else if (hasExpired(s)) {
      ctx.emit(Domain.SessionExpired.newBuilder().setSessionId(session.getSessionId()).build());
      ctx.fail("Cannot renew expired session!");
    }

    String newSessionID = generateSessionID();
    Domain.Session createdSession =
        Domain.Session.newBuilder()
            .setDeviceId(s.getDeviceId())
            .setSessionId(newSessionID)
            .setExpiration(newSessionExpiration())
            .build();

    ctx.emit(
        Domain.SessionRenewed.newBuilder()
            .setNewSession(createdSession)
            .setExpiredSessionId(session.getSessionId())
            .build());
    return convertToSessionResponse(createdSession);
  }

  @EventHandler
  public void sessionRenewed(Domain.SessionRenewed sessionRenewed) {
    String expiredSessionId = sessionRenewed.getExpiredSessionId();
    Sm.Session session = sessions.get(expiredSessionId);
    // Null session should never happen because before a renew, we should always already a session
    if (session != null) {
      sessions.remove(expiredSessionId);
    }
    session = convert(sessionRenewed.getNewSession());
    sessions.put(session.getSessionId(), session);
  }

  @CommandHandler
  public Empty tearDown(Sm.TearDownSession session, CommandContext ctx) {
    ctx.emit(Domain.SessionTearedDown.newBuilder().setSessionId(session.getSessionId()).build());
    return Empty.getDefaultInstance();
  }

  @EventHandler
  public void tearDown(Domain.SessionTearedDown sessionTearedDown) {
    String sessionId = sessionTearedDown.getSessionId();
    Sm.Session session = sessions.get(sessionId);
    // Null session should never happen because before a tearDown, session exists
    if (session != null) {
      sessions.remove(sessionId);
    }
  }

  @EventHandler
  public void sessionExpired(Domain.SessionExpired sessionExpired) {
    sessions.remove(sessionExpired.getSessionId());
  }

  @Snapshot
  public Domain.Home snapshot() {
    return Domain.Home.newBuilder()
        .addAllSessions(sessions.values().stream().map(this::convert).collect(Collectors.toList()))
        .setNbActiveSessions(maxActiveSessions)
        .build();
  }

  @SnapshotHandler
  public void handleSnapshot(Domain.Home home) {
    sessions.clear();
    for (Domain.Session session : home.getSessionsList()) {
      sessions.put(session.getSessionId(), convert(session));
    }
    maxActiveSessions = home.getNbActiveSessions();
  }
}
