package com.fst.tothesky.block;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.item.PackedColorsItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
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
 * 动力雕刻台的方块实体：4 栏位容器 + FE 储能 + 过滤器。
 * 每 tick 消耗 100FE 执行一次 ultramarine:chisel_table 配方（上限 10 次/tick）。
 * 槽 2 只收打包染料（packed_colors），匹配时按 StoredColors 顺序展开到 ultramarine 的染料槽。
 */
public class MechanicalChiselTableBlockEntity extends BlockEntity {
    private final ItemStackHandler inventory = new ItemStackHandler(4) {
        @Override
        public int getSlotLimit(int slot) {
            return slot == MechanicalChiselTableBlock.SLOT_TEMPLATE ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == MechanicalChiselTableBlock.SLOT_MATERIAL) {
                return com.fst.tothesky.util.DyeHelper.isPlankOrLog(stack);
            }
            if (slot == MechanicalChiselTableBlock.SLOT_TEMPLATE) {
                return MechanicalChiselTableBlock.isChiselTemplate(stack);
            }
            if (slot == MechanicalChiselTableBlock.SLOT_PACKED_COLORS) {
                return stack.getItem() == com.fst.tothesky.registry.ModItems.PACKED_COLORS.get();
            }
            return false; // 产物槽(3)只出不进，其余槽位拒绝
        }
    };
    private final EnergyStorage energy = new EnergyStorage(MechanicalChiselTableBlock.MAX_ENERGY, 1000, 0);
    @Nullable
    private ResourceLocation filterId;

    /** 对外物品能力：输入按 isItemValid 路由到正确槽位；输出优先产物槽，产物空时才按请求槽位提取（模板可被提取） */
    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> new IItemHandler() {
        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack result = inventory.getStackInSlot(MechanicalChiselTableBlock.SLOT_RESULT);
            if (!result.isEmpty()) {
                return inventory.extractItem(MechanicalChiselTableBlock.SLOT_RESULT, amount, simulate);
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return inventory.isItemValid(slot, stack);
        }
    });

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
        // ultramarine 匹配容器：0 材料 / 1 模板 / 2-5 染料（打包染料按 StoredColors 顺序展开）
        SimpleContainer container = new SimpleContainer(6);
        container.setItem(0, inventory.getStackInSlot(MechanicalChiselTableBlock.SLOT_MATERIAL));
        container.setItem(1, inventory.getStackInSlot(MechanicalChiselTableBlock.SLOT_TEMPLATE));
        List<ItemStack> dyes = PackedColorsItem.getColors(
                inventory.getStackInSlot(MechanicalChiselTableBlock.SLOT_PACKED_COLORS));
        for (int i = 0; i < dyes.size() && i < 4; i++) {
            container.setItem(2 + i, dyes.get(i));
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
        // 产物槽直接写入（绕过 isItemValid：产物槽拒绝外部输入，内部产出不受限）
        ItemStack output = inventory.getStackInSlot(MechanicalChiselTableBlock.SLOT_RESULT);
        if (!output.isEmpty() && (!ItemStack.isSameItemSameTags(output, result)
                || output.getCount() + result.getCount() > output.getMaxStackSize())) {
            return false;
        }
        // 消耗：材料 -1、打包染料整体 -1（模板不消耗）
        energy.extractEnergy(MechanicalChiselTableBlock.ENERGY_COST, false);
        inventory.extractItem(MechanicalChiselTableBlock.SLOT_MATERIAL, 1, false);
        inventory.extractItem(MechanicalChiselTableBlock.SLOT_PACKED_COLORS, 1, false);
        if (output.isEmpty()) {
            inventory.setStackInSlot(MechanicalChiselTableBlock.SLOT_RESULT, result);
        } else {
            output.grow(result.getCount());
        }
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
            filterId = ResourceLocation.parse(tag.getString("Filter"));
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