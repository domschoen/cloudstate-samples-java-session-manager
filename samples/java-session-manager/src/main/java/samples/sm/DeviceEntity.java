package samples.sm;

import com.example.sm.Sm;
import com.example.smdevice.Smdevice;
import com.example.smdevice.persistence.Domain;
import com.google.protobuf.Empty;
import io.cloudstate.javasupport.Context;
import io.cloudstate.javasupport.EntityId;
import io.cloudstate.javasupport.ServiceCall;
import io.cloudstate.javasupport.ServiceCallRef;
import io.cloudstate.javasupport.eventsourced.*;

import java.util.*;
import java.util.stream.Collectors;

/** An event sourced entity. */
@EventSourcedEntity
public class DeviceEntity {
  private final String entityId;
  private final ServiceCallRef<Sm.SessionSetup> sessionCreationRef;
  private Optional<Smdevice.DeviceInfo> device = Optional.empty();

  public DeviceEntity(@EntityId String entityId, Context ctx) {

    this.entityId = entityId;

    sessionCreationRef =
        ctx.serviceCallFactory()
            .lookup("com.example.sm.SM", "CreateSession", Sm.SessionSetup.class);
  }

  @Snapshot
  public Optional<Domain.Device> snapshot() {
    if (device.isPresent()) {
      return Optional.of(
          Domain.Device.newBuilder().setAccountId(device.get().getAccountId()).build());
    } else {
      return Optional.empty();
    }
  }

  @SnapshotHandler
  public void handleSnapshot(Domain.Device device) {
    this.device =
        Optional.of(Smdevice.DeviceInfo.newBuilder().setAccountId(device.getAccountId()).build());
  }

  @EventHandler
  public void deviceCreated(Domain.DeviceCreated deviceCreated) {
    device =
        Optional.of(
            Smdevice.DeviceInfo.newBuilder().setAccountId(deviceCreated.getAccountId()).build());
  }

  @EventHandler
  public void deviceDeleted(Domain.DeviceDeleted deviceDeleted) {
    device = Optional.empty();
  }

  @CommandHandler
  public void createSessionWithDevice(
      Smdevice.SessionSetupWithDevice sessionSetup, CommandContext ctx) {
    if (!device.isPresent()) {
      ctx.fail("Device not registered");
    }
    String accountID = device.get().getAccountId();
    ServiceCall call =
        sessionCreationRef.createCall(
            Sm.SessionSetup.newBuilder()
                .setAccountId(accountID)
                .setDeviceId(sessionSetup.getDeviceId())
                .build());
    ctx.forward(call);
  }

  @CommandHandler
  public Smdevice.DeviceInfo getDevice(CommandContext ctx) {
    // Return a copy
    if (!device.isPresent()) {
      ctx.fail("Device has been deleted");
    }
    return Smdevice.DeviceInfo.newBuilder().setAccountId(device.get().getAccountId()).build();
  }

  @CommandHandler
  public Empty createDevice(Smdevice.CreateDeviceParam param, CommandContext ctx) {
    ctx.emit(Domain.DeviceCreated.newBuilder().setAccountId(param.getAccountId()).build());
    return Empty.getDefaultInstance();
  }

  @CommandHandler
  public Empty deleteDevice(Smdevice.DeleteDeviceParam param, CommandContext ctx) {
    ctx.emit(Domain.DeviceDeleted.newBuilder().setAccountId(device.get().getAccountId()).build());
    return Empty.getDefaultInstance();
  }
}
