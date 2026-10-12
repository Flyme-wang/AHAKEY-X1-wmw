import com.example.ahakey.model.DeviceStatus;
import com.example.ahakey.model.HIDUsage;
import com.example.ahakey.model.KeyConfig;
import com.example.ahakey.model.ModeSlot;
import com.example.ahakey.model.StudioPart;
import com.example.ahakey.model.StudioState;
import com.example.ahakey.model.VoicePreset;
import com.example.ahakey.service.BleManager;
import com.example.ahakey.service.DeviceSyncService;
import com.example.ahakey.util.StudioStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ApplyDoubaoVoiceKey {
    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. load draft ===");
        StudioState.PersistedDraft draft = StudioStore.loadOrDefault();
        StudioState state = new StudioState();
        state.loadFromPersisted(draft);

        System.out.println("=== 2. KEY1 before ===");
        for (ModeSlot m : ModeSlot.values()) {
            KeyConfig kc = state.getKeyConfig(m, StudioPart.KEY1);
            System.out.printf("  %s: hid=0x%X desc=%s preset=%s macroSteps=%d%n",
                    m.name(), kc.getHidCode(), kc.getDescription(),
                    kc.getVoicePreset(), kc.getMacroStepCount());
        }

        System.out.println("=== 3. set KEY1 -> RIGHT_ALT (0x"
                + Integer.toHexString(HIDUsage.RIGHT_ALT) + ") ===");
        for (ModeSlot m : ModeSlot.values()) {
            KeyConfig kc = state.getKeyConfig(m, StudioPart.KEY1);
            kc.setHidCode(HIDUsage.RIGHT_ALT);
            kc.setVoicePreset(VoicePreset.CUSTOM);
            kc.setDescription("Doubao");
            kc.setMacro(new ArrayList<>());
        }
        for (ModeSlot m : ModeSlot.values()) {
            KeyConfig kc = state.getKeyConfig(m, StudioPart.KEY1);
            System.out.printf("  %s: hid=0x%X desc=%s preset=%s%n",
                    m.name(), kc.getHidCode(), kc.getDescription(), kc.getVoicePreset());
        }

        System.out.println("=== 4. connect device ===");
        CountDownLatch connected = new CountDownLatch(1);
        AtomicBoolean connOk = new AtomicBoolean(false);
        BleManager ble = BleManager.fromEnvironment(new BleManager.BleCallback() {
            @Override public void onConnected() { connOk.set(true); connected.countDown(); }
            @Override public void onDisconnected() { }
            @Override public void onStatusReceived(DeviceStatus s) { }
            @Override public void onError(String e) { System.out.println("  BLE error: " + e); }
        });
        ble.connect();
        if (!connected.await(20, TimeUnit.SECONDS) || !connOk.get()) {
            System.out.println("RESULT=CONNECT_FAILED");
            System.exit(2);
        }
        System.out.println("  connected: " + ble.isConnected());
        Thread.sleep(800);

        System.out.println("=== 5. write commands to device ===");
        List<DeviceSyncService.LabeledCommand> cmds = DeviceSyncService.commandsForModes(
                state, ModeSlot.MODE0, ModeSlot.MODE1, ModeSlot.MODE2, ModeSlot.MODE3);
        System.out.println("  total commands: " + cmds.size());
        AtomicInteger n = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        DeviceSyncService.writeSequentially(ble, cmds,
                () -> { success.set(true); done.countDown(); },
                () -> { done.countDown(); },
                s -> System.out.println("  [" + n.incrementAndGet() + "] " + s));
        if (!done.await(180, TimeUnit.SECONDS)) {
            System.out.println("RESULT=WRITE_TIMEOUT");
            System.exit(3);
        }
        System.out.println(success.get() ? "  write success" : "  write FAILED");

        System.out.println("=== 6. save draft ===");
        if (success.get()) {
            StudioStore.save(state.toPersisted());
            System.out.println("  draft saved");
        } else {
            System.out.println("  draft NOT saved (write failed)");
        }

        ble.disconnect();
        System.out.println("RESULT=" + (success.get() ? "OK" : "FAILED"));
        System.exit(success.get() ? 0 : 1);
    }
}
