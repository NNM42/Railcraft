/*------------------------------------------------------------------------------
 Copyright (c) CovertJaguar, 2011-2017
 http://railcraft.info

 This code is the property of CovertJaguar
 and may only be used with explicit written
 permission unless otherwise specified on the
 license page at http://railcraft.info/wiki/info:license.
 -----------------------------------------------------------------------------*/
package mods.railcraft.common.carts;

import mods.railcraft.api.carts.CartToolsAPI;
import mods.railcraft.api.carts.ILinkableCart;
import mods.railcraft.api.carts.ILinkageManager;
import mods.railcraft.common.core.RailcraftConfig;
import mods.railcraft.common.util.misc.Game;
import net.minecraft.entity.item.EntityMinecart;
import org.apache.logging.log4j.Level;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The LinkageManager contains all the functions needed to link and interacted
 * with linked carts.
 * <p/>
 * One concept if import is that of the Linkage Id. Every cart is given a unique
 * identifier by the LinkageManager the first time it encounters the cart.
 * <p/>
 * This identifier is stored in the entity's NBT data between world loads so
 * that links are persistent rather than transitory.
 * <p/>
 * Links are also stored in NBT data as an Integer value that contains the
 * Linkage Id of the cart it is linked to.
 * <p/>
 * Generally you can ignore most of this and use the functions that don't
 * require or return Linkage Ids.
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public class LinkageManager implements ILinkageManager {
    public static final String AUTO_LINK = "rcAutoLink";
    public static final String LINK_A_HIGH = "rcLinkAHigh";
    public static final String LINK_A_LOW = "rcLinkALow";
    public static final String LINK_B_HIGH = "rcLinkBHigh";
    public static final String LINK_B_LOW = "rcLinkBLow";

    private LinkageManager() {
    }

    /**
     * Return an instance of the LinkageManager
     *
     * @return LinkageManager
     */
    public static LinkageManager instance() {
        return (LinkageManager) CartToolsAPI.linkageManager;
    }

    public static void printDebug(String msg, Object... args) {
        if (RailcraftConfig.printLinkingDebug())
            Game.log(Level.DEBUG, msg, args);
    }

    public static void reset() {
        CartToolsAPI.linkageManager = new LinkageManager();
    }

    /**
     * Returns the linkage id of the cart and adds the cart the linkage cache.
     *
     * @param cart The EntityMinecart
     * @return The linkage id
     */
    public UUID getLinkageId(EntityMinecart cart) {
        return cart.getPersistentID();
    }

    @Nullable
    @Override
    @Deprecated
    public EntityMinecart getCartFromUUID(UUID id) {
        return null;
    }

    /**
     * Returns the square of the max distance two carts can be and still be
     * linkable.
     *
     * @param cart1 First Cart
     * @param cart2 Second Cart
     * @return The square of the linkage distance
     */
    private float getLinkageDistanceSq(EntityMinecart cart1, EntityMinecart cart2) {
        float dist = 0;
        if (cart1 instanceof ILinkableCart)
            dist += ((ILinkableCart) cart1).getLinkageDistance(cart2);
        else
            dist += LINKAGE_DISTANCE;
        if (cart2 instanceof ILinkableCart)
            dist += ((ILinkableCart) cart2).getLinkageDistance(cart1);
        else
            dist += LINKAGE_DISTANCE;
        return dist * dist;
    }

    @Override
    public boolean setAutoLink(EntityMinecart cart, boolean autoLink) {
        if (autoLink && hasFreeLink(cart)) {
            cart.getEntityData().setBoolean(AUTO_LINK, true);
            printDebug("Cart {0}({1}) Set To Auto Link With First Collision.", getLinkageId(cart), cart);
            return true;
        }
        if (!autoLink) {
            cart.getEntityData().removeTag(AUTO_LINK);
            return true;
        }
        return false;
    }

    @Override
    public boolean hasAutoLink(EntityMinecart cart) {
        if (!hasFreeLink(cart))
            cart.getEntityData().removeTag(AUTO_LINK);
        return cart.getEntityData().getBoolean(AUTO_LINK);
    }

    @Override
    public boolean tryAutoLink(EntityMinecart cart1, EntityMinecart cart2) {
        if ((hasAutoLink(cart1) || hasAutoLink(cart2))
                && createLink(cart1, cart2)) {
            cart1.getEntityData().removeTag(LinkageManager.AUTO_LINK);
            cart2.getEntityData().removeTag(LinkageManager.AUTO_LINK);
            printDebug("Automatically Linked Carts {0}({1}) and {2}({3}).", getLinkageId(cart1), cart1, getLinkageId(cart2), cart2);
            if (cart1 instanceof EntityLocomotive) {
                ((EntityLocomotive) cart1).setSpeed(EntityLocomotive.LocoSpeed.SLOWEST);
            }
            if (cart2 instanceof EntityLocomotive) {
                ((EntityLocomotive) cart2).setSpeed(EntityLocomotive.LocoSpeed.SLOWEST);
            }
            return true;
        }
        return false;
    }

    /**
     * Returns true if there is nothing preventing the two carts from being
     * linked.
     *
     * @param cart1 First Cart
     * @param cart2 Second Cart
     * @return True if can be linked
     */
    private boolean canLinkCarts(EntityMinecart cart1, EntityMinecart cart2) {
        if (cart1 == null || cart2 == null)
            return false;

        if (cart1 == cart2)
            return false;

        if (cart1 instanceof ILinkableCart) {
            ILinkableCart link = (ILinkableCart) cart1;
            if (!link.isLinkable() || !link.canLinkWithCart(cart2))
                return false;
        }

        if (cart2 instanceof ILinkableCart) {
            ILinkableCart link = (ILinkableCart) cart2;
            if (!link.isLinkable() || !link.canLinkWithCart(cart1))
                return false;
        }

        if (areLinked(cart1, cart2))
            return false;

        if (cart1.getDistanceSq(cart2) > getLinkageDistanceSq(cart1, cart2))
            return false;

        if (Train.areInSameTrain(cart1, cart2))
            return false;

        return hasFreeLink(cart1) && hasFreeLink(cart2);
    }

    /**
     * Creates a link between two carts, but only if there is nothing preventing
     * such a link.
     *
     * @param cart1 First Cart
     * @param cart2 Second Cart
     * @return True if the link succeeded.
     */
    @Override
    public boolean createLink(EntityMinecart cart1, EntityMinecart cart2) {
        if (canLinkCarts(cart1, cart2)) {
            Train train = Train.getLongestTrain(cart1, cart2);

            setLink(cart1, cart2);
            setLink(cart2, cart1);

            train.rebuild(cart1);

            if (cart1 instanceof ILinkableCart)
                ((ILinkableCart) cart1).onLinkCreated(cart2);
            if (cart2 instanceof ILinkableCart)
                ((ILinkableCart) cart2).onLinkCreated(cart1);

//            sendLinkInfo(cart1);
//            sendLinkInfo(cart2);
            return true;
        }
        return false;
    }

    @Override
    public boolean hasFreeLink(EntityMinecart cart) {
        return getLinkedCartA(cart) == null || (hasLink(cart, LinkType.LINK_B) && getLinkedCartB(cart) == null);
    }

    public boolean hasLink(EntityMinecart cart, LinkType linkType) {
        if (linkType == LinkType.LINK_B && cart instanceof ILinkableCart)
            return ((ILinkableCart) cart).hasTwoLinks();
        return true;
    }

    private boolean setLink(EntityMinecart cart1, EntityMinecart cart2) {
        if (getLinkedCartA(cart1) == null) {
            setLink(cart1, cart2, LinkType.LINK_A);
            return true;
        } else if (hasLink(cart1, LinkType.LINK_B) && getLinkedCartB(cart1) == null) {
            setLink(cart1, cart2, LinkType.LINK_B);
            return true;
        }
        return false;
    }

    public UUID getLink(EntityMinecart cart, LinkType linkType) {
        long high = cart.getEntityData().getLong(linkType.tagHigh);
        long low = cart.getEntityData().getLong(linkType.tagLow);
        return new UUID(high, low);
    }

    public UUID getLinkA(EntityMinecart cart) {
        return getLink(cart, LinkType.LINK_A);
    }

    public UUID getLinkB(EntityMinecart cart) {
        return getLink(cart, LinkType.LINK_B);
    }

    private void setLink(EntityMinecart cart1, EntityMinecart cart2, LinkType linkType) {
        if (!hasLink(cart1, linkType))
            return;
        UUID id = getLinkageId(cart2);
        cart1.getEntityData().setLong(linkType.tagHigh, id.getMostSignificantBits());
        cart1.getEntityData().setLong(linkType.tagLow, id.getLeastSignificantBits());
    }

    /**
     * Returns the cart linked to LinkType A or null if nothing is currently
     * occupying LinkType A.
     *
     * @param cart The cart for which to get the link
     * @return The linked cart or null
     */
    @Override
    public EntityMinecart getLinkedCartA(EntityMinecart cart) {
        return getLinkedCart(cart, LinkType.LINK_A);
    }

    /**
     * Returns the cart linked to LinkType B or null if nothing is currently
     * occupying LinkType B.
     *
     * @param cart The cart for which to get the link
     * @return The linked cart or null
     */
    @Override
    public EntityMinecart getLinkedCartB(EntityMinecart cart) {
        return getLinkedCart(cart, LinkType.LINK_B);
    }

    @Nullable
    public EntityMinecart getLinkedCart(EntityMinecart cart, LinkType type) {
        return CartTools.getCartFromUUID(cart.world, getLink(cart, type));
    }

    /**
     * Returns true if the two carts are linked directly to each other.
     *
     * @param cart1 First Cart
     * @param cart2 Second Cart
     * @return True if linked
     */
    @Override
    public boolean areLinked(EntityMinecart cart1, EntityMinecart cart2) {
        return areLinked(cart1, cart2, true);
    }

    /**
     * Returns true if the two carts are linked directly to each other.
     *
     * @param cart1  First Cart
     * @param cart2  Second Cart
     * @param strict true if both carts should have linking data pointing to the other cart,
     *               false if its ok if only one cart has the data (this is technically an invalid state, but its been known to happen)
     * @return True if linked
     */
    public boolean areLinked(EntityMinecart cart1, EntityMinecart cart2, boolean strict) {
        if (cart1 == null || cart2 == null)
            return false;
        if (cart1 == cart2)
            return false;

//        System.out.println("cart2 id = " + getLinkageId(cart2));
//        System.out.println("cart2 A = " + getLinkA(cart2));
//        System.out.println("cart2 B = " + getLinkB(cart2));
//        System.out.println("cart1 id = " + getLinkageId(cart1));
//        System.out.println("cart1 A = " + getLinkA(cart1));
//        System.out.println("cart1 B = " + getLinkB(cart1));
        boolean cart1Linked = false;
        UUID id1 = getLinkageId(cart1);
        UUID id2 = getLinkageId(cart2);
        if (id2.equals(getLinkA(cart1)) || id2.equals(getLinkB(cart1)))
            cart1Linked = true; //            System.out.println("cart1 linked");

        boolean cart2Linked = false;
        if (id1.equals(getLinkA(cart2)) || id1.equals(getLinkB(cart2)))
            cart2Linked = true; //            System.out.println("cart2 linked");

        if (strict)
            return cart1Linked && cart2Linked;
        else
            return cart1Linked || cart2Linked;
    }

    /**
     * Breaks a link between two carts, if any link exists.
     *
     * @param cart1 First Cart
     * @param cart2 Second Cart
     */
    @Override
    public void breakLink(EntityMinecart cart1, EntityMinecart cart2) {
//        if (!areLinked(cart1, cart2))
//            return;

        UUID link = getLinkageId(cart2);
        if (link.equals(getLinkA(cart1)))
            breakLinkA(cart1);

        if (link.equals(getLinkB(cart1)))
            breakLinkB(cart1);

//        int numCarts1 = numLinkedCarts(null, cart1);
//        int numCarts2 = numLinkedCarts(null, cart2);
//        if (numCarts1 >= numCarts2)
//            Train.getTrain(cart1).removeAllLinkedCarts(cart2);
//        else
//            Train.getTrain(cart2).removeAllLinkedCarts(cart1);
    }

    /**
     * Repairs an asymmetrical link between carts
     *
     * @param cart1 First Cart
     * @param cart2 Second Cart
     * @return true if the repair was successful.
     */
    public boolean repairLink(EntityMinecart cart1, EntityMinecart cart2) {
        boolean repaired = _repairLink(cart1, cart2) && _repairLink(cart2, cart1);
        if (repaired)
            Train.repairTrain(cart1, cart2);
        else
            breakLink(cart1, cart2);
        return repaired;
    }

    public boolean _repairLink(EntityMinecart cart1, EntityMinecart cart2) {
        UUID link = getLinkageId(cart2);

        return link.equals(getLinkA(cart1)) || link.equals(getLinkB(cart1)) || setLink(cart1, cart2);
    }

    /**
     * Breaks all links the passed cart has.
     *
     * @param cart Cart
     */
    @Override
    public void breakLinks(EntityMinecart cart) {
        breakLink(cart, LinkType.LINK_A);
        breakLink(cart, LinkType.LINK_B);
    }

    /**
     * Break only link A.
     *
     * @param cart Cart
     */
    @Override
    public void breakLinkA(EntityMinecart cart) {
        breakLink(cart, LinkType.LINK_A);
    }

    /**
     * Break only link B.
     *
     * @param cart Cart
     */
    @Override
    public void breakLinkB(EntityMinecart cart) {
        breakLink(cart, LinkType.LINK_B);
    }

    @Nullable
    private EntityMinecart breakLink(EntityMinecart cart, LinkType linkType) {
        Train.deleteTrain(cart);
        UUID link = getLink(cart, linkType);
        removeLinkTags(cart, linkType);
        EntityMinecart other = CartTools.getCartFromUUID(cart.world, link);
        if (other != null) {
            breakLink(other, cart);
        }
        if (cart instanceof ILinkableCart)
            ((ILinkableCart) cart).onLinkBroken(other);

        printDebug("Carts {0}({1}) and {2}({3}) unlinked ({4}).", getLinkageId(cart), cart, link, other != null ? other : "null", linkType.name());
        return other;
    }

    private void removeLinkTags(EntityMinecart cart, LinkType linkType) {
        cart.getEntityData().removeTag(linkType.tagHigh);
        cart.getEntityData().removeTag(linkType.tagLow);
    }

    /**
     * Counts how many carts are in the train.
     *
     * @param cart Any cart in the train
     * @return The number of carts in the train
     */
    @Override
    public int countCartsInTrain(EntityMinecart cart) {
        return Train.getTrain(cart).size();
    }

    private int numLinkedCarts(EntityMinecart prev, EntityMinecart next) {
        int count = 1;
        EntityMinecart linkA = getLinkedCartA(next);
        EntityMinecart linkB = getLinkedCartB(next);

        if (linkA != null && linkA != prev)
            count += numLinkedCarts(next, linkA);
        if (linkB != null && linkB != prev)
            count += numLinkedCarts(next, linkB);
        return count;
    }

    @Override
    public Iterable<EntityMinecart> trainIterator(EntityMinecart cart) {
        return Train.getTrain(cart);
    }

    public Iterable<EntityMinecart> linkIterator(final EntityMinecart start, final LinkType type) {
        return () -> new Iterator<EntityMinecart>() {
            private final LinkageManager lm = LinkageManager.instance();
            private EntityMinecart last;
            private EntityMinecart current = start;

            @Override
            public boolean hasNext() {
                if (last == null) {
                    EntityMinecart next = lm.getLinkedCart(current, type);
                    return next != null;
                }
                EntityMinecart next = lm.getLinkedCartA(current);
                if (next != null && next != last)
                    return true;
                next = lm.getLinkedCartB(current);
                return next != null && next != last;
            }

            @Override
            public EntityMinecart next() {
                if (last == null) {
                    EntityMinecart next = lm.getLinkedCart(current, type);
                    if (next == null)
                        throw new NoSuchElementException();
                    last = current;
                    current = next;
                    return current;
                }
                EntityMinecart next = lm.getLinkedCartA(current);
                if (next != null && next != last) {
                    last = current;
                    current = next;
                    return current;
                }
                next = lm.getLinkedCartB(current);
                if (next != null && next != last) {
                    last = current;
                    current = next;
                    return current;
                }
                throw new NoSuchElementException();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Removal not supported.");
            }
        };
    }

    public enum LinkType {
        LINK_A(LINK_A_HIGH, LINK_A_LOW),
        LINK_B(LINK_B_HIGH, LINK_B_LOW);
        public final String tagHigh, tagLow;

        LinkType(String tagHigh, String tagLow) {
            this.tagHigh = tagHigh;
            this.tagLow = tagLow;
        }
    }
}
