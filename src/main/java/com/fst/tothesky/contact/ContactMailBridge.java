package com.fst.tothesky.contact;

import com.flechazo.contact.common.item.ParcelItem;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.storage.MailToBeSent;
import com.flechazo.contact.data.PostcardDataManager;
import com.flechazo.contact.platform.PlatformHelper;
import com.fst.tothesky.ToTheSky;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

/**
 * Contact 类引用的唯一落点：只有本类 import {@code com.flechazo.contact.*}。
 *
 * <p>Contact 在 mods.toml 里是 {@code mandatory=false} 的编译期依赖，故这些引用必须
 * 与「调用前的 {@link ContactMail#loaded()} 检查」成对出现——未安装时本类永不被加载，
 * 也就不会 {@code NoClassDefFoundError}。这与 {@code ContactMailboxFixerEvents}
 * 用类名字符串判定 Contact 方块是同一个意图。
 *
 * <p>邮件 NBT 全部照抄 Contact 自己的写法，保证 mod 的读取路径（邮箱、明信片渲染、
 * 开包）原样认得：
 * <ul>
 *   <li>明信片 = {@link PostcardItem#getPostcard(ResourceLocation, boolean)} + {@code Text} + {@code Sender}
 *       （对照 {@code ContactCommand#deliverPostcard}）；</li>
 *   <li>包裹 = {@link ParcelItem#getParcel(SimpleContainer, boolean, String)}
 *       （{@code parcel} 容器标签 + {@code Sender}）；</li>
 *   <li>红包 = {@code contact:red_packet} + {@code parcel} 容器标签 + {@code blessing}
 *       （对照 {@code RedPacketEnvelopeScreenHandler#getPackedItem} 与 {@code PackageScreenHandler#removed}）。</li>
 * </ul>
 */
final class ContactMailBridge {
    /** 邮件容器标签（Contact 的 {@code parcel} 键，包裹与红包共用） */
    private static final String TAG_PARCEL = "parcel";
    /** 红包祝福语标签 */
    private static final String TAG_BLESSING = "blessing";
    /** 寄件人标签 */
    private static final String TAG_SENDER = "Sender";
    /** 红包物品 id（Contact 没有对外工厂方法，只能按注册名取） */
    private static final ResourceLocation RED_PACKET_ID = new ResourceLocation("contact", "red_packet");

    private ContactMailBridge() {
    }

    /**
     * 款式是否存在。
     * <p>数据包尚未装载（款式表为空）时不拦截——宁可交给 Contact 回落默认款式，
     * 也不要把合法款式误判成非法。
     */
    static boolean hasPostcard(ResourceLocation style) {
        return PostcardDataManager.getPostcards().isEmpty() || PostcardDataManager.hasPostcard(style);
    }

    static ItemStack postcard(ResourceLocation style, String text) {
        ItemStack card = PostcardItem.setText(PostcardItem.getPostcard(style, false), text);
        card.getOrCreateTag().putString(TAG_SENDER, ContactMail.SYSTEM_SENDER);
        return card;
    }

    static ItemStack parcel(List<ItemStack> contents) {
        SimpleContainer container = new SimpleContainer(ContactMail.PARCEL_CAPACITY);
        fill(container, contents);
        return ParcelItem.getParcel(container, false, ContactMail.SYSTEM_SENDER);
    }

    static ItemStack redPacket(List<ItemStack> contents, String blessing) {
        Item item = ForgeRegistries.ITEMS.getValue(RED_PACKET_ID);
        if (item == null) {
            // 只可能是 Contact 改了物品 id（注册期调用也会命中这里），明确报出来免得只看到「邮件无效」
            ToTheSky.LOGGER.warn("[往来] Contact 未注册物品 {}，红包未构建", RED_PACKET_ID);
            return ItemStack.EMPTY;
        }
        SimpleContainer container = new SimpleContainer(ContactMail.RED_PACKET_CAPACITY);
        fill(container, contents);
        ItemStack packet = new ItemStack(item);
        CompoundTag tag = packet.getOrCreateTag();
        tag.put(TAG_PARCEL, container.createTag());
        // 与 RedPacketEnvelopeScreenHandler 一致：没祝福语就不写标签，物品提示随之不显示祝福行
        if (blessing != null && !blessing.isBlank()) {
            tag.putString(TAG_BLESSING, blessing);
        }
        tag.putString(TAG_SENDER, ContactMail.SYSTEM_SENDER);
        return packet;
    }

    /**
     * 交给 Contact 的挂号队列：0 tick = 下一次投递轮询（20 tick 一轮）即入箱。
     * <p>收件人邮箱满时 Contact 会把这封信留在队列里等空位，并给在线的收件人
     * 发「有新邮件」提示——这正是 mod 自己的 {@code /contact postcard deliver} 行为。
     */
    static void deliver(UUID target, ItemStack mail) {
        PlatformHelper.getMailList().add(new MailToBeSent(target, mail.copy(), 0L));
    }

    private static void fill(SimpleContainer container, List<ItemStack> contents) {
        for (int i = 0; i < contents.size(); i++) {
            container.setItem(i, contents.get(i).copy());
        }
    }
}
