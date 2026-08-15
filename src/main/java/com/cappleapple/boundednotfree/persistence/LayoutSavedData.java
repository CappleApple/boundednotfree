package com.cappleapple.boundednotfree.persistence;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public final class LayoutSavedData extends SavedData {
    public static final String FILE_ID = "boundednotfree_layout";
    private String configHash = "";
    private String lockedConfigJson = "";

    public static SavedData.Factory<LayoutSavedData> factory() { return new SavedData.Factory<>(LayoutSavedData::new, LayoutSavedData::load); }
    public static LayoutSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        LayoutSavedData data = new LayoutSavedData();
        data.configHash = tag.getString("configHash");
        data.lockedConfigJson = tag.getString("lockedConfigJson");
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("configHash", configHash); tag.putString("lockedConfigJson", lockedConfigJson); return tag;
    }
    public boolean initialized() { return !configHash.isEmpty(); }
    public String configHash() { return configHash; }
    public String lockedConfigJson() { return lockedConfigJson; }
    public void initialize(String hash, String json) { if (!initialized()) { configHash = hash; lockedConfigJson = json; setDirty(); } }
}
