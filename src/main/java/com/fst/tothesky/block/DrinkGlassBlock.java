package com.fst.tothesky.block;

import com.fst.tothesky.cocktail.CocktailHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 可放置的鸡尾酒（无物品形态）：
 * <ul>
 *     <li>潜行 + 手持对应鸡尾酒右键坚实方块顶面 → 放置（见 {@code ModGameEvents}）</li>
 *     <li>手持同种鸡尾酒右键 → 叠一份（至上限）</li>
 *     <li>潜行空手右键 → 取回一份；取完即消失</li>
 *     <li>空手右键最后一份 → 直接喝掉，留下空杯</li>
 *     <li>非创造破坏 → 按份数掉落鸡尾酒</li>
 * </ul>
 * stacks 属性的上限因杯型而异，故拆成三个子类（方块状态属性必须在超类构造期可用）。
 *
 * 1.20.1 适配：useItemOn/useWithoutItem 合并为单一 use()，
 * 逻辑按「手持物 → 潜行 → 空手」的顺序在方法内分流。
 */
public abstract class DrinkGlassBlock extends HorizontalDirectionalBlock {
    public static final IntegerProperty STACKS_2 = IntegerProperty.create("stacks", 1, 2);
    public static final IntegerProperty STACKS_3 = IntegerProperty.create("stacks", 1, 3);
    public static final IntegerProperty STACKS_4 = IntegerProperty.create("stacks", 1, 4);

    private final ResourceLocation cocktailId;
    private final int maxStacks;
    private final java.util.function.Supplier<? extends Block> emptyGlass;
    private final VoxelShape shape;

    private DrinkGlassBlock(ResourceLocation cocktailId, int maxStacks, java.util.function.Supplier<? extends Block> emptyGlass,
                            Properties properties, int height) {
        super(properties);
        this.cocktailId = cocktailId;
        this.maxStacks = maxStacks;
        this.emptyGlass = emptyGlass;
        this.shape = Block.box(1, 0, 1, 15, height, 15);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(stacksProperty(), 1));
    }

    /** 在超类构造期间即被调用，因此必须只返回静态常量 */
    protected abstract IntegerProperty stacksProperty();

    public ResourceLocation cocktailId() {
        return cocktailId;
    }

    protected java.util.function.Supplier<? extends Block> emptyGlass() {
        return emptyGlass;
    }

    /** 马天尼杯，最多 2 份 */
    public static class Martini extends DrinkGlassBlock {
        public Martini(ResourceLocation cocktailId, java.util.function.Supplier<? extends Block> emptyGlass, Properties properties) {
            super(cocktailId, 2, emptyGlass, properties, 10);
        }

        @Override
        protected IntegerProperty stacksProperty() {
            return STACKS_2;
        }
    }

    /** 飓风杯，最多 3 份 */
    public static class Hurricane extends DrinkGlassBlock {
        public Hurricane(ResourceLocation cocktailId, java.util.function.Supplier<? extends Block> emptyGlass, Properties properties) {
            super(cocktailId, 3, emptyGlass, properties, 11);
        }

        @Override
        protected IntegerProperty stacksProperty() {
            return STACKS_3;
        }
    }

    /** 古典杯，最多 4 份 */
    public static class OldFashioned extends DrinkGlassBlock {
        public OldFashioned(ResourceLocation cocktailId, java.util.function.Supplier<? extends Block> emptyGlass, Properties properties) {
            super(cocktailId, 4, emptyGlass, properties, 7);
        }

        @Override
        protected IntegerProperty stacksProperty() {
            return STACKS_4;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, stacksProperty());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);
        int stacks = state.getValue(stacksProperty());

        // 1. 手持对应鸡尾酒 → 叠一份（至上限）
        if (!stack.isEmpty()) {
            ResourceLocation held = CocktailHelper.cocktailId(stack);
            if (cocktailId.equals(held) && stacks < maxStacks) {
                if (!level.isClientSide) {
                    level.setBlock(pos, state.setValue(stacksProperty(), stacks + 1), 3);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    player.swing(hand, true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }

        // 2. 空手
        if (player.isShiftKeyDown()) {
            // 取回一份；取完最后一份时整杯消失（不留空杯）
            if (!level.isClientSide) {
                if (!player.getAbilities().instabuild) {
                    ItemStack cocktail = CocktailHelper.createCocktail(cocktailId);
                    if (!cocktail.isEmpty() && !player.addItem(cocktail)) {
                        player.drop(cocktail, false);
                    }
                }
                player.swing(InteractionHand.MAIN_HAND, true);
                if (stacks <= 1) {
                    level.removeBlock(pos, false);
                } else {
                    level.setBlock(pos, state.setValue(stacksProperty(), stacks - 1), 3);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stacks != 1) {
            return InteractionResult.PASS;
        }
        // 空手右键最后一份：直接喝掉并留下空杯
        ItemStack cocktail = CocktailHelper.createCocktail(cocktailId);
        if (cocktail.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            cocktail.getItem().finishUsingItem(cocktail, level, player);
            level.setBlock(pos, emptyGlass.get().defaultBlockState().setValue(FACING, state.getValue(FACING)), 3);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f, level.random.nextFloat() * 0.1f + 0.9f);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // 无 loot table；破坏时按份数手动掉落鸡尾酒
        if (!level.isClientSide && !player.getAbilities().instabuild) {
            ItemStack drop = CocktailHelper.createCocktail(cocktailId);
            if (!drop.isEmpty()) {
                drop.setCount(state.getValue(stacksProperty()));
                popResource(level, pos, drop);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}