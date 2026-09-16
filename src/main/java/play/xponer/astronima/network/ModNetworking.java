package play.xponer.astronima.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import play.xponer.astronima.Astronima;

@EventBusSubscriber(modid = Astronima.MODID)
public final class ModNetworking {
    @SubscribeEvent
    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // The lambda body only classloads the client HUD when a payload actually
        // arrives, which never happens on a dedicated server.
        event.registrar("1").playToClient(
                AtmosphereStatusPayload.TYPE,
                AtmosphereStatusPayload.STREAM_CODEC,
                (payload, context) -> play.xponer.astronima.client.AnalyzerHud.accept(payload));

        event.registrar("1").playToClient(
                CoherenceStatusPayload.TYPE,
                CoherenceStatusPayload.STREAM_CODEC,
                (payload, context) -> play.xponer.astronima.client.CoherenceHud.accept(payload));

        event.registrar("1").playToClient(
                AstraFieldReadingPayload.TYPE,
                AstraFieldReadingPayload.STREAM_CODEC,
                (payload, context) -> play.xponer.astronima.client.render.AstraGlowFx.accept(payload));

        event.registrar("1").playToServer(
                MachineSettingPayload.TYPE,
                MachineSettingPayload.STREAM_CODEC,
                MachineSettingPayload::apply);

        event.registrar("1").playToServer(
                SlsControlPayload.TYPE,
                SlsControlPayload.STREAM_CODEC,
                SlsControlPayload::apply);

        event.registrar("1").playToServer(
                FocusPayload.TYPE,
                FocusPayload.STREAM_CODEC,
                FocusPayload::apply);

        event.registrar("1").playToServer(
                AirlockCommandPayload.TYPE,
                AirlockCommandPayload.STREAM_CODEC,
                AirlockCommandPayload::apply);

        event.registrar("1").playToServer(
                AirlockTerminalPayload.TYPE,
                AirlockTerminalPayload.STREAM_CODEC,
                AirlockTerminalPayload::apply);

        event.registrar("1").playToServer(
                WireCoilSettingPayload.TYPE,
                WireCoilSettingPayload.STREAM_CODEC,
                WireCoilSettingPayload::apply);

        event.registrar("1").playToServer(
                CircuitPlatePayload.TYPE,
                CircuitPlatePayload.STREAM_CODEC,
                CircuitPlatePayload::apply);

        event.registrar("1").playToServer(
                PartRotatePayload.TYPE,
                PartRotatePayload.STREAM_CODEC,
                PartRotatePayload::apply);

        event.registrar("1").playToServer(
                OrientationUpdatePayload.TYPE,
                OrientationUpdatePayload.STREAM_CODEC,
                OrientationUpdatePayload::apply);

        event.registrar("1").playToServer(
                PushOffPayload.TYPE,
                PushOffPayload.STREAM_CODEC,
                PushOffPayload::apply);

        event.registrar("1").playToServer(
                TetherFirePayload.TYPE,
                TetherFirePayload.STREAM_CODEC,
                TetherFirePayload::apply);

        event.registrar("1").playToServer(
                TetherReleasePayload.TYPE,
                TetherReleasePayload.STREAM_CODEC,
                TetherReleasePayload::apply);

        event.registrar("1").playToServer(
                ProcessorProgramPayload.TYPE,
                ProcessorProgramPayload.STREAM_CODEC,
                ProcessorProgramPayload::apply);

        event.registrar("1").playToServer(
                SuitRepairDonePayload.TYPE,
                SuitRepairDonePayload.STREAM_CODEC,
                (payload, context) -> SuitRepairHandler.apply(payload, context));

        event.registrar("1").playToServer(
                ClaimResolvePayload.TYPE,
                ClaimResolvePayload.STREAM_CODEC,
                ClaimResolvePayload::apply);

        event.registrar("1").playToServer(
                ClaimStageCompletePayload.TYPE,
                ClaimStageCompletePayload.STREAM_CODEC,
                ClaimStageCompletePayload::apply);

        event.registrar("1").playToServer(
                TelescopeCaptureAttemptPayload.TYPE,
                TelescopeCaptureAttemptPayload.STREAM_CODEC,
                TelescopeCaptureAttemptPayload::apply);

        event.registrar("1").playToServer(
                TelescopeAimUpdatePayload.TYPE,
                TelescopeAimUpdatePayload.STREAM_CODEC,
                TelescopeAimUpdatePayload::apply);

        event.registrar("1").playToServer(
                TelescopeStopObservingPayload.TYPE,
                TelescopeStopObservingPayload.STREAM_CODEC,
                TelescopeStopObservingPayload::apply);

        event.registrar("1").playToServer(
                SpectrumDecodeAttemptPayload.TYPE,
                SpectrumDecodeAttemptPayload.STREAM_CODEC,
                SpectrumDecodeAttemptPayload::apply);

        event.registrar("1").playToServer(
                TerminalSortPayload.TYPE,
                TerminalSortPayload.STREAM_CODEC,
                TerminalSortPayload::apply);

        event.registrar("1").playToServer(
                TerminalScrollPayload.TYPE,
                TerminalScrollPayload.STREAM_CODEC,
                TerminalScrollPayload::apply);
    }

    private ModNetworking() {}
}
