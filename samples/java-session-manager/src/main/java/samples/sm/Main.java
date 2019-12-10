package samples.sm;

import io.cloudstate.javasupport.*;
import com.example.sm.Sm;
import com.example.smdevice.Smdevice;
import static java.util.Collections.singletonMap;

public final class Main {
  public static final void main(String[] args) throws Exception {

    new CloudState()
        .registerEventSourcedEntity(
            DeviceEntity.class,
            Smdevice.getDescriptor().findServiceByName("Device"),
            com.example.smdevice.persistence.Domain.getDescriptor())
        .registerEventSourcedEntity(
            HomeEntity.class,
            Sm.getDescriptor().findServiceByName("SM"),
            com.example.sm.persistence.Domain.getDescriptor())
        .start()
        .toCompletableFuture()
        .get();
  }
}
