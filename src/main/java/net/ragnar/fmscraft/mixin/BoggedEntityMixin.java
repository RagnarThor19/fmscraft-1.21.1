package net.ragnar.fmscraft.mixin;

import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.BoggedEntity;
import net.minecraft.nbt.NbtCompound;
import net.ragnar.fmscraft.fragment.ShiveringBogged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The shivering Bogged of Envy (see EnvyTrigger): a flag saved with the mob and synced to clients. */
@Mixin(BoggedEntity.class)
public abstract class BoggedEntityMixin implements ShiveringBogged {
    @Unique
    private static final TrackedData<Byte> FMSCRAFT$SHIVER = DataTracker.registerData(BoggedEntity.class, TrackedDataHandlerRegistry.BYTE);
    @Unique
    private static final byte SHIVERING = 1, CALM = 2;
    @Unique
    private static final String KEY = "fmscraft:shivering";

    @Unique
    private DataTracker fmscraft$tracker() {
        return ((BoggedEntity) (Object) this).getDataTracker();
    }

    @Unique
    private void fmscraft$setBit(byte bit, boolean on) {
        byte flags = fmscraft$tracker().get(FMSCRAFT$SHIVER);
        fmscraft$tracker().set(FMSCRAFT$SHIVER, (byte) (on ? flags | bit : flags & ~bit));
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void fmscraft$trackShiver(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(FMSCRAFT$SHIVER, (byte) 0);
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void fmscraft$writeShiver(NbtCompound nbt, CallbackInfo ci) {
        if (fmscraft$isShivering()) nbt.putBoolean(KEY, true);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void fmscraft$readShiver(NbtCompound nbt, CallbackInfo ci) {
        fmscraft$setShivering(nbt.getBoolean(KEY));
    }

    @Override
    public boolean fmscraft$isShivering() {
        return (fmscraft$tracker().get(FMSCRAFT$SHIVER) & SHIVERING) != 0;
    }

    @Override
    public void fmscraft$setShivering(boolean shivering) {
        fmscraft$setBit(SHIVERING, shivering);
    }

    @Override
    public boolean fmscraft$isCalm() {
        return (fmscraft$tracker().get(FMSCRAFT$SHIVER) & CALM) != 0;
    }

    @Override
    public void fmscraft$setCalm(boolean calm) {
        fmscraft$setBit(CALM, calm);
    }
}
