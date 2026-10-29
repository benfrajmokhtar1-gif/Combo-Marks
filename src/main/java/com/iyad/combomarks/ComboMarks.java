package com.iyad.combomarks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ComboMarks implements ModInitializer {
    private static final long COMBO_WINDOW_MS = 3000L;
    private static final Map<String, Combo> COMBOS = new HashMap<>();

    private static class Combo {
        int hits;
        long lastHit;
        Combo(int hits, long lastHit) {
            this.hits = hits;
            this.lastHit = lastHit;
        }
    }

    @Override
    public void onInitialize() {
        AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            if (world.isClient() || !(target instanceof HostileEntity) || !(target instanceof LivingEntity)) {
                return ActionResult.PASS;
            }

            long now = System.currentTimeMillis();
            String key = player.getUuidAsString() + ":" + target.getUuidAsString();
            Combo combo = COMBOS.get(key);

            if (combo == null || now - combo.lastHit > COMBO_WINDOW_MS) {
                combo = new Combo(0, now);
                COMBOS.put(key, combo);
            }

            combo.hits++;
            combo.lastHit = now;

            if (world instanceof ServerWorld serverWorld) {
                LivingEntity livingTarget = (LivingEntity) target;
                serverWorld.spawnParticles(ParticleTypes.CRIT,
                        livingTarget.getX(), livingTarget.getBodyY(1.0), livingTarget.getZ(),
                        Math.min(3 + combo.hits, 12), 0.25, 0.25, 0.25, 0.02);

                if (combo.hits >= 3) {
                    serverWorld.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                            livingTarget.getX(), livingTarget.getBodyY(1.0), livingTarget.getZ(),
                            8, 0.35, 0.3, 0.35, 0.03);
                }

                if (combo.hits == 3) {
                    Vec3d away = livingTarget.getPos().subtract(player.getPos());
                    livingTarget.takeKnockback(0.65, away.x, away.z);
                }

                if (combo.hits == 5) {
                    serverWorld.spawnParticles(ParticleTypes.EXPLOSION,
                            livingTarget.getX(), livingTarget.getBodyY(0.5), livingTarget.getZ(),
                            1, 0.2, 0.2, 0.2, 0.0);

                    for (Entity nearby : world.getOtherEntities(livingTarget,
                    livingTarget.getBoundingBox().expand(3.5),
                    entity -> entity instanceof HostileEntity && entity.isAlive())) {
                    Vec3d away = nearby.getPos().subtract(livingTarget.getPos());
                    if (away.lengthSquared() > 0.001) {
                        if (nearby instanceof LivingEntity living) {
                            living.takeKnockback(1.15, away.x, away.z);
                        }
                    }
                }
                COMBOS.remove(key);
            }
        }

        return ActionResult.PASS;
    });
}
}
