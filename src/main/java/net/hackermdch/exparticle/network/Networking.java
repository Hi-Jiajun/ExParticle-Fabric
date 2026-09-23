package net.hackermdch.exparticle.network;

import net.hackermdch.exparticle.command.particleex.*;
import net.hackermdch.exparticle.compat.PayloadRegistrar;

public class Networking {
    private static void register(PayloadRegistrar registrar) {
        ClearParticlePayload.register(registrar);
        ClearCachePayload.register(registrar);
        NormalPayload.register(registrar);
        ConditionalPayload.register(registrar);
        ParameterPayload.register(registrar);
        ImagePayload.register(registrar);
        ImageMatrixPayload.register(registrar);
        VideoPayload.register(registrar);
        VideoMatrixPayload.register(registrar);
        TextPayload.register(registrar);
        GroupRemovePayload.register(registrar);
        GroupChangePayload.register(registrar);
        GlobalVariablePayload.register(registrar);
        UserFunctionPayload.register(registrar);
        CustomNormalPayload.register(registrar);
        CustomConditionalPayload.register(registrar);
        CustomParameterPayload.register(registrar);
        CustomImagePayload.register(registrar);
        CustomImageMatrixPayload.register(registrar);
        CustomVideoPayload.register(registrar);
        CustomVideoMatrixPayload.register(registrar);
        QueryPayload.register(registrar);
    }

    public static void register() {
        register(new PayloadRegistrar());
    }
}
