package com.toni.wings.client;

import com.toni.wings.Proxy;
import com.toni.wings.WingsMod;
import com.toni.wings.client.apparatus.WingForm;
import com.toni.wings.client.flight.Animator;
import com.toni.wings.client.flight.AnimatorAvian;
import com.toni.wings.client.flight.AnimatorInsectoid;
import com.toni.wings.client.flight.FlightView;
import com.toni.wings.client.model.ModelWings;
import com.toni.wings.client.model.ModelWingsAvian;
import com.toni.wings.client.model.ModelWingsInsectoid;
import com.toni.wings.client.renderer.LayerWings;
import com.toni.wings.server.flight.Flight;
import com.toni.wings.server.flight.Flights;
import com.toni.wings.server.item.WingsItems;
import com.toni.wings.server.net.serverbound.MessageControlFlying;
import com.toni.wings.util.KeyInputListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;
import java.util.stream.Stream;

public final class ClientProxy extends Proxy {
    private final ModelWings<AnimatorAvian> avianWings = new ModelWingsAvian();

    @Override
    public void init(IEventBus modBus) {
        super.init(modBus);
        MinecraftForge.EVENT_BUS.register(KeyInputListener.builder()
            .category("key.categories.wings")
            .key("key.wings.fly", KeyConflictContext.IN_GAME, KeyModifier.NONE, GLFW.GLFW_KEY_R)
            .onPress(() -> {
                Player player = Minecraft.getInstance().player;
                Flights.get(player).filter(flight -> flight.canFly(player)).ifPresent(flight ->
                    flight.toggleIsFlying(Flight.PlayerSet.ofOthers())
                );
            })
            .build()
        );
        modBus.<FMLClientSetupEvent>addListener(e -> {
            e.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                EntityRenderDispatcher manager = mc.getEntityRenderDispatcher();
                Stream.concat(manager.getSkinMap().values().stream(), manager.renderers.values().stream())
                    .filter(LivingEntityRenderer.class::isInstance)
                    .map(r -> (LivingEntityRenderer<?, ?>) r)
                    .filter(render -> render.getModel() instanceof HumanoidModel<?>)
                    .unordered()
                    .distinct()
                    .forEach(render -> {
                        ModelPart body = ((HumanoidModel<?>) render.getModel()).body;
                        @SuppressWarnings("unchecked") LivingEntityRenderer<LivingEntity, HumanoidModel<LivingEntity>> livingRender = (LivingEntityRenderer<LivingEntity, HumanoidModel<LivingEntity>>) render;
                        livingRender.addLayer(new LayerWings(livingRender, (player, stack) -> {
                            if (player.isCrouching()) {
                                stack.translate(0.0D, 0.2D, 0.0D);
                            }
                            body.translateAndRotate(stack);
                        }));
                    });
            });
            WingForm.register(WingsMod.ANGEL_WINGS, this.createAvianWings(WingsMod.WINGS.getKey(WingsMod.ANGEL_WINGS)));
            WingForm.register(WingsMod.PARROT_WINGS, this.createAvianWings(WingsMod.WINGS.getKey(WingsMod.PARROT_WINGS)));
            WingForm.register(WingsMod.BAT_WINGS, this.createAvianWings(WingsMod.WINGS.getKey(WingsMod.BAT_WINGS)));
            WingForm.register(WingsMod.BLUE_BUTTERFLY_WINGS, this.createInsectoidWings(WingsMod.WINGS.getKey(WingsMod.BLUE_BUTTERFLY_WINGS)));
            WingForm.register(WingsMod.DRAGON_WINGS, this.createAvianWings(WingsMod.WINGS.getKey(WingsMod.DRAGON_WINGS)));
            WingForm.register(WingsMod.EVIL_WINGS, this.createAvianWings(WingsMod.WINGS.getKey(WingsMod.EVIL_WINGS)));
            WingForm.register(WingsMod.FAIRY_WINGS, this.createInsectoidWings(WingsMod.WINGS.getKey(WingsMod.FAIRY_WINGS)));
            WingForm.register(WingsMod.FIRE_WINGS, this.createAvianWings(WingsMod.WINGS.getKey(WingsMod.FIRE_WINGS)));
            WingForm.register(WingsMod.MONARCH_BUTTERFLY_WINGS, this.createInsectoidWings(WingsMod.WINGS.getKey(WingsMod.MONARCH_BUTTERFLY_WINGS)));
            WingForm.register(WingsMod.SLIME_WINGS, this.createInsectoidWings(WingsMod.WINGS.getKey(WingsMod.SLIME_WINGS)));
        });
        modBus.<ColorHandlerEvent.Item>addListener(e -> {
            e.getItemColors().register((stack, pass) -> pass == 0 ? 0x9B172D : 0xFFFFFF, WingsItems.BAT_BLOOD_BOTTLE.get());
        });
    }

    /*@Override
    protected void setup(FMLCommonSetupEvent event) {
        super.setup(event);
        CapabilityManager.INSTANCE.register(FlightView.class, SimpleStorage.ofVoid(), () -> {
            throw new UnsupportedOperationException();
        });
    }*/

    @Override
    protected void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        super.registerCapabilities(event);
        event.register(FlightView.class);
    }

    @Override
    public void addFlightListeners(Player player, Flight flight) {
        super.addFlightListeners(player, flight);
        if (player.isLocalPlayer()) {
            Flight.Notifier notifier = Flight.Notifier.of(
                () -> {
                },
                p -> {
                },
                () -> this.network.sendToServer(new MessageControlFlying(flight.isFlying()))
            );
            flight.registerSyncListener(players -> players.notify(notifier));
        }
    }

    private WingForm<AnimatorAvian> createAvianWings(ResourceLocation name) {
        return this.createWings(name, AnimatorAvian::new, this.avianWings);
    }


    private WingForm<AnimatorInsectoid> createInsectoidWings(ResourceLocation name) {
        ModelWings<AnimatorInsectoid> insectoidWings = new ModelWingsInsectoid(getModel().bakeLayer(LayerWings.INSECTOID_WINGS));
        return this.createWings(name, AnimatorInsectoid::new, insectoidWings);
    }

    private <A extends Animator> WingForm<A> createWings(ResourceLocation name, Supplier<A> animator, ModelWings<A> model) {
        return WingForm.of(
            animator,
            model,
            new ResourceLocation(name.getNamespace(), String.format("textures/entity/%s.png", name.getPath()))
        );
    }

    @OnlyIn(Dist.CLIENT)
    private static net.minecraft.client.model.geom.EntityModelSet getModel()
    {
        return net.minecraft.client.Minecraft.getInstance().getEntityModels();
    }
}