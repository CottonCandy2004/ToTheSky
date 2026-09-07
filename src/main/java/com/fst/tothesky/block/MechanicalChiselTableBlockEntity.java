package com.fst.tothesky.block;

import com.fst.tothesky.ToTheSky;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 动力雕刻台的方块实体：7 栏位容器 + FE 储能 + 过滤器。
 * 每 tick 消耗 100FE 执行一次 ultramarine:chisel_table 配方（上限 10 次/tick）。
 */
public class MechanicalChiselTableBlockEntity extends BlockEntity {
    private final ItemStackHandler inventory = new ItemStackHandler(7) {
        @Override
        public int getSlotLimit(int slot) {
            return slot == MechanicalChiselTableBlock.SLOT_TEMPLATE ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == MechanicalChiselTableBlock.SLOT_TEMPLATE) {
                return MechanicalChiselTableBlock.isChiselTemplate(stack);
            }
            return true;
        }
    };
    private final EnergyStorage energy = new EnergyStorage(MechanicalChiselTableBlock.MAX_ENERGY, 1000, 0);
    @Nullable
    private ResourceLocation filterId;

    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> inventory);

    public MechanicalChiselTableBlockEntity(BlockPos pos, BlockState state) {
        super(com.fst.tothesky.registry.ModBlockEntities.MECHANICAL_CHISEL_TABLE.get(), pos, state);
    }

    // ---- 外部接口（方块 use 逻辑用） ----
    public int getContainerSize() {
        return inventory.getSlots();
    }

    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        // 输出槽只出不进；其他槽走包装校验
        if (slot == MechanicalChiselTableBlock.SLOT_RESULT) {
            return stack;
        }
        return inventory.insertItem(slot, stack, simulate);
    }

    public ItemStack removeItem(int slot, int amount) {
        return inventory.extractItem(slot, amount, false);
    }

    @Nullable
    public ResourceLocation getFilterId() {
        return filterId;
    }

    public void setFilterId(@Nullable ResourceLocation id) {
        this.filterId = id;
    }

    // ---- 逻辑 tick ----
    public static void serverTick(MechanicalChiselTableBlockEntity entity) {
        if (entity.energy.getEnergyStored() < MechanicalChiselTableBlock.ENERGY_COST) {
            return;
        }
        for (int op = 0; op < MechanicalChiselTableBlock.MAX_OPERATIONS_PER_TICK; op++) {
            if (!entity.tryCraftOnce()) {
                return;
            }
        }
    }

    private boolean tryCraftOnce() {
        if (energy.getEnergyStored() < MechanicalChiselTableBlock.ENERGY_COST || level == null) {
            return false;
        }
        // 栏位 0-5 为输入
        SimpleContainer container = new SimpleContainer(6);
        for (int i = 0; i < 6; i++) {
            container.setItem(i, inventory.getStackInSlot(i));
        }
        var recipeOpt = level.getRecipeManager()
                .getRecipeFor(com.voxelutopia.ultramarine.data.registry.RecipeTypeRegistry.CHISEL_TABLE.get(),
                        container, level);
        if (recipeOpt.isEmpty()) {
            return false;
        }
        ItemStack result = recipeOpt.get().assemble(container, level.registryAccess());
        if (filterId != null && !ForgeRegistries.ITEMS.getKey(result.getItem()).equals(filterId)) {
            return false;
        }
        // 输出槽模拟插入
        ItemStack remainder = inventory.insertItem(MechanicalChiselTableBlock.SLOT_RESULT, result, true);
        if (remainder.getCount() == result.getCount()) {
            return false;
        }
        // 消耗
        energy.extractEnergy(MechanicalChiselTableBlock.ENERGY_COST, false);
        inventory.extractItem(MechanicalChiselTableBlock.SLOT_MATERIAL, 1, false);
        for (int i = MechanicalChiselTableBlock.SLOT_COLOR_START; i <= MechanicalChiselTableBlock.SLOT_COLOR_END; i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                inventory.extractItem(i, 1, false);
            }
        }
        inventory.insertItem(MechanicalChiselTableBlock.SLOT_RESULT, result, false);
        setChanged();
        return true;
    }

    // ---- 能力 ----
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return LazyOptional.of(() -> energy).cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
    }

    // ---- 序列化 ----
    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        if (tag.contains("Energy")) {
            energy.receiveEnergy(tag.getInt("Energy"), false);
        }
        if (tag.contains("Filter") && !tag.getString("Filter").isEmpty()) {
            filterId = new ResourceLocation(tag.getString("Filter"));
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putString("Filter", filterId != null ? filterId.toString() : "");
    }
}